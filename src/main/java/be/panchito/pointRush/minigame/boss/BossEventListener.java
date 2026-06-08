package be.panchito.pointRush.minigame.boss;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Entity;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Bukkit events voor Boss Event.
 */
public final class BossEventListener implements Listener {

    private final BossEventGame game;

    public BossEventListener(BossEventGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }
        if (game.getState() != BossEventGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        BossEventPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null) {
            return;
        }
        BossEventGame.Phase phase = game.getPhase();
        if (phase == BossEventGame.Phase.ARENA_ROUND && !ps.isArenaAlive()) {
            event.setCancelled(true);
        } else if ((phase == BossEventGame.Phase.FINAL_ROUND || phase == BossEventGame.Phase.FINAL_COUNTDOWN)
                && !ps.isFinalAlive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }
        if (game.getState() != BossEventGame.State.RUNNING) {
            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDeathMessage(null);
        event.setDroppedExp(0);

        BossEventGame.Phase phase = game.getPhase();
        if (phase == BossEventGame.Phase.ARENA_ROUND) {
            game.handleArenaDeath(player);
        } else if (phase == BossEventGame.Phase.FINAL_ROUND) {
            game.handleFinalDeath(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }

        var loc = game.getRespawnLocation(player);
        if (loc != null) {
            event.setRespawnLocation(loc);
        }
        game.applySpectatorAfterRespawn(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (game.getState() != BossEventGame.State.RUNNING) {
            return;
        }
        Entity entity = event.getEntity();
        game.handleBossDeath(entity.getUniqueId());
    }

    /** MythicMobs bosses trigger this instead of (or before) Bukkit EntityDeathEvent. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (game.getState() != BossEventGame.State.RUNNING) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity != null) {
            game.handleBossDeath(entity.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            game.leave(event.getPlayer());
        }
    }
}
