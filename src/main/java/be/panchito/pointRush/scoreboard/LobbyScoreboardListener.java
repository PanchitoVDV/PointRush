package be.panchito.pointRush.scoreboard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Hooks player join / quit so the {@link LobbyScoreboard} can attach a
 * sidebar immediately rather than waiting for the next periodic tick.
 */
public final class LobbyScoreboardListener implements Listener {

    private final LobbyScoreboard scoreboard;

    public LobbyScoreboardListener(LobbyScoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scoreboard.attach(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboard.detach(event.getPlayer());
    }
}
