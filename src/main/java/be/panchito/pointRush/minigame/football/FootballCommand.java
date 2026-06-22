package be.panchito.pointRush.minigame.football;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /pointball} — setup + control voor het Voetbal-event (BlockBall-arena).
 */
public final class FootballCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.football.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setarena", "setduration", "setgoalpoints", "setwinbonus",
            "arenas", "reload", "leave", "help"
    );

    private final FootballGame game;
    private final FootballConfig config;
    private final BlockBallBridge bridge;

    public FootballCommand(FootballGame game, FootballConfig config, BlockBallBridge bridge) {
        this.game = game;
        this.config = config;
        this.bridge = bridge;
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
            case "setarena" -> handleSetArena(sender, args);
            case "setduration" -> handleSetDuration(sender, args);
            case "setgoalpoints" -> handleSetGoalPoints(sender, args);
            case "setwinbonus" -> handleSetWinBonus(sender, args);
            case "arenas" -> handleListArenas(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Voetbal ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/pointball info", "Bekijk setup en status"));
        sender.sendMessage(line("/pointball leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/pointball setarena <naam>", "Koppel een BlockBall-arena"));
            sender.sendMessage(line("/pointball arenas", "Lijst beschikbare BlockBall-arena's"));
            sender.sendMessage(line("/pointball setduration <min>", "Wedstrijdduur (default 5)"));
            sender.sendMessage(line("/pointball setgoalpoints <pts>", "Punten per goal (default 50)"));
            sender.sendMessage(line("/pointball setwinbonus <pts>", "Bonus voor het winnende team (default 150)"));
            sender.sendMessage(line("/pointball start", "Start het event"));
            sender.sendMessage(line("/pointball stop", "Stop het event"));
            sender.sendMessage(line("/pointball reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Voetbal ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("BlockBall: "
                + (bridge.isAvailable() ? "geladen" : "NIET geladen")));
        String arena = config.getArenaName();
        sender.sendMessage(Messages.info("Arena: " + (arena == null || arena.isBlank()
                ? "(niet ingesteld)" : arena)));
        sender.sendMessage(Messages.info("Wedstrijdduur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Punten per goal: " + config.getPointsPerGoal()));
        sender.sendMessage(Messages.info("Winst-bonus: " + config.getWinBonus()));
        if (game.getState() != FootballGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == FootballGame.State.RUNNING) {
                sender.sendMessage(Messages.info("Stand: rood " + game.getRedScore()
                        + " - " + game.getBlueScore() + " blauw"));
            }
        }
    }

    private void handleStart(CommandSender sender) {
        if (Commands.dispatchCrossServerStart(sender, "football")) {
            return;
        }
        if (game.getState() != FootballGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Voetbal-event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Voetbal setup niet compleet. Stel een arena in met /pointball setarena."));
            return;
        }
        if (!bridge.isAvailable()) {
            sender.sendMessage(Messages.error("BlockBall is niet geladen — kan geen arena starten."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Voetbal niet starten (arena onbekend of < "
                    + FootballGame.MIN_PLAYERS + " spelers)."));
            return;
        }
        sender.sendMessage(Messages.success("Voetbal-event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Voetbal-event."));
            return;
        }
        sender.sendMessage(Messages.success("Voetbal-event gestopt."));
    }

    private void handleSetArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointball setarena <naam>"));
            return;
        }
        String name = args[1];
        config.setArenaName(name);
        sender.sendMessage(Messages.success("Voetbal-arena ingesteld op '" + name + "'."));
        if (bridge.isAvailable() && bridge.getGameByName(name) == null) {
            sender.sendMessage(Messages.warn("Let op: BlockBall kent nu geen arena met die naam. "
                    + "Controleer met /pointball arenas."));
        }
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointball setduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 1 || min > 30) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1 en 30 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Wedstrijdduur: " + min + " minuten."));
    }

    private void handleSetGoalPoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointball setgoalpoints <punten>"));
            return;
        }
        int pts = parseInt(args[1], sender);
        if (pts < 0) return;
        if (pts > 1000) {
            sender.sendMessage(Messages.error("Waarde moet tussen 0 en 1000 zijn."));
            return;
        }
        config.setPointsPerGoal(pts);
        sender.sendMessage(Messages.success("Punten per goal: " + pts));
    }

    private void handleSetWinBonus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointball setwinbonus <punten>"));
            return;
        }
        int pts = parseInt(args[1], sender);
        if (pts < 0) return;
        if (pts > 2000) {
            sender.sendMessage(Messages.error("Waarde moet tussen 0 en 2000 zijn."));
            return;
        }
        config.setWinBonus(pts);
        sender.sendMessage(Messages.success("Winst-bonus: " + pts));
    }

    private void handleListArenas(CommandSender sender) {
        if (!bridge.isAvailable()) {
            sender.sendMessage(Messages.error("BlockBall is niet geladen."));
            return;
        }
        List<String> arenas = bridge.listArenaNames();
        if (arenas.isEmpty()) {
            sender.sendMessage(Messages.info("Geen BlockBall-arena's gevonden. Maak er een met /blockball."));
            return;
        }
        sender.sendMessage(Messages.info("BlockBall-arena's: " + String.join(", ", arenas)));
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
        sender.sendMessage(Messages.success("Voetbal config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het Voetbal-event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het Voetbal-event verlaten."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena") && bridge.isAvailable()) {
            return Commands.filterPrefix(bridge.listArenaNames(), args[1]);
        }
        return List.of();
    }
}
