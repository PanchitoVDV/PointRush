package be.panchito.pointRush.minigame.ctf;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Per-player runtime state for an active Capture the Flag event.
 */
public final class CtfPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;

    private CtfSide side;
    private boolean alive = true;
    private int deaths = 0;
    private int captures = 0;
    private int pointsEarned = 0;

    public CtfPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode,
                          ItemStack[] savedInventory) {
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
