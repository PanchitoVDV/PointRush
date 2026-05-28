package be.panchito.pointRush.minigame.parkour;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Parkour spawn / checkpoints / finish onder sectie {@code parkour} in {@code settings.yml}.
 */
public final class ParkourConfig {

    private static final String KEY = "parkour";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private Location finish;
    private final List<Location> checkpoints = new ArrayList<>();

    public ParkourConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        finish = null;
        checkpoints.clear();
        YamlConfiguration cfg = unified.yaml();
        ConfigurationSection root = cfg.getConfigurationSection(KEY);
        if (root == null) {
            return;
        }
        this.spawn = loadLocation(cfg, KEY + ".spawn");
        this.finish = loadLocation(cfg, KEY + ".finish");
        ConfigurationSection cps = cfg.getConfigurationSection(KEY + ".checkpoints");
        if (cps != null) {
            List<String> keys = new ArrayList<>(cps.getKeys(false));
            keys.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException ex) {
                    return a.compareTo(b);
                }
            });
            for (String cpKey : keys) {
                Location loc = loadLocation(cfg, KEY + ".checkpoints." + cpKey);
                if (loc != null) {
                    checkpoints.add(loc);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (spawn != null) saveLocation(cfg, KEY + ".spawn", spawn);
        if (finish != null) saveLocation(cfg, KEY + ".finish", finish);
        for (int i = 0; i < checkpoints.size(); i++) {
            saveLocation(cfg, KEY + ".checkpoints." + (i + 1), checkpoints.get(i));
        }
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawn="
                    + (spawn != null) + ", finish=" + (finish != null)
                    + ", checkpoints=" + checkpoints.size() + ").");
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

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
        save();
    }

    public List<Location> getCheckpoints() {
        return checkpoints;
    }

    public void addCheckpoint(Location loc) {
        checkpoints.add(loc);
        save();
    }

    public void clearCheckpoints() {
        checkpoints.clear();
        save();
    }

    public Location getFinish() {
        return finish;
    }

    public void setFinish(Location finish) {
        this.finish = finish;
        save();
    }

    public boolean isReady() {
        return spawn != null && finish != null;
    }
}
