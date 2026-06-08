package be.panchito.pointRush.minigame.holdthecrown;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit events voor Hold the Crown.
 */
public final class HoldTheCrownListener implements Listener {

    private final HoldTheCrownGame game;

    public HoldTheCrownListener(HoldTheCrownGame game) {
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

        HoldTheCrownPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps != null && !ps.isAlive()) {
            Location spawn = game.getConfig().getSpawn(ps.getSide());
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
                if (!player.isOnline()) return;
                // event may have ended between respawn and now - never strand outside an event
                if (!game.isParticipant(player.getUniqueId()) || game.getState() == HoldTheCrownGame.State.IDLE) return;
                player.getInventory().clear();
                player.setFireTicks(0);
                player.setFallDistance(0f);
                player.setGlowing(false);
                player.setGameMode(GameMode.SPECTATOR);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isParticipant(victim.getUniqueId())) return;
        if (game.getState() != HoldTheCrownGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }

        HoldTheCrownPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker.getUniqueId())) {
            HoldTheCrownPlayerState aState = game.getPlayerState(attacker.getUniqueId());
            if (aState != null && aState.isAlive()) {
                if (vState.getSide() == aState.getSide()) {
                    event.setCancelled(true);
                }
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
            HoldTheCrownPlayerState ps = game.getPlayerState(player.getUniqueId());
            if (ps != null) {
                Location spawn = game.getConfig().getSpawn(ps.getSide());
                if (spawn != null) {
                    game.getPlugin().getTeleporter().teleport(player, spawn);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (game.getState() == HoldTheCrownGame.State.STARTING) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (game.getState() == HoldTheCrownGame.State.RUNNING) {
            // Alleen bij een echte blok-verplaatsing; een puur roterende muisbeweging hoeft geen
            // getNearbyEntities-scan (PlayerMoveEvent vuurt ook bij alleen kijken).
            if (changedBlock(event)) {
                game.tryPickupDroppedCrown(player);
            }
        }
    }

    private static boolean changedBlock(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.isCrownItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            game.tryPickupDroppedCrown(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.isCrownItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
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
