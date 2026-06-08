package be.panchito.pointRush.minigame.gadgets;

import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Betrouwbare rechtsklik-afhandeling voor Rush-items (hand + block-deny).
 */
public final class MinigameGadgetInteract {

    private MinigameGadgetInteract() {
    }

    public static boolean isRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    /** Item in de hand die de interactie triggerde (betrouwbaarder dan {@link PlayerInteractEvent#getItem()}). */
    public static ItemStack itemInHand(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            hand = EquipmentSlot.HAND;
        }
        ItemStack fromInv = event.getPlayer().getInventory().getItem(hand);
        if (fromInv != null && !fromInv.getType().isAir()) {
            return fromInv;
        }
        ItemStack fromEvent = event.getItem();
        return fromEvent != null && !fromEvent.getType().isAir() ? fromEvent : null;
    }

    /** Voorkomt dat blokken/vanilla-itemacties de Rush-truc 'stelen'. */
    public static void denyVanillaUse(PlayerInteractEvent event) {
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
    }
}
