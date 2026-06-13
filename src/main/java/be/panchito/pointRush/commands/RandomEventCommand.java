package be.panchito.pointRush.commands;

import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.random.RandomEventService;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /randomevent} — draait het random event-rad en plant morgen's minigame.
 */
public final class RandomEventCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.randomevent.admin";
    private static final List<String> SUBCOMMANDS =
            List.of("spin", "forcespin", "disable", "enable", "list", "help");

    private final RandomEventService randomEventService;

    public RandomEventCommand(RandomEventService randomEventService) {
        this.randomEventService = randomEventService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        String sub = args.length == 0 ? "spin" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "list" -> showList(sender);
            case "spin", "start" -> handleSpin(sender);
            case "forcespin", "force" -> handleForceSpin(sender);
            case "disable", "off" -> handleSetDisabled(sender, args, true);
            case "enable", "on" -> handleSetDisabled(sender, args, false);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- Random Event ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/randomevent", "Draait het rad en kiest morgen's event (start niet meteen)"));
        sender.sendMessage(line("/randomevent forcespin", "Kies opnieuw, ook als er al iets gepland staat"));
        sender.sendMessage(line("/event start", "Start het geplande event"));
        sender.sendMessage(line("/randomevent disable <event>", "Haal een event tijdelijk uit het rad"));
        sender.sendMessage(line("/randomevent enable <event>", "Zet een event weer terug in het rad"));
        sender.sendMessage(line("/randomevent list", "Toon welke minigames klaar staan + wat uit staat"));
        sender.sendMessage(line("/randomevent help", "Deze help"));
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showList(CommandSender sender) {
        List<String> ready = randomEventService.listReadyEventNames();
        sender.sendMessage(Component.text(SmallText.of("--- Klaar voor random event ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        if (ready.isEmpty()) {
            sender.sendMessage(Messages.warn("Geen minigames klaar."));
            return;
        }
        for (String name : ready) {
            sender.sendMessage(Messages.info("• " + name));
        }

        List<String> disabled = randomEventService.disabledEventIds();
        if (!disabled.isEmpty()) {
            sender.sendMessage(Component.text(SmallText.of("--- Uit het rad (disabled) ---"),
                    NamedTextColor.RED, TextDecoration.BOLD));
            for (String id : disabled) {
                sender.sendMessage(Messages.warn("• " + MinigameRegistry.displayName(id)
                        + " (terug met: /randomevent enable " + id + ")"));
            }
        }

        if (randomEventService.isSpinning()) {
            sender.sendMessage(Messages.warn("Het rad draait momenteel..."));
        }
    }

    private void handleSpin(CommandSender sender) {
        if (!randomEventService.spin(sender)) {
            return;
        }
        sender.sendMessage(Messages.info("Het random event-rad draait — resultaat op de website en voor morgen."));
    }

    private void handleForceSpin(CommandSender sender) {
        if (!randomEventService.forceSpin(sender)) {
            return;
        }
        sender.sendMessage(Messages.info("Force spin — het rad draait opnieuw voor morgen's event."));
    }

    private void handleSetDisabled(CommandSender sender, String[] args, boolean disable) {
        String verb = disable ? "disable" : "enable";
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /randomevent " + verb + " <event>"));
            sendEventIds(sender);
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!MinigameRegistry.events().containsKey(id)) {
            sender.sendMessage(Messages.error("Onbekend event: " + id));
            sendEventIds(sender);
            return;
        }

        String name = MinigameRegistry.displayName(id);
        if (!randomEventService.setEventDisabled(id, disable)) {
            sender.sendMessage(Messages.warn(name + (disable
                    ? " stond al uit voor het rad."
                    : " stond al aan voor het rad.")));
            return;
        }

        if (disable) {
            sender.sendMessage(Messages.success(name
                    + " is uit het rad gehaald (tijdelijk, tot je 'enable' gebruikt)."));
            var upcoming = randomEventService.upcoming();
            if (upcoming != null && upcoming.eventId().equals(id)) {
                sender.sendMessage(Messages.warn("Let op: " + name + " staat nog wél gepland voor "
                        + upcoming.scheduledFor() + ". Gebruik /randomevent forcespin om opnieuw te kiezen."));
            }
        } else {
            sender.sendMessage(Messages.success(name + " staat weer in het rad."));
        }
    }

    private void sendEventIds(CommandSender sender) {
        sender.sendMessage(Messages.info("Geldige events: "
                + String.join(", ", MinigameRegistry.events().keySet())));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) return List.of();
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("enable") || sub.equals("on")) {
                // Alleen events die nu uit staan kun je weer aanzetten.
                return Commands.filterPrefix(randomEventService.disabledEventIds(), args[1]);
            }
            if (sub.equals("disable") || sub.equals("off")) {
                // Alleen events die nu aan staan kun je uitzetten.
                List<String> enabled = new ArrayList<>(MinigameRegistry.events().keySet());
                enabled.removeAll(randomEventService.disabledEventIds());
                return Commands.filterPrefix(enabled, args[1]);
            }
        }
        return List.of();
    }

}
