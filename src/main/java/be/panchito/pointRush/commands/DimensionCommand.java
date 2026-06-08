package be.panchito.pointRush.commands;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import be.panchito.pointRush.world.WorldAccessSettings;
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
 * {@code /dimension} — admin toggle for Nether / End access.
 *
 * <p>Usage:
 * <ul>
 *     <li>{@code /dimension} of {@code /dimension status} — toont de huidige status.</li>
 *     <li>{@code /dimension nether <aan|uit|toggle>}</li>
 *     <li>{@code /dimension end <aan|uit|toggle>}</li>
 * </ul>
 */
public final class DimensionCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.dimension.admin";

    private final WorldAccessSettings settings;

    public DimensionCommand(WorldAccessSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Je hebt geen permissie voor dit commando."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("help")) {
            sendStatus(sender);
            return true;
        }

        String dimension = args[0].toLowerCase(Locale.ROOT);
        boolean isNether = dimension.equals("nether");
        boolean isEnd = dimension.equals("end") || dimension.equals("theend") || dimension.equals("the_end");
        if (!isNether && !isEnd) {
            sender.sendMessage(Messages.error("Gebruik: /dimension <nether|end> <aan|uit|toggle>"));
            return true;
        }

        boolean current = isNether ? settings.isNetherEnabled() : settings.isEndEnabled();
        Boolean target = resolveTarget(args.length >= 2 ? args[1] : null, current);
        if (target == null) {
            sender.sendMessage(Messages.error("Gebruik: /dimension " + dimension + " <aan|uit|toggle>"));
            return true;
        }

        String label2 = isNether ? "Nether" : "End";
        if (isNether) {
            settings.setNetherEnabled(target);
        } else {
            settings.setEndEnabled(target);
        }

        if (target) {
            sender.sendMessage(Messages.success("De " + label2 + " is nu ingeschakeld."));
        } else {
            sender.sendMessage(Messages.warn("De " + label2 + " is nu uitgeschakeld. "
                    + "Spelers kunnen er niet meer in via portalen."));
        }
        return true;
    }

    /** Resolves an on/off/toggle token into the desired boolean, or null when invalid. */
    private Boolean resolveTarget(String token, boolean current) {
        if (token == null) {
            return null;
        }
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "on", "aan", "enable", "enabled", "true", "1" -> true;
            case "off", "uit", "disable", "disabled", "false", "0" -> false;
            case "toggle", "switch", "wissel" -> !current;
            default -> null;
        };
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- Dimensie-toegang ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(statusLine("Nether", settings.isNetherEnabled()));
        sender.sendMessage(statusLine("End", settings.isEndEnabled()));
        sender.sendMessage(Component.text(SmallText.of("wijzig met /dimension <nether|end> <aan|uit>"),
                NamedTextColor.DARK_GRAY));
    }

    private Component statusLine(String name, boolean enabled) {
        return Component.text()
                .append(Component.text(SmallText.of(name + ": "), NamedTextColor.GRAY))
                .append(enabled
                        ? Component.text(SmallText.of("ingeschakeld"), NamedTextColor.GREEN, TextDecoration.BOLD)
                        : Component.text(SmallText.of("uitgeschakeld"), NamedTextColor.RED, TextDecoration.BOLD))
                .build();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return Commands.filterPrefix(List.of("nether", "end", "status"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("nether")
                || args[0].equalsIgnoreCase("end"))) {
            return Commands.filterPrefix(List.of("aan", "uit", "toggle"), args[1]);
        }
        return List.of();
    }
}
