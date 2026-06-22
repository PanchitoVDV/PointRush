package be.panchito.pointRush.minigame.boss;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Boss Event configuratie onder sectie {@code bossevent} in {@code settings.yml}.
 */
public final class BossEventConfig {

    public static final int DEFAULT_PLAYERS_PER_ARENA = 20;
    public static final int DEFAULT_ARENA_ROUNDS = 3;
    public static final int DEFAULT_ROUND_TIMEOUT_MINUTES = 15;
    public static final int DEFAULT_INTERMISSION_SECONDS = 8;
    public static final int DEFAULT_SURVIVOR_POINTS = 100;
    public static final int DEFAULT_FINAL_BONUS_POINTS = 50;
    public static final int DEFAULT_FINAL_CONSOLATION_POINTS = 25;
    public static final int DEFAULT_MVP_POINTS = 75;

    private static final String KEY = "bossevent";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;
    private final MythicMobsBridge mythic;

    private final Map<String, BossEventArenaConfig> arenas = new LinkedHashMap<>();
    private Location finalPlayerSpawn;
    private Location finalBossSpawn;
    private String finalBoss;

    private int playersPerArena = DEFAULT_PLAYERS_PER_ARENA;
    private int arenaRounds = DEFAULT_ARENA_ROUNDS;
    private int roundTimeoutMinutes = DEFAULT_ROUND_TIMEOUT_MINUTES;
    private int intermissionSeconds = DEFAULT_INTERMISSION_SECONDS;
    private int survivorPoints = DEFAULT_SURVIVOR_POINTS;
    private int finalBonusPoints = DEFAULT_FINAL_BONUS_POINTS;
    private int finalConsolationPoints = DEFAULT_FINAL_CONSOLATION_POINTS;
    private int mvpPoints = DEFAULT_MVP_POINTS;

    public BossEventConfig(JavaPlugin plugin, UnifiedSettings unified, MythicMobsBridge mythic) {
        this.plugin = plugin;
        this.unified = unified;
        this.mythic = mythic;
    }

    public void load() {
        arenas.clear();
        finalPlayerSpawn = null;
        finalBossSpawn = null;
        finalBoss = null;
        playersPerArena = DEFAULT_PLAYERS_PER_ARENA;
        arenaRounds = DEFAULT_ARENA_ROUNDS;
        roundTimeoutMinutes = DEFAULT_ROUND_TIMEOUT_MINUTES;
        intermissionSeconds = DEFAULT_INTERMISSION_SECONDS;
        survivorPoints = DEFAULT_SURVIVOR_POINTS;
        finalBonusPoints = DEFAULT_FINAL_BONUS_POINTS;
        finalConsolationPoints = DEFAULT_FINAL_CONSOLATION_POINTS;
        mvpPoints = DEFAULT_MVP_POINTS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }

        if (cfg.isSet(KEY + ".playersPerArena")) {
            playersPerArena = Math.max(1, cfg.getInt(KEY + ".playersPerArena"));
        }
        if (cfg.isSet(KEY + ".arenaRounds")) {
            arenaRounds = Math.max(1, Math.min(3, cfg.getInt(KEY + ".arenaRounds")));
        }
        if (cfg.isSet(KEY + ".roundTimeoutMinutes")) {
            roundTimeoutMinutes = Math.max(2, cfg.getInt(KEY + ".roundTimeoutMinutes"));
        }
        if (cfg.isSet(KEY + ".intermissionSeconds")) {
            intermissionSeconds = Math.max(3, cfg.getInt(KEY + ".intermissionSeconds"));
        }
        if (cfg.isSet(KEY + ".survivorPoints")) {
            survivorPoints = Math.max(0, cfg.getInt(KEY + ".survivorPoints"));
        }
        if (cfg.isSet(KEY + ".finalBonusPoints")) {
            finalBonusPoints = Math.max(0, cfg.getInt(KEY + ".finalBonusPoints"));
        }
        if (cfg.isSet(KEY + ".finalConsolationPoints")) {
            finalConsolationPoints = Math.max(0, cfg.getInt(KEY + ".finalConsolationPoints"));
        }
        if (cfg.isSet(KEY + ".mvpPoints")) {
            mvpPoints = Math.max(0, cfg.getInt(KEY + ".mvpPoints"));
        }

        ConfigurationSection arenaSec = cfg.getConfigurationSection(KEY + ".arenas");
        if (arenaSec != null) {
            for (String id : arenaSec.getKeys(false)) {
                String base = KEY + ".arenas." + id;
                Location playerSpawn = loadLocation(cfg, base + ".playerSpawn");
                Location bossSpawn = loadLocation(cfg, base + ".bossSpawn");
                String[] bosses = new String[3];
                bosses[0] = cfg.getString(base + ".round1Boss");
                bosses[1] = cfg.getString(base + ".round2Boss");
                bosses[2] = cfg.getString(base + ".round3Boss");
                arenas.put(id.toLowerCase(), new BossEventArenaConfig(id.toLowerCase(), playerSpawn, bossSpawn, bosses));
            }
        }

