package be.panchito.pointRush.minigame.koth;

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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /koth} — setup + control voor Wipeout KOTH.
 */
public final class KothCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.koth.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "pos1", "pos2",
            "addspot", "delspot", "listspots",
            "setpoint", "setduration", "setspotrespawn",
            "reload", "leave", "help"
    );

    private static final List<String> SPOT_TYPES = Arrays.stream(KothSpotType.values())
            .map(t -> t.getConfigKey())
            .collect(Collectors.toList());

    private final KothGame game;
    private final KothConfig config;

    public KothCommand(KothGame game, KothConfig config) {
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
        if (sub.equals("listspots")) {
            handleListSpots(sender);
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
            case "addspot" -> handleAddSpot(sender, args);
            case "delspot" -> handleDelSpot(sender);
            case "setpoint" -> handleSetPoint(sender, args);
            case "setduration" -> handleSetDuration(sender, args);
            case "setspotrespawn" -> handleSetSpotRespawn(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush KOTH (Wipeout) ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/koth info", "Bekijk de setup en status"));
        sender.sendMessage(line("/koth leave", "Verlaat het lopende event"));
        sender.sendMessage(line("/koth listspots", "Toon alle power-spots"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/koth setspawn", "Zet de arena spawn op je locatie"));
            sender.sendMessage(line("/koth pos1 / pos2", "Markeer de hill region"));
            sender.sendMessage(line("/koth addspot [type]", "Power-spot op je locatie (random/troll/wind/knock/...)"));
            sender.sendMessage(line("/koth delspot", "Verwijder dichtstbijzijnde spot (3 blok)"));
            sender.sendMessage(line("/koth setpoint <sec>", "Seconden op hill voor 1 punt (default 60)"));
            sender.sendMessage(line("/koth setduration <min>", "Eventduur in minuten (default 30)"));
            sender.sendMessage(line("/koth setspotrespawn <sec>", "Spot respawn cooldown (default 45)"));
            sender.sendMessage(line("/koth start", "Start het event"));
            sender.sendMessage(line("/koth stop", "Stop het event"));
            sender.sendMessage(line("/koth reload", "Herlaad koth config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush KOTH ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        sender.sendMessage(Messages.info("Hill hoek 1: " + locText(config.getRegionMin())));
        sender.sendMessage(Messages.info("Hill hoek 2: " + locText(config.getRegionMax())));
        sender.sendMessage(Messages.info("Punt elke: " + config.getPointSeconds() + "s op hill"));
        sender.sendMessage(Messages.info("Eventduur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Spot respawn: " + config.getSpotRespawnSeconds() + "s"));
        sender.sendMessage(Messages.info("Power-spots: " + config.getPowerSpots().size()));
        sender.sendMessage(Messages.info("Spectator na dood: 3 minuten"));
        if (game.getState() != KothGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == KothGame.State.RUNNING) {
                sender.sendMessage(Messages.info("Resttijd: " + game.formatTime(game.getRunTimeLeftMs())));
            }
        }
    }

    private void handleListSpots(CommandSender sender) {
        List<KothPowerSpot> spots = config.getPowerSpots();
        if (spots.isEmpty()) {
            sender.sendMessage(Messages.info("Geen power-spots ingesteld."));
            return;
        }
        sender.sendMessage(Component.text(SmallText.of("--- KOTH Power Spots ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        for (KothPowerSpot spot : spots) {
            Location loc = spot.getLocation();
            sender.sendMessage(Messages.info(spot.getId() + " · " + spot.getType().getLabel()
                    + " · " + locText(loc)));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != KothGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een KOTH event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("KOTH setup is niet compleet. Stel spawn + pos1 + pos2 in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon KOTH niet starten (heb je minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("KOTH event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen KOTH event."));
            return;
        }
        sender.sendMessage(Messages.success("KOTH event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("KOTH spawn ingesteld."));
    }

    private void handleSetCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setCorner(index, player.getLocation().clone());
        player.sendMessage(Messages.success("Hill hoek " + index + " gemarkeerd."));
    }

    private void handleAddSpot(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen spots zetten."));
            return;
        }
        KothSpotType type = KothSpotType.RANDOM;
        if (args.length >= 2) {
            type = KothSpotType.fromConfig(args[1]);
        }
        KothPowerSpot spot = config.addPowerSpot(player.getLocation(), type);
        player.sendMessage(Messages.success("Power-spot " + spot.getId() + " (" + type.getLabel() + ") toegevoegd."));
    }

    private void handleDelSpot(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen spots verwijderen."));
            return;
        }
        KothPowerSpot removed = config.removeNearestSpot(player.getLocation(), 3.0);
        if (removed == null) {
            player.sendMessage(Messages.error("Geen spot binnen 3 blokken gevonden."));
            return;
        }
        player.sendMessage(Messages.success("Spot " + removed.getId() + " verwijderd."));
    }

    private void handleSetPoint(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /koth setpoint <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 15 || sec > 600) {
            sender.sendMessage(Messages.error("Waarde moet tussen 15 en 600 seconden zijn."));
            return;
        }
        config.setPointSeconds(sec);
        sender.sendMessage(Messages.success("1 hill punt elke " + sec + " seconden."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /koth setduration <minuten>"));
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

    private void handleSetSpotRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /koth setspotrespawn <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 10 || sec > 600) {
            sender.sendMessage(Messages.error("Waarde moet tussen 10 en 600 seconden zijn."));
            return;
        }
        config.setSpotRespawnSeconds(sec);
        sender.sendMessage(Messages.success("Spots respawnen na " + sec + " seconden."));
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
        sender.sendMessage(Messages.success("KOTH config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het KOTH event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het KOTH event verlaten."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addspot")) {
            return Commands.filterPrefix(SPOT_TYPES, args[1]);
        }
        return List.of();
    }

}
