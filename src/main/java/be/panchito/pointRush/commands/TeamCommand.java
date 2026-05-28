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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /team command handler. Supports create / invite / accept / deny / leave / kick /
 * info / list / disband / color.
 */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "leave",
            "kick", "info", "list", "disband", "color", "help"
    );

    private final TeamManager teamManager;
    private final DataManager dataManager;

    public TeamCommand(TeamManager teamManager, DataManager dataManager) {
        this.teamManager = teamManager;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen dit gebruiken."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "disband" -> handleDisband(player);
            case "color" -> handleColor(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text(SmallText.of("--- PointRush Teams ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(line("/team create <naam>", "Maak een nieuw team"));
        player.sendMessage(line("/team invite <speler>", "Nodig een speler uit"));
        player.sendMessage(line("/team accept", "Accepteer de laatste invite"));
        player.sendMessage(line("/team deny", "Weiger de laatste invite"));
        player.sendMessage(line("/team leave", "Verlaat je team"));
        player.sendMessage(line("/team kick <speler>", "Kick een teamlid (leader)"));
        player.sendMessage(line("/team info [team]", "Bekijk team info"));
        player.sendMessage(line("/team list", "Toon alle teams + punten"));
        player.sendMessage(line("/team disband", "Hef je team op (leader)"));
        player.sendMessage(line("/team color <kleur>", "Verander team kleur (leader)"));
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Gebruik: /team create <naam>"));
            return;
        }
        String name = args[1];
        if (name.length() < 2 || name.length() > 16) {
            player.sendMessage(Messages.error("Team naam moet tussen 2 en 16 karakters zijn."));
            return;
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage(Messages.error("Team naam mag enkel letters, cijfers en _ bevatten."));
            return;
        }
        try {
            Team team = teamManager.createTeam(name, player.getUniqueId(), NamedTextColor.WHITE);
            dataManager.save();
            player.sendMessage(Messages.success("Team aangemaakt: ")
                    .append(Component.text(SmallText.of(team.getName()), team.getColor())));
            player.sendMessage(Messages.info("Nodig vrienden uit met /team invite <speler> (max 4 leden)."));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            player.sendMessage(Messages.error(ex.getMessage()));
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Gebruik: /team invite <speler>"));
            return;
        }
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (!team.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Alleen de team leader kan spelers uitnodigen."));
            return;
        }
        if (team.isFull()) {
            player.sendMessage(Messages.error("Je team is vol (max " + Team.MAX_MEMBERS + " leden)."));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Messages.error("Speler niet online."));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Je kan jezelf niet uitnodigen."));
            return;
        }
        if (teamManager.getTeamOfPlayer(target.getUniqueId()) != null) {
            player.sendMessage(Messages.error("Die speler zit al in een team."));
            return;
        }
        teamManager.invite(player.getUniqueId(), target.getUniqueId(), team.getId());
        player.sendMessage(Messages.success("Invite verstuurd naar " + target.getName() + "."));
        target.sendMessage(Messages.info("Je bent uitgenodigd voor team ")
                .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                .append(Component.text(SmallText.of(" door " + player.getName() + "."), NamedTextColor.GRAY)));
        target.sendMessage(Messages.info("Gebruik /team accept of /team deny (60s)."));
    }

    private void handleAccept(Player player) {
        TeamManager.Invite invite = teamManager.consumeInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Messages.error("Je hebt geen actieve invite."));
            return;
        }
        Team team = teamManager.getTeam(invite.teamId());
        if (team == null) {
            player.sendMessage(Messages.error("Dat team bestaat niet meer."));
            return;
        }
        if (team.isFull()) {
            player.sendMessage(Messages.error("Dat team is ondertussen vol."));
            return;
        }
        if (!teamManager.addMember(team, player.getUniqueId())) {
            player.sendMessage(Messages.error("Kon je niet toevoegen aan het team."));
            return;
        }
        dataManager.save();
        broadcastToTeam(team, Messages.success(player.getName() + " is bij het team gekomen!"));
    }

    private void handleDeny(Player player) {
        TeamManager.Invite invite = teamManager.peekInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(Messages.error("Je hebt geen actieve invite."));
            return;
        }
        teamManager.cancelInvite(player.getUniqueId());
        player.sendMessage(Messages.info("Invite geweigerd."));
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) {
            inviter.sendMessage(Messages.warn(player.getName() + " heeft je invite geweigerd."));
        }
    }

    private void handleLeave(Player player) {
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (team.getLeader().equals(player.getUniqueId()) && team.size() > 1) {
            player.sendMessage(Messages.error("Geef eerst leiderschap door of gebruik /team disband."));
            return;
        }
        teamManager.removeMember(team, player.getUniqueId());
        if (team.size() == 0) {
            teamManager.disbandTeam(team);
            player.sendMessage(Messages.info("Team opgeheven."));
        } else {
            broadcastToTeam(team, Messages.warn(player.getName() + " heeft het team verlaten."));
            player.sendMessage(Messages.info("Je hebt het team verlaten."));
        }
        dataManager.save();
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Gebruik: /team kick <speler>"));
            return;
        }
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (!team.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Alleen de leader kan kicken."));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();
        if (!team.isMember(targetId)) {
            player.sendMessage(Messages.error("Die speler zit niet in jouw team."));
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Je kan jezelf niet kicken."));
            return;
        }
        teamManager.removeMember(team, targetId);
        dataManager.save();
        broadcastToTeam(team, Messages.warn(target.getName() + " is uit het team gekickt."));
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Messages.error("Je bent uit team " + team.getName() + " gekickt."));
        }
    }

    private void handleInfo(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = teamManager.getTeamByName(args[1]);
            if (team == null) {
                player.sendMessage(Messages.error("Geen team met die naam."));
                return;
            }
        } else {
            team = teamManager.getTeamOfPlayer(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Messages.error("Je zit niet in een team. Gebruik /team info <naam>."));
                return;
            }
        }

        player.sendMessage(Component.text(SmallText.of("--- Team " + team.getName() + " ---"),
                team.getColor(), TextDecoration.BOLD));
        player.sendMessage(Messages.info("Punten: ")
                .append(Component.text(team.getPoints(), NamedTextColor.GOLD)));
        player.sendMessage(Messages.info("Leden: " + team.size() + "/" + Team.MAX_MEMBERS));

        for (UUID memberId : team.getMembers()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            String name = member.getName() != null ? member.getName() : memberId.toString().substring(0, 8);
            String suffix = memberId.equals(team.getLeader()) ? " (leader)" : "";
            player.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(SmallText.of(name + suffix), team.getColor())));
        }
    }

    private void handleList(Player player) {
        var teams = teamManager.getLeaderboard();
        if (teams.isEmpty()) {
            player.sendMessage(Messages.info("Er zijn nog geen teams."));
            return;
        }
        player.sendMessage(Component.text(SmallText.of("--- PointRush Teams ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        int rank = 1;
        for (Team team : teams) {
            player.sendMessage(Component.text(SmallText.of("#" + rank + " "), NamedTextColor.DARK_GRAY)
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                    .append(Component.text(SmallText.of(" - "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(team.getPoints() + " pts", NamedTextColor.GOLD))
                    .append(Component.text(SmallText.of(" (" + team.size() + "/" + Team.MAX_MEMBERS + ")"),
                            NamedTextColor.GRAY)));
            rank++;
        }
    }

    private void handleDisband(Player player) {
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (!team.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Alleen de leader kan het team opheffen."));
            return;
        }
        broadcastToTeam(team, Messages.warn("Het team is opgeheven door " + player.getName() + "."));
        teamManager.disbandTeam(team);
        dataManager.save();
    }

    private void handleColor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.error("Gebruik: /team color <kleur>"));
            String available = String.join(", ",
                    TeamManager.AVAILABLE_COLORS.stream().map(NamedTextColor::toString).toList());
            player.sendMessage(Messages.info("Beschikbaar: " + available));
            return;
        }
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (!team.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Alleen de leader kan de kleur veranderen."));
            return;
        }
        NamedTextColor color = NamedTextColor.NAMES.value(args[1].toLowerCase(Locale.ROOT));
        if (color == null || !TeamManager.AVAILABLE_COLORS.contains(color)) {
            player.sendMessage(Messages.error("Ongeldige kleur."));
            return;
        }
        team.setColor(color);
        dataManager.save();
        broadcastToTeam(team, Messages.success("Team kleur is veranderd."));
    }

    private void broadcastToTeam(Team team, Component message) {
        for (UUID memberId : team.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            return switch (sub) {
                case "invite", "kick" -> Commands.filterPrefix(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).toList(), args[1]);
                case "info" -> Commands.filterPrefix(teamManager.getTeams().stream()
                        .map(Team::getName).toList(), args[1]);
                case "color" -> Commands.filterPrefix(TeamManager.AVAILABLE_COLORS.stream()
                        .map(NamedTextColor::toString).toList(), args[1]);
                default -> List.of();
            };
        }
        return List.of();
    }

}
