package be.panchito.pointRush.util;

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
