package be.panchito.pointRush.minigame.football;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

/**
 * Voetbal-event-config onder sectie {@code football} in {@code settings.yml}.
 *
 * <p>De arena zelf (veld, doelen, spawns, bal) wordt volledig in BlockBall opgezet via
 * {@code /blockball}. Wij bewaren alleen de arena-naam plus de PointRush-laag: duur,
 * punten per goal en de winst-bonus.</p>
 */
public final class FootballConfig {

    public static final int DEFAULT_DURATION_MINUTES = 5;
    public static final int DEFAULT_POINTS_PER_GOAL = 50;
    public static final int DEFAULT_WIN_BONUS = 150;

    private static final String KEY = "football";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private String arenaName = "";
    private int durationMinutes = DEFAULT_DURATION_MINUTES;
    private int pointsPerGoal = DEFAULT_POINTS_PER_GOAL;
    private int winBonus = DEFAULT_WIN_BONUS;

    public FootballConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        arenaName = "";
        durationMinutes = DEFAULT_DURATION_MINUTES;
        pointsPerGoal = DEFAULT_POINTS_PER_GOAL;
        winBonus = DEFAULT_WIN_BONUS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        arenaName = cfg.getString(KEY + ".arena", "");
        if (cfg.isSet(KEY + ".durationMinutes")) {
            durationMinutes = Math.max(1, cfg.getInt(KEY + ".durationMinutes"));
        }
        if (cfg.isSet(KEY + ".pointsPerGoal")) {
            pointsPerGoal = Math.max(0, cfg.getInt(KEY + ".pointsPerGoal"));
        }
        if (cfg.isSet(KEY + ".winBonus")) {
            winBonus = Math.max(0, cfg.getInt(KEY + ".winBonus"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".arena", arenaName);
        cfg.set(KEY + ".durationMinutes", durationMinutes);
        cfg.set(KEY + ".pointsPerGoal", pointsPerGoal);
        cfg.set(KEY + ".winBonus", winBonus);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": ready=" + isReady() + ").");
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan!", ex);
        }
    }

    public String getArenaName() {
        return arenaName;
    }

    public void setArenaName(String arenaName) {
        this.arenaName = arenaName != null ? arenaName : "";
        save();
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = Math.max(1, durationMinutes);
        save();
    }

    public long getDurationMs() {
        return durationMinutes * 60L * 1000L;
    }

    public int getPointsPerGoal() {
        return pointsPerGoal;
    }

    public void setPointsPerGoal(int pointsPerGoal) {
        this.pointsPerGoal = Math.max(0, pointsPerGoal);
        save();
    }

    public int getWinBonus() {
        return winBonus;
    }

    public void setWinBonus(int winBonus) {
        this.winBonus = Math.max(0, winBonus);
        save();
    }

    public boolean isReady() {
        return arenaName != null && !arenaName.isBlank();
    }
}
