package be.panchito.pointRush.minigame.holdthecrown;

import be.panchito.pointRush.coins.NexoCoinFactory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Nexo crown item ({@code crown_helmet}) for Hold the Crown.
 */
public final class CrownItem {

    public static final String NEXO_ID = "crown_helmet";

    private CrownItem() {
    }

    public static boolean isAvailable() {
        return Boolean.TRUE.equals(NexoCoinFactory.exists(NEXO_ID));
    }

    public static ItemStack create() {
        ItemStack nexo = NexoCoinFactory.stackForId(NEXO_ID);
        if (nexo != null && !nexo.getType().isAir()) {
            return nexo.clone();
        }
        ItemStack fallback = new ItemStack(Material.GOLDEN_HELMET);
        fallback.editMeta(ItemMeta.class, meta -> meta.setUnbreakable(true));
        return fallback;
    }

    public static boolean isCrown(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = NexoCoinFactory.idFromStack(stack);
        return NEXO_ID.equals(id);
    }
}
