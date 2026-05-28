package be.panchito.pointRush.minigame.koth;

import be.panchito.pointRush.minigame.gadgets.MinigameGadgetEngine;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetItems;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit events voor Wipeout KOTH.
 */
public final class KothListener implements Listener {

    private final KothGame game;

    public KothListener(KothGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDeathMessage(null);
        event.setDroppedExp(0);

        game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
            if (player.isOnline()) {
                game.handleDeath(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        KothPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps != null && !ps.isAlive()) {
            if (game.getConfig().getSpawn() != null) {
                event.setRespawnLocation(game.getConfig().getSpawn());
            }
            game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isParticipant(victim.getUniqueId())) return;
        if (game.getState() != KothGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }

        KothPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker.getUniqueId())) {
            KothPlayerState aState = game.getPlayerState(attacker.getUniqueId());
            if (aState != null && aState.isAlive()) {
                event.setDamage(Math.min(event.getDamage(), 1.0));
                return;
            }
        }
        event.setCancelled(true);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            player.setFireTicks(0);
            if (game.getConfig().getSpawn() != null) {
                player.teleport(game.getConfig().getSpawn());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (game.getState() == KothGame.State.STARTING) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (game.getState() == KothGame.State.RUNNING) {
            game.tryPickupSpot(player, System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != KothGame.State.RUNNING) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        if (MinigameGadgetEngine.tryKoth(game.getPlugin(), game, player, item, event.getHand())
                != MinigameGadgetEngine.Result.NOT_OURS) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            if (action == Action.RIGHT_CLICK_BLOCK) {
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != KothGame.State.RUNNING) return;
        if (MinigameGadgetItems.parse(game.getPlugin(), event.getItem()) != null) {
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
            if (p.getGameMode() == GameMode.SPECTATOR) {
                event.setCancelled(true);
            }
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
