package be.panchito.pointRush.minigame.boatrace;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Bootrace setup onder sectie {@code boatrace} in {@code settings.yml}.
 */
public final class BoatRaceConfig {

    public static final int DEFAULT_LAPS = 3;
    public static final String DEFAULT_BOAT_TYPE = "OAK";

    private static final String KEY = "boatrace";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location lobbySpawn;
    private Location finish;
    private Location startLightsGantry;
    private final List<Location> grid = new ArrayList<>();
    private final List<Location> checkpoints = new ArrayList<>();
    private int laps = DEFAULT_LAPS;
    private String boatType = DEFAULT_BOAT_TYPE;

    public BoatRaceConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        lobbySpawn = null;
        finish = null;
        startLightsGantry = null;
        grid.clear();
        checkpoints.clear();
        laps = DEFAULT_LAPS;
        boatType = DEFAULT_BOAT_TYPE;

        YamlConfiguration cfg = unified.yaml();
        ConfigurationSection root = cfg.getConfigurationSection(KEY);
        if (root == null) {
            return;
        }

        this.lobbySpawn = loadLocation(cfg, KEY + ".lobbySpawn");
        this.finish = loadLocation(cfg, KEY + ".finish");
        this.startLightsGantry = loadLocation(cfg, KEY + ".startLightsGantry");
        if (cfg.isSet(KEY + ".laps")) {
            this.laps = Math.max(1, cfg.getInt(KEY + ".laps"));
        }
        if (cfg.isSet(KEY + ".boatType")) {
            this.boatType = cfg.getString(KEY + ".boatType", DEFAULT_BOAT_TYPE);
        }
        loadOrderedLocations(cfg, KEY + ".grid", grid);
        loadOrderedLocations(cfg, KEY + ".checkpoints", checkpoints);
    }

    private void loadOrderedLocations(YamlConfiguration cfg, String pathPrefix, List<Location> out) {
        ConfigurationSection sec = cfg.getConfigurationSection(pathPrefix);
        if (sec == null) return;
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException ex) {
                return a.compareTo(b);
            }
        });
        for (String key : keys) {
            Location loc = loadLocation(cfg, pathPrefix + "." + key);
            if (loc != null) {
                out.add(loc);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (lobbySpawn != null) saveLocation(cfg, KEY + ".lobbySpawn", lobbySpawn);
        if (finish != null) saveLocation(cfg, KEY + ".finish", finish);
        if (startLightsGantry != null) saveLocation(cfg, KEY + ".startLightsGantry", startLightsGantry);
        cfg.set(KEY + ".laps", laps);
        cfg.set(KEY + ".boatType", boatType);
        for (int i = 0; i < grid.size(); i++) {
            saveLocation(cfg, KEY + ".grid." + (i + 1), grid.get(i));
        }
        for (int i = 0; i < checkpoints.size(); i++) {
            saveLocation(cfg, KEY + ".checkpoints." + (i + 1), checkpoints.get(i));
        }

        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": grid=" + grid.size()
                    + ", checkpoints=" + checkpoints.size()
                    + ", finish=" + (finish != null)
                    + ", startLights=" + (startLightsGantry != null)
                    + ", laps=" + laps
                    + ", boatType=" + boatType + ").");
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

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
        save();
    }

    public Location getFinish() {
        return finish;
    }

    public void setFinish(Location finish) {
        this.finish = finish;
        save();
    }

    public Location getStartLightsGantry() {
        return startLightsGantry;
    }

    public void setStartLightsGantry(Location startLightsGantry) {
        this.startLightsGantry = startLightsGantry;
        save();
    }

    public void clearStartLightsGantry() {
        this.startLightsGantry = null;
        save();
    }

    public List<Location> getGrid() {
        return grid;
    }

    public void addGridSlot(Location loc) {
        grid.add(loc);
        save();
    }

    public void clearGrid() {
        grid.clear();
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

    public int getLaps() {
        return laps;
    }

    public void setLaps(int laps) {
        this.laps = Math.max(1, laps);
        save();
    }

    public String getBoatType() {
        return boatType;
    }

    public void setBoatType(String boatType) {
        this.boatType = boatType != null && !boatType.isBlank()
                ? boatType.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_BOAT_TYPE;
        save();
    }

    /**
     * Resolves {@link #boatType} to a spawnable boat {@link EntityType}.
     * Accepts values like {@code OAK}, {@code OAK_BOAT}, {@code BAMBOO_RAFT}.
     */
    public EntityType getBoatEntityType() {
        String raw = boatType == null ? DEFAULT_BOAT_TYPE : boatType.trim().toUpperCase(Locale.ROOT);
        for (String candidate : List.of(raw, raw + "_BOAT", raw + "_RAFT")) {
            try {
                EntityType type = EntityType.valueOf(candidate);
                if (type.isSpawnable()) {
                    String name = type.name();
                    if (name.endsWith("_BOAT") || name.endsWith("_RAFT")) {
                        return type;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return EntityType.OAK_BOAT;
    }

    public int getCapacity() {
        return grid.size();
    }

    public boolean isReady() {
        return !grid.isEmpty() && finish != null && !checkpoints.isEmpty();
    }
}
