package be.panchito.pointRush.minigame.holdthecrown;

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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /holdthecrown} — setup + control voor Hold the Crown.
 */
public final class HoldTheCrownCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.holdthecrown.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setredspawn", "setbluespawn", "setcenter",
            "setpoint", "setduration", "setradius",
            "reload", "leave", "help"
    );

    private final HoldTheCrownGame game;
    private final HoldTheCrownConfig config;

    public HoldTheCrownCommand(HoldTheCrownGame game, HoldTheCrownConfig config) {
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
            case "setredspawn" -> handleSetSpawn(sender, true);
            case "setbluespawn" -> handleSetSpawn(sender, false);
            case "setcenter" -> handleSetCenter(sender);
            case "setpoint" -> handleSetPoint(sender, args);
            case "setduration" -> handleSetDuration(sender, args);
            case "setradius" -> handleSetRadius(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Hold the Crown ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/holdthecrown info", "Bekijk de setup en status"));
        sender.sendMessage(line("/holdthecrown leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/holdthecrown setredspawn", "Rood team spawn"));
            sender.sendMessage(line("/holdthecrown setbluespawn", "Blauw team spawn"));
            sender.sendMessage(line("/holdthecrown setcenter", "Kroon-pickup in het midden"));
            sender.sendMessage(line("/holdthecrown setpoint <sec>", "Seconden kroon voor 1 punt (default 60)"));
            sender.sendMessage(line("/holdthecrown setduration <min>", "Eventduur (default 30)"));
            sender.sendMessage(line("/holdthecrown setradius <blokken>", "Pickup radius midden (default 3)"));
            sender.sendMessage(line("/holdthecrown start", "Start het event"));
            sender.sendMessage(line("/holdthecrown stop", "Stop het event"));
            sender.sendMessage(line("/holdthecrown reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- Hold the Crown ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Rood spawn: " + locText(config.getRedSpawn())));
        sender.sendMessage(Messages.info("Blauw spawn: " + locText(config.getBlueSpawn())));
        sender.sendMessage(Messages.info("Midden (kroon): " + locText(config.getCenter())));
        sender.sendMessage(Messages.info("Pickup radius: " + config.getPickupRadius() + " blok"));
        sender.sendMessage(Messages.info("Punt elke: " + config.getPointSeconds() + "s met kroon"));
        sender.sendMessage(Messages.info("Eventduur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Nexo kroon: " + CrownItem.NEXO_ID
                + " (" + (CrownItem.isAvailable() ? "geladen" : "niet gevonden") + ")"));
        sender.sendMessage(Messages.info("Spectator na dood: 3 minuten"));
        if (game.getState() != HoldTheCrownGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == HoldTheCrownGame.State.RUNNING) {
                sender.sendMessage(Messages.info("Resttijd: " + game.formatTime(game.getRunTimeLeftMs())));
            }
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != HoldTheCrownGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Hold the Crown event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Setup niet compleet. Stel red/blauw spawn + center in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon event niet starten (minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("Hold the Crown gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Hold the Crown event."));
            return;
        }
        sender.sendMessage(Messages.success("Hold the Crown gestopt."));
    }

    private void handleSetSpawn(CommandSender sender, boolean red) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        if (red) {
            config.setRedSpawn(player.getLocation().clone());
            player.sendMessage(Messages.success("Rood spawn ingesteld."));
        } else {
            config.setBlueSpawn(player.getLocation().clone());
            player.sendMessage(Messages.success("Blauw spawn ingesteld."));
        }
    }

    private void handleSetCenter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setCenter(player.getLocation().clone());
        player.sendMessage(Messages.success("Kroon-midden ingesteld."));
    }

    private void handleSetPoint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /holdthecrown setpoint <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 15 || sec > 600) {
            sender.sendMessage(Messages.error("Waarde moet tussen 15 en 600 seconden zijn."));
            return;
        }
        config.setPointSeconds(sec);
        sender.sendMessage(Messages.success("1 punt elke " + sec + " seconden met de kroon."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /holdthecrown setduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 5 || min > 120) {
            sender.sendMessage(Messages.error("Waarde moet tussen 5 en 120 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Eventduur: " + min + " minuten."));
    }

    private void handleSetRadius(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /holdthecrown setradius <blokken>"));
            return;
        }
        double radius;
        try {
            radius = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
            return;
        }
        if (radius < 1.0 || radius > 20.0) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1 en 20 zijn."));
            return;
        }
        config.setPickupRadius(radius);
        sender.sendMessage(Messages.success("Pickup radius: " + radius + " blok."));
    }

    private int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
            return -1;
        }
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Hold the Crown config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan Hold the Crown."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt Hold the Crown verlaten."));
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
