package be.panchito.pointRush.minigame.tnttag;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Per-player runtime state for an active TNT Tag event.
 * Holds the pre-event snapshot (location, inventory, gamemode) and the live status:
 * whether the player is alive, currently tagged, and the cooldown that prevents
 * instant tag-backs after a pass.
 */
public final class TntTagPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;
    private final ItemStack[] savedInventory;
    private final ItemStack savedHelmet;

    private boolean alive = true;
    private boolean tagged = false;
    /** Wall-clock ms until tags to/from this player are ignored (anti-instant-back). */
    private long tagCooldownExpiresMs = 0L;
    /** Round number the player got eliminated in (0 = still alive or never died). */
    private int eliminatedInRound = 0;
    /** Cumulative number of rounds survived (a round counts when round ends without dying). */
    private int roundsSurvived = 0;
    /** How many times the tag was passed from this player to another - mostly for fun stats. */
    private int passes = 0;
    /** Total points earned by this player on behalf of their team during the event. */
    private int pointsEarned = 0;

    private int shopTagGhostPasses;
    private boolean shopTagIronRush;
    private boolean shopTagSecondWind;

    public TntTagPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode,
                             ItemStack[] savedInventory, ItemStack savedHelmet) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
        this.savedInventory = savedInventory;
        this.savedHelmet = savedHelmet;
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

    public ItemStack getSavedHelmet() {
        return savedHelmet;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean isTagged() {
        return tagged;
    }

    public void setTagged(boolean tagged) {
        this.tagged = tagged;
    }

    public long getTagCooldownExpiresMs() {
        return tagCooldownExpiresMs;
    }

    public void setTagCooldownExpiresMs(long tagCooldownExpiresMs) {
        this.tagCooldownExpiresMs = tagCooldownExpiresMs;
    }

    public int getEliminatedInRound() {
        return eliminatedInRound;
    }

    public void setEliminatedInRound(int eliminatedInRound) {
        this.eliminatedInRound = eliminatedInRound;
    }

    public int getRoundsSurvived() {
        return roundsSurvived;
    }

    public void incrementRoundsSurvived() {
        this.roundsSurvived++;
    }

    public int getPasses() {
        return passes;
    }

    public void incrementPasses() {
        this.passes++;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }

    public int getShopTagGhostPasses() {
        return shopTagGhostPasses;
    }

    public void setShopTagGhostPasses(int shopTagGhostPasses) {
        this.shopTagGhostPasses = shopTagGhostPasses;
    }

    public boolean hasShopTagIronRush() {
        return shopTagIronRush;
    }

    public void setShopTagIronRush(boolean shopTagIronRush) {
        this.shopTagIronRush = shopTagIronRush;
    }

    public boolean hasShopTagSecondWind() {
        return shopTagSecondWind;
    }

    public void setShopTagSecondWind(boolean shopTagSecondWind) {
        this.shopTagSecondWind = shopTagSecondWind;
    }
}
