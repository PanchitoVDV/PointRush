package be.panchito.pointRush.minigame.goldrush;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /goldrush} — survival mining event.
 */
public final class GoldRushCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.goldrush.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "top", "setduration", "setleaderboard", "reload", "help"
    );

    private final GoldRushGame game;
    private final GoldRushConfig config;

    public GoldRushCommand(GoldRushGame game, GoldRushConfig config) {
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
        if (sub.equals("top")) {
            game.broadcastLeaderboard(true);
            return true;
        }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "setduration" -> handleSetDuration(sender, args);
            case "setleaderboard" -> handleSetLeaderboard(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Gold Rush ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/goldrush info", "Event status en jouw score"));
        sender.sendMessage(line("/goldrush top", "Huidige top 5 in chat"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/goldrush start", "Start 1 uur gold rush"));
            sender.sendMessage(line("/goldrush stop", "Stop het event"));
            sender.sendMessage(line("/goldrush setduration <min>", "Duur (default 60)"));
            sender.sendMessage(line("/goldrush setleaderboard <sec>", "Interval stand in chat (default 300)"));
            sender.sendMessage(line("/goldrush reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Gold Rush ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Duur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Stand interval: " + config.getLeaderboardIntervalSeconds() + "s"));
        if (game.getState() == GoldRushGame.State.RUNNING) {
            sender.sendMessage(Messages.info("Resttijd: " + game.formatTime(game.getRunTimeLeftMs())));
        }
        if (sender instanceof Player player) {
            sender.sendMessage(Messages.info("Jouw score: " + game.getScore(player.getUniqueId()) + " goud"));
        }

        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(game.getScores().entrySet());
        top.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        if (!top.isEmpty()) {
            sender.sendMessage(Messages.info("Top miner: "
                    + Bukkit.getOfflinePlayer(top.get(0).getKey()).getName()
                    + " (" + top.get(0).getValue() + ")"));
        }
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != GoldRushGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Gold Rush."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Gold Rush niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Gold Rush gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Gold Rush."));
            return;
        }
        sender.sendMessage(Messages.success("Gold Rush gestopt."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /goldrush setduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 5 || min > 240) {
            sender.sendMessage(Messages.error("Waarde moet tussen 5 en 240 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Gold Rush duur: " + min + " minuten."));
    }

    private void handleSetLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /goldrush setleaderboard <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 60 || sec > 3600) {
            sender.sendMessage(Messages.error("Waarde moet tussen 60 en 3600 seconden zijn."));
            return;
        }
        config.setLeaderboardIntervalSeconds(sec);
        sender.sendMessage(Messages.success("Stand elke " + sec + " seconden in chat."));
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
        sender.sendMessage(Messages.success("Gold Rush config herladen."));
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
