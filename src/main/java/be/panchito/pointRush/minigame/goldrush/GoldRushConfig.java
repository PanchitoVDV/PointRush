package be.panchito.pointRush.minigame.goldrush;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

/**
 * Gold Rush instellingen onder {@code goldrush} in {@code settings.yml}.
 */
public final class GoldRushConfig {

    public static final int DEFAULT_DURATION_MINUTES = 60;
    public static final int DEFAULT_LEADERBOARD_INTERVAL_SECONDS = 300;

    private static final String KEY = "goldrush";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private int leaderboardIntervalSeconds = DEFAULT_LEADERBOARD_INTERVAL_SECONDS;

    public GoldRushConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        durationMinutes = DEFAULT_DURATION_MINUTES;
        leaderboardIntervalSeconds = DEFAULT_LEADERBOARD_INTERVAL_SECONDS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(5, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".leaderboardIntervalSeconds")) {
            leaderboardIntervalSeconds = Math.max(60, cfg.getInt(KEY + ".leaderboardIntervalSeconds"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".leaderboardIntervalSeconds", leaderboardIntervalSeconds);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan!", ex);
        }
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

    public int getLeaderboardIntervalSeconds() {
        return leaderboardIntervalSeconds;
    }

    public void setLeaderboardIntervalSeconds(int seconds) {
        this.leaderboardIntervalSeconds = Math.max(60, seconds);
        save();
    }

    public long getLeaderboardIntervalMs() {
        return leaderboardIntervalSeconds * 1000L;
    }

    public boolean isReady() {
        return true;
    }
}
