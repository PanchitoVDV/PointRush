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
        scoreboard.updateBossBar("start in " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.YELLOW);
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
        player.sendMessage(Messages.info("Boss Event — arena " + ps.getArenaId()
                + " (" + run.getPlayers().size() + " spelers)."));

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
        scoreboard.updateBossBar("start in " + formatTime(left), progress, BossBar.Color.YELLOW);
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
        scoreboard.updateBossBar("ronde " + round + " · " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.RED);
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
        float progress = left / (float) config.getRoundTimeoutMs();
        scoreboard.updateBossBar("ronde " + currentRound + " · " + formatTime(left), progress, BossBar.Color.RED);

        detectArenaBossDeaths();

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
        scoreboard.updateBossBar("pauze · ronde " + (currentRound + 1) + " over "
                + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.YELLOW);
    }

    private void tickIntermission(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) config.getIntermissionMs();
        scoreboard.updateBossBar("pauze · ronde " + (currentRound + 1) + " over " + formatTime(left),
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
        scoreboard.updateBossBar("finaal over " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.PURPLE);
    }

    private void tickFinalCountdown(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) (FINAL_COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateBossBar("finaal over " + formatTime(left), progress, BossBar.Color.PURPLE);
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
        scoreboard.updateBossBar("finaal · " + formatTime(getPhaseTimeLeftMs()), 1.0f, BossBar.Color.PURPLE);
    }

    private void tickFinalRound(long now) {
        long left = Math.max(0L, phaseEndsMs - now);
        float progress = left / (float) config.getRoundTimeoutMs();
        scoreboard.updateBossBar("finaal · " + formatTime(left), progress, BossBar.Color.PURPLE);

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
                Bukkit.broadcast(Messages.info("Boss verslagen in arena " + run.getId() + "!"));
                return;
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
        }
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Boss Event afgelopen."));
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
                    + " · finaal: " + (ps.isFinalSurvivor() ? "overleefd" : "neen");
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
