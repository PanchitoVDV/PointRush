package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Bukkit events voor Floor is Lava (bouw + knock + lava).
 */
public final class FloorIsLavaListener implements Listener {

    private final FloorIsLavaGame game;

    public FloorIsLavaListener(FloorIsLavaGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        if (game.getState() == FloorIsLavaGame.State.STARTING) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (game.getState() == FloorIsLavaGame.State.RUNNING) {
            game.checkLava(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isParticipant(victim.getUniqueId())) return;
        if (game.getState() != FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }

        FloorIsLavaPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker.getUniqueId())) {
            FloorIsLavaPlayerState aState = game.getPlayerState(attacker.getUniqueId());
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

        if (event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            if (game.getState() == FloorIsLavaGame.State.RUNNING) {
                game.eliminate(player);
            }
            return;
        }
        if (game.getState() == FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != FloorIsLavaGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        FloorIsLavaPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isAlive()) {
            event.setCancelled(true);
            return;
        }
        if (!game.canBuildAt(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    SmallText.of("Bouw alleen binnen de arena!"), NamedTextColor.RED));
            return;
        }
        game.trackPlacedBlock(event.getBlock());
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
