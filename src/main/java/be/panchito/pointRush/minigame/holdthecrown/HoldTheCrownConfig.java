package be.panchito.pointRush.minigame.holdthecrown;

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
 * Hold the Crown arena onder sectie {@code holdthecrown} in {@code settings.yml}.
 */
public final class HoldTheCrownConfig {

    public static final int DEFAULT_POINT_SECONDS = 60;
    public static final int DEFAULT_DURATION_MINUTES = 30;
    public static final double DEFAULT_PICKUP_RADIUS = 3.0;

    private static final String KEY = "holdthecrown";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location redSpawn;
    private Location blueSpawn;
    private Location center;
    private int pointSeconds = DEFAULT_POINT_SECONDS;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private double pickupRadius = DEFAULT_PICKUP_RADIUS;

    public HoldTheCrownConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        redSpawn = null;
        blueSpawn = null;
        center = null;
        pointSeconds = DEFAULT_POINT_SECONDS;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        pickupRadius = DEFAULT_PICKUP_RADIUS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        redSpawn = loadLocation(cfg, KEY + ".redSpawn");
        blueSpawn = loadLocation(cfg, KEY + ".blueSpawn");
        center = loadLocation(cfg, KEY + ".center");
        if (cfg.isSet(KEY + ".pointSeconds")) {
            pointSeconds = Math.max(15, cfg.getInt(KEY + ".pointSeconds"));
        }
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(5, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".pickupRadius")) {
            pickupRadius = Math.max(1.0, cfg.getDouble(KEY + ".pickupRadius"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (redSpawn != null) saveLocation(cfg, KEY + ".redSpawn", redSpawn);
        if (blueSpawn != null) saveLocation(cfg, KEY + ".blueSpawn", blueSpawn);
        if (center != null) saveLocation(cfg, KEY + ".center", center);
        cfg.set(KEY + ".pointSeconds", pointSeconds);
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".pickupRadius", pickupRadius);
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

    public Location getRedSpawn() {
        return redSpawn;
    }

    public void setRedSpawn(Location redSpawn) {
        this.redSpawn = redSpawn;
        save();
    }

    public Location getBlueSpawn() {
        return blueSpawn;
    }

    public void setBlueSpawn(Location blueSpawn) {
        this.blueSpawn = blueSpawn;
        save();
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
        save();
    }

    public Location getSpawn(be.panchito.pointRush.minigame.ctf.CtfSide side) {
        return side == be.panchito.pointRush.minigame.ctf.CtfSide.RED ? redSpawn : blueSpawn;
    }

    public int getPointSeconds() {
        return pointSeconds;
    }

    public void setPointSeconds(int pointSeconds) {
        this.pointSeconds = Math.max(15, pointSeconds);
        save();
    }

    public long getPointIntervalMs() {
        return pointSeconds * 1000L;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = Math.max(5, durationMinutes);
        save();
    }

    public long getDurationMs() {
        return durationMinutes * 60L * 1000L;
    }

    public double getPickupRadius() {
        return pickupRadius;
    }

    public void setPickupRadius(double pickupRadius) {
        this.pickupRadius = Math.max(1.0, pickupRadius);
        save();
    }

    public boolean isNearCenter(Location playerLoc) {
        Location c = center;
        if (c == null || playerLoc.getWorld() == null) return false;
        if (c.getWorld() != playerLoc.getWorld()) return false;
        return c.distanceSquared(playerLoc) <= pickupRadius * pickupRadius;
    }

    public boolean isReady() {
        return redSpawn != null && blueSpawn != null && center != null;
    }
}
