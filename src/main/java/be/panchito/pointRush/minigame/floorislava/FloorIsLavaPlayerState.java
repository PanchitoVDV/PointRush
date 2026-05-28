package be.panchito.pointRush.minigame.floorislava;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Runtime state for a single participant in a Floor is Lava event.
 */
public final class FloorIsLavaPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;

    private boolean alive = true;
    private long eliminatedAtMs = 0L;
    private int placement = 0;

    public FloorIsLavaPlayerState(UUID uuid, Location savedLocation,
                                  GameMode savedGameMode, ItemStack[] savedInventory) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
        this.savedInventory = savedInventory;
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public long getEliminatedAtMs() {
        return eliminatedAtMs;
    }

    public void setEliminatedAtMs(long eliminatedAtMs) {
        this.eliminatedAtMs = eliminatedAtMs;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }
}
