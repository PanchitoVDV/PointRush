package be.panchito.pointRush.minigame.finale;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Finale-arena onder sectie {@code finale} in {@code settings.yml}.
 *
 * <p>Bevat een lijst van mogelijke spawn-locaties (spelers worden willekeurig
 * verdeeld), de freeze-tijd (seconden dat iedereen stilstaat na de start), de
 * grace-tijd (looten zonder PvP), een optionele spectator-locatie, de
 * loot-instellingen, en de uitrusting
 * ({@link FinaleKit}) die elke deelnemer bij de start krijgt. Het gevecht zelf
 * heeft geen tijdslimiet: het loopt door tot er nog één speler/team over is.</p>
 */
public final class FinaleConfig {

    public static final int DEFAULT_FREEZE_SECONDS = 15;
    public static final int MIN_FREEZE_SECONDS = 3;
    public static final int MAX_FREEZE_SECONDS = 60;

    public static final int DEFAULT_GRACE_SECONDS = 120; // 2 minuten looten zonder PvP
    public static final int MIN_GRACE_SECONDS = 0;
    public static final int MAX_GRACE_SECONDS = 600;

    public static final int DEFAULT_LOOT_INTERVAL_SECONDS = 45;
    public static final int MIN_LOOT_INTERVAL_SECONDS = 10;
    public static final int MAX_LOOT_INTERVAL_SECONDS = 600;

    public static final int DEFAULT_LOOT_ITEMS_PER_DROP = 5;
    public static final int DEFAULT_LOOT_LIFETIME_SECONDS = 60;

    private static final String KEY = "finale";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private final List<Location> spawns = new ArrayList<>();
    private int freezeSeconds = DEFAULT_FREEZE_SECONDS;
    private int graceSeconds = DEFAULT_GRACE_SECONDS;
    private Location spectatorSpawn;
    private FinaleKit kit;

    private final List<Location> lootSpawns = new ArrayList<>();
    private final List<ItemStack> lootPool = new ArrayList<>();
    private int lootIntervalSeconds = DEFAULT_LOOT_INTERVAL_SECONDS;
    private int lootItemsPerDrop = DEFAULT_LOOT_ITEMS_PER_DROP;
    private int lootLifetimeSeconds = DEFAULT_LOOT_LIFETIME_SECONDS;

    public FinaleConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawns.clear();
        lootSpawns.clear();
        lootPool.clear();
        freezeSeconds = DEFAULT_FREEZE_SECONDS;
        graceSeconds = DEFAULT_GRACE_SECONDS;
        lootIntervalSeconds = DEFAULT_LOOT_INTERVAL_SECONDS;
        lootItemsPerDrop = DEFAULT_LOOT_ITEMS_PER_DROP;
        lootLifetimeSeconds = DEFAULT_LOOT_LIFETIME_SECONDS;
        spectatorSpawn = null;
        kit = null;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        if (cfg.isSet(KEY + ".freezeSeconds")) {
            freezeSeconds = clampFreeze(cfg.getInt(KEY + ".freezeSeconds"));
        }
        if (cfg.isSet(KEY + ".graceSeconds")) {
            graceSeconds = clampGrace(cfg.getInt(KEY + ".graceSeconds"));
        }
        if (cfg.isSet(KEY + ".lootIntervalSeconds")) {
            lootIntervalSeconds = clampLootInterval(cfg.getInt(KEY + ".lootIntervalSeconds"));
        }
        if (cfg.isSet(KEY + ".lootItemsPerDrop")) {
            lootItemsPerDrop = Math.max(1, cfg.getInt(KEY + ".lootItemsPerDrop"));
        }
        if (cfg.isSet(KEY + ".lootLifetimeSeconds")) {
            lootLifetimeSeconds = Math.max(5, cfg.getInt(KEY + ".lootLifetimeSeconds"));
        }
        spectatorSpawn = loadLocation(cfg, KEY + ".spectator");

        loadLocationList(cfg, KEY + ".spawns", spawns, "spawn");
        loadLocationList(cfg, KEY + ".lootSpawns", lootSpawns, "loot-spawn");

        ConfigurationSection lootSec = cfg.getConfigurationSection(KEY + ".loot");
        if (lootSec != null) {
            List<String> keys = new ArrayList<>(lootSec.getKeys(false));
            keys.sort(FinaleConfig::compareNumeric);
            for (String key : keys) {
                ItemStack item = lootSec.getItemStack(key);
                if (item != null && !item.getType().isAir()) {
                    lootPool.add(item);
                }
            }
        }

