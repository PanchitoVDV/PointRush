package be.panchito.pointRush.minigame.boatrace;

import be.panchito.pointRush.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/**
 * Bukkit event glue for the Boat Race minigame.
 */
public final class BoatRaceListener implements Listener {

    private final BoatRaceGame game;

    public BoatRaceListener(BoatRaceGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != BoatRaceGame.State.STARTING) return;
        if (event.getTo() == null) return;
        var from = event.getFrom();
        var to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(from.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        if (!(dismounted instanceof Player player)) return;
        if (!shouldLockIn(player)) return;
        event.setCancelled(true);
        player.sendActionBar(Messages.warn("Je zit vast in de bootrace — gebruik /boatrace leave om op te geven"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoatDestroy(VehicleDestroyEvent event) {
        Entity vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat boat)) return;
        for (BoatRacePlayerState ps : game.getAllPlayerStates()) {
            if (boat.getUniqueId().equals(ps.getBoatUuid())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && game.isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            return;
        }
        if (entity instanceof Boat boat) {
            for (BoatRacePlayerState ps : game.getAllPlayerStates()) {
                if (boat.getUniqueId().equals(ps.getBoatUuid())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean shouldLockIn(Player player) {
        BoatRacePlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null) return false;
        if (ps.isFinished()) return false;
        return game.getState() != BoatRaceGame.State.IDLE;
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
