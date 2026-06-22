package be.panchito.pointRush.minigame.football;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.LobbyWorld;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.PlayerRespawnUtil;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.bossbar.BossBar;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PointRush "Voetbal"-event bovenop BlockBall.
 *
 * <p>BlockBall bezit de volledige in-arena gameplay (bal, doelen, scoren, team-spawns,
 * respawns). Deze klasse is de PointRush-orkestratielaag — net als de andere minigames:
 * verdeelt lobby-spelers over rood/blauw, laat ze de BlockBall-arena joinen, draait een
 * eigen wedstrijdklok + scoreboard, vertaalt BlockBall-goals naar PointRush-teampunten en
 * teleporteert iedereen na afloop terug naar zijn startlocatie ({@code plugin.getTeleporter()}).</p>
 */
public final class FootballGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int COUNTDOWN_SECONDS = 8;
    public static final int MIN_PLAYERS = 2;

    private final PointRush plugin;
    private final FootballConfig config;
    private final BlockBallBridge bridge;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final FootballScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, FootballPlayerState> players = new HashMap<>();

    private Object blockBallGame; // ondoorzichtige SoccerGame-handle
    private int lastRedScore = 0;
    private int lastBlueScore = 0;

    private long countdownEndsMs = 0L;
    private long matchEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public FootballGame(PointRush plugin, FootballConfig config, BlockBallBridge bridge) {
        this.plugin = plugin;
        this.config = config;
        this.bridge = bridge;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new FootballScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public FootballConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public FootballPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<FootballPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int getRedScore() {
        return lastRedScore;
    }

    public int getBlueScore() {
        return lastBlueScore;
    }

    public long getCountdownTimeLeftMs() {
        if (state != State.STARTING) return 0L;
        return Math.max(0L, countdownEndsMs - System.currentTimeMillis());
    }

    public long getMatchTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, matchEndsMs - System.currentTimeMillis());
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        if (!bridge.isAvailable()) {
            Bukkit.broadcast(Messages.error("BlockBall is niet geladen — Voetbal-event kan niet starten."));
            return false;
        }
        Object game = bridge.getGameByName(config.getArenaName());
        if (game == null) {
            Bukkit.broadcast(Messages.error("BlockBall-arena '" + config.getArenaName()
                    + "' bestaat niet. Maak hem aan met /blockball."));
            return false;
        }

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Voetbal (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            eligible.add(online);
        }

        if (eligible.size() < MIN_PLAYERS) {
            Bukkit.broadcast(Messages.error("Voetbal heeft minstens " + MIN_PLAYERS + " spelers nodig."));
            return false;
        }

        blockBallGame = game;
        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        historyRecorded = false;
        lastRedScore = 0;
        lastBlueScore = 0;

        Map<UUID, FootballSide> sides = FootballSideAssigner.assignSides(teamManager, eligible);

        // Start-locatie + gamemode NU vastleggen (vóór de join): BlockBall teleporteert de speler
        // tijdens de join naar de arena, dus achteraf is zijn lobby-positie weg. Zo kunnen we hem
        // na afloop met onze eigen teleport terugzetten.
        Map<UUID, FootballPlayerState> prepared = new HashMap<>();
        for (Player player : eligible) {
            FootballSide side = sides.getOrDefault(player.getUniqueId(), FootballSide.RED);
            FootballPlayerState ps = new FootballPlayerState(
                    player.getUniqueId(), player.getLocation().clone(), player.getGameMode());
            ps.setSide(side);
            prepared.put(player.getUniqueId(), ps);
        }

        // BlockBall's join() vuurt GameJoinEvent, en álle BlockBall-events zijn async (Event(true)).
        // De join-calls moeten dus van een async thread komen; BlockBall regelt zelf de main-thread
        // teleport naar de arena. Daarna hoppen we terug naar de main thread om af te ronden.
        final List<Player> toJoin = new ArrayList<>(eligible);
        final Object game0 = blockBallGame;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> joinedIds = new ArrayList<>();
            for (Player player : toJoin) {
                FootballPlayerState ps = prepared.get(player.getUniqueId());
                if (ps == null) continue;
                if (bridge.join(game0, player, ps.getSide().getBlockBallTeam())) {
                    joinedIds.add(player.getUniqueId());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> finalizeStart(game0, prepared, joinedIds));
        });

        MinigameStartEffects.onStarted(plugin, "football");
        return true;
    }

    /** Loopt op de main thread nadat de async join-calls klaar zijn. */
    private void finalizeStart(Object game, Map<UUID, FootballPlayerState> prepared, List<UUID> joinedIds) {
        if (state != State.STARTING || game != blockBallGame) {
            // Event werd intussen gestopt — laat de eventueel gejoinde spelers de arena weer verlaten.
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                for (UUID id : joinedIds) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) bridge.leave(game, p);
                }
                bridge.closeGame(game);
            });
            return;
        }

        for (UUID id : joinedIds) {
            FootballPlayerState ps = prepared.get(id);
            Player player = Bukkit.getPlayer(id);
            if (ps == null || player == null) continue;
            players.put(id, ps);
            scoreboard.attach(player);
            player.sendMessage(Messages.info("Je speelt voetbal voor team "
                    + ps.getSide().getDisplayName() + "!"));
        }
        for (UUID id : prepared.keySet()) {
            if (!joinedIds.contains(id)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendMessage(Messages.warn("Kon je niet aan de voetbal-arena toevoegen."));
                }
            }
        }

        if (players.size() < MIN_PLAYERS) {
            Bukkit.broadcast(Messages.error("Te weinig spelers konden de arena joinen — Voetbal afgebroken."));
            cleanupAfterStop();
            return;
        }

        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        scoreboard.start();
        scoreboard.updateBossBar("aftrap...", 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("VOETBAL"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("rood vs blauw · scoor in het doel"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        endEvent(false);
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case STARTING -> tickStarting(now);
            case RUNNING -> tickRunning(now);
            default -> {
            }
        }
    }

    private void tickStarting(long now) {
        long left = Math.max(0L, countdownEndsMs - now);
        float progress = left / (float) (COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateBossBar("aftrap over " + formatTime(left), progress, BossBar.Color.YELLOW);
        if (left <= 0) {
            beginMatch();
        }
    }

    private void beginMatch() {
        state = State.RUNNING;
        matchEndsMs = System.currentTimeMillis() + config.getDurationMs();
        lastRedScore = bridge.getRedScore(blockBallGame);
        lastBlueScore = bridge.getBlueScore(blockBallGame);
        broadcastTitle(
                Component.text(SmallText.of("AFTRAP!"), NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(SmallText.of("scoor zoveel mogelijk goals"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
    }

    private void tickRunning(long now) {
        pollScores();

        // BlockBall kan de wedstrijd zelf beëindigen (eigen arena-timer) — als (bijna) niemand meer
        // in de arena zit, ronden we ook af.
        if (countParticipantsInArena() < MIN_PLAYERS) {
            endEvent(true);
            return;
        }

        if (now >= matchEndsMs) {
            endEvent(true);
            return;
        }

        long left = Math.max(0L, matchEndsMs - now);
        float progress = Math.min(1f, left / (float) config.getDurationMs());
        String label = "rood " + lastRedScore + " · " + lastBlueScore + " blauw · " + formatTime(left);
        scoreboard.updateBossBar(label, progress, BossBar.Color.GREEN);
    }

    /** Leest de BlockBall-stand en kent punten toe wanneer een team scoort. */
    private void pollScores() {
        int red = bridge.getRedScore(blockBallGame);
        int blue = bridge.getBlueScore(blockBallGame);

        if (red > lastRedScore) {
            onGoal(FootballSide.RED, red - lastRedScore);
        }
        if (blue > lastBlueScore) {
            onGoal(FootballSide.BLUE, blue - lastBlueScore);
        }
        lastRedScore = red;
        lastBlueScore = blue;
    }

    private void onGoal(FootballSide side, int goals) {
        int points = config.getPointsPerGoal() * Math.max(1, goals);
        if (points > 0) {
            awardSide(side, points);
        }
        Component msg = Component.text()
                .append(Component.text(side.getDisplayName(), side.getTextColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" scoort! "), NamedTextColor.GOLD))
                .append(Component.text(lastRedScore + (side == FootballSide.RED ? goals : 0)
                        + " - " + (lastBlueScore + (side == FootballSide.BLUE ? goals : 0)), NamedTextColor.WHITE))
                .build();
        broadcastToParticipants(msg);
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    /** Kent punten toe aan alle PointRush-teams aan de gegeven kant (per team één keer). */
    private void awardSide(FootballSide side, int points) {
        Set<UUID> awardedTeams = new HashSet<>();
        for (FootballPlayerState ps : players.values()) {
            if (ps.getSide() != side) continue;
            ps.addPointsEarned(points);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null && awardedTeams.add(team.getId())) {
                dataManager.addTeamPoints(team, points);
            }
        }
    }

    private int countParticipantsInArena() {
        if (blockBallGame == null) return 0;
        Set<Player> inArena = bridge.getPlayers(blockBallGame);
        int count = 0;
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && inArena.contains(p)) {
                count++;
            }
        }
        return count;
    }

    private FootballSide determineWinner() {
        if (lastRedScore > lastBlueScore) return FootballSide.RED;
        if (lastBlueScore > lastRedScore) return FootballSide.BLUE;
        return null;
    }

    private void endEvent(boolean natural) {
        if (state == State.IDLE) return;

        pollScores();
        FootballSide winner = determineWinner();
        if (natural) {
            if (winner != null) {
                if (config.getWinBonus() > 0) {
                    awardSide(winner, config.getWinBonus());
                }
                broadcastTitle(
                        Component.text(SmallText.of("VOETBAL AFGELOPEN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text()
                                .append(Component.text(winner.getDisplayName(), winner.getTextColor(), TextDecoration.BOLD))
                                .append(Component.text(SmallText.of(" wint "), NamedTextColor.GRAY))
                                .append(Component.text(lastRedScore + " - " + lastBlueScore, NamedTextColor.WHITE))
                                .build()
                );
            } else {
                broadcastTitle(
                        Component.text(SmallText.of("VOETBAL AFGELOPEN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(SmallText.of("gelijkspel " + lastRedScore + " - " + lastBlueScore),
                                NamedTextColor.GRAY)
                );
            }
            playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }

        recordHistoryEntry(winner);
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Voetbal-event afgelopen."));
    }

    public void removeParticipant(Player player, boolean teleport) {
        FootballPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;

        scoreboard.detach(player);

        // BlockBall leave() vuurt een async-only event; daarna (op de main thread, met een paar ticks
        // marge zodat BlockBall's eigen leave-teleport eerst klaar is) zetten we de speler terug.
        if (player.isOnline()) {
            final Object game = blockBallGame;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                bridge.leave(game, player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        restorePlayer(player, ps, teleport);
                    }
                }, 3L);
            });
        }

        if (players.size() < MIN_PLAYERS && state != State.IDLE) {
            endEvent(state == State.RUNNING);
        }
    }

    private void cleanupAfterStop() {
        if (state == State.IDLE && players.isEmpty() && tickTask == null) {
            return;
        }
        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();

        // Snapshot maken vóór we async werk inplannen — daarna is de live staat alweer gereset.
        final List<FootballPlayerState> snapshot = new ArrayList<>(players.values());
        final Object game = blockBallGame;
        players.clear();
        blockBallGame = null;
        lastRedScore = 0;
        lastBlueScore = 0;

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (FootballPlayerState ps : snapshot) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) p.showTitle(endTitle);
        }

        // Alle BlockBall-mutaties (leave/close vuren async-only events) op een async thread; de
        // terug-teleport een paar ticks later op de main thread, ná BlockBall's eigen leave-teleport.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (FootballPlayerState ps : snapshot) {
                Player p = Bukkit.getPlayer(ps.getUuid());
                if (p != null) bridge.leave(game, p);
            }
            bridge.closeGame(game);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (FootballPlayerState ps : snapshot) {
                    Player p = Bukkit.getPlayer(ps.getUuid());
                    if (p != null) restorePlayer(p, ps, true);
                }
            }, 3L);
        });
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void restorePlayer(Player player, FootballPlayerState ps, boolean teleport) {
        PlayerRespawnUtil.prepareForRestore(player);
        clearPotionEffects(player);
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setHealth(20.0);

        if (teleport && ps.getSavedLocation() != null && ps.getSavedLocation().getWorld() != null) {
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
    }

    private void clearPotionEffects(Player player) {
        for (org.bukkit.potion.PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(eff.getType());
        }
    }

    private void recordHistoryEntry(FootballSide winner) {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<FootballPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int scoreA = a.getSide() == FootballSide.RED ? lastRedScore : lastBlueScore;
            int scoreB = b.getSide() == FootballSide.RED ? lastRedScore : lastBlueScore;
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            return Integer.compare(b.getPointsEarned(), a.getPointsEarned());
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (FootballPlayerState ps : sorted) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int sideScore = ps.getSide() == FootballSide.RED ? lastRedScore : lastBlueScore;
            boolean won = winner != null && ps.getSide() == winner;
            String detail = ps.getSide().getDisplayName() + " · " + sideScore + " goals"
                    + (won ? " · winnaar" : "");
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : ps.getSide().getTextColor().toString(),
                    ps.getPointsEarned(),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "football",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastToParticipants(Component msg) {
        Component full = Messages.PREFIX.append(msg);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(full);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
