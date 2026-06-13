package be.panchito.pointRush.minigame.hiddentarget;

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
 * {@code /hiddentarget} — setup + control voor Hidden Target.
 */
public final class HiddenTargetCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.hiddentarget.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "addspawn", "setspawn", "delspawn", "clearspawns",
            "pos1", "pos2", "clearregion", "setduration", "setkillpoints", "setpenalty", "setrespawn",
            "reload", "leave", "help"
    );

    private final HiddenTargetGame game;
    private final HiddenTargetConfig config;

    public HiddenTargetCommand(HiddenTargetGame game, HiddenTargetConfig config) {
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
            case "addspawn" -> handleAddSpawn(sender);
            case "setspawn" -> handleSetSpawn(sender);
            case "delspawn" -> handleDelSpawn(sender);
            case "clearspawns" -> handleClearSpawns(sender);
            case "pos1" -> handleSetCorner(sender, 1);
            case "pos2" -> handleSetCorner(sender, 2);
            case "clearregion" -> handleClearRegion(sender);
            case "setduration" -> handleSetDuration(sender, args);
            case "setkillpoints" -> handleSetKillPoints(sender, args);
            case "setpenalty" -> handleSetPenalty(sender, args);
            case "setrespawn" -> handleSetRespawn(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Hidden Target ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/hiddentarget info", "Bekijk setup en status"));
        sender.sendMessage(line("/hiddentarget leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/hiddentarget addspawn", "Voeg een spawnpunt toe (spelers worden verdeeld)"));
            sender.sendMessage(line("/hiddentarget setspawn", "Reset naar één spawnpunt op je locatie"));
            sender.sendMessage(line("/hiddentarget delspawn", "Verwijder dichtstbijzijnde spawnpunt"));
            sender.sendMessage(line("/hiddentarget clearspawns", "Wis alle spawnpunten"));
            sender.sendMessage(line("/hiddentarget pos1 / pos2", "Markeer arena region (optioneel)"));
            sender.sendMessage(line("/hiddentarget clearregion", "Verwijder de arena region (geen grens meer)"));
            sender.sendMessage(line("/hiddentarget setduration <min>", "Eventduur (default 10)"));
            sender.sendMessage(line("/hiddentarget setkillpoints <n>", "Punten per target kill (default 1)"));
            sender.sendMessage(line("/hiddentarget setpenalty <n>", "Puntverlies als gejaagd (default 1)"));
            sender.sendMessage(line("/hiddentarget setrespawn <sec>", "Respawn tijd (default 60)"));
            sender.sendMessage(line("/hiddentarget start", "Start het event"));
            sender.sendMessage(line("/hiddentarget stop", "Stop het event"));
            sender.sendMessage(line("/hiddentarget reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Hidden Target ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawnpunten: " + config.getSpawnCount()));
        int n = 1;
        for (Location s : config.getSpawns()) {
            sender.sendMessage(Messages.info("  #" + n++ + " " + locText(s)));
        }
        sender.sendMessage(Messages.info("Region hoek 1: " + locText(config.getRegionMin())));
        sender.sendMessage(Messages.info("Region hoek 2: " + locText(config.getRegionMax())));
        sender.sendMessage(Messages.info("Duur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Punten per kill: " + config.getKillPoints()));
        sender.sendMessage(Messages.info("Gejaagd penalty: " + config.getHuntedPenalty()));
        sender.sendMessage(Messages.info("Respawn: " + config.getRespawnSeconds() + "s"));
        if (game.getState() != HiddenTargetGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == HiddenTargetGame.State.RUNNING) {
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
        if (Commands.dispatchCrossServerStart(sender, "hiddentarget")) {
            return;
        }
        if (game.getState() != HiddenTargetGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Hidden Target event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Stel eerst minstens 1 spawnpunt in met /hiddentarget addspawn."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Hidden Target niet starten (minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("Hidden Target event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Hidden Target event."));
            return;
        }
        sender.sendMessage(Messages.success("Hidden Target event gestopt."));
    }

    private void handleAddSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        int total = config.addSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Spawnpunt #" + total + " toegevoegd (spelers worden verdeeld)."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.clearSpawns();
        config.addSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Hidden Target spawn gereset naar 1 punt op je locatie."));
    }

    private void handleDelSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        if (config.getSpawnCount() == 0) {
            player.sendMessage(Messages.error("Er zijn geen spawnpunten ingesteld."));
            return;
        }
        if (config.removeNearestSpawn(player.getLocation())) {
            player.sendMessage(Messages.success("Dichtstbijzijnde spawnpunt verwijderd (" + config.getSpawnCount() + " over)."));
        } else {
            player.sendMessage(Messages.error("Geen spawnpunt in deze wereld gevonden."));
        }
    }

    private void handleClearSpawns(CommandSender sender) {
        config.clearSpawns();
        sender.sendMessage(Messages.success("Alle Hidden Target spawnpunten gewist."));
    }

    private void handleClearRegion(CommandSender sender) {
        config.clearRegion();
        sender.sendMessage(Messages.success("Arena region verwijderd — spelers worden niet meer begrensd."));
    }

    private void handleSetCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setCorner(index, player.getLocation().clone());
        player.sendMessage(Messages.success("Arena hoek " + index + " gemarkeerd."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /hiddentarget setduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 3 || min > 60) {
            sender.sendMessage(Messages.error("Waarde moet tussen 3 en 60 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Eventduur: " + min + " minuten."));
    }

    private void handleSetKillPoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /hiddentarget setkillpoints <punten>"));
            return;
        }
        int pts = parseInt(args[1], sender);
        if (pts < 0) return;
        if (pts < 1 || pts > 50) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1 en 50 zijn."));
            return;
        }
        config.setKillPoints(pts);
        sender.sendMessage(Messages.success("Punten per target kill: " + pts));
    }

    private void handleSetPenalty(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /hiddentarget setpenalty <punten>"));
            return;
        }
        int pts = parseInt(args[1], sender);
        if (pts < 0) return;
        if (pts < 1 || pts > 50) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1 en 50 zijn."));
            return;
        }
        config.setHuntedPenalty(pts);
        sender.sendMessage(Messages.success("Gejaagd penalty: " + pts + " pt"));
    }

    private void handleSetRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /hiddentarget setrespawn <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 2 || sec > 120) {
            sender.sendMessage(Messages.error("Waarde moet tussen 2 en 120 seconden zijn."));
            return;
        }
        config.setRespawnSeconds(sec);
        sender.sendMessage(Messages.success("Respawn na " + sec + " seconden."));
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
        sender.sendMessage(Messages.success("Hidden Target config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan Hidden Target."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt Hidden Target verlaten."));
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
