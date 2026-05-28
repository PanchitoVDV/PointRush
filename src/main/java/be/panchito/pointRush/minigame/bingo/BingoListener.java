package be.panchito.pointRush.minigame.bingo;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Map-klik opent GUI; team-sync via {@link BingoGame#syncTeam}.
 */
public final class BingoListener implements Listener {

    private final BingoGame game;

    public BingoListener(BingoGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (game.getState() != BingoGame.State.RUNNING) return;
        if (!game.isParticipant(event.getPlayer().getUniqueId())) return;

        ItemStack item = event.getItem();
        if (!BingoItems.isBingoMap(game.getPlugin(), item)) return;

        event.setCancelled(true);
        openGui(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof BingoGui.BingoGuiHolder) {
            event.setCancelled(true);
            return;
        }

        if (game.getState() != BingoGame.State.RUNNING || !game.isParticipant(player.getUniqueId())) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (!BingoItems.isBingoMap(game.getPlugin(), current)) return;

        if (event.getClickedInventory() != player.getInventory()) return;

        event.setCancelled(true);
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            openGui(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BingoGui.BingoGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            game.untrackOpenGui(player.getUniqueId());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (BingoItems.isBingoMap(game.getPlugin(), event.getItemDrop().getItemStack())) {
            if (game.isParticipant(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        game.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            game.removeParticipant(event.getPlayer());
        }
    }

    private void openGui(Player player) {
        game.syncTeam(game.bucketFor(player.getUniqueId()));
        BingoGui.open(player, game);
    }
}
