package be.panchito.pointRush.minigame.hiddentarget;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Per-player runtime state for Hidden Target.
 */
public final class HiddenTargetPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;

    private boolean alive = true;
    private UUID targetId;
    private long respawnAtMs = 0L;
    private int targetKills = 0;
    private int deaths = 0;
    private int huntedDeaths = 0;
    private int pointsEarned = 0;
    private int pointsLost = 0;

    public HiddenTargetPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode,
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public long getRespawnAtMs() {
        return respawnAtMs;
    }

    public void setRespawnAtMs(long respawnAtMs) {
        this.respawnAtMs = respawnAtMs;
    }

    public int getTargetKills() {
        return targetKills;
    }

    public void incrementTargetKills() {
        this.targetKills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        this.deaths++;
    }

    public int getHuntedDeaths() {
        return huntedDeaths;
    }

    public void incrementHuntedDeaths() {
        this.huntedDeaths++;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }

    public int getPointsLost() {
        return pointsLost;
    }

    public void addPointsLost(int amount) {
        this.pointsLost += amount;
    }

    public int netScore() {
        return pointsEarned - pointsLost;
    }
}
