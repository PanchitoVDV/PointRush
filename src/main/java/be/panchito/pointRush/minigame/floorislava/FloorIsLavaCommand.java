package be.panchito.pointRush.minigame.floorislava;

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
 * {@code /floorislava} — setup + control voor Floor is Lava.
 */
public final class FloorIsLavaCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.floorislava.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "pos1", "pos2",
            "setdeathy", "setlava", "setitems", "reload", "leave", "help"
    );

    private final FloorIsLavaGame game;
    private final FloorIsLavaConfig config;

    public FloorIsLavaCommand(FloorIsLavaGame game, FloorIsLavaConfig config) {
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

        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }
        if (sub.equals("info")) {
            showInfo(sender);
            return true;
        }
        if (sub.equals("leave")) {
            handleLeave(sender);
            return true;
        }

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
            case "setlava" -> handleSetLavaRise(sender, args);
            case "setitems" -> handleSetItemDrop(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Floor is Lava ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/floorislava info", "Bekijk de setup en status"));
        sender.sendMessage(line("/floorislava leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/floorislava setspawn", "Zet de arena spawn op je locatie"));
            sender.sendMessage(line("/floorislava pos1", "Markeer hoek 1 van de arena"));
            sender.sendMessage(line("/floorislava pos2", "Markeer hoek 2 van de arena"));
            sender.sendMessage(line("/floorislava setdeathy <y>", "Stel de death plane Y in"));
            sender.sendMessage(line("/floorislava setlava <sec>", "Seconden tussen lava-stijging (min 30)"));
            sender.sendMessage(line("/floorislava setitems <sec>", "Seconden tussen random kit-drop (min 10)"));
            sender.sendMessage(line("/floorislava start", "Start het event"));
            sender.sendMessage(line("/floorislava stop", "Stop het event"));
            sender.sendMessage(line("/floorislava reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Floor is Lava ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        sender.sendMessage(Messages.info("Hoek 1: " + locText(config.getRegionMin())));
        sender.sendMessage(Messages.info("Hoek 2: " + locText(config.getRegionMax())));
        sender.sendMessage(Messages.info("Death Y: " + config.getDeathY()));
        sender.sendMessage(Messages.info("Lava stijgt elke: " + config.getLavaRiseSeconds() + "s"));
        sender.sendMessage(Messages.info("Kit drop elke: " + config.getItemDropSeconds() + "s"));
        if (game.getState() != FloorIsLavaGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()
                    + " (alive: " + game.aliveCount() + ")"));
            if (game.getCurrentLavaY() > Integer.MIN_VALUE) {
                sender.sendMessage(Messages.info("Huidige lava Y: " + game.getCurrentLavaY()));
            }
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != FloorIsLavaGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Floor is Lava event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Setup niet compleet. Stel spawn + pos1 + pos2 in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Floor is Lava niet starten (minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("Floor is Lava event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Floor is Lava event."));
            return;
        }
        sender.sendMessage(Messages.success("Floor is Lava event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Floor is Lava spawn ingesteld."));
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
            sender.sendMessage(Messages.error("Gebruik: /floorislava setdeathy <y>"));
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

    private void handleSetLavaRise(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /floorislava setlava <seconden>"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Seconden moet een geheel getal zijn."));
            return;
        }
        if (sec < 30 || sec > 300) {
            sender.sendMessage(Messages.error("Waarde moet tussen 30 en 300 seconden zijn."));
            return;
        }
        config.setLavaRiseSeconds(sec);
        sender.sendMessage(Messages.success("Lava stijgt nu elke " + sec + " seconden."));
    }

    private void handleSetItemDrop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /floorislava setitems <seconden>"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Seconden moet een geheel getal zijn."));
            return;
        }
        if (sec < 10 || sec > 120) {
            sender.sendMessage(Messages.error("Waarde moet tussen 10 en 120 seconden zijn."));
            return;
        }
        config.setItemDropSeconds(sec);
        sender.sendMessage(Messages.success("Random kit drop elke " + sec + " seconden."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Floor is Lava config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het Floor is Lava event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het Floor is Lava event verlaten."));
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
