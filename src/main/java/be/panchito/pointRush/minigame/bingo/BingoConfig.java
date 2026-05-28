package be.panchito.pointRush.minigame.bingo;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Bingo instellingen onder {@code bingo} in {@code settings.yml}.
 */
public final class BingoConfig {

    public static final int DEFAULT_DURATION_MINUTES = 60;

    private static final String KEY = "bingo";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private List<Material> materialPool = BingoMaterialPool.defaultPoolCopy();

    public BingoConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        materialPool = BingoMaterialPool.defaultPoolCopy();

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        spawn = loadLocation(cfg, KEY + ".spawn");
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(5, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".material-pool")) {
            materialPool = BingoMaterialPool.parseFromYaml(cfg.getList(KEY + ".material-pool"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".spawn", null);
        if (spawn != null) {
            saveLocation(cfg, KEY + ".spawn", spawn);
        }
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".material-pool", BingoMaterialPool.toYamlNames(materialPool));
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": pool="
                    + materialPool.size() + ").");
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
        this.spawn = spawn.clone();
        save();
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

    public List<Material> getMaterialPool() {
        return Collections.unmodifiableList(new ArrayList<>(materialPool));
    }

    public boolean isReady() {
        return materialPool.size() >= BingoGrid.RANDOM_SLOTS;
    }
}
