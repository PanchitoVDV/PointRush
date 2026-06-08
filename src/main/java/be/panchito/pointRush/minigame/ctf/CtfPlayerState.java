package be.panchito.pointRush.minigame.ctf;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Per-player runtime state for an active Capture the Flag event.
 * Inventories are managed per world by Multiverse-Inventories.
 */
public final class CtfPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private CtfSide side;
    private boolean alive = true;
    private long respawnAtMs = 0L;
    private int deaths = 0;
    private int captures = 0;
    private int pointsEarned = 0;

    public CtfPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getSavedLocation() {
        return savedLocation;
    }

    public GameMode getSavedGameMode() {
        return savedGameMode;
    }

    public CtfSide getSide() {
        return side;
    }

    public void setSide(CtfSide side) {
        this.side = side;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public long getRespawnAtMs() {
        return respawnAtMs;
    }

    public void setRespawnAtMs(long respawnAtMs) {
        this.respawnAtMs = respawnAtMs;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public int getCaptures() {
        return captures;
    }

    public void incrementCaptures() {
        this.captures++;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }
}
