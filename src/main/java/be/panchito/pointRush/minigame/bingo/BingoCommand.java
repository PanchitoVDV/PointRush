package be.panchito.pointRush.minigame.bingo;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BingoCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.bingo.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "progress", "leave", "setspawn", "setduration", "reload", "help"
    );

    private final BingoGame game;
    private final BingoConfig config;

    public BingoCommand(BingoGame game, BingoConfig config) {
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
        if (sub.equals("progress")) {
            handleProgress(sender);
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
            case "setduration" -> handleSetDuration(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Bingo ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/bingo info", "Status van het event"));
        sender.sendMessage(line("/bingo progress", "Jouw team voortgang"));
        sender.sendMessage(line("/bingo leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/bingo start", "Start bingo (1 uur)"));
            sender.sendMessage(line("/bingo stop", "Stop het event"));
            sender.sendMessage(line("/bingo setspawn", "Optionele hub-locatie"));
            sender.sendMessage(line("/bingo setduration <min>", "Duur (default 60)"));
            sender.sendMessage(line("/bingo reload", "Herlaad config"));
        }
    }

    private Component line(String usage, String desc) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(desc), NamedTextColor.GRAY))
                .build();
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Bingo ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Pool: " + config.getMaterialPool().size() + " materialen"));
        sender.sendMessage(Messages.info("Duur: " + config.getDurationMinutes() + " min"));
        if (game.getState() == BingoGame.State.RUNNING) {
            sender.sendMessage(Messages.info("Resttijd: " + game.formatTime(game.getRunTimeLeftMs())));
            sender.sendMessage(Messages.info("Teams: " + game.teams().sortedByFound().size()));
        }
    }

    private void handleProgress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (game.getState() != BingoGame.State.RUNNING) {
            sender.sendMessage(Messages.info("Geen actieve bingo."));
            return;
        }
        UUID bucket = game.bucketFor(player.getUniqueId());
        BingoTeamProgress prog = game.getTeamProgress(bucket);
        if (prog == null) {
            sender.sendMessage(Messages.warn("Je doet niet mee."));
            return;
        }
        game.syncTeam(bucket);
        sender.sendMessage(Messages.info("Team " + prog.getLabel() + ": "
                + prog.countFound() + "/" + BingoGrid.TOTAL + " vakken"
                + (prog.isComplete() ? " · compleet" : "")));
        BingoGui.open(player, game);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (game.getState() != BingoGame.State.RUNNING) {
            sender.sendMessage(Messages.error("Er loopt geen Bingo event."));
            return;
        }
        if (game.getTeamProgress(game.bucketFor(player.getUniqueId())) == null) {
            sender.sendMessage(Messages.error("Je doet niet mee aan Bingo."));
            return;
        }
        game.removeParticipant(player);
        player.sendMessage(Messages.info("Je hebt Bingo verlaten."));
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != BingoGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Bingo event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Material pool te klein (min 24 items)."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Bingo niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Bingo gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Bingo event."));
            return;
        }
        sender.sendMessage(Messages.success("Bingo gestopt."));
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        config.setSpawn(player.getLocation());
        sender.sendMessage(Messages.success("Bingo spawn opgeslagen."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bingo setduration <minuten>"));
            return;
        }
        int min;
        try {
            min = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
            return;
        }
        if (min < 5 || min > 240) {
            sender.sendMessage(Messages.error("Waarde moet tussen 5 en 240 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Duur: " + min + " minuten."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Bingo config herladen."));
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
