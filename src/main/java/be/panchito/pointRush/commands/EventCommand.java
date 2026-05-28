package be.panchito.pointRush.commands;

import be.panchito.pointRush.random.RandomEventService;
import be.panchito.pointRush.random.UpcomingEvent;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /event start} — start het geplande random event.
 */
public final class EventCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.event.admin";
    private static final List<String> SUBCOMMANDS = List.of("start", "status", "help");

    private final RandomEventService randomEventService;

    public EventCommand(RandomEventService randomEventService) {
        this.randomEventService = randomEventService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "status" -> showStatus(sender);
            case "start" -> randomEventService.startScheduled(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- Event planning ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/event start", "Start het geplande random event"));
        sender.sendMessage(line("/event status", "Toon gepland event en pool"));
        sender.sendMessage(line("/randomevent", "Kies morgen's event via het rad (start niet meteen)"));
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- Event status ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));

        UpcomingEvent upcoming = randomEventService.upcoming();
        if (upcoming != null) {
            sender.sendMessage(Messages.info("Gepland: " + upcoming.displayName()
                    + " op " + upcoming.scheduledFor()));
        } else {
            sender.sendMessage(Messages.warn("Geen event gepland. Gebruik /randomevent."));
        }

        List<String> pool = randomEventService.listPoolDisplayNames();
        sender.sendMessage(Messages.info("Pool (" + pool.size() + "): "
                + (pool.isEmpty() ? "leeg (wordt automatisch gevuld)" : String.join(", ", pool))));

        if (randomEventService.isSpinning()) {
            sender.sendMessage(Messages.warn("Het rad draait momenteel..."));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) return List.of();
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        return List.of();
    }
}
