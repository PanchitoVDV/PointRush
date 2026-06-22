package be.panchito.pointRush.minigame.boss;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    /**
     * Speler-vs-speler schade gaat volledig uit (co-op boss-fight) en schade aan een actieve boss
     * wordt per speler bijgehouden voor de MVP-prijs.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);

        if (event.getEntity() instanceof Player victim && game.isParticipant(victim.getUniqueId())) {
            if (isProtected(victim)) {
                event.setCancelled(true);
                return;
            }
            // Geen friendly fire: deelnemers kunnen elkaar niet raken.
            if (attacker != null && game.isParticipant(attacker.getUniqueId())
                    && !attacker.getUniqueId().equals(victim.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }

        if (game.getState() == BossEventGame.State.RUNNING
                && attacker != null && game.isParticipant(attacker.getUniqueId())
                && game.isActiveBoss(event.getEntity().getUniqueId())) {
            game.addBossDamage(attacker, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            return;
        }
        if (isProtected(player)) {
            event.setCancelled(true);
        }
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

    /** True wanneer de speler géén actieve vechter is en dus geen schade hoort te krijgen. */
    private boolean isProtected(Player player) {
        if (game.getState() != BossEventGame.State.RUNNING) {
            return true;
        }
        BossEventPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null) {
            return true;
        }
        return switch (game.getPhase()) {
            case ARENA_ROUND -> !ps.isArenaAlive();
            case FINAL_ROUND, FINAL_COUNTDOWN -> !ps.isFinalAlive();
            default -> true;
        };
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (game.isParticipant(event.getPlayer().getUniqueId())) {
            game.leave(event.getPlayer());
        }
    }
}
