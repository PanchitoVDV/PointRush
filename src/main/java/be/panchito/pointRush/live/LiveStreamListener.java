package be.panchito.pointRush.live;

import be.panchito.pointRush.commands.LiveCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LiveStreamListener implements Listener {

    private final LiveCommand liveCommand;

    public LiveStreamListener(LiveCommand liveCommand) {
        this.liveCommand = liveCommand;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        liveCommand.clearLive(event.getPlayer(), true);
    }
}
