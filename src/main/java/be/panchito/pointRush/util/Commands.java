package be.panchito.pointRush.util;

import be.panchito.pointRush.PointRush;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared helpers for command executors and tab completers.
 */
public final class Commands {

    private Commands() {
    }

    public static boolean isAdmin(CommandSender sender, String permission) {
        return sender.hasPermission(permission) || sender instanceof ConsoleCommandSender;
    }

    /**
     * In network-modus op de survival-host start een minigame niet lokaal maar als cross-server event:
     * de deelnemers gaan naar de events-server, die de game host. Roep dit als eerste regel in een
     * {@code handleStart} aan.
     *
     * @return {@code true} als de start cross-server is afgehandeld (het command moet dan stoppen);
     *         {@code false} betekent normaal lokaal starten (standalone of de events-server zelf).
     */
    public static boolean dispatchCrossServerStart(CommandSender sender, String minigameId) {
        PointRush plugin = PointRush.getInstance();
        if (plugin == null || plugin.getNetworkSettings() == null
                || !plugin.getNetworkSettings().isSurvivalHost()) {
            return false;
        }
        if (plugin.isEventLiveNow()) {
            sender.sendMessage(Messages.error("Er loopt al een event."));
            return true;
        }
        if (plugin.getCrossServerEventService().requestStart(minigameId)) {
            sender.sendMessage(Messages.success(
                    "Event wordt gestart op de event-server — spelers worden gestuurd."));
        } else {
            sender.sendMessage(Messages.error("Kon cross-server event niet aanvragen."));
        }
        return true;
    }

    public static List<String> filterPrefix(List<String> source, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
