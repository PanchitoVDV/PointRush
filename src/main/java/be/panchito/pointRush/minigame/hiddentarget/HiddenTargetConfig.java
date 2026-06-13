package be.panchito.pointRush.minigame.hiddentarget;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Hidden Target arena onder sectie {@code hiddentarget} in {@code settings.yml}.
 */
public final class HiddenTargetConfig {

    public static final int DEFAULT_DURATION_MINUTES = 10;
    public static final int DEFAULT_KILL_POINTS = 1;
    public static final int DEFAULT_HUNTED_PENALTY = 1;
    public static final int DEFAULT_RESPAWN_SECONDS = 60;

    private static final String KEY = "hiddentarget";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    /** Eén of meer start/respawn-spawns; spelers worden hierover verdeeld. */
    private final List<Location> spawns = new ArrayList<>();
    private Location regionMin;
    private Location regionMax;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private int killPoints = DEFAULT_KILL_POINTS;
    private int huntedPenalty = DEFAULT_HUNTED_PENALTY;
    private int respawnSeconds = DEFAULT_RESPAWN_SECONDS;

    public HiddenTargetConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawns.clear();
        regionMin = null;
        regionMax = null;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        killPoints = DEFAULT_KILL_POINTS;
        huntedPenalty = DEFAULT_HUNTED_PENALTY;
        respawnSeconds = DEFAULT_RESPAWN_SECONDS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        loadSpawns(cfg);
        Location a = loadLocation(cfg, KEY + ".region.a");
        Location b = loadLocation(cfg, KEY + ".region.b");
        if (a != null && b != null) {
            normalizeRegion(a, b);
        }
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(3, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".killPoints")) {
            killPoints = Math.max(1, cfg.getInt(KEY + ".killPoints"));
        }
        if (cfg.isSet(KEY + ".huntedPenalty")) {
            huntedPenalty = Math.max(1, cfg.getInt(KEY + ".huntedPenalty"));
        }
        if (cfg.isSet(KEY + ".respawnSeconds")) {
            respawnSeconds = Math.max(2, cfg.getInt(KEY + ".respawnSeconds"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        for (int i = 0; i < spawns.size(); i++) {
            saveLocation(cfg, KEY + ".spawns." + i, spawns.get(i));
        }
        if (regionMin != null) saveLocation(cfg, KEY + ".region.a", regionMin);
        if (regionMax != null) saveLocation(cfg, KEY + ".region.b", regionMax);
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".killPoints", killPoints);
        cfg.set(KEY + ".huntedPenalty", huntedPenalty);
        cfg.set(KEY + ".respawnSeconds", respawnSeconds);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawns=" + spawns.size()
                    + ", region=" + (regionMin != null && regionMax != null) + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan!", ex);
        }
    }

    private void loadSpawns(YamlConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection(KEY + ".spawns");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Location loc = loadLocation(cfg, KEY + ".spawns." + key);
                if (loc != null) {
                    spawns.add(loc);
                }
            }
        }
        // Migratie: oude config met één enkele 'spawn' wordt het eerste spawnpunt.
        if (spawns.isEmpty()) {
            Location legacy = loadLocation(cfg, KEY + ".spawn");
            if (legacy != null) {
                spawns.add(legacy);
            }
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

    private void normalizeRegion(Location a, Location b) {
        if (a.getWorld() != b.getWorld()) {
            regionMin = a;
            regionMax = b;
            return;
        }
        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX());
        double maxY = Math.max(a.getY(), b.getY());
        double maxZ = Math.max(a.getZ(), b.getZ());
        regionMin = new Location(a.getWorld(), minX, minY, minZ);
        regionMax = new Location(a.getWorld(), maxX, maxY, maxZ);
    }

    /** Primair spawnpunt (eerste in de lijst) — gebruikt als anker voor o.a. vuurwerk. */
    public Location getSpawn() {
        return spawns.isEmpty() ? null : spawns.get(0);
    }

    /** Alle spawnpunten (alleen-lezen). */
    public List<Location> getSpawns() {
        return Collections.unmodifiableList(spawns);
    }

    public int getSpawnCount() {
        return spawns.size();
    }

    /** Willekeurig spawnpunt voor respawns/void — verdeelt spelers over de arena. */
    public Location randomSpawn() {
        if (spawns.isEmpty()) return null;
        if (spawns.size() == 1) return spawns.get(0);
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
    }

    /** Voegt een spawnpunt toe en geeft het nieuwe totaal terug. */
    public int addSpawn(Location loc) {
        spawns.add(loc.clone());
        save();
        return spawns.size();
    }

    /** Wist alle spawnpunten. */
    public void clearSpawns() {
        spawns.clear();
        save();
    }

    /**
     * Verwijdert het spawnpunt het dichtst bij {@code loc} (zelfde wereld). Geeft {@code true}
     * als er iets verwijderd is.
     */
    public boolean removeNearestSpawn(Location loc) {
        int best = -1;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < spawns.size(); i++) {
            Location s = spawns.get(i);
            if (s.getWorld() != loc.getWorld()) continue;
            double dSq = s.distanceSquared(loc);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = i;
            }
        }
        if (best < 0) return false;
        spawns.remove(best);
        save();
        return true;
    }

    public Location getRegionMin() {
        return regionMin;
    }

    public Location getRegionMax() {
        return regionMax;
    }

    public void clearRegion() {
        regionMin = null;
        regionMax = null;
        save();
    }

    public void setCorner(int index, Location loc) {
        if (index == 1) {
            if (regionMax != null) normalizeRegion(loc, regionMax);
            else regionMin = loc.clone();
        } else {
            if (regionMin != null) normalizeRegion(regionMin, loc);
            else regionMax = loc.clone();
        }
        save();
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int minutes) {
        this.durationMinutes = Math.max(3, minutes);
        save();
    }

    public long getDurationMs() {
        return durationMinutes * 60L * 1000L;
    }

    public int getKillPoints() {
        return killPoints;
    }

    public void setKillPoints(int points) {
        this.killPoints = Math.max(1, points);
        save();
    }

    public int getHuntedPenalty() {
        return huntedPenalty;
    }

    public void setHuntedPenalty(int penalty) {
        this.huntedPenalty = Math.max(1, penalty);
        save();
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int seconds) {
        this.respawnSeconds = Math.max(2, seconds);
        save();
    }

    public long getRespawnMs() {
        return respawnSeconds * 1000L;
    }

    public boolean contains(Location loc) {
        if (regionMin == null || regionMax == null || loc.getWorld() == null) return true;
        if (loc.getWorld() != regionMin.getWorld()) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= regionMin.getBlockX() && bx <= regionMax.getBlockX()
                && by >= regionMin.getBlockY() - 1 && by <= regionMax.getBlockY()
                && bz >= regionMin.getBlockZ() && bz <= regionMax.getBlockZ();
    }

    public boolean isReady() {
        // At least one spawn is required: the event teleports everyone there at start and on respawn.
        return !spawns.isEmpty();
    }
}
