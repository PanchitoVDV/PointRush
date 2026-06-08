package be.panchito.pointRush.minigame.tntrun;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import be.panchito.pointRush.minigame.gadgets.MinigameGadgetEngine;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetInteract;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetItems;

/**
 * Glues Bukkit events into {@link TntRunGame}.
 *
 * <ul>
 *     <li>Lets participants walk freely during the countdown (blocks only decay once running).</li>
 *     <li>Routes movement to the game's decay handler.</li>
 *     <li>Cancels damage, block changes, item drops, etc. while in the event.</li>
 *     <li>On quit, removes the participant so the event can wind down cleanly.</li>
 * </ul>
 */
public final class TntRunListener implements Listener {

    private final TntRunGame game;

    public TntRunListener(TntRunGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        // During the countdown players may walk around freely so they can spread
        // out instead of stacking on the spawn. Blocks only start decaying once
        // the game is RUNNING (handleMove no-ops in any other state).
        if (game.getState() != TntRunGame.State.RUNNING) return;
        // Puur roteren (zelfde blok) hoeft geen blok-lookups: vallen of lopen verandert altijd
        // de blok-coördinaat, dus de death-plane- en decay-logica missen niets.
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        game.handleMove(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        if (!MinigameGadgetInteract.isRightClick(event)) {
            if (event.getAction() == Action.PHYSICAL) return;
            event.setCancelled(true);
            return;
        }

        ItemStack item = MinigameGadgetInteract.itemInHand(event);
        if (item == null) {
            event.setCancelled(true);
            return;
        }

        if (MinigameGadgetEngine.tryTntRun(game.getPlugin(), game, player, item, event.getHand())
                != MinigameGadgetEngine.Result.NOT_OURS) {
            event.setCancelled(true);
            MinigameGadgetInteract.denyVanillaUse(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (MinigameGadgetItems.parse(game.getPlugin(), event.getItem()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            game.eliminate(player);
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && game.isParticipant(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (game.isParticipant(p.getUniqueId()) && p.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (game.isParticipant(p.getUniqueId()) && p.getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && game.isParticipant(p.getUniqueId())) {
            event.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (game.isParticipant(player.getUniqueId())) {
            game.removeParticipant(player, false);
        }
    }
}
