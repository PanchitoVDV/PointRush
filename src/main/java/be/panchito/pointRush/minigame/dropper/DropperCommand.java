package be.panchito.pointRush.minigame.dropper;

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
 * {@code /dropper} — setup + besturing voor de Dropper-minigame.
 *
 * <p>Publieke subcommando's: {@code info}, {@code leave}, {@code help}.
 * Admin: {@code setspawn}, {@code settop}, {@code pos1}, {@code pos2}, {@code addround},
 * {@code clearrounds}, {@code setroundtime}, {@code start}, {@code stop}, {@code reload}.
 *
 * <p>Een ronde bouw je zo: ga naar de top-spawn en doe {@code /dropper settop}, markeer
 * dan met {@code /dropper pos1} en {@code /dropper pos2} de twee hoeken van het waterbad,
 * en bevestig met {@code /dropper addround}. Herhaal voor elke ronde.
 */
public final class DropperCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.dropper.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setspawn", "settop", "pos1", "pos2",
            "addround", "clearrounds", "setroundtime", "reload", "leave", "help"
    );

    private final DropperGame game;
    private final DropperConfig config;

    // Concept-ronde tijdens setup: top + twee finish-hoeken, bevestigd via /dropper addround.
    private Location draftTop;
    private Location draftCornerA;
    private Location draftCornerB;

    public DropperCommand(DropperGame game, DropperConfig config) {
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
            case "settop" -> handleSetTop(sender);
            case "pos1" -> handleSetCorner(sender, 1);
            case "pos2" -> handleSetCorner(sender, 2);
            case "addround" -> handleAddRound(sender);
            case "clearrounds" -> handleClearRounds(sender);
            case "setroundtime" -> handleSetRoundTime(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Dropper ---"),
                NamedTextColor.AQUA, TextDecoration.BOLD));
        sender.sendMessage(line("/dropper info", "Bekijk de setup en status"));
        sender.sendMessage(line("/dropper leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/dropper setspawn", "Zet de lobby-/wachtspawn"));
            sender.sendMessage(line("/dropper settop", "Markeer de top van de concept-ronde"));
            sender.sendMessage(line("/dropper pos1", "Markeer hoek 1 van de finish-zone"));
            sender.sendMessage(line("/dropper pos2", "Markeer hoek 2 van de finish-zone"));
            sender.sendMessage(line("/dropper addround", "Voeg de concept-ronde toe"));
            sender.sendMessage(line("/dropper clearrounds", "Verwijder alle rondes"));
            sender.sendMessage(line("/dropper setroundtime <sec>", "Tijd per ronde (sec)"));
            sender.sendMessage(line("/dropper start", "Start het event"));
            sender.sendMessage(line("/dropper stop", "Stop het event"));
            sender.sendMessage(line("/dropper reload", "Herlaad de dropper-config"));
        }
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.AQUA))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Dropper ---"),
                NamedTextColor.AQUA, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawn: " + locText(config.getSpawn())));
        sender.sendMessage(Messages.info("Rondes: " + config.getRounds().size()));
        sender.sendMessage(Messages.info("Tijd per ronde: " + config.getRoundSeconds() + "s"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.info("Concept-ronde: top=" + (draftTop != null)
                    + ", hoek1=" + (draftCornerA != null) + ", hoek2=" + (draftCornerB != null)));
        }
        if (game.getState() != DropperGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()
                    + " (alive: " + game.aliveCount() + ", ronde " + game.getCurrentRoundNumber()
                    + "/" + game.getTotalRounds() + ")"));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (Commands.dispatchCrossServerStart(sender, "dropper")) {
            return;
        }
        if (game.getState() != DropperGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Dropper event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Dropper setup is niet compleet."
                    + " Stel spawn + minstens 1 ronde in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Dropper niet starten (heb je minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("Dropper event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Dropper event."));
            return;
        }
        sender.sendMessage(Messages.success("Dropper event gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Dropper spawn ingesteld."));
    }

    private void handleSetTop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        draftTop = player.getLocation().clone();
        player.sendMessage(Messages.success("Concept-ronde top gemarkeerd. Zet nu pos1 + pos2 en doe /dropper addround."));
    }

    private void handleSetCorner(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        if (index == 1) {
            draftCornerA = player.getLocation().clone();
        } else {
            draftCornerB = player.getLocation().clone();
        }
        player.sendMessage(Messages.success("Finish-hoek " + index + " gemarkeerd."));
    }

    private void handleAddRound(CommandSender sender) {
        if (draftTop == null || draftCornerA == null || draftCornerB == null) {
            sender.sendMessage(Messages.error("Concept-ronde onvolledig. Zet eerst settop + pos1 + pos2."));
            return;
        }
        if (draftCornerA.getWorld() != draftCornerB.getWorld()) {
            sender.sendMessage(Messages.error("Beide finish-hoeken moeten in dezelfde wereld liggen."));
            return;
        }
        config.addRound(new DropperRound(draftTop, draftCornerA, draftCornerB));
        draftTop = null;
        draftCornerA = null;
        draftCornerB = null;
        sender.sendMessage(Messages.success("Ronde toegevoegd. Totaal nu: " + config.getRounds().size() + "."));
    }

    private void handleClearRounds(CommandSender sender) {
        config.clearRounds();
        sender.sendMessage(Messages.success("Alle dropper-rondes verwijderd."));
    }

    private void handleSetRoundTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /dropper setroundtime <seconden>"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Tijd moet een geheel getal (seconden) zijn."));
            return;
        }
        config.setRoundSeconds(sec);
        sender.sendMessage(Messages.success("Tijd per ronde ingesteld op " + config.getRoundSeconds() + "s."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Dropper config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het Dropper event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het Dropper event verlaten."));
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
