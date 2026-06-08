package be.panchito.pointRush.player;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Safety net for a vanilla survival server: a player who is NOT in an active
 * PointRush event must never end up in spectator. Minigames manage their own
 * participants; this only steps in when no event owns the player.
 *
 * <p>Covers two cases:
 * <ul>
 *     <li>Respawn while no event is running -&gt; force survival instead of spectator.</li>
 *     <li>Login while stranded in spectator (e.g. logged out mid-event that has since
 *         ended) -&gt; restore survival.</li>
 * </ul>
 *
 * <p>Players with {@code pointrush.spectator.bypass} (default op) are left alone so
 * staff can use spectator freely.
 */
public final class RespawnSafetyListener implements Listener {

    private static final String BYPASS_PERMISSION = "pointrush.spectator.bypass";

    private final PointRush plugin;

    public RespawnSafetyListener(PointRush plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // an active event owns this player's respawn - let the minigame decide
        if (MinigameRegistry.isPlayerInActiveEvent(plugin, player.getUniqueId())) {
            return;
        }
        // run next tick so this lands after any minigame's own (scheduled) gamemode change
        plugin.getServer().getScheduler().runTask(plugin, () -> rescueIfStranded(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rescueIfStranded(event.getPlayer());
    }

    private void rescueIfStranded(Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        if (MinigameRegistry.isPlayerInActiveEvent(plugin, player.getUniqueId())) {
            return;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
    }
}
