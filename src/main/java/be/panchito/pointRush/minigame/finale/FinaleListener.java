package be.panchito.pointRush.minigame.finale;

import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.PlayerRespawnUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Koppelt Bukkit-events aan {@link FinaleGame}.
 *
 * <ul>
 *     <li>Tijdens de freeze staan deelnemers vast (alleen rondkijken mag) en zijn ze onkwetsbaar.</li>
 *     <li>Tijdens het gevecht telt PvP; friendly fire binnen één team is uit.</li>
 *     <li>Dodelijke schade wordt onderschept: niemand sterft echt — je gaat naar spectator.</li>
 *     <li>Block-acties, drops en inventory worden geblokkeerd; honger blijft vol.</li>
 *     <li>Bij quit wordt de deelnemer netjes verwijderd.</li>
 * </ul>
 */
public final class FinaleListener implements Listener {

    private final FinaleGame game;

    public FinaleListener(FinaleGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isFreezing() || !game.isParticipant(player.getUniqueId())) return;
        FinalePlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // alleen gedraaid - toegestaan
        }
        // Hou de positie vast, sta wel toe om rond te kijken.
        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }

    /** Registreer aanvaller (voor kill-credit) en blokkeer friendly fire. Loopt vóór de schade-check. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!game.isParticipant(victim.getUniqueId())) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // FFA: na de grace mag iedereen iedereen raken (ook teamgenoten).
        if (game.isGracePeriod()) {
            event.setCancelled(true);
            attacker.sendActionBar(Messages.warn("Nog even geduld - PvP is uit tijdens de grace-periode."));
            return;
        }
        game.recordDamager(victim, attacker);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;

        FinaleGame.State state = game.getState();

        // Onkwetsbaar tijdens de freeze en buiten het actieve gevecht.
        if (state != FinaleGame.State.RUNNING) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            return;
        }

        // Tijdens de grace-periode is iedereen onkwetsbaar; void brengt je terug naar je spawn.
        if (game.isGracePeriod()) {
            event.setCancelled(true);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            player.setHealth(20.0);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                game.returnToSpawn(player);
            }
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.setHealth(20.0);
            player.setFallDistance(0f);
            game.handleDeath(player);
            return;
        }

        // Dodelijke klap? Niet echt sterven - netjes elimineren naar spectator.
        if (game.isFatal(player, event.getFinalDamage())) {
            // Heeft de speler een totem in de hand? Laat vanilla die poppen (speler overleeft).
            if (hasTotem(player)) {
                return;
            }
            event.setCancelled(true);
            player.setHealth(20.0);
            player.setFireTicks(0);
            player.setFallDistance(0f);
            game.handleElimination(player);
        }
    }

    private boolean hasTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!game.isParticipant(player.getUniqueId())) return;
        // Failsafe: mocht iemand toch echt sterven, geen drops en netjes elimineren.
        event.getDrops().clear();
        event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(game.getPlugin(), () -> {
            PlayerRespawnUtil.forceRespawnIfDead(player);
            game.handleDeath(player);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        // Loot-drop claimen onderdrukt de vanilla chest-GUI.
        if (game.tryClaimLootDrop(player, clicked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!game.isParticipant(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        // Vrij breken tijdens het gevecht; buiten dat (freeze/einde) niet.
        if (game.getState() != FinaleGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        // Bewaar de oorspronkelijke staat zodat de arena bij het einde hersteld wordt.
        game.recordOriginalBlock(event.getBlock(), event.getBlock().getState());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!game.isParticipant(p.getUniqueId()) || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        // Vrij plaatsen tijdens het gevecht; buiten dat (freeze/einde) niet.
        if (game.getState() != FinaleGame.State.RUNNING) {
            event.setCancelled(true);
            return;
        }
        game.recordOriginalBlock(event.getBlock(), event.getBlockReplacedState());
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
