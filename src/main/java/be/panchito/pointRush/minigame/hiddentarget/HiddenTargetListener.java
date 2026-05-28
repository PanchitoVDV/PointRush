package be.panchito.pointRush.minigame.hiddentarget;

import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bukkit events voor Hidden Target.
 */
public final class HiddenTargetListener implements Listener {

    private final HiddenTargetGame game;

    public HiddenTargetListener(HiddenTargetGame game) {
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

        Player killer = player.getKiller();
        game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
            if (player.isOnline()) {
                game.handleDeath(player, killer);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;

        HiddenTargetPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps != null && !ps.isAlive()) {
            Location respawn = game.getConfig().getSpawn();
            if (respawn == null) {
                respawn = ps.getSavedLocation();
            }
            if (respawn != null) {
                event.setRespawnLocation(respawn);
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
        if (game.getState() != HiddenTargetGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }

        HiddenTargetPlayerState vState = game.getPlayerState(victim.getUniqueId());
        if (vState == null || !vState.isAlive()) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker.getUniqueId())) {
            HiddenTargetPlayerState aState = game.getPlayerState(attacker.getUniqueId());
            if (aState != null && aState.isAlive()) {
                if (sameTeam(attacker, victim)) {
                    event.setCancelled(true);
                    return;
                }
                game.recordDamager(victim.getUniqueId(), attacker.getUniqueId());
                return;
            }
        }
        event.setCancelled(true);
    }

    private boolean sameTeam(Player a, Player b) {
        var teamA = game.getPlugin().getTeamManager().getTeamOfPlayer(a.getUniqueId());
        var teamB = game.getPlugin().getTeamManager().getTeamOfPlayer(b.getUniqueId());
        return teamA != null && teamB != null && teamA.getId().equals(teamB.getId());
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

        if (game.getState() == HiddenTargetGame.State.STARTING) {
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setTo(event.getFrom().clone());
            }
            return;
        }

        if (game.getState() == HiddenTargetGame.State.RUNNING) {
            HiddenTargetPlayerState ps = game.getPlayerState(player.getUniqueId());
            if (ps != null && ps.isAlive() && !game.getConfig().contains(event.getTo())) {
                event.setTo(event.getFrom().clone());
                player.sendActionBar(Component.text(SmallText.of("buiten de arena!"), NamedTextColor.RED));
            }
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
