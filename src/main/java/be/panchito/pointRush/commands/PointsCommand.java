package be.panchito.pointRush.commands;

import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
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

/**
 * /points command - admin tool to manage team points and view the leaderboard.
 * Subcommands: add / remove / set / get / top / reset.
 */
public final class PointsCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.points.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "add", "remove", "set", "get", "top", "reset"
    );

    private final TeamManager teamManager;
    private final DataManager dataManager;

    public PointsCommand(TeamManager teamManager, DataManager dataManager) {
        this.teamManager = teamManager;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        // /points top is everyone-allowed; everything else is admin
        if (sub.equals("top")) {
            handleTop(sender);
            return true;
        }
        if (sub.equals("get")) {
            handleGet(sender, args);
            return true;
        }
        if (!sender.hasPermission(PERMISSION) && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Messages.error("Je hebt geen permissie voor dit commando."));
            return true;
        }

        switch (sub) {
            case "add" -> handleModify(sender, args, ModifyMode.ADD);
            case "remove" -> handleModify(sender, args, ModifyMode.REMOVE);
            case "set" -> handleModify(sender, args, ModifyMode.SET);
            case "reset" -> handleReset(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Points ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/points top", "Bekijk de leaderboard"));
        sender.sendMessage(line("/points get [team]", "Toon punten van een team"));
        if (sender.hasPermission(PERMISSION)) {
            sender.sendMessage(line("/points add <team> <n>", "Voeg punten toe"));
            sender.sendMessage(line("/points remove <team> <n>", "Trek punten af"));
            sender.sendMessage(line("/points set <team> <n>", "Stel punten in"));
            sender.sendMessage(line("/points reset", "Reset alle punten naar 0"));
        }
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private enum ModifyMode { ADD, REMOVE, SET }

    private void handleModify(CommandSender sender, String[] args, ModifyMode mode) {
        if (args.length < 3) {
            sender.sendMessage(Messages.error("Gebruik: /points " + mode.name().toLowerCase() + " <team> <aantal>"));
            return;
        }
        Team team = teamManager.getTeamByName(args[1]);
        if (team == null) {
            sender.sendMessage(Messages.error("Geen team met die naam."));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Aantal moet een geldig getal zijn."));
            return;
        }
        if (amount < 0 && mode != ModifyMode.SET) {
            sender.sendMessage(Messages.error("Gebruik een positief getal."));
            return;
        }

        switch (mode) {
            case ADD -> team.addPoints(amount);
            case REMOVE -> team.removePoints(amount);
            case SET -> team.setPoints(amount);
        }
        dataManager.save();

        sender.sendMessage(Messages.success(
                "Team " + team.getName() + " heeft nu " + team.getPoints() + " punten."));
    }



    private void handleGet(CommandSender sender, String[] args) {
        Team team = null;
        if (args.length >= 2) {
            team = teamManager.getTeamByName(args[1]);
            if (team == null) {
                sender.sendMessage(Messages.error("Geen team met die naam."));
                return;
            }
        } else if (sender instanceof Player player) {
            team = teamManager.getTeamOfPlayer(player.getUniqueId());
            if (team == null) {
                sender.sendMessage(Messages.error("Je zit niet in een team. Gebruik /points get <team>."));
                return;
            }
        } else {
            sender.sendMessage(Messages.error("Gebruik: /points get <team>"));
            return;
        }
        sender.sendMessage(Messages.info("Team ")
                .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                .append(Component.text(SmallText.of(" heeft "), NamedTextColor.GRAY))
                .append(Component.text(team.getPoints(), NamedTextColor.GOLD))
                .append(Component.text(SmallText.of(" punten."), NamedTextColor.GRAY)));
    }

    private void handleTop(CommandSender sender) {
        var teams = teamManager.getLeaderboard();
        if (teams.isEmpty()) {
            sender.sendMessage(Messages.info("Er zijn nog geen teams."));
            return;
        }
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Leaderboard ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        int rank = 1;
        int limit = Math.min(teams.size(), 10);
        for (int i = 0; i < limit; i++) {
            Team team = teams.get(i);
            sender.sendMessage(Component.text(SmallText.of("#" + rank + " "),
                            rank == 1 ? NamedTextColor.GOLD :
                                    rank == 2 ? NamedTextColor.GRAY :
                                            rank == 3 ? NamedTextColor.DARK_RED : NamedTextColor.DARK_GRAY)
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                    .append(Component.text(SmallText.of(" - "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(team.getPoints() + " pts", NamedTextColor.GOLD)));
            rank++;
        }
    }

    private void handleReset(CommandSender sender) {
        for (Team team : teamManager.getTeams()) {
            team.setPoints(0);
        }
        dataManager.save();
        sender.sendMessage(Messages.success("Alle team punten zijn gereset naar 0."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove") || sub.equals("set") || sub.equals("get")) {
                return Commands.filterPrefix(teamManager.getTeams().stream().map(Team::getName).toList(), args[1]);
            }
        }
        if (args.length == 3) {
            return List.of("1", "10", "50", "100");
        }
        return List.of();
    }

}
