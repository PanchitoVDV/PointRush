package be.panchito.pointRush.minigame.race;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
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
 * {@code /race} — setup + control for the Race minigame.
 *
 * <p>Public subcommands: {@code info}, {@code leave}, {@code help}.
 * Admin subcommands: {@code setlobby}, {@code setfinish}, {@code addgrid},
 * {@code addcheckpoint}, {@code addvehicle}, {@code addvehiclehere},
 * {@code setlights}, {@code clearlights}, {@code cleargrid}, {@code clearcheckpoints},
 * {@code clearvehicles},
 * {@code setlaps}, {@code start}, {@code stop}, {@code reload}.
 */
public final class RaceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.race.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "help", "leave",
            "setlobby", "setfinish", "addgrid", "addcheckpoint",
            "addvehicle", "addvehiclehere",
            "setlights", "clearlights",
            "cleargrid", "clearcheckpoints", "clearvehicles",
            "setlaps", "reload"
    );

    private final RaceGame game;
    private final RaceConfig config;

    public RaceCommand(RaceGame game, RaceConfig config) {
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
            case "setlobby" -> handleSetLobby(sender);
            case "setfinish" -> handleSetFinish(sender);
            case "addgrid" -> handleAddGrid(sender);
            case "addcheckpoint" -> handleAddCheckpoint(sender);
            case "addvehicle" -> handleAddVehicle(sender, args);
            case "addvehiclehere" -> handleAddVehicleHere(sender);
            case "setlights" -> handleSetLights(sender);
            case "clearlights" -> handleClearLights(sender);
            case "cleargrid" -> handleClearGrid(sender);
            case "clearcheckpoints" -> handleClearCheckpoints(sender);
            case "clearvehicles" -> handleClearVehicles(sender);
            case "setlaps" -> handleSetLaps(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Race ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/race info", "Bekijk de setup en status"));
        sender.sendMessage(line("/race leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/race setlobby", "Zet de lobby spawn op je locatie"));
            sender.sendMessage(line("/race setfinish", "Zet de finish/start lijn op je locatie"));
            sender.sendMessage(line("/race addgrid", "Voeg startgrid slot toe op je locatie"));
            sender.sendMessage(line("/race addcheckpoint", "Voeg checkpoint toe op je locatie"));
            sender.sendMessage(line("/race addvehicle <plate>", "Voeg vehicle plate toe aan de pool"));
            sender.sendMessage(line("/race addvehiclehere", "Pak het voertuig waarin je zit"));
            sender.sendMessage(line("/race setlights", "Midden + richting van het F1-startlicht-paneel"));
            sender.sendMessage(line("/race clearlights", "Verwijder het startlicht-paneel (countdown ipv F1)"));
            sender.sendMessage(line("/race cleargrid", "Wis alle grid slots"));
            sender.sendMessage(line("/race clearcheckpoints", "Wis alle checkpoints"));
            sender.sendMessage(line("/race clearvehicles", "Wis de vehicle pool"));
            sender.sendMessage(line("/race setlaps <n>", "Stel het aantal laps in (default 3)"));
            sender.sendMessage(line("/race start", "Start het event"));
            sender.sendMessage(line("/race stop", "Stop het event"));
            sender.sendMessage(line("/race reload", "Herlaad race.yml"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Race ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Lobby spawn: " + locText(config.getLobbySpawn())));
        sender.sendMessage(Messages.info("Finish: " + locText(config.getFinish())));
        sender.sendMessage(Messages.info("Grid slots: " + config.getGrid().size()));
        sender.sendMessage(Messages.info("Vehicle plates: " + config.getVehiclePlates().size()));
        sender.sendMessage(Messages.info("Checkpoints: " + config.getCheckpoints().size()));
        sender.sendMessage(Messages.info("Laps: " + config.getLaps()));
        sender.sendMessage(Messages.info("Startlichten (F1): " + lightsState()));
        sender.sendMessage(Messages.info("Capaciteit: " + config.getCapacity() + " spelers"));
        if (game.getState() != RaceGame.State.IDLE) {
            sender.sendMessage(Messages.info("Deelnemers: " + game.getAllPlayerStates().size()));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != RaceGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een race event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Race setup is niet compleet."
                    + " Zorg voor ≥1 grid + ≥1 vehicle + finish + ≥1 checkpoint."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon race niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Race event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen race event."));
            return;
        }
        sender.sendMessage(Messages.success("Race event gestopt."));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setLobbySpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Race lobby spawn ingesteld."));
    }

    private void handleSetFinish(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setFinish(player.getLocation().clone());
        player.sendMessage(Messages.success("Race finish/start lijn ingesteld."));
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

    private void handleAddVehicle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /race addvehicle <licensePlate>"));
            return;
        }
        String plate = args[1].trim();
        if (plate.isEmpty()) {
            sender.sendMessage(Messages.error("Lege license plate is niet toegestaan."));
            return;
        }
        config.addVehiclePlate(plate);
        sender.sendMessage(Messages.success("Vehicle plate '" + plate + "' toegevoegd. "
                + "Pool grootte: " + config.getVehiclePlates().size()));
    }

    private void handleAddVehicleHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        String plate;
        try {
            plate = VehicleUtils.getDrivenVehiclePlate(player);
        } catch (Throwable t) {
            sender.sendMessage(Messages.error("MTVehicles API faalde: " + t.getMessage()));
            return;
        }
        if (plate == null || plate.isBlank()) {
            sender.sendMessage(Messages.error("Je rijdt momenteel geen voertuig — stap in je race-auto en probeer opnieuw."));
            return;
        }
        config.addVehiclePlate(plate);
        player.sendMessage(Messages.success("Vehicle plate '" + plate + "' toegevoegd. "
                + "Pool grootte: " + config.getVehiclePlates().size()));
    }

    private void handleSetLights(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen het lichtpaneel zetten."));
            return;
        }
        Location loc = player.getLocation().clone();
        config.setStartLightsGantry(loc);
        player.sendMessage(Messages.success("F1-startlicht midden ingesteld. Sta in het midden van de 5 lampjes,"
                + " kijk naar het veld — de rij staat loodrecht op je kijkrichting."));
    }

    private void handleClearLights(CommandSender sender) {
        config.clearStartLightsGantry();
        sender.sendMessage(Messages.success("Startlichten verwijderd. Bij /race start wordt nu de klassieke"
                + " " + RaceGame.COUNTDOWN_SECONDS + "s aftel gebruikt (zelfde rooster-blokkade)."));
    }

    private String lightsState() {
        Location g = config.getStartLightsGantry();
        if (g == null || g.getWorld() == null) {
            return "uit (gebruik /race setlights voor rood→groen + GO)";
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

    private void handleClearVehicles(CommandSender sender) {
        config.clearVehiclePlates();
        sender.sendMessage(Messages.success("Vehicle pool gewist."));
    }

    private void handleSetLaps(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /race setlaps <n>"));
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

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Race config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het race event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het race event verlaten."));
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
