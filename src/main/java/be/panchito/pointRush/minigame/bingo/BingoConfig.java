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
    private boolean hasSpawn = false;
    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;
    private boolean spawnWorldWarned = false;
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private List<Material> materialPool = BingoMaterialPool.defaultPoolCopy();

    public BingoConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        hasSpawn = false;
        spawnWorld = null;
        spawnWorldWarned = false;
        durationMinutes = DEFAULT_DURATION_MINUTES;
        materialPool = BingoMaterialPool.defaultPoolCopy();

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        loadSpawnRaw(cfg, KEY + ".spawn");
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
        if (hasSpawn && spawnWorld != null) {
            cfg.set(KEY + ".spawn.world", spawnWorld);
            cfg.set(KEY + ".spawn.x", spawnX);
            cfg.set(KEY + ".spawn.y", spawnY);
            cfg.set(KEY + ".spawn.z", spawnZ);
            cfg.set(KEY + ".spawn.yaw", spawnYaw);
            cfg.set(KEY + ".spawn.pitch", spawnPitch);
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

    private void loadSpawnRaw(YamlConfiguration cfg, String path) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return;
        String worldName = sec.getString("world");
        if (worldName == null) return;
        spawnWorld = worldName;
        spawnX = sec.getDouble("x");
        spawnY = sec.getDouble("y");
        spawnZ = sec.getDouble("z");
        spawnYaw = (float) sec.getDouble("yaw");
        spawnPitch = (float) sec.getDouble("pitch");
        hasSpawn = true;
    }

    /**
     * Resolvet de spawn lui: de wereld kan later geladen zijn (bv. via Multiverse) dan deze plugin.
     */
    public Location getSpawn() {
        if (!hasSpawn || spawnWorld == null) {
            return null;
        }
        if (spawn != null) {
            return spawn;
        }
        World world = Bukkit.getWorld(spawnWorld);
        if (world == null) {
            if (!spawnWorldWarned) {
                spawnWorldWarned = true;
                plugin.getLogger().warning("Bingo spawn-wereld '" + spawnWorld
                        + "' is (nog) niet geladen; teleport wordt overgeslagen.");
            }
            return null;
        }
        spawn = new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
        return spawn;
    }

    public void setSpawn(Location loc) {
        this.spawn = loc.clone();
        this.spawnWorld = loc.getWorld() != null ? loc.getWorld().getName() : null;
        this.spawnX = loc.getX();
        this.spawnY = loc.getY();
        this.spawnZ = loc.getZ();
        this.spawnYaw = loc.getYaw();
        this.spawnPitch = loc.getPitch();
        this.hasSpawn = this.spawnWorld != null;
        this.spawnWorldWarned = false;
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
