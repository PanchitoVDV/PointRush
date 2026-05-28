package be.panchito.pointRush.minigame.koth;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Wipeout-style KOTH arena onder sectie {@code koth} in {@code settings.yml}.
 */
public final class KothConfig {

    public static final int DEFAULT_POINT_SECONDS = 60;
    public static final int DEFAULT_DURATION_MINUTES = 30;
    public static final int DEFAULT_SPOT_RESPAWN_SECONDS = 45;

    private static final String KEY = "koth";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private Location regionMin;
    private Location regionMax;
    private int pointSeconds = DEFAULT_POINT_SECONDS;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private int spotRespawnSeconds = DEFAULT_SPOT_RESPAWN_SECONDS;
    private final Map<String, KothPowerSpot> powerSpots = new LinkedHashMap<>();

    public KothConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        regionMin = null;
        regionMax = null;
        pointSeconds = DEFAULT_POINT_SECONDS;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        spotRespawnSeconds = DEFAULT_SPOT_RESPAWN_SECONDS;
        powerSpots.clear();

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        this.spawn = loadLocation(cfg, KEY + ".spawn");
        Location a = loadLocation(cfg, KEY + ".region.a");
        Location b = loadLocation(cfg, KEY + ".region.b");
        if (a != null && b != null) {
            normalizeRegion(a, b);
        }
        if (cfg.isSet(KEY + ".pointSeconds")) {
            pointSeconds = Math.max(15, cfg.getInt(KEY + ".pointSeconds"));
        }
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(5, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".spotRespawnSeconds")) {
            spotRespawnSeconds = Math.max(10, cfg.getInt(KEY + ".spotRespawnSeconds"));
        }
        loadSpots(cfg);
    }

    private void loadSpots(YamlConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection(KEY + ".spots");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            Location loc = loadLocation(cfg, KEY + ".spots." + id);
            if (loc == null) continue;
            String typeRaw = sec.getString(id + ".type", "random");
            powerSpots.put(id, new KothPowerSpot(id, loc, KothSpotType.fromConfig(typeRaw)));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (spawn != null) saveLocation(cfg, KEY + ".spawn", spawn);
        if (regionMin != null) saveLocation(cfg, KEY + ".region.a", regionMin);
        if (regionMax != null) saveLocation(cfg, KEY + ".region.b", regionMax);
        cfg.set(KEY + ".pointSeconds", pointSeconds);
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".spotRespawnSeconds", spotRespawnSeconds);
        cfg.set(KEY + ".spots", null);
        for (KothPowerSpot spot : powerSpots.values()) {
            String base = KEY + ".spots." + spot.getId();
            saveLocation(cfg, base, spot.getLocation());
            cfg.set(base + ".type", spot.getType().getConfigKey());
        }
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawn=" + (spawn != null)
                    + ", region=" + (regionMin != null && regionMax != null)
                    + ", spots=" + powerSpots.size() + ").");
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

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
        save();
    }

    public Location getRegionMin() {
        return regionMin;
    }

    public Location getRegionMax() {
        return regionMax;
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

    public int getPointSeconds() {
        return pointSeconds;
    }

    public void setPointSeconds(int seconds) {
        this.pointSeconds = Math.max(15, seconds);
        save();
    }

    public long getPointIntervalMs() {
        return pointSeconds * 1000L;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int minutes) {
        this.durationMinutes = Math.max(5, minutes);
        save();
    }

    public long getDurationMs() {
        return durationMinutes * 60L * 1000L;
    }

    public int getSpotRespawnSeconds() {
        return spotRespawnSeconds;
    }

    public void setSpotRespawnSeconds(int seconds) {
        this.spotRespawnSeconds = Math.max(10, seconds);
        save();
    }

    public long getSpotRespawnMs() {
        return spotRespawnSeconds * 1000L;
    }

    public List<KothPowerSpot> getPowerSpots() {
        return Collections.unmodifiableList(new ArrayList<>(powerSpots.values()));
    }

    public KothPowerSpot addPowerSpot(Location location, KothSpotType type) {
        String id = "spot-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        KothPowerSpot spot = new KothPowerSpot(id, location.clone(), type);
        powerSpots.put(id, spot);
        save();
        return spot;
    }

    public KothPowerSpot removeNearestSpot(Location from, double maxDistance) {
        if (from.getWorld() == null) return null;
        KothPowerSpot nearest = null;
        double best = maxDistance * maxDistance;
        for (KothPowerSpot spot : powerSpots.values()) {
            Location loc = spot.getLocation();
            if (loc.getWorld() != from.getWorld()) continue;
            double distSq = loc.distanceSquared(from);
            if (distSq <= best) {
                best = distSq;
                nearest = spot;
            }
        }
        if (nearest != null) {
            powerSpots.remove(nearest.getId());
            save();
        }
        return nearest;
    }

    public boolean contains(Location loc) {
        if (regionMin == null || regionMax == null || loc.getWorld() == null) return false;
        if (loc.getWorld() != regionMin.getWorld()) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= regionMin.getBlockX() && bx <= regionMax.getBlockX()
                && by >= regionMin.getBlockY() - 1 && by <= regionMax.getBlockY()
                && bz >= regionMin.getBlockZ() && bz <= regionMax.getBlockZ();
    }

    public boolean isReady() {
        return spawn != null && regionMin != null && regionMax != null;
    }
}
