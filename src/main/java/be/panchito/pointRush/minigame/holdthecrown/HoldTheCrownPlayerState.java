package be.panchito.pointRush.minigame.holdthecrown;

import be.panchito.pointRush.minigame.ctf.CtfSide;
import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Per-player runtime state for an active Hold the Crown event.
 * Inventories are managed per world by Multiverse-Inventories.
 */
public final class HoldTheCrownPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private CtfSide side;
    private boolean alive = true;
    private long respawnAtMs = 0L;
    private int deaths = 0;
    /** Total ms this player wore the crown during the event. */
    private long holdTimeMs = 0L;
    /** Wall-clock ms when current crown session started (0 = not wearing). */
    private long crownSessionStartMs = 0L;
    private int placement = 0;

    public HoldTheCrownPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
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

    public long getHoldTimeMs() {
        return holdTimeMs;
    }

    public void addHoldTimeMs(long ms) {
        if (ms > 0) {
            holdTimeMs += ms;
        }
    }

    public long getCrownSessionStartMs() {
        return crownSessionStartMs;
    }

    public void setCrownSessionStartMs(long crownSessionStartMs) {
        this.crownSessionStartMs = crownSessionStartMs;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    /** Total hold time including an active session. */
    public long totalHoldTimeMs(long now) {
        if (crownSessionStartMs <= 0L) {
            return holdTimeMs;
        }
        return holdTimeMs + Math.max(0L, now - crownSessionStartMs);
    }
}
