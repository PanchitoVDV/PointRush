package be.panchito.pointRush.profile;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.team.LeaderboardCache;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Leest PointRush-profielgegevens voor Ultimate UI (PlaceholderAPI) en chat-commando's.
 */
public final class PlayerProfileService {

    private final PointRush plugin;
    private final TeamManager teamManager;
    private final LeaderboardCache leaderboardCache;

    public PlayerProfileService(PointRush plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        this.leaderboardCache = plugin.getLeaderboardCache();
    }

    public String websiteUrl() {
        return plugin.getUnifiedSettings().yaml()
                .getString("profile.website-url", "https://cloudito.cloud");
    }

    public long teamPoints(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        return team != null ? team.getPoints() : 0L;
    }

    public String teamName(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        return team != null ? team.getName() : "";
    }

    public String teamNameDisplay(UUID playerId) {
        String name = teamName(playerId);
        return name.isBlank() ? "Geen team" : name;
    }

    public String teamColored(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        if (team == null) {
            return "&7Geen team";
        }
        return legacyColor(team.getColor()) + team.getName();
    }

    public boolean hasTeam(UUID playerId) {
        return teamManager.getTeamOfPlayer(playerId) != null;
    }

    public int teamRank(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        if (team == null) {
            return 0;
        }
        boolean cacheLb = plugin.getUnifiedSettings().yaml()
                .getBoolean("cache.leaderboard-cache-enabled", true);
        List<Team> board = leaderboardCache.leaderboard(teamManager, cacheLb);
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).getId().equals(team.getId())) {
                return i + 1;
            }
        }
        return 0;
    }

    public String rankDisplay(UUID playerId) {
        int rank = teamRank(playerId);
        return rank > 0 ? "#" + rank : "&7-";
    }

    public int totalRushCoins(UUID playerId) {
        be.panchito.pointRush.coins.CoinTotalCache cache = plugin.getCoinTotalCache();
        // Niet-blokkerend: leest uit de async ververste cache i.p.v. een MongoDB-query op de
        // hoofd-thread (deze methode draait vanuit een PlaceholderAPI-render, elke tick).
        return cache != null ? cache.get(playerId) : 0;
    }

    public List<String> teammateNames(UUID viewerId) {
        Team team = teamManager.getTeamOfPlayer(viewerId);
        if (team == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (UUID member : team.getMembers()) {
            if (member.equals(viewerId)) {
                continue;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(member);
            String name = off.getName();
            names.add(name != null && !name.isBlank() ? name : "?");
        }
        return names;
    }

    public String teammatesLine(UUID viewerId) {
        List<String> names = teammateNames(viewerId);
        if (names.isEmpty()) {
            return teamManager.getTeamOfPlayer(viewerId) == null
                    ? "&7Geen team &8(/team)"
                    : "&7Alleen jij";
        }
        return "&f" + String.join("&7, &f", names);
    }

    public String teammateSlot(UUID viewerId, int index) {
        List<String> names = teammateNames(viewerId);
        if (index < 1 || index > names.size()) {
            return "&7-";
        }
        return "&f" + names.get(index - 1);
    }

    public String resolve(UUID playerId, String params) {
        if (playerId == null || params == null) {
            return "";
        }
        UUID id = playerId;
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "points", "team_points", "score" -> String.valueOf(teamPoints(id));
            case "team", "team_name" -> teamNameDisplay(id);
            case "team_colored", "team_color_name" -> teamColored(id);
            case "team_color" -> {
                Team t = teamManager.getTeamOfPlayer(id);
                yield t != null ? t.getColor().toString() : "gray";
            }
            case "rank", "team_rank" -> rankDisplay(id);
            case "teammates", "team_members", "mates" -> teammatesLine(id);
            case "teammate_1", "mate_1" -> teammateSlot(id, 1);
            case "teammate_2", "mate_2" -> teammateSlot(id, 2);
            case "teammate_3", "mate_3" -> teammateSlot(id, 3);
            case "coins", "rush_coins", "munten" -> String.valueOf(totalRushCoins(id));
            case "website", "website_url", "url" -> websiteUrl();
            case "has_team" -> hasTeam(id) ? "Ja" : "Nee";
            // Live minigame-HUD (stats_display.yml)
            case "hud_timer" -> hudTimer();
            case "hud_score" -> String.valueOf(teamPoints(id));
            case "hud_players" -> String.valueOf(hudPlayers());
            case "hud_rank" -> hudRank(id);
            case "hud_event" -> hudEvent();
            case "hud_objective" -> hudObjective();
            default -> null;
        };
    }

    private String hudTimer() {
        return plugin.getMinigameHudService() != null
                ? plugin.getMinigameHudService().formattedTimer()
                : "00:00";
    }

    private int hudPlayers() {
        return plugin.getMinigameHudService() != null
                ? plugin.getMinigameHudService().playerCount()
                : 0;
    }

    private String hudRank(UUID playerId) {
        int rank = teamRank(playerId);
        return rank > 0 ? "#" + rank : "-";
    }

    private String hudEvent() {
        return plugin.getMinigameHudService() != null
                ? plugin.getMinigameHudService().eventMessage()
                : "";
    }

    private String hudObjective() {
        return plugin.getMinigameHudService() != null
                ? plugin.getMinigameHudService().activeDisplayName()
                : "PointRush";
    }

    private static String legacyColor(NamedTextColor color) {
        if (color == null) {
            return "&7";
        }
        String name = color.toString();
        return switch (name) {
            case "red" -> "&c";
            case "blue" -> "&9";
            case "green" -> "&a";
            case "yellow" -> "&e";
            case "aqua" -> "&b";
            case "light_purple" -> "&d";
            case "gold" -> "&6";
            case "white" -> "&f";
            case "dark_red" -> "&4";
            case "dark_blue" -> "&1";
            case "dark_green" -> "&2";
            case "dark_aqua" -> "&3";
            case "dark_purple" -> "&5";
            case "gray", "grey" -> "&7";
            case "dark_gray", "dark_grey" -> "&8";
            default -> "&f";
        };
    }
}
