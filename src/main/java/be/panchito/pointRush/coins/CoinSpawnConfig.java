package be.panchito.pointRush.coins;

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
 * Sectie {@code coins} in {@code settings.yml}: inschakelvlag, pool, refill-interval en spawnpunten.
 */
public final class CoinSpawnConfig {

    private static final String KEY = "coins";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private boolean enabled = true;
    private long refillIntervalTicks = 160L;
    /** Na pickup pas opnieuw spawnen op die plek (20 TPS × 600 s). */
    private long pickupRespawnDelayTicks = 12000L;
    private double proximityRadius = 2.5D;
    private final List<String> pool = new ArrayList<>();
    private final List<Location> spawns = new ArrayList<>();

    public CoinSpawnConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        YamlConfiguration cfg = unified.yaml();
        ConfigurationSection root = cfg.getConfigurationSection(KEY);
        if (root == null) {
            enabled = false;
            pool.clear();
            spawns.clear();
            return;
        }
        enabled = root.getBoolean("enabled", true);
        refillIntervalTicks = Math.max(20L, root.getLong("refill-interval-ticks", 160L));
        pickupRespawnDelayTicks = Math.max(20L, root.getLong("pickup-respawn-delay-ticks", 12000L));
        proximityRadius = Math.max(0.5D, root.getDouble("proximity-radius", 2.5D));

        pool.clear();
        List<String> yamlPool = root.getStringList("pool");
        for (String id : yamlPool) {
            if (id != null && !id.isBlank()) {
                pool.add(id.trim());
            }
        }

        spawns.clear();
        ConfigurationSection spawnSec = cfg.getConfigurationSection(KEY + ".spawns");
        if (spawnSec != null) {
            List<String> keys = new ArrayList<>(spawnSec.getKeys(false));
            keys.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException ex) {
                    return a.compareTo(b);
                }
            });
            for (String spawnKey : keys) {
                Location loc = loadLocation(cfg, KEY + ".spawns." + spawnKey);
                if (loc != null) {
                    spawns.add(loc);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();

        ConfigurationSection coins = cfg.createSection(KEY);
        coins.set("enabled", enabled);
        coins.set("refill-interval-ticks", refillIntervalTicks);
        coins.set("pickup-respawn-delay-ticks", pickupRespawnDelayTicks);
        coins.set("proximity-radius", proximityRadius);
        coins.set("pool", new ArrayList<>(pool));

        cfg.set(KEY + ".spawns", null);
        for (int i = 0; i < spawns.size(); i++) {
            saveLocation(cfg, KEY + ".spawns." + (i + 1), spawns.get(i));
        }

        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (coins: spawns="
                    + spawns.size() + ", pool-overrides=" + pool.size()
                    + ", enabled=" + enabled + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan (coins)!", ex);
        }
    }

    private Location loadLocation(YamlConfiguration cfg, String path) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) {
            return null;
        }
        String worldName = sec.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
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
        if (loc.getWorld() == null) {
            return;
        }
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", loc.getYaw());
        cfg.set(path + ".pitch", loc.getPitch());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public long getRefillIntervalTicks() {
        return refillIntervalTicks;
    }

    public long getPickupRespawnDelayTicks() {
        return pickupRespawnDelayTicks;
    }

    /** Geschatte wachttijd na pickup (~50 ms per tick). */
    public long getPickupRespawnDelayMillisApprox() {
        return pickupRespawnDelayTicks * 50L;
    }

    public void setRefillIntervalTicks(long ticks) {
        this.refillIntervalTicks = Math.max(20L, ticks);
        save();
    }

    public double getProximityRadius() {
        return proximityRadius;
    }

    public List<Location> getSpawns() {
        return spawns;
    }

    public List<String> getResolvedPoolIds() {
        if (!pool.isEmpty()) {
            return List.copyOf(pool);
        }
        return defaultGoldenCoinIds();
    }

    private static List<String> defaultGoldenCoinIds() {
        return List.of(
                "coin_golden_cross_anim",
                "coin_golden_diamond_anim",
                "coin_golden_emerald_anim",
                "coin_golden_heart_anim",
                "coin_golden_hole_anim",
                "coin_golden_long_anim",
                "coin_golden_person_anim",
                "coin_golden_pet_anim",
                "coin_golden_pickaxe_anim",
                "coin_golden_rectangle_anim",
                "coin_golden_redstone_anim",
                "coin_golden_skull_anim"
        );
    }

    public boolean isPoolIdConfigured(String nexoId) {
        List<String> p = getResolvedPoolIds();
        for (String s : p) {
            if (s.equalsIgnoreCase(nexoId)) {
                return true;
            }
        }
        return false;
    }

    public void addSpawn(Location loc) {
        spawns.add(loc.clone());
        save();
    }

    public void clearSpawns() {
        spawns.clear();
        save();
    }

    public boolean isRunnable() {
        return enabled && !spawns.isEmpty() && !getResolvedPoolIds().isEmpty();
    }

    /**
     * Spawn-slot uit {@link #getSpawns()} dat blok‑gewijs het dichtst bij {@code loc} ligt (zelfde wereld).
     */
    public int nearestSpawnSlot(Location loc) {
        if (loc == null || loc.getWorld() == null || spawns.isEmpty()) {
            return -1;
        }
        int best = -1;
        long bestDistSq = Long.MAX_VALUE;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        for (int i = 0; i < spawns.size(); i++) {
            Location s = spawns.get(i);
            if (s == null || s.getWorld() == null || !s.getWorld().equals(loc.getWorld())) {
                continue;
            }
            long dx = (long) s.getBlockX() - bx;
            long dy = (long) s.getBlockY() - by;
            long dz = (long) s.getBlockZ() - bz;
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestDistSq) {
                bestDistSq = d;
                best = i;
            }
        }
        return best;
    }

    public String poolSummary() {
        List<String> p = getResolvedPoolIds();
        if (pool.isEmpty()) {
            return p.size() + " standaard ids (YAML coins.pool is leeg)";
        }
        return p.size() + " ids uit settings";
    }
}

