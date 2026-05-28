package be.panchito.pointRush.minigame.tntrun;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Runtime state for a single participant in a TNT Run event.
 *
 * <p>Captures the pre-event snapshot (location, gamemode, inventory) for
 * restore on stop/quit and the live status: whether the player is still
 * alive, when they were eliminated, and their final placement.
 */
public final class TntRunPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;

    private boolean alive = true;
    /** Wall-clock ms when the player got eliminated (0 = still alive). */
    private long eliminatedAtMs = 0L;
    /** Final placement (1 = winner). 0 while alive. */
    private int placement = 0;

    private int shopRunDecayBonusTicks;
    private boolean shopRunFeather;
    private boolean shopRunResist;

    public TntRunPlayerState(UUID uuid, Location savedLocation,
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

    public int getShopRunDecayBonusTicks() {
        return shopRunDecayBonusTicks;
    }

    public void setShopRunDecayBonusTicks(int shopRunDecayBonusTicks) {
        this.shopRunDecayBonusTicks = shopRunDecayBonusTicks;
    }

    public boolean hasShopRunFeather() {
        return shopRunFeather;
    }

    public void setShopRunFeather(boolean shopRunFeather) {
        this.shopRunFeather = shopRunFeather;
    }

    public boolean hasShopRunResist() {
        return shopRunResist;
    }

    public void setShopRunResist(boolean shopRunResist) {
        this.shopRunResist = shopRunResist;
    }
}
