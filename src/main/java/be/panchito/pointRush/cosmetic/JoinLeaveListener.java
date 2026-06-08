package be.panchito.pointRush.cosmetic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Clean, minimal join / leave messages: a green {@code +} on join and a red
 * {@code -} on leave, followed by the player name.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} so it overrides whatever Essentials
 * (or another plugin) set earlier in the chain - the last writer wins.
 */
public final class JoinLeaveListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(format("+", NamedTextColor.GREEN, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(format("-", NamedTextColor.RED, event.getPlayer()));
    }

    private Component format(String symbol, NamedTextColor symbolColor, Player player) {
        return Component.text()
                .append(Component.text(symbol, symbolColor, TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(player.getName(), NamedTextColor.GRAY))
                .build();
    }
}
