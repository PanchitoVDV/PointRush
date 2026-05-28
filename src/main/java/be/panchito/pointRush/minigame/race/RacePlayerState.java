package be.panchito.pointRush.minigame.race;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Runtime state for a single participant in a Race event.
 *
 * <p>Captures the pre-event snapshot (location, gamemode, inventory) for
 * restore on stop/quit. Tracks lap progression:
 * <ul>
 *     <li>{@code currentLap} — laps completed (0 = on lap 1, 3 = finished a 3-lap race).</li>
 *     <li>{@code nextCheckpoint} — index of the next checkpoint the player must touch
 *         in the current lap.</li>
 *     <li>{@code finishLineArmed} — true once all checkpoints in the current lap are
 *         hit; the next finish-line touch counts as a lap.</li>
 *     <li>{@code placement} — 0 while still racing, otherwise final placement (1 = winner).</li>
 *     <li>{@code licensePlate} — the MTVehicles license plate assigned to this driver.</li>
 * </ul>
 */
public final class RacePlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;
    private final int gridSlot;
    private final String licensePlate;

    private int currentLap = 0;
    private int nextCheckpoint = 0;
    private boolean finishLineArmed = false;
    private int placement = 0;
    private long lastProgressTimeMs = 0L;

    private boolean shopRaceNitro;
    private boolean shopRaceCage;
    private boolean shopRacePit;

    public RacePlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode,
                           ItemStack[] savedInventory, int gridSlot, String licensePlate) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
        this.savedInventory = savedInventory;
        this.gridSlot = gridSlot;
        this.licensePlate = licensePlate;
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

    public String getLicensePlate() {
        return licensePlate;
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

    public boolean hasShopRaceNitro() {
        return shopRaceNitro;
    }

    public void setShopRaceNitro(boolean shopRaceNitro) {
        this.shopRaceNitro = shopRaceNitro;
    }

    public boolean hasShopRaceCage() {
        return shopRaceCage;
    }

    public void setShopRaceCage(boolean shopRaceCage) {
        this.shopRaceCage = shopRaceCage;
    }

    public boolean hasShopRacePit() {
        return shopRacePit;
    }

    public void setShopRacePit(boolean shopRacePit) {
        this.shopRacePit = shopRacePit;
    }
}
