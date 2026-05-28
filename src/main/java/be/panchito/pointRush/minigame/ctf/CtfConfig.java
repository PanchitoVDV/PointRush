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
    public static final int DEFAULT_ROUNDS = 2;
    public static final int DEFAULT_POINTS_PER_CAPTURE = 75;
    public static final double DEFAULT_DELIVERY_RADIUS = 3.0;

    private static final String KEY = "ctf";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location flagSpawn;
    private Location redSpawn;
    private Location blueSpawn;
    private Location redDelivery;
    private Location blueDelivery;
    private int roundMinutes = DEFAULT_ROUND_MINUTES;
    private int rounds = DEFAULT_ROUNDS;
    private int pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
    private double deliveryRadius = DEFAULT_DELIVERY_RADIUS;

    public CtfConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        flagSpawn = null;
        redSpawn = null;
        blueSpawn = null;
        redDelivery = null;
        blueDelivery = null;
        roundMinutes = DEFAULT_ROUND_MINUTES;
        rounds = DEFAULT_ROUNDS;
        pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
        deliveryRadius = DEFAULT_DELIVERY_RADIUS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        flagSpawn = loadLocation(cfg, KEY + ".flagSpawn");
        redSpawn = loadLocation(cfg, KEY + ".redSpawn");
        blueSpawn = loadLocation(cfg, KEY + ".blueSpawn");
        redDelivery = loadLocation(cfg, KEY + ".redDelivery");
        blueDelivery = loadLocation(cfg, KEY + ".blueDelivery");
        if (cfg.isSet(KEY + ".roundMinutes")) {
            roundMinutes = Math.max(2, cfg.getInt(KEY + ".roundMinutes"));
        }
        if (cfg.isSet(KEY + ".rounds")) {
            rounds = Math.max(2, cfg.getInt(KEY + ".rounds"));
            if (rounds % 2 != 0) {
                rounds++;
            }
        }
        if (cfg.isSet(KEY + ".pointsPerCapture")) {
            pointsPerCapture = Math.max(10, cfg.getInt(KEY + ".pointsPerCapture"));
        }
        if (cfg.isSet(KEY + ".deliveryRadius")) {
            deliveryRadius = Math.max(1.0, cfg.getDouble(KEY + ".deliveryRadius"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (flagSpawn != null) saveLocation(cfg, KEY + ".flagSpawn", flagSpawn);
        if (redSpawn != null) saveLocation(cfg, KEY + ".redSpawn", redSpawn);
        if (blueSpawn != null) saveLocation(cfg, KEY + ".blueSpawn", blueSpawn);
        if (redDelivery != null) saveLocation(cfg, KEY + ".redDelivery", redDelivery);
        if (blueDelivery != null) saveLocation(cfg, KEY + ".blueDelivery", blueDelivery);
        cfg.set(KEY + ".roundMinutes", roundMinutes);
        cfg.set(KEY + ".rounds", rounds);
        cfg.set(KEY + ".pointsPerCapture", pointsPerCapture);
        cfg.set(KEY + ".deliveryRadius", deliveryRadius);
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

    public Location getFlagSpawn() {
        return flagSpawn;
    }

    public void setFlagSpawn(Location flagSpawn) {
        this.flagSpawn = flagSpawn;
        save();
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

    public Location getRedDelivery() {
        return redDelivery;
    }

    public void setRedDelivery(Location redDelivery) {
        this.redDelivery = redDelivery;
        save();
    }

    public Location getBlueDelivery() {
        return blueDelivery;
    }

    public void setBlueDelivery(Location blueDelivery) {
        this.blueDelivery = blueDelivery;
        save();
    }

    public Location getSpawn(CtfSide side) {
        return side == CtfSide.RED ? redSpawn : blueSpawn;
    }

    public Location getDelivery(CtfSide side) {
        return side == CtfSide.RED ? redDelivery : blueDelivery;
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
        this.rounds = Math.max(2, rounds);
        if (this.rounds % 2 != 0) {
            this.rounds++;
        }
        save();
    }

    public int getPointsPerCapture() {
        return pointsPerCapture;
    }

    public void setPointsPerCapture(int pointsPerCapture) {
        this.pointsPerCapture = Math.max(10, pointsPerCapture);
        save();
    }

    public double getDeliveryRadius() {
        return deliveryRadius;
    }

    public void setDeliveryRadius(double deliveryRadius) {
        this.deliveryRadius = Math.max(1.0, deliveryRadius);
        save();
    }

    public boolean isNearDelivery(CtfSide side, Location playerLoc) {
        Location delivery = getDelivery(side);
        if (delivery == null || playerLoc.getWorld() == null) return false;
        if (delivery.getWorld() != playerLoc.getWorld()) return false;
        return delivery.distanceSquared(playerLoc) <= deliveryRadius * deliveryRadius;
    }

    public boolean isReady() {
        return flagSpawn != null && redSpawn != null && blueSpawn != null
                && redDelivery != null && blueDelivery != null;
    }
}
