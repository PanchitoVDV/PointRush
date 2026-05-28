package be.panchito.pointRush.minigame.parkour;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Per-player state tracked while a parkour event is running.
 * Captures the pre-event snapshot we need to restore on stop/quit.
 */
public final class ParkourPlayerState {

    public enum VisibilityMode {
        ALL,
        TEAM,
        NONE
    }

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;

    /** -1 = nog geen enkele checkpoint, anders index in {@code ParkourConfig#getCheckpoints()}. */
    private int checkpointIndex = -1;
    private VisibilityMode visibilityMode = VisibilityMode.ALL;
    /** 0 = niet gefinisht, anders eindplaatsing (1 = eerste). */
    private int placement = 0;
    /** Timestamp van laatste progressie (checkpoint of finish). Gebruikt voor leaderboard tie-breaking. */
    private long lastProgressTimeMs = 0L;

    private boolean shopParkourSpeed;
    private boolean shopParkourJump;
    private boolean shopParkourCloud;

    public ParkourPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode, ItemStack[] savedInventory) {
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

    public int getCheckpointIndex() {
        return checkpointIndex;
    }

    public void setCheckpointIndex(int checkpointIndex) {
        this.checkpointIndex = checkpointIndex;
    }

    public VisibilityMode getVisibilityMode() {
        return visibilityMode;
    }

    public void setVisibilityMode(VisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode;
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

    public boolean hasShopParkourSpeed() {
        return shopParkourSpeed;
    }

    public void setShopParkourSpeed(boolean shopParkourSpeed) {
        this.shopParkourSpeed = shopParkourSpeed;
    }

    public boolean hasShopParkourJump() {
        return shopParkourJump;
    }

    public void setShopParkourJump(boolean shopParkourJump) {
        this.shopParkourJump = shopParkourJump;
    }

    public boolean hasShopParkourCloud() {
        return shopParkourCloud;
    }

    public void setShopParkourCloud(boolean shopParkourCloud) {
        this.shopParkourCloud = shopParkourCloud;
    }
}
