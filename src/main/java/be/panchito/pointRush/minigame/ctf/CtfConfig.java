package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

/**
 * Capture the Flag arena onder sectie {@code ctf} in {@code settings.yml}.
 */
public final class CtfConfig {

    public static final int DEFAULT_ROUND_MINUTES = 5;
    public static final int DEFAULT_ROUNDS = 3;
    public static final int DEFAULT_HIDE_PHASE_SECONDS = 60;
    public static final int DEFAULT_POINTS_PER_CAPTURE = 75;
    public static final double DEFAULT_CAPTURE_RADIUS = 4.0;

    private static final String KEY = "ctf";

    private final JavaPlugin plugin;
    private final UnifiedSettings unified;

    private Location castleSpawn;
    private Location redSpawn;
    private Location blueSpawn;
    private int roundMinutes = DEFAULT_ROUND_MINUTES;
    private int rounds = DEFAULT_ROUNDS;
    private int hidePhaseSeconds = DEFAULT_HIDE_PHASE_SECONDS;
    private int pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
    private double captureRadius = DEFAULT_CAPTURE_RADIUS;

    public CtfConfig(JavaPlugin plugin, UnifiedSettings unified) {
        this.plugin = plugin;
        this.unified = unified;
    }

    public void load() {
        castleSpawn = null;
        redSpawn = null;
        blueSpawn = null;
        roundMinutes = DEFAULT_ROUND_MINUTES;
        rounds = DEFAULT_ROUNDS;
        hidePhaseSeconds = DEFAULT_HIDE_PHASE_SECONDS;
        pointsPerCapture = DEFAULT_POINTS_PER_CAPTURE;
        captureRadius = DEFAULT_CAPTURE_RADIUS;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        castleSpawn = loadLocation(cfg, KEY + ".castleSpawn");
        redSpawn = loadLocation(cfg, KEY + ".redSpawn");
        blueSpawn = loadLocation(cfg, KEY + ".blueSpawn");
        if (cfg.isSet(KEY + ".roundMinutes")) {
            roundMinutes = Math.max(2, cfg.getInt(KEY + ".roundMinutes"));
        }
        if (cfg.isSet(KEY + ".rounds")) {
            rounds = Math.max(1, cfg.getInt(KEY + ".rounds"));
        }
        if (cfg.isSet(KEY + ".hidePhaseSeconds")) {
            hidePhaseSeconds = Math.max(15, cfg.getInt(KEY + ".hidePhaseSeconds"));
        }
        if (cfg.isSet(KEY + ".pointsPerCapture")) {
            pointsPerCapture = Math.max(10, cfg.getInt(KEY + ".pointsPerCapture"));
        }
        if (cfg.isSet(KEY + ".captureRadius")) {
            captureRadius = Math.max(1.0, cfg.getDouble(KEY + ".captureRadius"));
        } else if (cfg.isSet(KEY + ".deliveryRadius")) {
            captureRadius = Math.max(1.0, cfg.getDouble(KEY + ".deliveryRadius"));
        }
    }

