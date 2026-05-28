package be.panchito.pointRush.minigame.boatrace;

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
 * {@code /boatrace} — setup + control for the Boat Race minigame.
 */
public final class BoatRaceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.boatrace.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "help", "leave",
            "setlobby", "setfinish", "addgrid", "addcheckpoint",
            "setlights", "clearlights",
            "cleargrid", "clearcheckpoints",
            "setlaps", "setboat", "reload"
    );

    private static final List<String> BOAT_TYPES = List.of(
            "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "CHERRY", "DARK_OAK", "MANGROVE", "BAMBOO"
    );

    private final BoatRaceGame game;
    private final BoatRaceConfig config;

    public BoatRaceCommand(BoatRaceGame game, BoatRaceConfig config) {
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
            case "setlobby" -> handleSetLobby(sender);
            case "setfinish" -> handleSetFinish(sender);
            case "addgrid" -> handleAddGrid(sender);
            case "addcheckpoint" -> handleAddCheckpoint(sender);
            case "setlights" -> handleSetLights(sender);
            case "clearlights" -> handleClearLights(sender);
            case "cleargrid" -> handleClearGrid(sender);
            case "clearcheckpoints" -> handleClearCheckpoints(sender);
            case "setlaps" -> handleSetLaps(sender, args);
            case "setboat" -> handleSetBoat(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Bootrace ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/boatrace info", "Bekijk de setup en status"));
        sender.sendMessage(line("/boatrace leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/boatrace setlobby", "Zet de lobby spawn op je locatie"));
            sender.sendMessage(line("/boatrace setfinish", "Zet de finish/start lijn op je locatie"));
            sender.sendMessage(line("/boatrace addgrid", "Voeg startgrid slot toe op je locatie"));
            sender.sendMessage(line("/boatrace addcheckpoint", "Voeg checkpoint toe op je locatie"));
            sender.sendMessage(line("/boatrace setlights", "Midden + richting van het F1-startlicht-paneel"));
            sender.sendMessage(line("/boatrace clearlights", "Verwijder startlichten (countdown ipv F1)"));
            sender.sendMessage(line("/boatrace setboat <type>", "Boottype (OAK, SPRUCE, BAMBOO, ...)"));
            sender.sendMessage(line("/boatrace cleargrid", "Wis alle grid slots"));
            sender.sendMessage(line("/boatrace clearcheckpoints", "Wis alle checkpoints"));
            sender.sendMessage(line("/boatrace setlaps <n>", "Stel het aantal laps in (default 3)"));
            sender.sendMessage(line("/boatrace start", "Start het event"));
            sender.sendMessage(line("/boatrace stop", "Stop het event"));
            sender.sendMessage(line("/boatrace reload", "Herlaad boatrace config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Bootrace ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Lobby spawn: " + locText(config.getLobbySpawn())));
        sender.sendMessage(Messages.info("Finish: " + locText(config.getFinish())));
        sender.sendMessage(Messages.info("Grid slots: " + config.getGrid().size()));
        sender.sendMessage(Messages.info("Checkpoints: " + config.getCheckpoints().size()));
        sender.sendMessage(Messages.info("Laps: " + config.getLaps()));
        sender.sendMessage(Messages.info("Boottype: " + config.getBoatType() + " → " + config.getBoatEntityType().name()));
        sender.sendMessage(Messages.info("Startlichten (F1): " + lightsState()));
        sender.sendMessage(Messages.info("Capaciteit: " + config.getCapacity() + " spelers"));
        if (game.getState() != BoatRaceGame.State.IDLE) {
            sender.sendMessage(Messages.info("Deelnemers: " + game.getAllPlayerStates().size()));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != BoatRaceGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een bootrace event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Bootrace setup is niet compleet."
                    + " Zorg voor ≥1 grid + finish + ≥1 checkpoint."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon bootrace niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Bootrace event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen bootrace event."));
            return;
        }
        sender.sendMessage(Messages.success("Bootrace event gestopt."));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setLobbySpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Bootrace lobby spawn ingesteld."));
    }

    private void handleSetFinish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setFinish(player.getLocation().clone());
        player.sendMessage(Messages.success("Bootrace finish/start lijn ingesteld."));
    }

    private void handleAddGrid(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.addGridSlot(player.getLocation().clone());
        player.sendMessage(Messages.success("Grid slot #" + config.getGrid().size() + " toegevoegd."));
    }

    private void handleAddCheckpoint(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.addCheckpoint(player.getLocation().clone());
        player.sendMessage(Messages.success("Checkpoint #" + config.getCheckpoints().size() + " toegevoegd."));
    }

    private void handleSetLights(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen het lichtpaneel zetten."));
            return;
        }
        config.setStartLightsGantry(player.getLocation().clone());
        player.sendMessage(Messages.success("F1-startlicht midden ingesteld."));
    }

    private void handleClearLights(CommandSender sender) {
        config.clearStartLightsGantry();
        sender.sendMessage(Messages.success("Startlichten verwijderd. Bij /boatrace start wordt nu de "
                + BoatRaceGame.COUNTDOWN_SECONDS + "s aftel gebruikt."));
    }

    private String lightsState() {
        Location g = config.getStartLightsGantry();
        if (g == null || g.getWorld() == null) {
            return "uit (gebruik /boatrace setlights voor rood→groen + GO)";
        }
        return locText(g) + "  yaw→rij";
    }

    private void handleClearGrid(CommandSender sender) {
        config.clearGrid();
        sender.sendMessage(Messages.success("Alle grid slots gewist."));
    }

    private void handleClearCheckpoints(CommandSender sender) {
        config.clearCheckpoints();
        sender.sendMessage(Messages.success("Alle checkpoints gewist."));
    }

    private void handleSetLaps(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /boatrace setlaps <n>"));
            return;
        }
        int n;
        try {
            n = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Aantal laps moet een geheel getal zijn."));
            return;
        }
        if (n < 1 || n > 50) {
            sender.sendMessage(Messages.error("Aantal laps moet tussen 1 en 50 zijn."));
            return;
        }
        config.setLaps(n);
        sender.sendMessage(Messages.success("Aantal laps ingesteld op " + n + "."));
    }

    private void handleSetBoat(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /boatrace setboat <type>  (bijv. OAK, BAMBOO)"));
            return;
        }
        config.setBoatType(args[1]);
        sender.sendMessage(Messages.success("Boottype ingesteld op " + config.getBoatType()
                + " → " + config.getBoatEntityType().name()));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Bootrace config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het bootrace event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het bootrace event verlaten."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setboat")) {
            return Commands.filterPrefix(BOAT_TYPES, args[1]);
        }
        return List.of();
    }

}
