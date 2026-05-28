package be.panchito.pointRush.coins;

import com.nexomc.nexo.items.ItemBuilder;
import com.nexomc.nexo.api.NexoItems;
import org.bukkit.inventory.ItemStack;

/**
 * Nexo hooks for collectible coin ItemStacks.
 */
public final class NexoCoinFactory {

    private NexoCoinFactory() {
    }

    public static Boolean exists(String id) {
        try {
            return NexoItems.exists(id);
        } catch (Exception ex) {
            return false;
        }
    }

    public static ItemStack stackForId(String id) {
        try {
            ItemBuilder builder = NexoItems.optionalItemFromId(id).orElse(null);
            if (builder == null) {
                return null;
            }
            return builder.build().clone();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String idFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            if (!NexoItems.exists(stack)) {
                return null;
            }
            return NexoItems.idFromItem(stack);
        } catch (Exception ex) {
            return null;
        }
    }
}
