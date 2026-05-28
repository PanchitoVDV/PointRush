package be.panchito.pointRush.minigame.race;

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
 * Race setup onder sectie {@code race} in {@code settings.yml}.
 */
public final class RaceConfig {

    public static final int DEFAULT_LAPS = 3;

    private static final String KEY = "race";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location lobbySpawn;
    private Location finish;
    private Location startLightsGantry;
    private final List<Location> grid = new ArrayList<>();
    private final List<String> vehiclePlates = new ArrayList<>();
    private final List<Location> checkpoints = new ArrayList<>();
    private int laps = DEFAULT_LAPS;

    public RaceConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        lobbySpawn = null;
        finish = null;
        startLightsGantry = null;
        grid.clear();
        vehiclePlates.clear();
        checkpoints.clear();
        laps = DEFAULT_LAPS;

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
        loadOrderedLocations(cfg, KEY + ".grid", grid);
        loadOrderedLocations(cfg, KEY + ".checkpoints", checkpoints);

        ConfigurationSection plates = cfg.getConfigurationSection(KEY + ".vehiclePlates");
        if (plates != null) {
            List<String> plateKeys = new ArrayList<>(plates.getKeys(false));
            plateKeys.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException ex) {
                    return a.compareTo(b);
                }
            });
            for (String pk : plateKeys) {
                String plate = plates.getString(pk);
                if (plate != null && !plate.isBlank()) {
                    vehiclePlates.add(plate.trim());
                }
            }
        }
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
        for (int i = 0; i < grid.size(); i++) {
            saveLocation(cfg, KEY + ".grid." + (i + 1), grid.get(i));
        }
        for (int i = 0; i < checkpoints.size(); i++) {
            saveLocation(cfg, KEY + ".checkpoints." + (i + 1), checkpoints.get(i));
        }
        for (int i = 0; i < vehiclePlates.size(); i++) {
            cfg.set(KEY + ".vehiclePlates." + (i + 1), vehiclePlates.get(i));
        }

        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": grid=" + grid.size()
                    + ", vehicles=" + vehiclePlates.size()
                    + ", checkpoints=" + checkpoints.size()
                    + ", finish=" + (finish != null)
                    + ", startLights=" + (startLightsGantry != null)
                    + ", laps=" + laps + ").");
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

    public List<String> getVehiclePlates() {
        return vehiclePlates;
    }

    public void addVehiclePlate(String plate) {
        vehiclePlates.add(plate);
        save();
    }

    public void clearVehiclePlates() {
        vehiclePlates.clear();
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

    public int getCapacity() {
        return Math.min(grid.size(), vehiclePlates.size());
    }

    public boolean isReady() {
        return getCapacity() >= 1 && finish != null && !checkpoints.isEmpty();
    }
}
