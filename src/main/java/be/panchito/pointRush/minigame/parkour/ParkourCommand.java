package be.panchito.pointRush.minigame.parkour;

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
 * /parkour command - setup, control and join/leave for the parkour event.
 */
public final class ParkourCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.parkour.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "addcheckpoint", "setfinish",
            "clearcheckpoints", "reload", "leave", "help"
    );

    private final ParkourGame game;
    private final ParkourConfig config;

    public ParkourCommand(ParkourGame game, ParkourConfig config) {
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
            case "addcheckpoint" -> handleAddCheckpoint(sender);
            case "setfinish" -> handleSetFinish(sender);
            case "clearcheckpoints" -> handleClearCheckpoints(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Parkour ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/parkour info", "Bekijk de setup en status"));
        sender.sendMessage(line("/parkour leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/parkour setspawn", "Zet de start spawn op je locatie"));
            sender.sendMessage(line("/parkour addcheckpoint", "Voeg checkpoint toe op je locatie"));
            sender.sendMessage(line("/parkour setfinish", "Zet de finish op je locatie"));
            sender.sendMessage(line("/parkour clearcheckpoints", "Wis alle checkpoints"));
            sender.sendMessage(line("/parkour start", "Start het event"));
            sender.sendMessage(line("/parkour stop", "Stop het event"));
            sender.sendMessage(line("/parkour reload", "Herlaad parkour.yml"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Parkour ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        sender.sendMessage(Messages.info("Finish: " + locText(config.getFinish())));
        sender.sendMessage(Messages.info("Checkpoints: " + config.getCheckpoints().size()));
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != ParkourGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een parkour event."));
            return;
        }
        if (config.getSpawn() == null) {
            sender.sendMessage(Messages.error("Geen spawn ingesteld. Gebruik /parkour setspawn."));
            return;
        }
        if (config.getFinish() == null) {
            sender.sendMessage(Messages.error("Geen finish ingesteld. Gebruik /parkour setfinish."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon parkour niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Parkour event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen parkour event."));
            return;
        }
        sender.sendMessage(Messages.success("Parkour event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Parkour spawn ingesteld en opgeslagen in parkour.yml."));
    }

    private void handleAddCheckpoint(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.addCheckpoint(player.getLocation().clone());
        player.sendMessage(Messages.success("Checkpoint #" + config.getCheckpoints().size()
                + " toegevoegd en opgeslagen."));
    }

    private void handleSetFinish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setFinish(player.getLocation().clone());
        player.sendMessage(Messages.success("Parkour finish ingesteld en opgeslagen in parkour.yml."));
    }

    private void handleClearCheckpoints(CommandSender sender) {
        config.clearCheckpoints();
        sender.sendMessage(Messages.success("Alle checkpoints gewist en opgeslagen."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Parkour config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het parkour event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het parkour event verlaten."));
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
