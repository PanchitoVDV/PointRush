package be.panchito.pointRush.commands;

import be.panchito.pointRush.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Klik-afhandeling voor het {@link AdminEventMenu}. Het menu is read-only: elke klik wordt geannuleerd
 * en alleen de knoppen sturen navigatie of de verwijderactie aan.
 */
public final class AdminEventMenuListener implements Listener {

    private final AdminEventMenu menu;

    public AdminEventMenuListener(AdminEventMenu menu) {
        this.menu = menu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminEventMenu.Holder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Alleen kliks in de menu-inventory tellen (niet in de eigen inventory onderaan).
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        switch (holder.getView()) {
            case LIST -> handleListClick(player, holder, slot);
            case CONFIRM -> handleConfirmClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminEventMenu.Holder) {
            event.setCancelled(true);
        }
    }

    private void handleListClick(Player player, AdminEventMenu.Holder holder, int slot) {
        if (slot == AdminEventMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == AdminEventMenu.SLOT_PREV) {
            menu.openList(player, holder.getPage() - 1);
            return;
        }
        if (slot == AdminEventMenu.SLOT_NEXT) {
            menu.openList(player, holder.getPage() + 1);
            return;
        }
        String eventId = holder.eventAtSlot(slot);
        if (eventId != null) {
            menu.openConfirm(player, eventId);
        }
    }

    private void handleConfirmClick(Player player, AdminEventMenu.Holder holder, int slot) {
        if (slot == AdminEventMenu.SLOT_CONFIRM_NO) {
            menu.openList(player, 0);
            return;
        }
        if (slot == AdminEventMenu.SLOT_CONFIRM_YES) {
            AdminEventMenu.DeleteResult result = menu.deleteEvent(holder.getEventId());
            if (result == null) {
                player.sendMessage(Messages.error("Dat event bestaat niet meer."));
            } else {
                player.sendMessage(Messages.success("Event '" + result.eventLabel()
                        + "' verwijderd · " + result.pointsReversed() + " punten teruggedraaid bij "
                        + result.teamsAffected() + " team(s)."));
            }
            menu.openList(player, 0);
        }
    }
}
