package be.panchito.pointRush.commands;

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
    private static final List<String> SUBCOMMANDS = List.of("spin", "forcespin", "list", "help");

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
        sender.sendMessage(line("/randomevent list", "Toon welke minigames klaar staan"));
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