    public void save() {
        YamlConfiguration cfg = unified.yaml();
        cfg.set(KEY + ".flagSpawn", null);
        cfg.set(KEY + ".redDelivery", null);
        cfg.set(KEY + ".blueDelivery", null);
        if (castleSpawn != null) saveLocation(cfg, KEY + ".castleSpawn", castleSpawn);
        if (redSpawn != null) saveLocation(cfg, KEY + ".redSpawn", redSpawn);
        if (blueSpawn != null) saveLocation(cfg, KEY + ".blueSpawn", blueSpawn);
        cfg.set(KEY + ".roundMinutes", roundMinutes);
        cfg.set(KEY + ".rounds", rounds);
        cfg.set(KEY + ".hidePhaseSeconds", hidePhaseSeconds);
        cfg.set(KEY + ".pointsPerCapture", pointsPerCapture);
        cfg.set(KEY + ".captureRadius", captureRadius);
        try {
            unified.save();
            plugin.getLogger().info("settings.yml opgeslagen (" + KEY + ": ready=" + isReady() + ").");
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

    public Location getCastleSpawn() {
        return castleSpawn;
    }

    public void setCastleSpawn(Location castleSpawn) {
        this.castleSpawn = castleSpawn;
        save();
    }

    /**
     * Fysieke spawn voor een teamkleur deze ronde. Oneven rondes: rood→{@link #redSpawn},
     * blauw→{@link #blueSpawn}. Even rondes: omgedraaid.
     */
    public Location getRoundTeamSpawn(CtfSide side, int roundNumber) {
        if (redSpawn == null || blueSpawn == null) {
            Location fallback = getSpawn(side);
            return fallback != null ? fallback.clone() : null;
        }
        boolean swapSides = roundNumber % 2 == 0;
        if (side == CtfSide.RED) {
            return (swapSides ? blueSpawn : redSpawn).clone();
        }
        return (swapSides ? redSpawn : blueSpawn).clone();
    }

    /** Waar het verstop-team naartoe gaat (kasteel op oneven rondes, anders ronde-kamp). */
    public Location resolveHideSpawn(CtfSide hidingSide, int roundNumber) {
        if (castleSpawn != null && roundNumber % 2 == 1) {
            return castleSpawn.clone();
        }
        return getRoundTeamSpawn(hidingSide, roundNumber);
    }

    /**
     * Wachtplek voor het zoek-team. Staat op het kamp dat niet de verstop-locatie is.
     */
    public Location resolveWaitSpawn(CtfSide seekingSide, CtfSide hidingSide, int roundNumber) {
        Location hideSite = resolveHideSpawn(hidingSide, roundNumber);
        Location seekBase = getRoundTeamSpawn(seekingSide, roundNumber);
        if (hideSite == null) {
            return seekBase;
        }
        if (seekBase == null) {
            return hideSite.clone();
        }
        if (isSameSite(seekBase, hideSite)) {
            Location opposite = getRoundTeamSpawn(seekingSide.opposite(), roundNumber);
            return opposite != null ? opposite.clone() : seekBase.clone();
        }
        return seekBase.clone();
    }

    private static boolean isSameSite(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }
        return a.distanceSquared(b) <= 16 || a.getY() >= b.getY() - 0.5;
    }

    /**
     * Spawn voor een speler deze ronde: verstop-team → hide-kamp, zoek-team → wachtplek.
     */
    public Location getRoundSpawn(CtfSide side, boolean hidingThisRound, CtfSide hidingSide, int roundNumber) {
        if (hidingThisRound) {
            return resolveHideSpawn(hidingSide, roundNumber);
        }
        return resolveWaitSpawn(side, hidingSide, roundNumber);
    }

    public Location getRedSpawn() {
        return redSpawn;
    }

    public void setRedSpawn(Location redSpawn) {
        this.redSpawn = redSpawn;
        save();
    }

    public Location getBlueSpawn() {
        return blueSpawn;
    }

    public void setBlueSpawn(Location blueSpawn) {
        this.blueSpawn = blueSpawn;
        save();
    }

    public Location getSpawn(CtfSide side) {
        return side == CtfSide.RED ? redSpawn : blueSpawn;
    }

    public int getRoundMinutes() {
        return roundMinutes;
    }

    public void setRoundMinutes(int roundMinutes) {
        this.roundMinutes = Math.max(2, roundMinutes);
        save();
    }

    public long getRoundDurationMs() {
        return roundMinutes * 60L * 1000L;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = Math.max(1, rounds);
        save();
    }

    public int getHidePhaseSeconds() {
        return hidePhaseSeconds;
    }

    public void setHidePhaseSeconds(int hidePhaseSeconds) {
        this.hidePhaseSeconds = Math.max(15, hidePhaseSeconds);
        save();
    }

    public long getHidePhaseMs() {
        return hidePhaseSeconds * 1000L;
    }

    public int getPointsPerCapture() {
        return pointsPerCapture;
    }

    public void setPointsPerCapture(int pointsPerCapture) {
        this.pointsPerCapture = Math.max(10, pointsPerCapture);
        save();
    }

    public double getCaptureRadius() {
        return captureRadius;
    }

    public void setCaptureRadius(double captureRadius) {
        this.captureRadius = Math.max(1.0, captureRadius);
        save();
    }

    public boolean isNearSpawn(CtfSide side, Location playerLoc, int roundNumber) {
        Location spawn = getRoundTeamSpawn(side, roundNumber);
        if (spawn == null || playerLoc.getWorld() == null) return false;
        if (spawn.getWorld() != playerLoc.getWorld()) return false;
        return spawn.distanceSquared(playerLoc) <= captureRadius * captureRadius;
    }

    public boolean isReady() {
        return redSpawn != null && blueSpawn != null;
    }
}
