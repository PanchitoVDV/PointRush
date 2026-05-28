package be.panchito.pointRush.minigame.parkour;

import be.panchito.pointRush.minigame.gadgets.MinigameGadgetEngine;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Glue between Bukkit events and {@link ParkourGame}.
 * Handles freeze, checkpoint/finish detection, item usage and damage cancellation.
 */
public final class ParkourListener implements Listener {

    private final ParkourGame game;

    public ParkourListener(ParkourGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (game.getState() == ParkourGame.State.STARTING) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location restored = new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                        to.getYaw(), to.getPitch());
                event.setTo(restored);
            }
            return;
        }

        if (game.getState() != ParkourGame.State.RUNNING) return;
        ParkourPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || ps.isFinished()) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location playerLoc = player.getLocation();
        List<Location> checkpoints = game.getConfig().getCheckpoints();
        for (int i = ps.getCheckpointIndex() + 1; i < checkpoints.size(); i++) {
            Location cp = checkpoints.get(i);
            if (cp == null || cp.getWorld() == null || cp.getWorld() != playerLoc.getWorld()) continue;
            if (cp.distanceSquared(playerLoc) <= ParkourGame.TOUCH_RADIUS_SQ) {
                game.onCheckpointReached(player, i);
                break;
            }
        }

        Location finish = game.getConfig().getFinish();
        if (finish != null && finish.getWorld() == playerLoc.getWorld()
                && finish.distanceSquared(playerLoc) <= ParkourGame.TOUCH_RADIUS_SQ) {
            game.onFinishReached(player);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            if (action == Action.PHYSICAL) return;
            event.setCancelled(true);
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            event.setCancelled(true);
            return;
        }
        if (MinigameGadgetEngine.tryParkour(game.getPlugin(), game, player, item, event.getHand())
                != MinigameGadgetEngine.Result.NOT_OURS) {
            event.setCancelled(true);
            return;
        }
        String tag = game.getItemTag(item);
        if (tag == null) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);

        if (game.getState() == ParkourGame.State.STARTING) {
            if (tag.equals(ParkourGame.ITEM_VISIBILITY)) {
                game.cycleVisibility(player);
            }
            return;
        }
        if (game.getState() != ParkourGame.State.RUNNING) return;

        switch (tag) {
            case ParkourGame.ITEM_CHECKPOINT -> game.teleportToCheckpoint(player);
            case ParkourGame.ITEM_VISIBILITY -> game.cycleVisibility(player);
            default -> { }
        }
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
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            player.setFallDistance(0f);
            game.teleportToCheckpoint(player);
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
