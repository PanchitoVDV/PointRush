package be.panchito.pointRush.minigame.race;

import be.panchito.pointRush.util.Messages;
import nl.mtvehicles.core.events.VehicleFuelEvent;
import nl.mtvehicles.core.events.VehicleLeaveEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
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

/**
 * Bukkit/MTVehicles event glue for the Race minigame.
 *
 * <ul>
 *     <li>{@link VehicleLeaveEvent} is cancelled for participants — this keeps
 *         them locked inside their car for the whole race. They can only get
 *         out via {@code /race leave}, by finishing, or when the event stops.</li>
 *     <li>Damage, block changes, item drops, food loss and inventory clicks
 *         are blocked for participants so the arena stays clean.</li>
 *     <li>While {@link RaceGame.State#STARTING}, positional movement is cancelled
 *         so nobody leaves the grid before GO (or before the 10s countdown ends
 *         when no start-light gantry is configured).</li>
 * </ul>
 */
public final class RaceListener implements Listener {

    private final RaceGame game;

    public RaceListener(RaceGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player.getUniqueId())) return;
        if (game.getState() != RaceGame.State.STARTING) return;
        if (event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(from.clone());
        }
    }

    /**
     * Force participants to stay in their vehicle while the race is active.
     * The only sanctioned ways out are finishing or {@code /race leave}, both
     * of which call {@code VehicleUtils.kickOut} server-side (which does NOT
     * fire this event for players we manage).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleLeave(VehicleLeaveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!shouldLockIn(player)) return;
        event.setCancelled(true);
        player.sendActionBar(Messages.warn("Je zit vast in de race — gebruik /race leave om op te geven"));
    }

    /**
     * Belt-and-suspenders for {@link #onVehicleLeave(VehicleLeaveEvent)}: vanilla
     * shift-to-dismount fires Bukkit's {@link EntityDismountEvent} BEFORE
     * MTVehicles' VehicleLeaveEvent, so cancelling only the MTV-side one isn't
     * guaranteed to keep the player seated. Cancelling the Bukkit-side event
     * here prevents the player from coming off the armor-stand mount at all.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        if (!(dismounted instanceof Player player)) return;
        if (!shouldLockIn(player)) return;
        event.setCancelled(true);
    }

    /**
     * Fuel must stay full for the entire race. {@link VehicleFuelEvent} is
     * MTVehicles' refuel event (jerry can), not a consumption event, so on its
     * own it does not stop fuel from being drained while driving. The actual
     * top-up is done from a tick task in {@link RaceGame}; cancelling this
     * event additionally blocks any out-of-band refuel attempt by spectators.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleFuel(VehicleFuelEvent event) {
        String plate = event.getLicensePlate();
        if (plate == null) return;
        for (RacePlayerState ps : game.getAllPlayerStates()) {
            if (plate.equals(ps.getLicensePlate())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * True if {@code player} is currently a racer that we want pinned inside
     * their vehicle. Finished players (placement awarded) and idle state get a
     * free pass so the natural cleanup flow can dismount them.
     */
    private boolean shouldLockIn(Player player) {
        RacePlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null) return false;
        if (ps.isFinished()) return false;
        return game.getState() != RaceGame.State.IDLE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player.getUniqueId())) return;
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setFallDistance(0f);
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
