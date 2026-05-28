package be.panchito.pointRush.minigame.goldrush;

import be.panchito.pointRush.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Gold Rush: tel gouderts, blokkeer goud-blokken plaatsen tegen cheat.
 */
public final class GoldRushListener implements Listener {

    private final GoldRushGame game;

    public GoldRushListener(GoldRushGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (game.getState() != GoldRushGame.State.RUNNING) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        if (GoldRushMaterials.isCountedOre(block.getType())) {
            game.addPoint(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (game.getState() != GoldRushGame.State.RUNNING) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (GoldRushMaterials.isPlaceBlocked(event.getBlock().getType())) {
            event.setCancelled(true);
            player.sendMessage(Messages.warn("Tijdens Gold Rush mag je geen goud-blokken of -erts plaatsen."));
        }
    }
}