        kit = FinaleKit.load(cfg.getConfigurationSection(KEY + ".kit"));
    }

    private void loadLocationList(YamlConfiguration cfg, String path, List<Location> target, String label) {
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec == null) return;
        List<String> keys = new ArrayList<>(sec.getKeys(false));
        keys.sort(FinaleConfig::compareNumeric);
        for (String key : keys) {
            Location loc = loadLocation(cfg, path + "." + key);
            if (loc != null) {
                target.add(loc);
            } else {
                plugin.getLogger().warning("Finale-" + label + " '" + key + "' overgeslagen (wereld onbekend?).");
            }
        }
    }

    private static int compareNumeric(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException ex) {
            return a.compareTo(b);
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".spawns", null);
        cfg.set(KEY + ".freezeSeconds", freezeSeconds);
        cfg.set(KEY + ".graceSeconds", graceSeconds);
        if (spectatorSpawn != null) {
            saveLocation(cfg, KEY + ".spectator", spectatorSpawn);
        } else {
            cfg.set(KEY + ".spectator", null);
        }
        for (int i = 0; i < spawns.size(); i++) {
            saveLocation(cfg, KEY + ".spawns." + (i + 1), spawns.get(i));
        }
        cfg.set(KEY + ".lootSpawns", null);
        for (int i = 0; i < lootSpawns.size(); i++) {
            saveLocation(cfg, KEY + ".lootSpawns." + (i + 1), lootSpawns.get(i));
        }
        cfg.set(KEY + ".lootIntervalSeconds", lootIntervalSeconds);
        cfg.set(KEY + ".lootItemsPerDrop", lootItemsPerDrop);
        cfg.set(KEY + ".lootLifetimeSeconds", lootLifetimeSeconds);
        cfg.set(KEY + ".loot", null);
        for (int i = 0; i < lootPool.size(); i++) {
            cfg.set(KEY + ".loot." + (i + 1), lootPool.get(i));
        }
        cfg.set(KEY + ".kit", null);
        if (kit != null) {
            kit.save(cfg.createSection(KEY + ".kit"));
        }
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawns=" + spawns.size()
                    + ", loot-spawns=" + lootSpawns.size() + ", loot-items=" + lootPool.size()
                    + ", kit=" + (kit != null ? kit.itemCount() + " items" : "geen")
                    + ", freeze=" + freezeSeconds + "s).");
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

    public List<Location> getSpawns() {
        return spawns;
    }

    public void addSpawn(Location loc) {
        spawns.add(loc.clone());
        save();
    }

    public boolean removeLastSpawn() {
        if (spawns.isEmpty()) return false;
        spawns.remove(spawns.size() - 1);
        save();
        return true;
    }

    public void clearSpawns() {
        spawns.clear();
        save();
    }

    public int getFreezeSeconds() {
        return freezeSeconds;
    }

    public void setFreezeSeconds(int seconds) {
        this.freezeSeconds = clampFreeze(seconds);
        save();
    }

    public int getGraceSeconds() {
        return graceSeconds;
    }

    public void setGraceSeconds(int seconds) {
        this.graceSeconds = clampGrace(seconds);
        save();
    }

    public Location getSpectatorSpawn() {
        return spectatorSpawn;
    }

    public void setSpectatorSpawn(Location loc) {
        this.spectatorSpawn = loc != null ? loc.clone() : null;
        save();
    }

    public FinaleKit getKit() {
        return kit;
    }

    public boolean hasKit() {
        return kit != null && !kit.isEmpty();
    }

    public void setKit(FinaleKit kit) {
        this.kit = kit;
        save();
    }

    public void clearKit() {
        this.kit = null;
        save();
    }

    // ---------------------------------------------------------------- loot

    public List<Location> getLootSpawns() {
        return lootSpawns;
    }

    public void addLootSpawn(Location loc) {
        lootSpawns.add(loc.clone());
        save();
    }

    public boolean removeLastLootSpawn() {
        if (lootSpawns.isEmpty()) return false;
        lootSpawns.remove(lootSpawns.size() - 1);
        save();
        return true;
    }

    public void clearLootSpawns() {
        lootSpawns.clear();
        save();
    }

    public List<ItemStack> getLootPool() {
        return lootPool;
    }

    public boolean hasLoot() {
        return !lootPool.isEmpty() && !lootSpawns.isEmpty();
    }

    /** Vervangt de loot-pool door een momentopname van de meegegeven items (nulls worden genegeerd). */
    public void setLootPool(List<ItemStack> items) {
        lootPool.clear();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                lootPool.add(item.clone());
            }
        }
        save();
    }

    public void clearLootPool() {
        lootPool.clear();
        save();
    }

    public int getLootIntervalSeconds() {
        return lootIntervalSeconds;
    }

    public void setLootIntervalSeconds(int seconds) {
        this.lootIntervalSeconds = clampLootInterval(seconds);
        save();
    }

    public int getLootItemsPerDrop() {
        return lootItemsPerDrop;
    }

    public int getLootLifetimeSeconds() {
        return lootLifetimeSeconds;
    }

    private static int clampLootInterval(int seconds) {
        return Math.max(MIN_LOOT_INTERVAL_SECONDS, Math.min(MAX_LOOT_INTERVAL_SECONDS, seconds));
    }

    private static int clampFreeze(int seconds) {
        return Math.max(MIN_FREEZE_SECONDS, Math.min(MAX_FREEZE_SECONDS, seconds));
    }

    private static int clampGrace(int seconds) {
        return Math.max(MIN_GRACE_SECONDS, Math.min(MAX_GRACE_SECONDS, seconds));
    }

    /** Klaar om te starten zodra er minstens twee spawn-locaties zijn. */
    public boolean isReady() {
        return spawns.size() >= 2;
    }
}
