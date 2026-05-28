package be.panchito.pointRush.minigame.tntrun;

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
 * TNT Run arena onder sectie {@code tntrun} in {@code settings.yml}.
 */
public final class TntRunConfig {

    private static final String KEY = "tntrun";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private Location regionMin;
    private Location regionMax;
    private Double deathY;

    public TntRunConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        regionMin = null;
        regionMax = null;
        deathY = null;
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
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (spawn != null) saveLocation(cfg, KEY + ".spawn", spawn);
        if (regionMin != null) saveLocation(cfg, KEY + ".region.a", regionMin);
        if (regionMax != null) saveLocation(cfg, KEY + ".region.b", regionMax);
        if (deathY != null) cfg.set(KEY + ".deathY", deathY);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawn=" + (spawn != null)
                    + ", region=" + (regionMin != null && regionMax != null)
                    + ", deathY=" + deathY + ").");
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
