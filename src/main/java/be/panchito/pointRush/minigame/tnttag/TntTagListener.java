package be.panchito.pointRush.minigame.tnttag;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import be.panchito.pointRush.minigame.gadgets.MinigameGadgetEngine;

/**
 * Glues Bukkit events into {@link TntTagGame}.
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Cancel any damage on participants - the only "damage" is the TNT explosion,
 *         which we handle ourselves via {@link TntTagGame#tryPassTag(Player, Player)}.</li>
 *     <li>Translate melee hits between participants into tag passes.</li>
 *     <li>Lock down block break / place / inventory / drops / starvation while the event runs.</li>
 *     <li>Recover players from void by teleporting them back to the arena spawn.</li>
 * </ul>
 */
public final class TntTagListener implements Listener {

    private final TntTagGame game;

    public TntTagListener(TntTagGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!game.isParticipant(victim.getUniqueId()) || !game.isParticipant(attacker.getUniqueId())) {
            return;
        }
        // melee hit between two participants - always cancel damage
        event.setCancelled(true);
        TntTagPlayerState aState = game.getPlayerState(attacker.getUniqueId());
        TntTagPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (aState == null || vState == null) return;
        if (!aState.isAlive() || !vState.isAlive()) return;

        if (aState.isTagged() && !vState.isTagged()) {
            game.tryPassTag(attacker, victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        // Void damage -> teleport back to spawn instead of dying
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            if (game.getConfig().getSpawn() != null) {
                player.setFallDistance(0f);
                player.setFireTicks(0);
                player.teleport(game.getConfig().getSpawn());
            }
            return;
        }
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (game.getState() == TntTagGame.State.STARTING) {
            // freeze players at spawn during countdown - only allow head rotation
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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

        if (MinigameGadgetEngine.tryTntTag(game.getPlugin(), game, player, item, event.getHand())
                != MinigameGadgetEngine.Result.NOT_OURS) {
            event.setCancelled(true);
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
