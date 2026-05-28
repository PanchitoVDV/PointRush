package be.panchito.pointRush.minigame.tntrun;

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
 * {@code /tntrun} — setup + control for the TNT Run minigame.
 *
 * <p>Public subcommands: {@code info}, {@code leave}, {@code help}.
 * Admin subcommands: {@code setspawn}, {@code pos1}, {@code pos2},
 * {@code setdeathy}, {@code start}, {@code stop}, {@code reload}.
 */
public final class TntRunCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.tntrun.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "pos1", "pos2",
            "setdeathy", "reload", "leave", "help"
    );

    private final TntRunGame game;
    private final TntRunConfig config;

    public TntRunCommand(TntRunGame game, TntRunConfig config) {
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
            case "pos1" -> handleSetCorner(sender, 1);
            case "pos2" -> handleSetCorner(sender, 2);
            case "setdeathy" -> handleSetDeathY(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush TNT Run ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/tntrun info", "Bekijk de setup en status"));
        sender.sendMessage(line("/tntrun leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/tntrun setspawn", "Zet de arena spawn op je locatie"));
            sender.sendMessage(line("/tntrun pos1", "Markeer hoek 1 van de vloer"));
            sender.sendMessage(line("/tntrun pos2", "Markeer hoek 2 van de vloer"));
            sender.sendMessage(line("/tntrun setdeathy <y>", "Stel de death plane Y in"));
            sender.sendMessage(line("/tntrun start", "Start het event"));
            sender.sendMessage(line("/tntrun stop", "Stop het event"));
            sender.sendMessage(line("/tntrun reload", "Herlaad tntrun.yml"));
        }
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush TNT Run ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        sender.sendMessage(Messages.info("Hoek 1: " + locText(config.getRegionMin())));
        sender.sendMessage(Messages.info("Hoek 2: " + locText(config.getRegionMax())));
        sender.sendMessage(Messages.info("Death Y: " + config.getDeathY()));
        if (game.getState() != TntRunGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()
                    + " (alive: " + game.aliveCount() + ")"));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != TntRunGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een TNT Run event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("TNT Run setup is niet compleet."
                    + " Stel spawn + pos1 + pos2 in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon TNT Run niet starten (heb je minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("TNT Run event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen TNT Run event."));
            return;
        }
        sender.sendMessage(Messages.success("TNT Run event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("TNT Run spawn ingesteld."));
    }

    private void handleSetCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setCorner(index, player.getLocation().clone());
        player.sendMessage(Messages.success("Hoek " + index + " gemarkeerd."));
    }

    private void handleSetDeathY(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /tntrun setdeathy <y>"));
            return;
        }
        double y;
        try {
            y = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Death Y moet een geldig getal zijn."));
            return;
        }
        config.setDeathY(y);
        sender.sendMessage(Messages.success("Death Y ingesteld op " + y));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("TNT Run config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het TNT Run event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het TNT Run event verlaten."));
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