        finalPlayerSpawn = loadLocation(cfg, KEY + ".final.playerSpawn");
        finalBossSpawn = loadLocation(cfg, KEY + ".final.bossSpawn");
        finalBoss = cfg.getString(KEY + ".final.boss");
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".playersPerArena", playersPerArena);
        cfg.set(KEY + ".arenaRounds", arenaRounds);
        cfg.set(KEY + ".roundTimeoutMinutes", roundTimeoutMinutes);
        cfg.set(KEY + ".intermissionSeconds", intermissionSeconds);
        cfg.set(KEY + ".survivorPoints", survivorPoints);
        cfg.set(KEY + ".finalBonusPoints", finalBonusPoints);
        cfg.set(KEY + ".finalConsolationPoints", finalConsolationPoints);
        cfg.set(KEY + ".mvpPoints", mvpPoints);

        cfg.set(KEY + ".arenas", null);
        for (BossEventArenaConfig arena : arenas.values()) {
            String base = KEY + ".arenas." + arena.getId();
            if (arena.getPlayerSpawn() != null) {
                saveLocation(cfg, base + ".playerSpawn", arena.getPlayerSpawn());
            }
            if (arena.getBossSpawn() != null) {
                saveLocation(cfg, base + ".bossSpawn", arena.getBossSpawn());
            }
            String[] bosses = arena.getRoundBosses();
            cfg.set(base + ".round1Boss", bosses[0]);
            cfg.set(base + ".round2Boss", bosses[1]);
            cfg.set(base + ".round3Boss", bosses[2]);
        }

        if (finalPlayerSpawn != null) {
            saveLocation(cfg, KEY + ".final.playerSpawn", finalPlayerSpawn);
        }
        if (finalBossSpawn != null) {
            saveLocation(cfg, KEY + ".final.bossSpawn", finalBossSpawn);
        }
        cfg.set(KEY + ".final.boss", finalBoss);

        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": ready=" + isReady() + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan!", ex);
        }
    }

    private Location loadLocation(YamlConfiguration cfg, String path) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return null;
        String worldName = sec.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw"),
                (float) sec.getDouble("pitch")
        );
    }

    private void saveLocation(YamlConfiguration cfg, String path, Location loc) {
        if (loc.getWorld() == null) return;
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", loc.getYaw());
        cfg.set(path + ".pitch", loc.getPitch());
    }

    public MythicMobsBridge getMythic() {
        return mythic;
    }

    public List<BossEventArenaConfig> getArenas() {
        return new ArrayList<>(arenas.values());
    }

    public BossEventArenaConfig getArena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase());
    }

    public BossEventArenaConfig addArena(String id) {
        String key = id.toLowerCase();
        BossEventArenaConfig arena = new BossEventArenaConfig(key, null, null, new String[3]);
        arenas.put(key, arena);
        save();
        return arena;
    }

    public boolean removeArena(String id) {
        if (id == null) return false;
        boolean removed = arenas.remove(id.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public void setArenaPlayerSpawn(String id, Location loc) {
        BossEventArenaConfig arena = requireArena(id);
        arenas.put(arena.getId(), new BossEventArenaConfig(
                arena.getId(), loc, arena.getBossSpawn(), arena.getRoundBosses()));
        save();
    }

    public void setArenaBossSpawn(String id, Location loc) {
        BossEventArenaConfig arena = requireArena(id);
        arenas.put(arena.getId(), new BossEventArenaConfig(
                arena.getId(), arena.getPlayerSpawn(), loc, arena.getRoundBosses()));
        save();
    }

    public void setArenaRoundBoss(String id, int round, String mobId) {
        BossEventArenaConfig arena = requireArena(id);
        String[] bosses = arena.getRoundBosses();
        if (round >= 1 && round <= bosses.length) {
            bosses[round - 1] = mobId;
        }
        arenas.put(arena.getId(), new BossEventArenaConfig(
                arena.getId(), arena.getPlayerSpawn(), arena.getBossSpawn(), bosses));
        save();
    }

    private BossEventArenaConfig requireArena(String id) {
        BossEventArenaConfig arena = getArena(id);
        if (arena == null) {
            throw new IllegalArgumentException("Arena '" + id + "' bestaat niet.");
        }
        return arena;
    }

    public Location getFinalPlayerSpawn() {
        return finalPlayerSpawn != null ? finalPlayerSpawn.clone() : null;
    }

    public void setFinalPlayerSpawn(Location finalPlayerSpawn) {
        this.finalPlayerSpawn = finalPlayerSpawn;
        save();
    }

    public Location getFinalBossSpawn() {
        return finalBossSpawn != null ? finalBossSpawn.clone() : null;
    }

    public void setFinalBossSpawn(Location finalBossSpawn) {
        this.finalBossSpawn = finalBossSpawn;
        save();
    }

    public String getFinalBoss() {
        return finalBoss;
    }

    public void setFinalBoss(String finalBoss) {
        this.finalBoss = finalBoss;
        save();
    }

    public int getPlayersPerArena() {
        return playersPerArena;
    }

    public void setPlayersPerArena(int playersPerArena) {
        this.playersPerArena = Math.max(1, playersPerArena);
        save();
    }

    public int getArenaRounds() {
        return arenaRounds;
    }

    public void setArenaRounds(int arenaRounds) {
        this.arenaRounds = Math.max(1, Math.min(3, arenaRounds));
        save();
    }

    public int getRoundTimeoutMinutes() {
        return roundTimeoutMinutes;
    }

    public void setRoundTimeoutMinutes(int roundTimeoutMinutes) {
        this.roundTimeoutMinutes = Math.max(2, roundTimeoutMinutes);
        save();
    }

    public long getRoundTimeoutMs() {
        return roundTimeoutMinutes * 60L * 1000L;
    }

    public int getIntermissionSeconds() {
        return intermissionSeconds;
    }

    public void setIntermissionSeconds(int intermissionSeconds) {
        this.intermissionSeconds = Math.max(3, intermissionSeconds);
        save();
    }

    public long getIntermissionMs() {
        return intermissionSeconds * 1000L;
    }

    public int getSurvivorPoints() {
        return survivorPoints;
    }

    public void setSurvivorPoints(int survivorPoints) {
        this.survivorPoints = Math.max(0, survivorPoints);
        save();
    }

    public int getFinalBonusPoints() {
        return finalBonusPoints;
    }

    public void setFinalBonusPoints(int finalBonusPoints) {
        this.finalBonusPoints = Math.max(0, finalBonusPoints);
        save();
    }

    public int getFinalConsolationPoints() {
        return finalConsolationPoints;
    }

    public void setFinalConsolationPoints(int finalConsolationPoints) {
        this.finalConsolationPoints = Math.max(0, finalConsolationPoints);
        save();
    }

    public int getMvpPoints() {
        return mvpPoints;
    }

    public void setMvpPoints(int mvpPoints) {
        this.mvpPoints = Math.max(0, mvpPoints);
        save();
    }

    public int requiredArenaCount(int playerCount) {
        return (int) Math.ceil((double) playerCount / playersPerArena);
    }

    /**
     * Leesbare lijst van wat nog ontbreekt voor {@link #isReady()}.
     */
    public List<String> getSetupIssues() {
        List<String> issues = new ArrayList<>();

        if (!mythic.isAvailable()) {
            issues.add("MythicMobs is niet geladen op de server");
        }
        if (arenas.isEmpty()) {
            issues.add("Geen arenas — /bossevent addarena <id>");
        }

        for (BossEventArenaConfig arena : arenas.values()) {
            collectArenaIssues(issues, arena);
        }

        if (finalPlayerSpawn == null) {
            issues.add("Finaal speler-spawn ontbreekt — /bossevent setfinalspawn");
        }
        if (finalBossSpawn == null) {
            issues.add("Finaal boss-spawn ontbreekt — /bossevent setfinalbossspawn");
        }
        if (finalBoss == null || finalBoss.isBlank()) {
            issues.add("Finaal boss ontbreekt — /bossevent setfinalboss <MythicMob>");
        } else if (mythic.isAvailable() && !mythic.mobExists(finalBoss)) {
            issues.add("Finaal boss '" + finalBoss + "' bestaat niet in MythicMobs");
        }

        return issues;
    }

    private void collectArenaIssues(List<String> issues, BossEventArenaConfig arena) {
        String id = arena.getId();
        if (arena.getPlayerSpawn() == null) {
            issues.add("Arena '" + id + "': speler-spawn — /bossevent setarenaspawn " + id);
        }
        if (arena.getBossSpawn() == null) {
            issues.add("Arena '" + id + "': boss-spawn — /bossevent setarenabossspawn " + id);
        }
        for (int round = 1; round <= arenaRounds; round++) {
            String boss = arena.getRoundBoss(round);
            if (boss == null) {
                issues.add("Arena '" + id + "': ronde " + round + " boss — /bossevent setarenaboss "
                        + id + " " + round + " <MythicMob>");
            } else if (mythic.isAvailable() && !mythic.mobExists(boss)) {
                issues.add("Arena '" + id + "': ronde " + round + " boss '" + boss + "' bestaat niet in MythicMobs");
            }
        }
    }

    public boolean isReady() {
        return getSetupIssues().isEmpty();
    }
}
