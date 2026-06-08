package be.panchito.pointRush.minigame.koth;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Per-player runtime state for an active King of the Hill event.
 * Inventories are managed per world by Multiverse-Inventories.
 */
public final class KothPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private boolean alive = true;
    /** Wall-clock ms when the player may respawn after death (0 = not waiting). */
    private long respawnAtMs = 0L;
    private int deaths = 0;
    private int pointsEarned = 0;

    public KothPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
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

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }
}
