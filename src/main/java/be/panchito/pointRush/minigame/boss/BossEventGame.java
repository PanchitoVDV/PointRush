package be.panchito.pointRush.minigame.boss;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Boss Event: parallelle arena-rondes (MythicMobs) + gezamenlijke finaalronde.
 */
public final class BossEventGame {

    public enum State { IDLE, STARTING, RUNNING }

    public enum Phase {
        COUNTDOWN,
        ARENA_ROUND,
        ARENA_INTERMISSION,
        FINAL_COUNTDOWN,
        FINAL_ROUND,
        ENDING
    }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final int FINAL_COUNTDOWN_SECONDS = 10;

    private final PointRush plugin;
    private final BossEventConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final MythicMobsBridge mythic;
    private final BossEventScoreboard scoreboard;

    private State state = State.IDLE;
    private Phase phase = Phase.COUNTDOWN;
    private final Map<UUID, BossEventPlayerState> players = new HashMap<>();
    private final List<BossEventArenaRun> activeArenas = new ArrayList<>();

    private int currentRound = 0;
    private UUID finalBossId;
    private boolean finalRoundComplete;

    private long phaseEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private boolean historyRecorded;

    private BukkitTask tickTask;

    public BossEventGame(PointRush plugin, BossEventConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.mythic = config.getMythic();
        this.scoreboard = new BossEventScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public Phase getPhase() {
        return phase;
    }

    public BossEventConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public Collection<BossEventPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public List<BossEventArenaRun> getActiveArenas() {
        return Collections.unmodifiableList(activeArenas);
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public BossEventPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public BossEventArenaRun getArenaForPlayer(UUID playerId) {
        BossEventPlayerState ps = players.get(playerId);
        if (ps == null || ps.getArenaId() == null) {
            return null;
        }
        for (BossEventArenaRun run : activeArenas) {
            if (run.getId().equals(ps.getArenaId())) {
                return run;
            }
        }
        return null;
    }

    public long getPhaseTimeLeftMs() {
        return Math.max(0L, phaseEndsMs - System.currentTimeMillis());
    }

    public String formatTime(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    public boolean start() {
        if (state != State.IDLE) {
            return false;
        }
        if (!config.isReady()) {
            return false;
        }

        List<Player> eligible = collectEligiblePlayers();
        if (eligible.isEmpty()) {
            Bukkit.broadcast(Messages.error("Boss Event heeft minstens 1 speler nodig."));
            return false;
        }

        int neededArenas = config.requiredArenaCount(eligible.size());
        List<BossEventArenaConfig> arenaConfigs = config.getArenas();
        if (neededArenas > arenaConfigs.size()) {
            Bukkit.broadcast(Messages.error("Niet genoeg arenas geconfigureerd: "
                    + neededArenas + " nodig voor " + eligible.size() + " spelers, "
                    + arenaConfigs.size() + " beschikbaar."));
            return false;
        }

        state = State.STARTING;
        phase = Phase.COUNTDOWN;
        currentRound = 0;
        finalBossId = null;
        finalRoundComplete = false;
        historyRecorded = false;
        players.clear();
        activeArenas.clear();

        Collections.shuffle(eligible);
        assignPlayersToArenas(eligible, arenaConfigs.subList(0, neededArenas));

        List<String> arenaIds = new ArrayList<>();
        for (BossEventArenaRun run : activeArenas) {
            arenaIds.add(run.getId());
        }
        scoreboard.initArenaBars(arenaIds);

        long now = System.currentTimeMillis();
        eventStartedAtMs = now;
        phaseEndsMs = now + COUNTDOWN_SECONDS * 1000L;

        for (BossEventPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                prepareParticipant(p, ps);
            }
        }

        scoreboard.start();
        scoreboard.updateInfoBar("start in " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("BOSS EVENT"), NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text(SmallText.of("3 arena-rondes · daarna de finaal"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.8f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        MinigameStartEffects.onStarted(plugin);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) {
            return false;
        }
        endEvent(false);
        return true;
    }

    private List<Player> collectEligiblePlayers() {
        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Boss Event (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            eligible.add(online);
        }
        return eligible;
    }

    private void assignPlayersToArenas(List<Player> eligible, List<BossEventArenaConfig> arenaConfigs) {
        int perArena = config.getPlayersPerArena();
        for (int i = 0; i < arenaConfigs.size(); i++) {
            BossEventArenaConfig arenaConfig = arenaConfigs.get(i);
            BossEventArenaRun run = new BossEventArenaRun(arenaConfig);
            activeArenas.add(run);

            int from = i * perArena;
            int to = Math.min(from + perArena, eligible.size());
            if (from >= to) {
                break;
            }
            for (int p = from; p < to; p++) {
                Player player = eligible.get(p);
                BossEventPlayerState ps = createPlayerState(player);
                ps.setArenaId(arenaConfig.getId());
                players.put(player.getUniqueId(), ps);
                run.addPlayer(player.getUniqueId());
            }
        }
    }

    private BossEventPlayerState createPlayerState(Player player) {
        return new BossEventPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode()
        );
    }

    private void prepareParticipant(Player player, BossEventPlayerState ps) {
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setGameMode(GameMode.SURVIVAL);

        BossEventArenaRun run = getArenaForPlayer(player.getUniqueId());
        scoreboard.attach(player);
        int arenaSize = run != null ? run.getPlayers().size() : 0;
        player.sendMessage(Messages.info("Boss Event — arena " + ps.getArenaId()
                + " (" + arenaSize + " spelers)."));

        if (run != null && run.getConfig().getPlayerSpawn() != null) {
            // Kit pas ná de (mogelijk async, cross-world) teleport geven — anders wist een per-wereld
            // inventory-swap (Multiverse-Inventories) 'm meteen weer. Zie ParkourGame#joinPlayer.
            plugin.getTeleporter().teleport(player, run.getConfig().getPlayerSpawn(), false, () -> {
                if (player.isOnline() && getArenaForPlayer(player.getUniqueId()) != null) {
                    BossEventKit.give(player);
                }
            });
        } else {
            BossEventKit.give(player);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (phase) {
            case COUNTDOWN -> tickCountdown(now);
            case ARENA_ROUND -> tickArenaRound(now);
            case ARENA_INTERMISSION -> tickIntermission(now);
            case FINAL_COUNTDOWN -> tickFinalCountdown(now);
            case FINAL_ROUND -> tickFinalRound(now);
            default -> {
            }
        }
    }

    private void tickCountdown(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) (COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateInfoBar("start in " + formatTime(left), progress, BossBar.Color.YELLOW);
        if (left <= 0) {
            state = State.RUNNING;
            beginArenaRound(1);
        }
    }

    private void beginArenaRound(int round) {
        currentRound = round;
        phase = Phase.ARENA_ROUND;
        phaseEndsMs = System.currentTimeMillis() + config.getRoundTimeoutMs();

        for (BossEventArenaRun run : activeArenas) {
            run.resetRoundState();
            respawnArenaPlayers(run);
            spawnArenaBoss(run, round);
        }

        broadcastTitle(
                Component.text(SmallText.of("ARENA RONDE " + round), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("versla de boss — dood = uitgeschakeld"), NamedTextColor.RED)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.0f);
        updateArenaBars();
    }

    /** Werk de live HP-bar van elke arena bij (groen wanneer de boss verslagen is). */
    private void updateArenaBars() {
        for (BossEventArenaRun run : activeArenas) {
            String label = "arena " + run.getId();
            if (run.isRoundComplete()) {
                scoreboard.updateArenaBar(run.getId(), label + " · boss verslagen!", 1.0f, BossBar.Color.GREEN);
                continue;
            }
            double frac = mythic.getBossHealthFraction(run.getActiveBossId());
            if (frac < 0) {
                scoreboard.updateArenaBar(run.getId(), label + " · ronde " + currentRound, 1.0f, BossBar.Color.RED);
                continue;
            }
            BossBar.Color color = frac > 0.25 ? BossBar.Color.RED : BossBar.Color.YELLOW;
            int pct = (int) Math.ceil(frac * 100);
            scoreboard.updateArenaBar(run.getId(), label + " · boss " + pct + "%", (float) frac, color);
        }
    }

    /** Live HP-bar van de finaal-boss (gedeelde info-bar). */
    private void updateFinalBar() {
        if (finalRoundComplete) {
            scoreboard.updateInfoBar("FINAAL · boss verslagen!", 1.0f, BossBar.Color.GREEN);
            return;
        }
        double frac = mythic.getBossHealthFraction(finalBossId);
        if (frac < 0) {
            scoreboard.updateInfoBar("FINAAL · versla de boss", 1.0f, BossBar.Color.PURPLE);
            return;
        }
        BossBar.Color color = frac > 0.25 ? BossBar.Color.PURPLE : BossBar.Color.RED;
        int pct = (int) Math.ceil(frac * 100);
        scoreboard.updateInfoBar("FINAAL · boss " + pct + "%", (float) frac, color);
    }

    private void respawnArenaPlayers(BossEventArenaRun run) {
        Location spawn = run.getConfig().getPlayerSpawn();
        if (spawn == null) {
            return;
        }
        for (UUID id : run.getPlayers()) {
            BossEventPlayerState ps = players.get(id);
            if (ps == null || !ps.isArenaAlive()) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            plugin.getTeleporter().teleport(p, spawn);
            p.setGameMode(GameMode.SURVIVAL);
            BossEventKit.give(p);
        }
    }

    private void spawnArenaBoss(BossEventArenaRun run, int round) {
        String mobId = run.getConfig().getRoundBoss(round);
        Location bossSpawn = run.getConfig().getBossSpawn();
        if (mobId == null || bossSpawn == null) {
            run.setRoundComplete(true);
            return;
        }
        mythic.spawnBoss(mobId, bossSpawn).ifPresentOrElse(
                run::setActiveBossId,
                () -> {
                    Bukkit.broadcast(Messages.error("Kon boss '" + mobId + "' niet spawnen in arena "
                            + run.getId() + "."));
                    run.setRoundComplete(true);
                }
        );
    }

    private void tickArenaRound(long now) {
        long left = Math.max(0L, phaseEndsMs - now);

        detectArenaBossDeaths();
        updateArenaBars();

        if (allArenasRoundComplete()) {
            onArenaRoundComplete();
            return;
        }
        if (left <= 0) {
            Bukkit.broadcast(Messages.warn("Arena ronde " + currentRound + " timeout — door naar volgende fase."));
            forceCompleteArenaRounds();
            onArenaRoundComplete();
        }
    }

    private boolean allArenasRoundComplete() {
        for (BossEventArenaRun run : activeArenas) {
            if (!run.isRoundComplete()) {
                return false;
            }
        }
        return true;
    }

    private void forceCompleteArenaRounds() {
        for (BossEventArenaRun run : activeArenas) {
            if (!run.isRoundComplete()) {
                cleanupBoss(run.getActiveBossId());
                run.setRoundComplete(true);
            }
        }
    }

    private void onArenaRoundComplete() {
        cleanupAllArenaBosses();

        if (currentRound >= config.getArenaRounds()) {
            markArenaSurvivors();
            awardArenaSurvivorPoints();
            beginFinalCountdown();
            return;
        }

        phase = Phase.ARENA_INTERMISSION;
        phaseEndsMs = System.currentTimeMillis() + config.getIntermissionMs();
        broadcastTitle(
                Component.text(SmallText.of("RONDE " + currentRound + " KLAAR"), NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text(SmallText.of("volgende ronde over " + config.getIntermissionSeconds() + "s"), NamedTextColor.GRAY)
        );
        scoreboard.updateInfoBar("pauze · ronde " + (currentRound + 1) + " over "
                + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.YELLOW);
    }

    private void tickIntermission(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) config.getIntermissionMs();
        scoreboard.updateInfoBar("pauze · ronde " + (currentRound + 1) + " over " + formatTime(left),
                progress, BossBar.Color.YELLOW);
        if (left <= 0) {
            beginArenaRound(currentRound + 1);
        }
    }

    private void markArenaSurvivors() {
        for (BossEventPlayerState ps : players.values()) {
            ps.setArenaSurvivor(ps.isArenaAlive());
        }
    }

    private void awardArenaSurvivorPoints() {
        int pts = config.getSurvivorPoints();
        if (pts <= 0) {
            return;
        }
        for (BossEventPlayerState ps : players.values()) {
            if (!ps.isArenaSurvivor()) {
                continue;
            }
            ps.addPointsEarned(pts);
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                p.sendMessage(Messages.success("Je overleefde alle arena-rondes: +" + pts + " punten!"));
            }
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null) {
                dataManager.addTeamPoints(team, pts);
            }
        }
    }

    private void beginFinalCountdown() {
        phase = Phase.FINAL_COUNTDOWN;
        phaseEndsMs = System.currentTimeMillis() + FINAL_COUNTDOWN_SECONDS * 1000L;
        finalRoundComplete = false;
        finalBossId = null;

        Location spawn = config.getFinalPlayerSpawn();
        for (BossEventPlayerState ps : players.values()) {
            ps.setFinalAlive(true);
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null || spawn == null) {
                continue;
            }
            plugin.getTeleporter().teleport(p, spawn);
            p.setGameMode(GameMode.SURVIVAL);
            BossEventKit.give(p);
            if (ps.isArenaSurvivor()) {
                p.sendMessage(Messages.info("Finaalronde — overleef voor een bonus!"));
            } else {
                p.sendMessage(Messages.info("Finaalronde — je krijgt een tweede kans (troostprijs)!"));
            }
        }

        broadcastTitle(
                Component.text(SmallText.of("FINAALRONDE"), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text(SmallText.of("iedereen vecht de laatste boss"), NamedTextColor.GRAY)
        );
        scoreboard.updateInfoBar("finaal over " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.PURPLE);
    }

    private void tickFinalCountdown(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) (FINAL_COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateInfoBar("finaal over " + formatTime(left), progress, BossBar.Color.PURPLE);
        if (left <= 0) {
            beginFinalRound();
        }
    }

    private void beginFinalRound() {
        phase = Phase.FINAL_ROUND;
        phaseEndsMs = System.currentTimeMillis() + config.getRoundTimeoutMs();
        finalRoundComplete = false;

        Location bossSpawn = config.getFinalBossSpawn();
        String mobId = config.getFinalBoss();
        if (bossSpawn == null || mobId == null) {
            endEvent(true);
            return;
        }

        mythic.spawnBoss(mobId, bossSpawn).ifPresentOrElse(
                id -> finalBossId = id,
                () -> {
                    Bukkit.broadcast(Messages.error("Kon finaal-boss '" + mobId + "' niet spawnen."));
                    endEvent(true);
                }
        );

        broadcastTitle(
                Component.text(SmallText.of("VERSLA DE BOSS"), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                Component.text(SmallText.of("bonus of troostprijs bij overleven"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);
        scoreboard.updateInfoBar("FINAAL · boss verschijnt", 1.0f, BossBar.Color.PURPLE);
    }

    private void tickFinalRound(long now) {
        long left = Math.max(0L, phaseEndsMs - now);

        updateFinalBar();

        if (!finalRoundComplete && finalBossId != null && mythic.isBossGone(finalBossId)) {
            handleBossDeath(finalBossId);
        }

        if (finalRoundComplete) {
            endEvent(true);
            return;
        }
        if (left <= 0) {
            Bukkit.broadcast(Messages.warn("Finaalronde timeout."));
            cleanupBoss(finalBossId);
            finalBossId = null;
            endEvent(true);
        }
    }

    private void detectArenaBossDeaths() {
        for (BossEventArenaRun run : activeArenas) {
            if (run.isRoundComplete()) {
                continue;
            }
            UUID bossId = run.getActiveBossId();
            if (bossId != null && mythic.isBossGone(bossId)) {
                handleBossDeath(bossId);
            }
        }
    }

    /** True wanneer dit entity-id een momenteel levende arena- of finaal-boss is. */
    public boolean isActiveBoss(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        if (entityId.equals(finalBossId)) {
            return true;
        }
        for (BossEventArenaRun run : activeArenas) {
            if (entityId.equals(run.getActiveBossId())) {
                return true;
            }
        }
        return false;
    }

    /** Tel schade aan een boss mee voor de MVP-prijs (alleen tijdens echte vecht-fases). */
    public void addBossDamage(Player player, double amount) {
        if (amount <= 0 || (phase != Phase.ARENA_ROUND && phase != Phase.FINAL_ROUND)) {
            return;
        }
        BossEventPlayerState ps = players.get(player.getUniqueId());
        if (ps != null) {
            ps.addBossDamage(amount);
        }
    }

    public void handleBossDeath(UUID entityId) {
        if (entityId == null) {
            return;
        }

        if (phase == Phase.FINAL_ROUND && entityId.equals(finalBossId)) {
            if (finalRoundComplete) {
                return;
            }
            finalRoundComplete = true;
            finalBossId = null;
            broadcastTitle(
                    Component.text(SmallText.of("BOSS VERSLAGEN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(SmallText.of("punten worden uitgedeeld"), NamedTextColor.GRAY)
            );
            playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            return;
        }

        if (phase != Phase.ARENA_ROUND) {
            return;
        }

        for (BossEventArenaRun run : activeArenas) {
            if (entityId.equals(run.getActiveBossId())) {
                if (run.isRoundComplete()) {
                    return;
                }
                run.setActiveBossId(null);
                run.setRoundComplete(true);
                titleArena(run,
                        Component.text(SmallText.of("BOSS VERSLAGEN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text(SmallText.of("ronde " + currentRound + " geklaard!"), NamedTextColor.GREEN));
                playSoundArena(run, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                playSoundArena(run, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                Bukkit.broadcast(Messages.info("Arena " + run.getId() + " heeft de boss verslagen!"));
                return;
            }
        }
    }

    private void titleArena(BossEventArenaRun run, Component title, Component subtitle) {
        Title t = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500)));
        for (UUID id : run.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(t);
            }
        }
    }

    private void playSoundArena(BossEventArenaRun run, Sound sound, float volume, float pitch) {
        for (UUID id : run.getPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void handleArenaDeath(Player player) {
        if (phase != Phase.ARENA_ROUND) {
            return;
        }
        BossEventPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isArenaAlive()) {
            return;
        }
        ps.setArenaAlive(false);
        player.sendMessage(Messages.warn("Je bent uitgeschakeld — je krijgt nog een kans in de finaalronde."));
    }

    public void handleFinalDeath(Player player) {
        if (phase != Phase.FINAL_ROUND) {
            return;
        }
        BossEventPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isFinalAlive()) {
            return;
        }
        ps.setFinalAlive(false);
        player.sendMessage(Messages.warn("Je overleefde de finaalronde niet."));
    }

    public Location getRespawnLocation(Player player) {
        BossEventPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) {
            return null;
        }
        if (phase == Phase.FINAL_ROUND || phase == Phase.FINAL_COUNTDOWN) {
            if (!ps.isFinalAlive()) {
                return config.getFinalPlayerSpawn();
            }
            return config.getFinalPlayerSpawn();
        }
        if (!ps.isArenaAlive()) {
            BossEventArenaRun run = getArenaForPlayer(player.getUniqueId());
            if (run != null && run.getConfig().getPlayerSpawn() != null) {
                return run.getConfig().getPlayerSpawn();
            }
        }
        BossEventArenaRun run = getArenaForPlayer(player.getUniqueId());
        if (run != null && run.getConfig().getPlayerSpawn() != null) {
            return run.getConfig().getPlayerSpawn();
        }
        return config.getFinalPlayerSpawn();
    }

    public void applySpectatorAfterRespawn(Player player) {
        if (!players.containsKey(player.getUniqueId())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            // The event can end (cleanup + players.clear()) between the respawn and this
            // task. Re-check here so we never strand a player in spectator with no event
            // running - that looked like "hardcore" was on.
            BossEventPlayerState ps = players.get(player.getUniqueId());
            if (state != State.RUNNING || ps == null) {
                return;
            }

            boolean stillFighting;
            if (phase == Phase.ARENA_ROUND) {
                stillFighting = ps.isArenaAlive();
            } else if (phase == Phase.FINAL_ROUND || phase == Phase.FINAL_COUNTDOWN) {
                stillFighting = ps.isFinalAlive();
            } else {
                // countdown / intermission / ending: not actively eliminated, respawn normally
                stillFighting = true;
            }

            player.getInventory().clear();
            player.setFireTicks(0);
            player.setFallDistance(0f);
            if (stillFighting) {
                BossEventKit.give(player);
                player.setGameMode(GameMode.SURVIVAL);
            } else {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    private void endEvent(boolean natural) {
        if (phase == Phase.FINAL_ROUND || phase == Phase.FINAL_COUNTDOWN || natural) {
            markFinalSurvivors();
            awardFinalPoints();
            awardMvp();
        }
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Boss Event afgelopen."));
    }

    /** Beloon de speler die in totaal de meeste boss-schade uitdeelde. */
    private void awardMvp() {
        int mvpPts = config.getMvpPoints();
        if (mvpPts <= 0) {
            return;
        }
        BossEventPlayerState best = null;
        for (BossEventPlayerState ps : players.values()) {
            if (ps.getBossDamage() <= 0) {
                continue;
            }
            if (best == null || ps.getBossDamage() > best.getBossDamage()) {
                best = ps;
            }
        }
        if (best == null) {
            return;
        }
        best.setMvp(true);
        best.addPointsEarned(mvpPts);
        Team team = teamManager.getTeamOfPlayer(best.getUuid());
        if (team != null) {
            dataManager.addTeamPoints(team, mvpPts);
        }

        Player mvp = Bukkit.getPlayer(best.getUuid());
        String name = mvp != null ? mvp.getName() : "Een speler";
        for (BossEventPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                p.sendMessage(Messages.success("MVP: " + name + " deelde de meeste boss-schade uit (+"
                        + mvpPts + " punten)!"));
            }
        }
        if (mvp != null) {
            mvp.showTitle(Title.title(
                    Component.text(SmallText.of("MVP!"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(SmallText.of("meeste boss-schade · +" + mvpPts + " punten"), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
            mvp.playSound(mvp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.4f);
        }
    }

    private void markFinalSurvivors() {
        for (BossEventPlayerState ps : players.values()) {
            ps.setFinalSurvivor(ps.isFinalAlive());
        }
    }

    private void awardFinalPoints() {
        int bonus = config.getFinalBonusPoints();
        int consolation = config.getFinalConsolationPoints();

        for (BossEventPlayerState ps : players.values()) {
            if (!ps.isFinalSurvivor()) {
                continue;
            }
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (ps.isArenaSurvivor() && bonus > 0) {
                ps.addPointsEarned(bonus);
                if (p != null) {
                    p.sendMessage(Messages.success("Finaal overleefd — bonus: +" + bonus + " punten!"));
                }
                Team team = teamManager.getTeamOfPlayer(ps.getUuid());
                if (team != null) {
                    dataManager.addTeamPoints(team, bonus);
                }
            } else if (!ps.isArenaSurvivor() && consolation > 0) {
                ps.addPointsEarned(consolation);
                if (p != null) {
                    p.sendMessage(Messages.success("Finaal overleefd — troostprijs: +" + consolation + " punten!"));
                }
                Team team = teamManager.getTeamOfPlayer(ps.getUuid());
                if (team != null) {
                    dataManager.addTeamPoints(team, consolation);
                }
            }
        }
    }

    private void cleanupAllArenaBosses() {
        for (BossEventArenaRun run : activeArenas) {
            cleanupBoss(run.getActiveBossId());
            run.setActiveBossId(null);
        }
    }

    private void cleanupBoss(UUID entityId) {
        if (entityId != null) {
            mythic.removeBoss(entityId);
        }
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        phase = Phase.COUNTDOWN;
        cancelTask(tickTask);
        tickTask = null;
        cleanupAllArenaBosses();
        cleanupBoss(finalBossId);
        finalBossId = null;
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, BossEventPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue());
            }
        }
        players.clear();
        activeArenas.clear();
        currentRound = 0;
    }

    private void restorePlayer(Player player, BossEventPlayerState ps) {
        PlayerRespawnUtil.prepareForRestore(player);
        plugin.getTeleporter().teleport(player, ps.getSavedLocation());
        player.setGameMode(ps.getSavedGameMode());
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    public void leave(Player player) {
        BossEventPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) {
            return;
        }
        for (BossEventArenaRun run : activeArenas) {
            run.getPlayers().remove(player.getUniqueId());
        }
        restorePlayer(player, ps);
        scoreboard.detach(player);
        player.sendMessage(Messages.info("Je hebt Boss Event verlaten."));
    }

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) {
            return;
        }
        historyRecorded = true;

        List<BossEventPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparingInt(BossEventPlayerState::getPointsEarned).reversed());

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (BossEventPlayerState ps : sorted) {
            if (ps.getPointsEarned() <= 0) {
                continue;
            }
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            String detail = (ps.isArenaSurvivor() ? "arena-overlever" : "uitgeschakeld")
                    + " · finaal: " + (ps.isFinalSurvivor() ? "overleefd" : "neen")
                    + " · schade: " + (long) ps.getBossDamage()
                    + (ps.isMvp() ? " · MVP" : "");
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    ps.getPointsEarned(),
                    detail
            ));
        }

        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "bossevent",
                eventStartedAtMs,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (BossEventPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                p.showTitle(t);
            }
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (BossEventPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }
}
