package be.panchito.pointRush.network;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Stuurt spelers die op de events-server binnenkomen zonder lopend event terug naar survival.
 * De daadwerkelijke logica (rol-check, vertraging) zit in {@link CrossServerEventService#handleJoin}.
 */
public final class CrossServerEventListener implements Listener {

    private final CrossServerEventService service;

    public CrossServerEventListener(CrossServerEventService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }
}
