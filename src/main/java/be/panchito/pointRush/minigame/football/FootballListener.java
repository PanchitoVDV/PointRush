package be.panchito.pointRush.minigame.football;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit-events voor het Voetbal-event. De in-arena gameplay (bal, doelen, scoren, respawns)
 * wordt volledig door BlockBall afgehandeld; wij vangen alleen het verlaten op zodat de
 * PointRush-staat en het teleport-terug netjes opgeruimd worden.
 */
public final class FootballListener implements Listener {

    private final FootballGame game;

    public FootballListener(FootballGame game) {
        this.game = game;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (game.isParticipant(player.getUniqueId())) {
            game.removeParticipant(player, false);
        }
    }
}
