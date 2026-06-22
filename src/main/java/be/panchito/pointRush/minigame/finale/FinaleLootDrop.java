package be.panchito.pointRush.minigame.finale;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Een actieve loot-drop tijdens een Finale-gevecht: een tijdelijke crate (chest-block) op een
 * vaste locatie, gevuld met een willekeurige selectie loot. Spelers claimen hem door erop te
 * rechts-klikken; verschijnt met een zichtbare beam zodat iedereen ernaartoe rent.
 *
 * <p>De vorige {@link BlockData} wordt bewaard zodat het blok bij claim, verloop of stop netjes
 * hersteld wordt.</p>
 */
public final class FinaleLootDrop {

    private final Location location;
    private final BlockState previousState;
    private final List<ItemStack> items;
    private final long expiresAtMs;
    private boolean removed = false;

    public FinaleLootDrop(Location location, BlockState previousState, List<ItemStack> items, long expiresAtMs) {
        this.location = location;
        this.previousState = previousState;
        this.items = items;
        this.expiresAtMs = expiresAtMs;
    }

    public Location getLocation() {
        return location;
    }

    public BlockState getPreviousState() {
        return previousState;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public boolean isExpired(long nowMs) {
        return nowMs >= expiresAtMs;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void markRemoved() {
        this.removed = true;
    }

    /** True wanneer deze drop op exact dit blok staat. */
    public boolean isAt(Location blockLoc) {
        return location.getWorld() != null
                && blockLoc.getWorld() != null
                && location.getWorld().equals(blockLoc.getWorld())
                && location.getBlockX() == blockLoc.getBlockX()
                && location.getBlockY() == blockLoc.getBlockY()
                && location.getBlockZ() == blockLoc.getBlockZ();
    }
}
