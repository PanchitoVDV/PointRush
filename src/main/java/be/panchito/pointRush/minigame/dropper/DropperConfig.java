package be.panchito.pointRush.minigame.dropper;

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
 * Dropper-arena onder sectie {@code dropper} in {@code settings.yml}.
 *
 * <p>Bevat de lobby-/wachtspawn, de ronde-tijd (seconden per ronde) en een
 * geordende lijst rondes. Elke ronde heeft een top-spawn ({@code rounds.<n>.top})
 * en een finish-zone ({@code rounds.<n>.finish.a} / {@code .b}).
 */
public final class DropperConfig {

    public static final int DEFAULT_ROUND_SECONDS = 300; // 5 minuten
    public static final int MIN_ROUND_SECONDS = 30;
    public static final int MAX_ROUND_SECONDS = 900;

    private static final String KEY = "dropper";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location spawn;
    private int roundSeconds = DEFAULT_ROUND_SECONDS;
    private final List<DropperRound> rounds = new ArrayList<>();

    public DropperConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        spawn = null;
        roundSeconds = DEFAULT_ROUND_SECONDS;
        rounds.clear();
        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        this.spawn = loadLocation(cfg, KEY + ".spawn");
        if (cfg.isSet(KEY + ".roundSeconds")) {
            roundSeconds = clampSeconds(cfg.getInt(KEY + ".roundSeconds"));
        }
        ConfigurationSection roundsSec = cfg.getConfigurationSection(KEY + ".rounds");
        if (roundsSec != null) {
            List<String> keys = new ArrayList<>(roundsSec.getKeys(false));
            keys.sort((a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                } catch (NumberFormatException ex) {
                    return a.compareTo(b);
                }
            });
            for (String rKey : keys) {
                String base = KEY + ".rounds." + rKey;
                Location top = loadLocation(cfg, base + ".top");
                Location a = loadLocation(cfg, base + ".finish.a");
                Location b = loadLocation(cfg, base + ".finish.b");
                if (top != null && a != null && b != null) {
                    rounds.add(new DropperRound(top, a, b));
                } else {
                    plugin.getLogger().warning("Dropper-ronde '" + rKey + "' overgeslagen (top/finish onvolledig).");
                }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY, null);
        if (spawn != null) saveLocation(cfg, KEY + ".spawn", spawn);
        cfg.set(KEY + ".roundSeconds", roundSeconds);
        for (int i = 0; i < rounds.size(); i++) {
            DropperRound r = rounds.get(i);
            String base = KEY + ".rounds." + (i + 1);
            saveLocation(cfg, base + ".top", r.getTop());
            saveLocation(cfg, base + ".finish.a", r.getFinishMin());
            saveLocation(cfg, base + ".finish.b", r.getFinishMax());
        }
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": spawn=" + (spawn != null)
                    + ", rondes=" + rounds.size() + ", roundSeconds=" + roundSeconds + ").");
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

    public int getRoundSeconds() {
        return roundSeconds;
    }

    public void setRoundSeconds(int seconds) {
        this.roundSeconds = clampSeconds(seconds);
        save();
    }

    /** Ronde-duur in server-ticks (voor de timeout-scheduler). */
    public long getRoundTicks() {
        return roundSeconds * 20L;
    }

    public List<DropperRound> getRounds() {
        return rounds;
    }

    public void addRound(DropperRound round) {
        rounds.add(round);
        save();
    }

    public void clearRounds() {
        rounds.clear();
        save();
    }

    private static int clampSeconds(int seconds) {
        return Math.max(MIN_ROUND_SECONDS, Math.min(MAX_ROUND_SECONDS, seconds));
    }

    public boolean isReady() {
        return spawn != null && !rounds.isEmpty();
    }
}
