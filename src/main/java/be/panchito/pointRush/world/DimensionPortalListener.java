package be.panchito.pointRush.world;

import be.panchito.pointRush.util.Messages;
import org.bukkit.Location;
import org.bukkit.World.Environment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Blocks travel <em>into</em> the Nether / End while those dimensions are disabled.
 *
 * <p>Direction matters: we only cancel when the destination is the Nether or the
 * End, so players already stuck inside can still use the return portal to leave.
 */
public final class DimensionPortalListener implements Listener {

    private final WorldAccessSettings settings;

    public DimensionPortalListener(WorldAccessSettings settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Environment target = targetEnvironment(event.getTo(), event.getCause());
        if (target == Environment.NETHER && !settings.isNetherEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.warn("De Nether is momenteel uitgeschakeld."));
        } else if (target == Environment.THE_END && !settings.isEndEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Messages.warn("De End is momenteel uitgeschakeld."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Environment env = to.getWorld().getEnvironment();
        if (env == Environment.NETHER && !settings.isNetherEnabled()) {
            event.setCancelled(true);
        } else if (env == Environment.THE_END && !settings.isEndEnabled()) {
            event.setCancelled(true);
        }
    }

    /**
     * Determine the destination dimension. Prefers the resolved target world (so we
     * can tell entering from leaving); falls back to the teleport cause when the
     * destination hasn't been computed yet.
     */
    private Environment targetEnvironment(Location to, TeleportCause cause) {
        if (to != null && to.getWorld() != null) {
            return to.getWorld().getEnvironment();
        }
        if (cause == TeleportCause.NETHER_PORTAL) {
            return Environment.NETHER;
        }
        if (cause == TeleportCause.END_PORTAL) {
            return Environment.THE_END;
        }
        return Environment.NORMAL;
    }
}
