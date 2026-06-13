package be.panchito.pointRush.commands;

import be.panchito.pointRush.PointRush;
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
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /team command handler. Supports create / invite / accept / deny / leave / kick /
 * info / list / disband / color.
 */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "invite", "accept", "deny", "leave",
            "kick", "info", "list", "disband", "color", "sethome", "home", "help"
    );

    /** Seconds the player must stand still before being teleported to the team home. */
    private static final int HOME_WARMUP_SECONDS = 10;
    /** Squared distance (blocks^2) the player may drift before the warmup is cancelled. */
    private static final double HOME_MOVE_TOLERANCE_SQ = 0.04;

    private final PointRush plugin;
    private final TeamManager teamManager;
    private final DataManager dataManager;

    /** Players currently in a /team home warmup, mapped to their running tick task. */
    private final Map<UUID, BukkitTask> activeWarmups = new ConcurrentHashMap<>();

    public TeamCommand(PointRush plugin, TeamManager teamManager, DataManager dataManager) {
        this.plugin = plugin;
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
            case "sethome" -> handleSetHome(player);
            case "home" -> handleHome(player);
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
        player.sendMessage(line("/team sethome", "Zet de team home hier (leader)"));
        player.sendMessage(line("/team home", "Teleport naar team home (10s stilstaan)"));
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
            dataManager.deleteTeam(team.getId());
            player.sendMessage(Messages.info("Team opgeheven."));
        } else {
            broadcastToTeam(team, Messages.warn(player.getName() + " heeft het team verlaten."));
            player.sendMessage(Messages.info("Je hebt het team verlaten."));
            dataManager.save();
        }
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
        dataManager.deleteTeam(team.getId());
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

    private void handleSetHome(Player player) {
        if (plugin.isEventLiveNow()) {
            player.sendMessage(Messages.error("Je kan /team sethome niet gebruiken tijdens een event."));
            return;
        }
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        if (!team.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(Messages.error("Alleen de leader kan de team home zetten."));
            return;
        }
        team.setHome(player.getLocation());
        dataManager.save();
        broadcastToTeam(team, Messages.success("Team home is ingesteld door " + player.getName() + "."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
    }

    private void handleHome(Player player) {
        if (plugin.isEventLiveNow()) {
            player.sendMessage(Messages.error("Je kan /team home niet gebruiken tijdens een event."));
            return;
        }
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Messages.error("Je zit niet in een team."));
            return;
        }
        Location home = team.getHome();
        if (home == null || home.getWorld() == null) {
            player.sendMessage(Messages.error("Je team heeft nog geen home. Gebruik /team sethome."));
            return;
        }
        if (activeWarmups.containsKey(player.getUniqueId())) {
            player.sendMessage(Messages.error("Je bent al aan het teleporteren - blijf stilstaan."));
            return;
        }

        Location start = player.getLocation().clone();
        player.sendMessage(Messages.info("Teleport naar team home over " + HOME_WARMUP_SECONDS
                + "s - blijf stilstaan."));
        player.sendActionBar(Component.text(SmallText.of("teleport in " + HOME_WARMUP_SECONDS + "s"),
                NamedTextColor.GOLD));
        player.playSound(start, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.0f);

        UUID id = player.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) {
                    cancelWarmup(id);
                    return;
                }
                if (plugin.isEventLiveNow()) {
                    p.sendMessage(Messages.error("Teleport geannuleerd: er is een event gestart."));
                    p.sendActionBar(Component.text(SmallText.of("teleport geannuleerd"), NamedTextColor.RED));
                    cancelWarmup(id);
                    return;
                }
                if (hasMoved(start, p.getLocation())) {
                    p.sendMessage(Messages.error("Teleport geannuleerd: je bewoog."));
                    p.sendActionBar(Component.text(SmallText.of("teleport geannuleerd"), NamedTextColor.RED));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                    cancelWarmup(id);
                    return;
                }

                ticks++;
                if (ticks % 20 == 0) {
                    int remaining = HOME_WARMUP_SECONDS - (ticks / 20);
                    if (remaining > 0) {
                        p.sendActionBar(Component.text(SmallText.of("teleport in " + remaining + "s"),
                                NamedTextColor.GOLD));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                    }
                }

                if (ticks >= HOME_WARMUP_SECONDS * 20) {
                    cancelWarmup(id);
                    Team current = teamManager.getTeamOfPlayer(id);
                    Location dest = current != null ? current.getHome() : null;
                    if (dest == null || dest.getWorld() == null) {
                        p.sendMessage(Messages.error("Team home is niet meer beschikbaar."));
                        return;
                    }
                    p.teleport(dest);
                    p.setFallDistance(0f);
                    p.sendActionBar(Component.text(SmallText.of("welkom bij de team home"), NamedTextColor.GREEN));
                    p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeWarmups.put(id, task);
    }

    private void cancelWarmup(UUID id) {
        BukkitTask task = activeWarmups.remove(id);
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    private static boolean hasMoved(Location start, Location now) {
        if (start.getWorld() == null || now.getWorld() == null) {
            return true;
        }
        if (!start.getWorld().equals(now.getWorld())) {
            return true;
        }
        return start.distanceSquared(now) > HOME_MOVE_TOLERANCE_SQ;
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
