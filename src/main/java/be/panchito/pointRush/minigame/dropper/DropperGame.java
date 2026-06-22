package be.panchito.pointRush.minigame.dropper;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.LobbyWorld;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.PlayerRespawnUtil;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dropper-minigame: spring je vanaf de top correct in de finish-zone (het waterbad),
 * dan ga je door naar de volgende ronde. Mis je het water, dan respawn je bovenaan om
 * opnieuw te proberen. Wie een ronde niet binnen de tijd voltooit, valt af.
 *
 * <p>Verloop:
 * <ul>
 *     <li>{@link State#IDLE} — geen event.</li>
 *     <li>{@link State#STARTING} — spelers staan klaar, countdown loopt.</li>
 *     <li>{@link State#RUNNING} — een ronde loopt: voltooien = door, time-out = uit.</li>
 * </ul>
 *
 * <p>Per ronde krijgt iedere nog levende speler {@link DropperConfig#getRoundSeconds()}
 * seconden om de finish te halen. Voltooiers gaan naar de volgende ronde; wie de tijd niet
 * haalt wordt uitgeschakeld (placement in uitschakel-volgorde). De laatste overlevende(n)
 * winnen; haalt iedereen de laatste ronde, dan delen ze de winst.
 */
public final class DropperGame {

    public enum State { IDLE, STARTING, RUNNING }

    /**
     * Punten voor uitschakel-plaatsen 1..10 — dezelfde schaal als TNT Run (helft van parkour),
     * zodat de events in verhouding blijven.
     */
    public static final int[] PLACEMENT_POINTS = { 50, 40, 30, 25, 20, 15, 12, 10, 8, 5 };

    /** Win-bonus voor het laatste team — één keer per team, ~100 zoals de andere events. */
    public static final int WIN_TEAM_BONUS = 100;

    public static final int COUNTDOWN_SECONDS = 10;
    /** Pauze (ticks) tussen het einde van een ronde en de start van de volgende. */
    public static final long ROUND_INTERMISSION_TICKS = 60L;

    private final PointRush plugin;
    private final DropperConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final DropperScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, DropperPlayerState> players = new HashMap<>();

    private int currentRoundIndex = -1;
    /** Wall-clock ms waarop de huidige ronde afloopt (voor de scoreboard-timer). */
    private long roundEndsAtMs = 0L;
    private long eventStartedAtMs = 0L;
    private int nextEliminationPlacement = 0;
    private boolean historyRecorded = false;

    private BukkitTask countdownTask;
    private BukkitTask roundTimerTask;

    public DropperGame(PointRush plugin, DropperConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new DropperScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public DropperConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public DropperPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<DropperPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** 1-gebaseerd rondenummer voor weergave (0 zolang er nog geen ronde loopt). */
    public int getCurrentRoundNumber() {
        return currentRoundIndex + 1;
    }

    public int getTotalRounds() {
        return config.getRounds().size();
    }

    /** Resterende seconden in de huidige ronde (0 wanneer geen ronde loopt). */
    public int getRoundSecondsLeft() {
        if (state != State.RUNNING || roundEndsAtMs <= 0L) return 0;
        long ms = roundEndsAtMs - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) ((ms + 999L) / 1000L);
    }

    public int aliveCount() {
        int n = 0;
        for (DropperPlayerState ps : players.values()) {
            if (ps.isAlive()) n++;
        }
        return n;
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        currentRoundIndex = -1;
        roundEndsAtMs = 0L;
        nextEliminationPlacement = 0;
        historyRecorded = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Dropper (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("Dropper heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        // Eerste uitschakeling krijgt de laagste rang (= aantal spelers).
        nextEliminationPlacement = players.size();
        scoreboard.start();
        broadcastTitle(
                Component.text(SmallText.of("DROPPER"), NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text(SmallText.of("spring in het water!"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        startCountdown();
        MinigameStartEffects.onStarted(plugin);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Dropper event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(countdownTask); countdownTask = null;
        cancelTask(roundTimerTask); roundTimerTask = null;
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, DropperPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        currentRoundIndex = -1;
        roundEndsAtMs = 0L;
        nextEliminationPlacement = 0;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    private void joinPlayer(Player player) {
        DropperPlayerState ps = new DropperPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode()
        );
        players.put(player.getUniqueId(), ps);

        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        scoreboard.attach(player);
        player.sendMessage(Messages.info("Dropper start binnenkort - maak je klaar!"));

        Location spawn = config.getSpawn();
        if (spawn != null) {
            plugin.getTeleporter().teleport(player, spawn, false, () -> {
                if (player.isOnline() && players.containsKey(player.getUniqueId())) {
                    player.getInventory().clear();
                }
            });
        } else {
            player.getInventory().clear();
        }
    }

    private void startCountdown() {
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancelTask(countdownTask);
                    countdownTask = null;
                    return;
                }
                if (remaining > 0) {
                    broadcastActionBar("start in " + remaining + "s");
                    if (remaining <= 5) {
                        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    remaining--;
                } else {
                    cancelTask(countdownTask);
                    countdownTask = null;
                    beginRound(0);
                }
            }
        }, 0L, 20L);
    }

    /**
     * Start ronde {@code index}: alle levende spelers naar de top, completed-vlag reset,
     * ronde-timer (her)start.
     */
    private void beginRound(int index) {
        List<DropperRound> rounds = config.getRounds();
        if (index < 0 || index >= rounds.size()) {
            // Geen rondes meer — overlevenden winnen.
            finishGame();
            return;
        }
        state = State.RUNNING;
        currentRoundIndex = index;
        DropperRound round = rounds.get(index);
        roundEndsAtMs = System.currentTimeMillis() + config.getRoundSeconds() * 1000L;

        for (DropperPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            ps.setCompletedCurrentRound(false);
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            sendToTop(p, round);
            p.showTitle(Title.title(
                    Component.text(SmallText.of("RONDE " + (index + 1) + " / " + rounds.size()),
                            NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text(SmallText.of(config.getRoundSeconds() + "s om in het water te landen"),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(300))
            ));
        }
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);

        roundTimerTask = Bukkit.getScheduler().runTaskLater(plugin, this::onRoundTimeout, config.getRoundTicks());
    }

    /** Teleporteert een speler naar de top van de ronde en reset val-state. */
    private void sendToTop(Player player, DropperRound round) {
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.setHealth(20.0);
        plugin.getTeleporter().teleport(player, round.getTop());
        player.setFallDistance(0f);
    }

    /**
     * Aangeroepen vanuit de listener bij elke beweging. Detecteert of de speler in de
     * finish-zone van de huidige ronde is geland.
     */
    public void handleMove(Player player) {
        if (state != State.RUNNING) return;
        DropperPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.hasCompletedCurrentRound()) return;
        DropperRound round = currentRound();
        if (round == null) return;

        if (round.contains(player.getLocation())) {
            // Pas voltooid wanneer de speler écht het water/de grond raakt — niet al terwijl hij nog
            // door de (mogelijk ruim gemarkeerde) lucht boven de finish-zone valt.
            if (player.isInWater() || player.isOnGround()) {
                onComplete(player, ps, round);
            }
            return;
        }
        // Buiten de finish maar wel in water beland (decoy/verkeerd bad) = gemist → opnieuw bovenaan.
        if (player.isInWater()) {
            onFailedLanding(player);
        }
    }

    /**
     * Aangeroepen vanuit de listener wanneer een nog niet voltooide speler val-/void-schade
     * oploopt: hij miste het water en mag bovenaan opnieuw proberen tot de tijd op is.
     */
    public void onFailedLanding(Player player) {
        if (state != State.RUNNING) return;
        DropperPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.hasCompletedCurrentRound()) return;
        DropperRound round = currentRound();
        if (round == null) return;
        sendToTop(player, round);
        player.sendActionBar(Messages.warn("Mis! Probeer opnieuw."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 0.8f);
    }

    private DropperRound currentRound() {
        List<DropperRound> rounds = config.getRounds();
        if (currentRoundIndex < 0 || currentRoundIndex >= rounds.size()) return null;
        return rounds.get(currentRoundIndex);
    }

    private void onComplete(Player player, DropperPlayerState ps, DropperRound round) {
        ps.setCompletedCurrentRound(true);
        ps.incrementRoundsCompleted();

        player.showTitle(Title.title(
                Component.text(SmallText.of("VOLTOOID!"), NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(SmallText.of("wacht op de volgende ronde"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))
        ));
        player.sendMessage(Messages.success("Je voltooide ronde " + (currentRoundIndex + 1) + "!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);

        Location spawn = config.getSpawn();
        if (spawn != null) {
            player.setFallDistance(0f);
            plugin.getTeleporter().teleport(player, spawn);
            player.setFallDistance(0f);
        }

        // Iedereen klaar? Dan de ronde meteen afronden i.p.v. de timer uit te zitten.
        if (allAliveCompleted()) {
            endRound();
        }
    }

    private boolean allAliveCompleted() {
        for (DropperPlayerState ps : players.values()) {
            if (ps.isAlive() && !ps.hasCompletedCurrentRound()) {
                return false;
            }
        }
        return true;
    }

    private void onRoundTimeout() {
        if (state != State.RUNNING) return;
        endRound();
    }

    /**
     * Sluit de huidige ronde af: wie niet voltooide wordt uitgeschakeld, voltooiers gaan door.
     * Bepaalt daarna of het event eindigt of de volgende ronde start.
     */
    private void endRound() {
        cancelTask(roundTimerTask);
        roundTimerTask = null;
        roundEndsAtMs = 0L;

        // Uitschakelen wie de ronde niet haalde (in willekeurige maar stabiele volgorde).
        List<DropperPlayerState> failed = new ArrayList<>();
        for (DropperPlayerState ps : players.values()) {
            if (ps.isAlive() && !ps.hasCompletedCurrentRound()) {
                failed.add(ps);
            }
        }
        for (DropperPlayerState ps : failed) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            eliminate(ps, p);
        }

        // Win-conditie evalueren.
        List<DropperPlayerState> survivors = aliveStates();
        boolean lastRound = currentRoundIndex >= config.getRounds().size() - 1;
        if (survivors.size() <= 1 || lastRound) {
            finishGame();
            return;
        }

        // Korte pauze, dan volgende ronde.
        int next = currentRoundIndex + 1;
        state = State.STARTING; // bevriest moves/landingen tussen rondes
        broadcastTitle(
                Component.text(SmallText.of("VOLGENDE RONDE"), NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text(SmallText.of(survivors.size() + " spelers door"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        roundTimerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.STARTING) {
                beginRound(next);
            }
        }, ROUND_INTERMISSION_TICKS);
    }

    private List<DropperPlayerState> aliveStates() {
        List<DropperPlayerState> alive = new ArrayList<>();
        for (DropperPlayerState ps : players.values()) {
            if (ps.isAlive()) alive.add(ps);
        }
        return alive;
    }

    /** Schakelt één speler uit, kent placement + punten toe en zet hem in spectator. */
    private void eliminate(DropperPlayerState ps, Player player) {
        if (!ps.isAlive()) return;
        ps.setAlive(false);
        ps.setEliminatedAtMs(System.currentTimeMillis());
        ps.setPlacement(nextEliminationPlacement);
        if (nextEliminationPlacement > 0) nextEliminationPlacement--;

        int pts = pointsForPlacement(ps.getPlacement());
        Team team = teamManager.getTeamOfPlayer(ps.getUuid());
        if (team != null && pts > 0) {
            dataManager.addTeamPoints(team, pts);
        }

        String name = player != null ? player.getName() : ps.getUuid().toString().substring(0, 8);
        Component msg;
        if (team != null) {
            msg = Component.text()
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" valt af als #" + ps.getPlacement()
                            + " (+" + pts + " pts) - team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                    .build();
        } else {
            msg = Component.text()
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" valt af als #" + ps.getPlacement()
                            + " (geen team, geen punten)"), NamedTextColor.GRAY))
                    .build();
        }
        Bukkit.broadcast(Messages.PREFIX.append(msg));

        if (player != null) {
            player.showTitle(Title.title(
                    Component.text(SmallText.of("uitgeschakeld"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("#" + ps.getPlacement() + " · +" + pts + " pts"),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
            ));
            player.setGameMode(GameMode.SPECTATOR);
            if (config.getSpawn() != null) {
                plugin.getTeleporter().teleport(player, config.getSpawn());
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.5f);
        }
    }

    private int pointsForPlacement(int placement) {
        if (placement < 1 || placement > PLACEMENT_POINTS.length) return 0;
        return PLACEMENT_POINTS[placement - 1];
    }

    /** Rondt het event af: overlevenden delen plaats #1 + win-bonus per team. */
    private void finishGame() {
        cancelTask(roundTimerTask);
        roundTimerTask = null;
        roundEndsAtMs = 0L;

        List<DropperPlayerState> survivors = aliveStates();
        Set<UUID> winningTeams = new HashSet<>();
        for (DropperPlayerState ps : survivors) {
            ps.setPlacement(1);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null) winningTeams.add(team.getId());
        }
        for (UUID teamId : winningTeams) {
            Team team = teamManager.getTeam(teamId);
            if (team != null) {
                dataManager.addTeamPoints(team, WIN_TEAM_BONUS);
            }
        }

        announceWinner(survivors);
        recordHistoryEntry();
        state = State.STARTING; // bevries verdere acties
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("Dropper event afgelopen."));
            }
        }, 60L);
    }

    private void announceWinner(List<DropperPlayerState> survivors) {
        Component winnerLine;
        if (survivors.isEmpty()) {
            winnerLine = Component.text(SmallText.of("iedereen viel af - gelijkspel"), NamedTextColor.GRAY);
        } else if (survivors.size() == 1) {
            UUID solo = survivors.get(0).getUuid();
            Player p = Bukkit.getPlayer(solo);
            String name = p != null ? p.getName() : solo.toString().substring(0, 8);
            Team t = teamManager.getTeamOfPlayer(solo);
            var b = Component.text()
                    .append(Component.text(name, t != null ? t.getColor() : NamedTextColor.AQUA,
                            TextDecoration.BOLD));
            if (t != null) {
                b.append(Component.text(SmallText.of("  (team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(t.getName()), t.getColor()))
                        .append(Component.text(")", NamedTextColor.GRAY));
            }
            b.append(Component.text(SmallText.of(" wint!"), NamedTextColor.GRAY));
            winnerLine = b.build();
        } else {
            winnerLine = Component.text()
                    .append(Component.text(SmallText.of(survivors.size() + " spelers"),
                            NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" overleefden alle rondes!"), NamedTextColor.GRAY))
                    .build();
        }

        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text(SmallText.of("EVENT VOORBIJ"), NamedTextColor.AQUA, TextDecoration.BOLD),
                    winnerLine
            ));
        }
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
    }

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<DropperPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int ap = a.getPlacement();
            int bp = b.getPlacement();
            if (ap == 0 && bp == 0) return 0;
            if (ap == 0) return 1;
            if (bp == 0) return -1;
            return Integer.compare(ap, bp);
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (DropperPlayerState ps : sorted) {
            if (ps.getPlacement() <= 0) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            String detail = ps.isAlive()
                    ? "overleefd (" + ps.getRoundsCompleted() + " rondes)"
                    : "uitgeschakeld (" + ps.getRoundsCompleted() + " rondes)";
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    ps.isAlive() ? WIN_TEAM_BONUS : pointsForPlacement(ps.getPlacement()),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "dropper",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    public void removeParticipant(Player player, boolean teleport) {
        DropperPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (state == State.RUNNING) {
            // Vertrek kan de ronde of het event beëindigen.
            if (allAliveCompleted() && aliveCount() > 0) {
                endRound();
            } else {
                List<DropperPlayerState> survivors = aliveStates();
                if (survivors.size() <= 1) {
                    finishGame();
                }
            }
        }
        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, DropperPlayerState ps, boolean teleport) {
        PlayerRespawnUtil.prepareForRestore(player);
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);

        if (!teleport || ps.getSavedLocation() == null || ps.getSavedLocation().getWorld() == null) {
            return;
        }
        try {
            plugin.getTeleporter().teleport(player, ps.getSavedLocation());
            player.setFallDistance(0f);
            player.sendActionBar(Messages.info("Terug naar je startlocatie."));
            player.playSound(ps.getSavedLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Kon speler " + player.getName()
                    + " niet terugteleporteren: " + ex.getMessage());
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastActionBar(String text) {
        Component c = Component.text(SmallText.of(text), NamedTextColor.AQUA);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(c);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
