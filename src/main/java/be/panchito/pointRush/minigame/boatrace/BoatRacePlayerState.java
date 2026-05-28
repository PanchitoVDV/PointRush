package be.panchito.pointRush.minigame.boatrace;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Runtime state for a single participant in a Boat Race event.
 */
public final class BoatRacePlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;
    private final int gridSlot;
    private final UUID boatUuid;

    private int currentLap = 0;
    private int nextCheckpoint = 0;
    private boolean finishLineArmed = false;
    private int placement = 0;
    private long lastProgressTimeMs = 0L;

    public BoatRacePlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode,
                               ItemStack[] savedInventory, int gridSlot, UUID boatUuid) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
        this.savedInventory = savedInventory;
        this.gridSlot = gridSlot;
        this.boatUuid = boatUuid;
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

    public ItemStack[] getSavedInventory() {
        return savedInventory;
    }

    public int getGridSlot() {
        return gridSlot;
    }

    public UUID getBoatUuid() {
        return boatUuid;
    }

    public int getCurrentLap() {
        return currentLap;
    }

    public void setCurrentLap(int currentLap) {
        this.currentLap = currentLap;
    }

    public int getNextCheckpoint() {
        return nextCheckpoint;
    }

    public void setNextCheckpoint(int nextCheckpoint) {
        this.nextCheckpoint = nextCheckpoint;
    }

    public boolean isFinishLineArmed() {
        return finishLineArmed;
    }

    public void setFinishLineArmed(boolean finishLineArmed) {
        this.finishLineArmed = finishLineArmed;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    public boolean isFinished() {
        return placement > 0;
    }

    public long getLastProgressTimeMs() {
        return lastProgressTimeMs;
    }

    public void setLastProgressTimeMs(long lastProgressTimeMs) {
        this.lastProgressTimeMs = lastProgressTimeMs;
    }
}
