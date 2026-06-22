package be.panchito.pointRush.minigame.finale;

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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finale — de grote afsluiter. Iedereen wordt willekeurig over een set spawn-locaties
 * verdeeld, krijgt een vooraf ingestelde kit, en staat dan {@code freezeSeconds} lang
 * bevroren op zijn plek. Na de aftel-vrijlating barst de FFA-PvP los (iedereen tegen iedereen,
 * ook teamgenoten): de laatste speler overeind wint. Wie sneuvelt blijft als spectator toekijken.
 *
 * <p>Verloop:
 * <ul>
 *     <li>{@link State#IDLE} — geen event.</li>
 *     <li>{@link State#STARTING} — iedereen bevroren op spawn, vrijlating-countdown loopt.</li>
 *     <li>{@link State#RUNNING} — eerst een grace-periode (looten, geen PvP), daarna FFA-gevecht
 *         met eliminaties tot er één speler over is.</li>
 *     <li>{@link State#ENDING} — winnaar in beeld, korte afsluit-pauze.</li>
 * </ul>
 */
public final class FinaleGame {

    public enum State { IDLE, STARTING, RUNNING, ENDING }

    /** Punten voor uitschakel-plaatsen 1..10 — iets ruimer dan de gewone events (dit is de finale). */
    public static final int[] PLACEMENT_POINTS = { 100, 70, 50, 40, 30, 25, 20, 15, 10, 5 };

    /** Win-bonus per overlevend team. */
    public static final int WIN_TEAM_BONUS = 150;

    /** Bonus per kill — extra punch voor agressief spel. */
    public static final int KILL_POINTS = 15;

    /** Een rake klap telt als kill-credit als het slachtoffer binnen dit venster valt. */
    private static final long KILL_CREDIT_WINDOW_MS = 10_000L;

    /** Korte afsluit-pauze (ticks) voordat iedereen terug-teleporteert. */
    private static final long END_DELAY_TICKS = 80L;

    private final PointRush plugin;
    private final FinaleConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final FinaleScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, FinalePlayerState> players = new HashMap<>();

    private long eventStartedAtMs = 0L;
    private long freezeEndsAtMs = 0L;
    private long graceEndsAtMs = 0L;
    private int nextEliminationPlacement = 0;
    private boolean historyRecorded = false;

    private BukkitTask freezeTask;
    private BukkitTask graceTask;

    private final List<FinaleLootDrop> activeDrops = new ArrayList<>();
    private BukkitTask lootSpawnTask;
    private BukkitTask lootTickTask;

    /** Door spelers gewijzigde blokken (key → oorspronkelijke staat) voor restore bij het einde. */
    private final Map<String, BlockState> originalBlocks = new HashMap<>();

    public FinaleGame(PointRush plugin, FinaleConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new FinaleScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public FinaleConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public FinalePlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<FinalePlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int aliveCount() {
        int n = 0;
        for (FinalePlayerState ps : players.values()) {
            if (ps.isAlive()) n++;
        }
        return n;
    }

    /** Resterende vrijlating-seconden tijdens de freeze (0 buiten de freeze). */
    public int getFreezeSecondsLeft() {
        if (state != State.STARTING || freezeEndsAtMs <= 0L) return 0;
        long ms = freezeEndsAtMs - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) ((ms + 999L) / 1000L);
    }

    /** True tijdens de grace-periode: spelers kunnen looten maar geen PvP doen. */
    public boolean isGracePeriod() {
        return state == State.RUNNING && graceEndsAtMs > 0L
                && System.currentTimeMillis() < graceEndsAtMs;
    }

    /** Resterende grace-seconden (0 buiten de grace-periode). */
    public int getGraceSecondsLeft() {
        if (!isGracePeriod()) return 0;
        long ms = graceEndsAtMs - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) ((ms + 999L) / 1000L);
    }

    // ---------------------------------------------------------------- start / stop

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        nextEliminationPlacement = 0;
        historyRecorded = false;

        List<Player> joining = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan de Finale (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            joining.add(online);
        }

        if (joining.size() < 2) {
            Bukkit.broadcast(Messages.error("De Finale heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        List<Location> assignment = buildSpawnAssignment(joining.size());
        for (int i = 0; i < joining.size(); i++) {
            joinPlayer(joining.get(i), assignment.get(i));
        }

        nextEliminationPlacement = players.size();
        scoreboard.start();

        broadcastTitle(
                Component.text(SmallText.of("DE FINALE"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("maak je klaar..."), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.1f);
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.0f);

        startFreeze();
        MinigameStartEffects.onStarted(plugin, "finale");
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Finale event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(freezeTask); freezeTask = null;
        cancelTask(graceTask); graceTask = null;
        stopLoot();
        restoreModifiedBlocks();
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, FinalePlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        freezeEndsAtMs = 0L;
        graceEndsAtMs = 0L;
        nextEliminationPlacement = 0;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    /** Bouwt een lijst van even verdeelde, willekeurig geschudde spawn-locaties (één per speler). */
    private List<Location> buildSpawnAssignment(int playerCount) {
        List<Location> spawns = new ArrayList<>(config.getSpawns());
        Collections.shuffle(spawns);
        List<Location> assignment = new ArrayList<>(playerCount);
        // Round-robin door de geschudde spawns zodat er bij meer spelers dan spawns
        // zo gelijk mogelijk verdeeld wordt.
        for (int i = 0; i < playerCount; i++) {
            assignment.add(spawns.get(i % spawns.size()).clone());
        }
        // Nog eens schudden zodat de round-robin-volgorde niet voorspelbaar is.
        Collections.shuffle(assignment);
        return assignment;
    }

    private void joinPlayer(Player player, Location spawn) {
        FinalePlayerState ps = new FinalePlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode()
        );
        ps.setSpawn(spawn);
        players.put(player.getUniqueId(), ps);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);

        scoreboard.attach(player);
        player.sendMessage(Messages.info("De Finale begint! Je staat zo bevroren op je spawn."));

        plugin.getTeleporter().teleport(player, spawn, false, () -> {
            if (player.isOnline() && players.containsKey(player.getUniqueId())) {
                applyKit(player);
                player.setFallDistance(0f);
                // Sterke slowness houdt de speler client-side op zijn plek (de move-lock doet
                // de rest); zo geen rubber-banding tijdens de freeze.
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60, 6, false, false, false));
            }
        });
    }

    private void applyKit(Player player) {
        if (config.hasKit()) {
            config.getKit().apply(player);
        } else {
            player.getInventory().clear();
            player.updateInventory();
        }
    }

    // ---------------------------------------------------------------- freeze

    private void startFreeze() {
        int freeze = config.getFreezeSeconds();
        freezeEndsAtMs = System.currentTimeMillis() + freeze * 1000L;
        freezeTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = freeze;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancelTask(freezeTask);
                    freezeTask = null;
                    return;
                }
                if (remaining > 0) {
                    Component sub = Component.text(SmallText.of("vrijgelaten in "), NamedTextColor.GRAY)
                            .append(Component.text(remaining + "s", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    for (UUID id : players.keySet()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p == null) continue;
                        p.sendActionBar(sub);
                        p.setHealth(Math.min(20.0, p.getHealth() + 1.0));
                        p.setFoodLevel(20);
                    }
                    if (remaining <= 5) {
                        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 0.8f + (5 - remaining) * 0.1f);
                        broadcastTitle(
                                Component.text(SmallText.of(String.valueOf(remaining)),
                                        NamedTextColor.YELLOW, TextDecoration.BOLD),
                                Component.text(SmallText.of("hou je vast..."), NamedTextColor.GRAY)
                        );
                    }
                    remaining--;
                } else {
                    cancelTask(freezeTask);
                    freezeTask = null;
                    beginCombat();
                }
            }
        }, 0L, 20L);
    }

    private void beginCombat() {
        state = State.RUNNING;
        freezeEndsAtMs = 0L;

        // Als er door quits tijdens de freeze nog maar één speler over is, meteen afronden.
        if (aliveCount() <= 1) {
            finishGame();
            return;
        }

        int grace = config.getGraceSeconds();
        boolean useGrace = grace > 0;
        graceEndsAtMs = useGrace ? System.currentTimeMillis() + grace * 1000L : 0L;

        for (FinalePlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            clearPotionEffects(p);
            p.setFallDistance(0f);
            if (useGrace) {
                p.showTitle(Title.title(
                        Component.text(SmallText.of("GRACE PERIODE"), NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.text(SmallText.of(graceLabel(grace) + " looten - geen PvP"), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(400))
                ));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.2f);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
            } else {
                showFightTitle(p);
            }
        }

        // Geen tijdslimiet: het gevecht loopt door tot er nog één speler/team overeind staat.
        startLoot();

        if (useGrace) {
            graceTask = Bukkit.getScheduler().runTaskLater(plugin, this::endGrace, grace * 20L);
        }
    }

    /** Sluit de grace-periode af: PvP gaat aan. */
    private void endGrace() {
        if (state != State.RUNNING) return;
        graceEndsAtMs = 0L;
        cancelTask(graceTask);
        graceTask = null;
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            showFightTitle(p);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 1.4f);
            p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.7f, 1.2f);
        }
        Bukkit.broadcast(Messages.PREFIX.append(
                Component.text(SmallText.of("PvP is nu AAN - vecht!"), NamedTextColor.RED, TextDecoration.BOLD)));
    }

    private void showFightTitle(Player p) {
        p.showTitle(Title.title(
                Component.text(SmallText.of("VECHTEN!"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("laatste overlevende wint"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(300))
        ));
    }

    private String graceLabel(int seconds) {
        if (seconds % 60 == 0) {
            return (seconds / 60) + " min";
        }
        return seconds + "s";
    }

    /** True zolang deelnemers bevroren horen te staan (vrijlating-fase). */
    public boolean isFreezing() {
        return state == State.STARTING;
    }

    /** Teleporteert een levende deelnemer terug naar zijn toegewezen spawn (bv. void tijdens grace). */
    public void returnToSpawn(Player player) {
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.getSpawn() == null) return;
        player.setFallDistance(0f);
        plugin.getTeleporter().teleport(player, ps.getSpawn());
        player.setFallDistance(0f);
    }

    // ---------------------------------------------------------------- combat / damage

    /** Onthoudt wie een speler raakte, voor kill-credit. */
    public void recordDamager(Player victim, Player attacker) {
        if (state != State.RUNNING) return;
        FinalePlayerState ps = players.get(victim.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        ps.recordDamager(attacker.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Aangeroepen door de listener wanneer schade een speler zou doden. We laten niemand
     * echt sterven (geen dood-scherm); in plaats daarvan elimineren we hem netjes naar
     * spectator. Returnt {@code true} als de schade dodelijk was en is afgehandeld.
     */
    public boolean isFatal(Player player, double finalDamage) {
        if (state != State.RUNNING) return false;
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return false;
        return (player.getHealth() - finalDamage) <= 0.0;
    }

    public void handleElimination(Player player) {
        if (state != State.RUNNING) return;
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        UUID killerId = null;
        if (ps.hasRecentDamager(System.currentTimeMillis(), KILL_CREDIT_WINDOW_MS)) {
            killerId = ps.getLastDamager();
        }
        eliminate(ps, player, killerId);
        evaluateWinCondition();
    }

    /** Void/echte dood-fallback: behandel als directe eliminatie. */
    public void handleDeath(Player player) {
        if (state != State.RUNNING && state != State.STARTING) return;
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        UUID killerId = ps.hasRecentDamager(System.currentTimeMillis(), KILL_CREDIT_WINDOW_MS)
                ? ps.getLastDamager() : null;
        eliminate(ps, player, killerId);
        evaluateWinCondition();
    }

    private void eliminate(FinalePlayerState ps, Player player, UUID killerId) {
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

        // Kill-credit voor de aanvaller.
        FinalePlayerState killerPs = killerId != null ? players.get(killerId) : null;
        Player killer = killerId != null ? Bukkit.getPlayer(killerId) : null;
        if (killerPs != null && killerPs.isAlive() && !killerId.equals(ps.getUuid())) {
            killerPs.addKill();
            Team killerTeam = teamManager.getTeamOfPlayer(killerId);
            if (killerTeam != null) {
                dataManager.addTeamPoints(killerTeam, KILL_POINTS);
            }
            if (killer != null) {
                killer.sendActionBar(Messages.success("Kill! +" + KILL_POINTS + " punten"));
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
                killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
        }

        String name = player != null ? player.getName() : ps.getUuid().toString().substring(0, 8);
        broadcastElimination(ps, name, team, killer, pts);

        if (player != null) {
            player.showTitle(Title.title(
                    Component.text(SmallText.of("uitgeschakeld"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("#" + ps.getPlacement() + " · +" + pts + " pts · spectate"),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))
            ));
            player.getInventory().clear();
            clearPotionEffects(player);
            player.setHealth(20.0);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            player.setGameMode(GameMode.SPECTATOR);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.9f, 0.6f);
            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 0.7f, 0.8f);
            if (config.getSpectatorSpawn() != null) {
                plugin.getTeleporter().teleport(player, config.getSpectatorSpawn());
            }
        }
    }

    private void broadcastElimination(FinalePlayerState ps, String name, Team team, Player killer, int pts) {
        var b = Component.text().append(Component.text(name,
                team != null ? team.getColor() : NamedTextColor.WHITE));
        if (killer != null) {
            Team killerTeam = teamManager.getTeamOfPlayer(killer.getUniqueId());
            b.append(Component.text(SmallText.of(" werd verslagen door "), NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(),
                            killerTeam != null ? killerTeam.getColor() : NamedTextColor.WHITE));
        } else {
            b.append(Component.text(SmallText.of(" viel af"), NamedTextColor.GRAY));
        }
        b.append(Component.text(SmallText.of("  (#" + ps.getPlacement() + ", +" + pts + " pts)"),
                NamedTextColor.DARK_GRAY));
        Bukkit.broadcast(Messages.PREFIX.append(b.build()));
    }

    private int pointsForPlacement(int placement) {
        if (placement < 1 || placement > PLACEMENT_POINTS.length) return 0;
        return PLACEMENT_POINTS[placement - 1];
    }

    private void evaluateWinCondition() {
        if (state != State.RUNNING) return;
        // FFA: het event eindigt zodra er nog hooguit één speler in leven is.
        if (aliveCount() <= 1) {
            finishGame();
        }
    }

    private List<FinalePlayerState> aliveStates() {
        List<FinalePlayerState> alive = new ArrayList<>();
        for (FinalePlayerState ps : players.values()) {
            if (ps.isAlive()) alive.add(ps);
        }
        return alive;
    }

    private void finishGame() {
        if (state == State.ENDING || state == State.IDLE) return;
        state = State.ENDING;
        freezeEndsAtMs = 0L;
        graceEndsAtMs = 0L;
        cancelTask(graceTask);
        graceTask = null;
        stopLoot();

        List<FinalePlayerState> survivors = aliveStates();
        Set<UUID> winningTeams = new HashSet<>();
        for (FinalePlayerState ps : survivors) {
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("Finale event afgelopen."));
            }
        }, END_DELAY_TICKS);
    }

    private void announceWinner(List<FinalePlayerState> survivors) {
        Component winnerLine;
        if (survivors.isEmpty()) {
            winnerLine = Component.text(SmallText.of("iedereen viel af - gelijkspel"), NamedTextColor.GRAY);
        } else if (survivors.size() == 1) {
            UUID solo = survivors.get(0).getUuid();
            Player p = Bukkit.getPlayer(solo);
            String name = p != null ? p.getName() : solo.toString().substring(0, 8);
            Team t = teamManager.getTeamOfPlayer(solo);
            var b = Component.text().append(Component.text(name,
                    t != null ? t.getColor() : NamedTextColor.GOLD, TextDecoration.BOLD));
            if (t != null) {
                b.append(Component.text(SmallText.of("  (team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(t.getName()), t.getColor()))
                        .append(Component.text(")", NamedTextColor.GRAY));
            }
            b.append(Component.text(SmallText.of(" wint de Finale!"), NamedTextColor.GRAY));
            winnerLine = b.build();
        } else {
            winnerLine = Component.text()
                    .append(Component.text(SmallText.of(survivors.size() + " spelers"),
                            NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" overleefden de Finale!"), NamedTextColor.GRAY))
                    .build();
        }

        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text(SmallText.of("FINALE VOORBIJ"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    winnerLine,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(600))
            ));
        }
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f);
        playSoundAll(Sound.ITEM_TOTEM_USE, 0.6f, 1.0f);

        for (FinalePlayerState ps : survivors) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                spawnVictoryFireworks(p.getLocation());
            }
        }
    }

    private void spawnVictoryFireworks(Location loc) {
        if (loc.getWorld() == null) return;
        for (int i = 0; i < 3; i++) {
            final int delay = i * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (loc.getWorld() == null) return;
                Firework fw = loc.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .withColor(Color.YELLOW, Color.ORANGE)
                        .withFade(Color.WHITE)
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .trail(true)
                        .flicker(true)
                        .build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            }, delay);
        }
    }

    // ---------------------------------------------------------------- loot drops

    private void startLoot() {
        if (!config.hasLoot()) return;
        long intervalTicks = Math.max(20L, config.getLootIntervalSeconds() * 20L);
        // Eerste drop pas na één interval zodat het gevecht eerst op gang komt.
        lootSpawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == State.RUNNING) spawnLootDrop();
        }, intervalTicks, intervalTicks);
        lootTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::lootTick, 5L, 5L);
    }

    private void stopLoot() {
        cancelTask(lootSpawnTask); lootSpawnTask = null;
        cancelTask(lootTickTask); lootTickTask = null;
        for (FinaleLootDrop drop : new ArrayList<>(activeDrops)) {
            restoreBlock(drop);
        }
        activeDrops.clear();
    }

    /** Plaatst een crate op een vrije, geladen loot-locatie en kondigt hem luid aan. */
    public boolean spawnLootDrop() {
        if (state != State.RUNNING || !config.hasLoot()) return false;

        List<Location> candidates = new ArrayList<>();
        for (Location loc : config.getLootSpawns()) {
            if (loc.getWorld() == null) continue;
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            if (isDropAt(loc)) continue; // hier staat al een actieve drop
            candidates.add(loc);
        }
        if (candidates.isEmpty()) return false;

        Location loc = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Block block = loc.getBlock();
        BlockState previous = block.getState();
        block.setType(Material.CHEST, false);

        long expiresAt = System.currentTimeMillis() + config.getLootLifetimeSeconds() * 1000L;
        FinaleLootDrop drop = new FinaleLootDrop(block.getLocation(), previous, rollLoot(), expiresAt);
        activeDrops.add(drop);

        announceLootDrop(block.getLocation());
        return true;
    }

    private boolean isDropAt(Location loc) {
        Location blockLoc = loc.getBlock().getLocation();
        for (FinaleLootDrop drop : activeDrops) {
            if (!drop.isRemoved() && drop.isAt(blockLoc)) return true;
        }
        return false;
    }

    /** Kiest willekeurig tot {@code lootItemsPerDrop} items uit de pool. */
    private List<ItemStack> rollLoot() {
        List<ItemStack> pool = new ArrayList<>(config.getLootPool());
        Collections.shuffle(pool);
        int count = Math.min(config.getLootItemsPerDrop(), pool.size());
        List<ItemStack> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(pool.get(i).clone());
        }
        return out;
    }

    private void announceLootDrop(Location loc) {
        Component chat = Component.text()
                .append(Component.text(SmallText.of("LOOT DROP! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("bij x" + loc.getBlockX() + " z" + loc.getBlockZ()
                        + " - ren erheen!"), NamedTextColor.YELLOW))
                .build();
        Title title = Title.title(
                Component.text(SmallText.of("LOOT DROP"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("x" + loc.getBlockX() + " z" + loc.getBlockZ()), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(400))
        );
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.sendMessage(Messages.PREFIX.append(chat));
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.9f, 1.0f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        }
    }

    private void lootTick() {
        if (activeDrops.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (FinaleLootDrop drop : new ArrayList<>(activeDrops)) {
            if (drop.isRemoved()) {
                activeDrops.remove(drop);
                continue;
            }
            if (drop.isExpired(now)) {
                restoreBlock(drop);
                drop.markRemoved();
                activeDrops.remove(drop);
                continue;
            }
            spawnBeam(drop.getLocation());
        }
    }

    private void spawnBeam(Location loc) {
        if (loc.getWorld() == null) return;
        Location base = loc.clone().add(0.5, 1.1, 0.5);
        for (int i = 0; i < 5; i++) {
            loc.getWorld().spawnParticle(Particle.END_ROD,
                    base.clone().add(0, i * 0.6, 0), 1, 0.03, 0.03, 0.03, 0.0);
        }
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, base, 4, 0.25, 0.4, 0.25, 0.0);
    }

    /**
     * Probeert de loot-drop te claimen op de plek die de speler aanklikte. Returnt {@code true}
     * als er een drop stond (en geclaimd is). Aangeroepen vanuit de listener bij rechts-klik.
     */
    public boolean tryClaimLootDrop(Player player, Block block) {
        if (state != State.RUNNING) return false;
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return false;

        Location blockLoc = block.getLocation();
        FinaleLootDrop drop = null;
        for (FinaleLootDrop d : activeDrops) {
            if (!d.isRemoved() && d.isAt(blockLoc)) {
                drop = d;
                break;
            }
        }
        if (drop == null) return false;

        drop.markRemoved();
        activeDrops.remove(drop);
        restoreBlock(drop);
        giveLoot(player, drop.getItems());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
        player.sendActionBar(Messages.success("Loot drop geclaimd!"));

        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        Component msg = Component.text()
                .append(Component.text(player.getName(), team != null ? team.getColor() : NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" claimde een loot drop!"), NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        return true;
    }

    private void giveLoot(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack overflow : leftover.values()) {
                if (player.getWorld() != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
            }
        }
        player.updateInventory();
    }

    private void restoreBlock(FinaleLootDrop drop) {
        Location loc = drop.getLocation();
        if (loc.getWorld() == null) return;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
        if (loc.getBlock().getType() == Material.CHEST) {
            drop.getPreviousState().update(true, false);
        }
    }

    // ---------------------------------------------------------------- bouwen

    /**
     * Onthoudt de oorspronkelijke staat van een blok dat een speler wijzigt (plaatst of breekt),
     * de eerste keer dat die locatie verandert. Zo wordt de arena bij het einde volledig hersteld.
     */
    public void recordOriginalBlock(Block block, BlockState originalState) {
        originalBlocks.putIfAbsent(blockKey(block.getLocation()), originalState);
    }

    /** Herstelt alle door spelers gewijzigde blokken naar hun oorspronkelijke staat. */
    private void restoreModifiedBlocks() {
        for (BlockState original : originalBlocks.values()) {
            Location loc = original.getLocation();
            if (loc.getWorld() == null) continue;
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            original.update(true, false);
        }
        originalBlocks.clear();
    }

    private static String blockKey(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        return world + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    // ---------------------------------------------------------------- leave / quit

    public void removeParticipant(Player player, boolean teleport) {
        FinalePlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;

        boolean wasAlive = ps.isAlive();
        ps.setAlive(false);
        if (ps.getPlacement() == 0 && nextEliminationPlacement > 0) {
            ps.setPlacement(nextEliminationPlacement);
            nextEliminationPlacement--;
        }
        players.remove(player.getUniqueId());
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (state == State.RUNNING && wasAlive) {
            evaluateWinCondition();
        }
        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, FinalePlayerState ps, boolean teleport) {
        PlayerRespawnUtil.prepareForRestore(player);
        clearPotionEffects(player);
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }
        player.getInventory().clear();
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

    // ---------------------------------------------------------------- history

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<FinalePlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int ap = a.getPlacement();
            int bp = b.getPlacement();
            if (ap == 0 && bp == 0) return 0;
            if (ap == 0) return 1;
            if (bp == 0) return -1;
            return Integer.compare(ap, bp);
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (FinalePlayerState ps : sorted) {
            if (ps.getPlacement() <= 0) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int points = ps.isAlive()
                    ? WIN_TEAM_BONUS + ps.getKills() * KILL_POINTS
                    : pointsForPlacement(ps.getPlacement()) + ps.getKills() * KILL_POINTS;
            String detail = (ps.isAlive() ? "overleefd" : "uitgeschakeld") + " · " + ps.getKills() + " kills";
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    points,
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "finale",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    // ---------------------------------------------------------------- helpers

    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
