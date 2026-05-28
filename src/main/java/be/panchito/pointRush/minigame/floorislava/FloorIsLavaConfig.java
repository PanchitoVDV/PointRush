package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

/**
 * Floor is Lava arena onder sectie {@code floorislava} in {@code settings.yml}.
 */
public final class FloorIsLavaConfig {

    public static final int DEFAULT_LAVA_RISE_SECONDS = 60;
    public static final int DEFAULT_ITEM_DROP_SECONDS = 30;

    private static final String KEY = "floorislava";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private Location regionMin;
    private Location regionMax;
    private Double deathY;
    private int lavaRiseSeconds = DEFAULT_LAVA_RISE_SECONDS;
    private int itemDropSeconds = DEFAULT_ITEM_DROP_SECONDS;

    public FloorIsLavaConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        regionMin = null;
        regionMax = null;
        deathY = null;
        lavaRiseSeconds = DEFAULT_LAVA_RISE_SECONDS;
        itemDropSeconds = DEFAULT_ITEM_DROP_SECONDS;

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
        if (cfg.isSet(KEY + ".deathY")) {
            this.deathY = cfg.getDouble(KEY + ".deathY");
        }
        if (cfg.isSet(KEY + ".lavaRiseSeconds")) {
            this.lavaRiseSeconds = Math.max(30, cfg.getInt(KEY + ".lavaRiseSeconds"));
        }
        if (cfg.isSet(KEY + ".itemDropSeconds")) {
            this.itemDropSeconds = Math.max(10, cfg.getInt(KEY + ".itemDropSeconds"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (spawn != null) saveLocation(cfg, KEY + ".spawn", spawn);
        if (regionMin != null) saveLocation(cfg, KEY + ".region.a", regionMin);
        if (regionMax != null) saveLocation(cfg, KEY + ".region.b", regionMax);
        if (deathY != null) cfg.set(KEY + ".deathY", deathY);
        cfg.set(KEY + ".lavaRiseSeconds", lavaRiseSeconds);
        cfg.set(KEY + ".itemDropSeconds", itemDropSeconds);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawn=" + (spawn != null)
                    + ", region=" + (regionMin != null && regionMax != null)
                    + ", deathY=" + deathY
                    + ", lavaRiseSeconds=" + lavaRiseSeconds
                    + ", itemDropSeconds=" + itemDropSeconds + ").");
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

    public double getDeathY() {
        if (deathY != null) return deathY;
        if (regionMin != null) return regionMin.getY() - 5.0;
        return Double.NEGATIVE_INFINITY;
    }

    public void setDeathY(double y) {
        this.deathY = y;
        save();
    }

    public int getLavaRiseSeconds() {
        return lavaRiseSeconds;
    }

    public void setLavaRiseSeconds(int seconds) {
        this.lavaRiseSeconds = Math.max(30, seconds);
        save();
    }

    public int getItemDropSeconds() {
        return itemDropSeconds;
    }

    public void setItemDropSeconds(int seconds) {
        this.itemDropSeconds = Math.max(10, seconds);
        save();
    }

    public long getLavaRiseIntervalTicks() {
        return lavaRiseSeconds * 20L;
    }

    public long getItemDropIntervalTicks() {
        return itemDropSeconds * 20L;
    }

    /** Horizontaal binnen arena (toren bouwen mag omhoog). */
    public boolean canBuildAt(Location loc) {
        if (regionMin == null || regionMax == null || loc.getWorld() == null) return false;
        if (loc.getWorld() != regionMin.getWorld()) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= regionMin.getBlockX() && bx <= regionMax.getBlockX()
                && bz >= regionMin.getBlockZ() && bz <= regionMax.getBlockZ()
                && by >= regionMin.getBlockY() - 1;
    }

    public boolean isReady() {
        return spawn != null && regionMin != null && regionMax != null;
    }
}
