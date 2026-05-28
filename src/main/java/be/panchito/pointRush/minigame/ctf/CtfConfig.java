package be.panchito.pointRush.minigame.ctf;

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
 * Capture the Flag arena onder sectie {@code ctf} in {@code settings.yml}.
 */
public final class CtfConfig {

    public static final int DEFAULT_ROUND_MINUTES = 5;
    public static final int DEFAULT_ROUNDS = 3;
    public static final int DEFAULT_HIDE_PHASE_SECONDS = 60;
    public static final int DEFAULT_POINTS_PER_CAPTURE = 75;
    public static final double DEFAULT_CAPTURE_RADIUS = 4.0;

    private static final String KEY = "ctf";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location redSpawn;
    private Location blueSpawn;
    private int roundMinutes = DEFAULT_ROUND_MINUTES;
    private int rounds = DEFAULT_ROUNDS;
    private int hidePhaseSeconds = DEFAULT_HIDE_PHASE_SECONDS;
    private int pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
    private double captureRadius = DEFAULT_CAPTURE_RADIUS;

    public CtfConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        redSpawn = null;
        blueSpawn = null;
        roundMinutes = DEFAULT_ROUND_MINUTES;
        rounds = DEFAULT_ROUNDS;
        hidePhaseSeconds = DEFAULT_HIDE_PHASE_SECONDS;
        pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
        captureRadius = DEFAULT_CAPTURE_RADIUS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        redSpawn = loadLocation(cfg, KEY + ".redSpawn");
        blueSpawn = loadLocation(cfg, KEY + ".blueSpawn");
        if (cfg.isSet(KEY + ".roundMinutes")) {
            roundMinutes = Math.max(2, cfg.getInt(KEY + ".roundMinutes"));
        }
        if (cfg.isSet(KEY + ".rounds")) {
            rounds = Math.max(1, cfg.getInt(KEY + ".rounds"));
        }
        if (cfg.isSet(KEY + ".hidePhaseSeconds")) {
            hidePhaseSeconds = Math.max(15, cfg.getInt(KEY + ".hidePhaseSeconds"));
        }
        if (cfg.isSet(KEY + ".pointsPerCapture")) {
            pointsPerCapture = Math.max(10, cfg.getInt(KEY + ".pointsPerCapture"));
        }
        if (cfg.isSet(KEY + ".captureRadius")) {
            captureRadius = Math.max(1.0, cfg.getDouble(KEY + ".captureRadius"));
        } else if (cfg.isSet(KEY + ".deliveryRadius")) {
            captureRadius = Math.max(1.0, cfg.getDouble(KEY + ".deliveryRadius"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".flagSpawn", null);
        cfg.set(KEY + ".redDelivery", null);
        cfg.set(KEY + ".blueDelivery", null);
        if (redSpawn != null) saveLocation(cfg, KEY + ".redSpawn", redSpawn);
        if (blueSpawn != null) saveLocation(cfg, KEY + ".blueSpawn", blueSpawn);
        cfg.set(KEY + ".roundMinutes", roundMinutes);
        cfg.set(KEY + ".rounds", rounds);
        cfg.set(KEY + ".hidePhaseSeconds", hidePhaseSeconds);
        cfg.set(KEY + ".pointsPerCapture", pointsPerCapture);
        cfg.set(KEY + ".captureRadius", captureRadius);
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

    public Location getSpawn(CtfSide side) {
        return side == CtfSide.RED ? redSpawn : blueSpawn;
    }

    public int getRoundMinutes() {
        return roundMinutes;
    }

    public void setRoundMinutes(int roundMinutes) {
        this.roundMinutes = Math.max(2, roundMinutes);
        save();
    }

    public long getRoundDurationMs() {
        return roundMinutes * 60L * 1000L;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = Math.max(1, rounds);
        save();
    }

    public int getHidePhaseSeconds() {
        return hidePhaseSeconds;
    }

    public void setHidePhaseSeconds(int hidePhaseSeconds) {
        this.hidePhaseSeconds = Math.max(15, hidePhaseSeconds);
        save();
    }

    public long getHidePhaseMs() {
        return hidePhaseSeconds * 1000L;
    }

    public int getPointsPerCapture() {
        return pointsPerCapture;
    }

    public void setPointsPerCapture(int pointsPerCapture) {
        this.pointsPerCapture = Math.max(10, pointsPerCapture);
        save();
    }

    public double getCaptureRadius() {
        return captureRadius;
    }

    public void setCaptureRadius(double captureRadius) {
        this.captureRadius = Math.max(1.0, captureRadius);
        save();
    }

    public boolean isNearSpawn(CtfSide side, Location playerLoc) {
        Location spawn = getSpawn(side);
        if (spawn == null || playerLoc.getWorld() == null) return false;
        if (spawn.getWorld() != playerLoc.getWorld()) return false;
        return spawn.distanceSquared(playerLoc) <= captureRadius * captureRadius;
    }

    public boolean isReady() {
        return redSpawn != null && blueSpawn != null;
    }
}
