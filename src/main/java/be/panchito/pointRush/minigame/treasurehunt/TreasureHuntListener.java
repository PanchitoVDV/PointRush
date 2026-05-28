package be.panchito.pointRush.minigame.treasurehunt;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Treasure Hunt: claim bij bewegen of rechtsklik in de buurt van een schat.
 */
public final class TreasureHuntListener implements Listener {

    private final TreasureHuntGame game;

    public TreasureHuntListener(TreasureHuntGame game) {
        this.game = game;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (game.getState() != TreasureHuntGame.State.RUNNING) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        game.tryClaimNearby(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (game.getState() != TreasureHuntGame.State.RUNNING) return;
        Player player = event.getPlayer();
        game.tryClaimNearby(player);
    }
}
