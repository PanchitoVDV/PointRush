package be.panchito.pointRush.minigame.tnttag;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /tnttag command - admin setup + join/leave for the TNT Tag minigame.
 */
public final class TntTagCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.tnttag.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "reload", "leave", "help"
    );

    private final TntTagGame game;
    private final TntTagConfig config;

    public TntTagCommand(TntTagGame game, TntTagConfig config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) { sendHelp(sender); return true; }
        if (sub.equals("info")) { showInfo(sender); return true; }
        if (sub.equals("leave")) { handleLeave(sender); return true; }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush TNT Tag ---"),
                NamedTextColor.RED, TextDecoration.BOLD));
        sender.sendMessage(line("/tnttag info", "Bekijk de setup en status"));
        sender.sendMessage(line("/tnttag leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/tnttag setspawn", "Zet de arena spawn op je locatie"));
            sender.sendMessage(line("/tnttag start", "Start het event"));
            sender.sendMessage(line("/tnttag stop", "Stop het event"));
            sender.sendMessage(line("/tnttag reload", "Herlaad tnttag.yml"));
        }
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.RED))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush TNT Tag ---"),
                NamedTextColor.RED, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        if (game.getState() != TntTagGame.State.IDLE) {
            sender.sendMessage(Messages.info("Ronde: " + game.getRoundNumber()));
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != TntTagGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een TNT Tag event."));
            return;
        }
        if (config.getSpawn() == null) {
            sender.sendMessage(Messages.error("Geen spawn ingesteld. Gebruik /tnttag setspawn."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon TNT Tag niet starten (heb je minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("TNT Tag event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen TNT Tag event."));
            return;
        }
        sender.sendMessage(Messages.success("TNT Tag event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("TNT Tag spawn ingesteld en opgeslagen in tnttag.yml."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("TNT Tag config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het TNT Tag event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het TNT Tag event verlaten."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        return List.of();
    }

}
