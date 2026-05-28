package be.panchito.pointRush.minigame.treasurehunt;

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
 * Treasure Hunt onder sectie {@code treasurehunt} in {@code settings.yml}.
 */
public final class TreasureHuntConfig {

    public static final int DEFAULT_HINT_INTERVAL_SECONDS = 120;
    public static final int DEFAULT_DURATION_MINUTES = 45;
    public static final double DEFAULT_CLAIM_RADIUS = 2.5;

    private static final String KEY = "treasurehunt";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private int hintIntervalSeconds = DEFAULT_HINT_INTERVAL_SECONDS;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private double claimRadius = DEFAULT_CLAIM_RADIUS;
    private final Map<String, TreasureLocation> treasures = new LinkedHashMap<>();

    public TreasureHuntConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        hintIntervalSeconds = DEFAULT_HINT_INTERVAL_SECONDS;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        claimRadius = DEFAULT_CLAIM_RADIUS;
        treasures.clear();

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        if (cfg.isSet(KEY + ".hintIntervalSeconds")) {
            hintIntervalSeconds = Math.max(30, cfg.getInt(KEY + ".hintIntervalSeconds"));
        }
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(5, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".claimRadius")) {
            claimRadius = Math.max(1.0, cfg.getDouble(KEY + ".claimRadius"));
        }
        loadTreasures(cfg);
    }

    private void loadTreasures(YamlConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection(KEY + ".treasures");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            Location loc = loadLocation(cfg, KEY + ".treasures." + id);
            if (loc == null) continue;
            String tierRaw = sec.getString(id + ".tier", "normal");
            String hint = sec.getString(id + ".hint", "Zoek de schat");
            treasures.put(id, new TreasureLocation(id, loc, TreasureTier.fromConfig(tierRaw), hint));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".hintIntervalSeconds", hintIntervalSeconds);
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".claimRadius", claimRadius);
        cfg.set(KEY + ".treasures", null);
        for (TreasureLocation treasure : treasures.values()) {
            String base = KEY + ".treasures." + treasure.getId();
            saveLocation(cfg, base, treasure.getLocation());
            cfg.set(base + ".tier", treasure.getTier().getConfigKey());
            cfg.set(base + ".hint", treasure.getHint());
        }
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": treasures=" + treasures.size() + ").");
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

    public int getHintIntervalSeconds() {
        return hintIntervalSeconds;
    }

    public void setHintIntervalSeconds(int seconds) {
        this.hintIntervalSeconds = Math.max(30, seconds);
        save();
    }

    public long getHintIntervalMs() {
        return hintIntervalSeconds * 1000L;
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

    public double getClaimRadius() {
        return claimRadius;
    }

    public void setClaimRadius(double radius) {
        this.claimRadius = Math.max(1.0, radius);
        save();
    }

    public List<TreasureLocation> getTreasures() {
        return Collections.unmodifiableList(new ArrayList<>(treasures.values()));
    }

    public TreasureLocation getTreasure(String id) {
        return treasures.get(id);
    }

    public TreasureLocation addTreasure(Location location, TreasureTier tier, String hint) {
        String id = "treasure-" + UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        TreasureLocation treasure = new TreasureLocation(id, location.clone(), tier, hint);
        treasures.put(id, treasure);
        save();
        return treasure;
    }

    public TreasureLocation removeNearest(Location from, double maxDistance) {
        if (from.getWorld() == null) return null;
        TreasureLocation nearest = null;
        double best = maxDistance * maxDistance;
        for (TreasureLocation treasure : treasures.values()) {
            Location loc = treasure.getLocation();
            if (loc.getWorld() != from.getWorld()) continue;
            double distSq = loc.distanceSquared(from);
            if (distSq <= best) {
                best = distSq;
                nearest = treasure;
            }
        }
        if (nearest != null) {
            treasures.remove(nearest.getId());
            save();
        }
        return nearest;
    }

    public boolean isReady() {
        return !treasures.isEmpty();
    }
}
