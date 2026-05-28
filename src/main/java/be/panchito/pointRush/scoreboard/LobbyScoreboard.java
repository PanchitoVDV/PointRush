package be.panchito.pointRush.scoreboard;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.team.LeaderboardCache;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.SmallText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Always-on PointRush sidebar for players that are not inside an active event.
 *
 * <p>Shows a static layout:
 * <pre>
 *     ᴘᴏɪɴᴛʀᴜѕʜ                 (title)
 *
 *     ᴛᴏᴘ 5 ᴛᴇᴀᴍѕ
 *     #1 Red       150 pts
 *     #2 Blue      120 pts
 *     ...
 *
 *     ᴊᴏᴜᴡ ᴛᴇᴀᴍ
 *     Red          150 pts
 *
 *     /pointrush
 *     cloudito.cloud
 * </pre>
 *
 * <p>The tick task self-heals: every second it re-renders attached boards,
 * detaches participants in active events, and re-attaches former participants
 * once their event ends. No explicit coupling with the minigame scoreboards
 * is required.
 */
public final class LobbyScoreboard {

    private static final String OBJ_KEY = "pr_lobby";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final TeamManager teamManager;
    private final LeaderboardCache leaderboardCache;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask tickTask;

    public LobbyScoreboard(PointRush plugin) {
        this.plugin = plugin;
        this.teamManager = plugin.getTeamManager();
        this.leaderboardCache = plugin.getLeaderboardCache();
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isInActiveEvent(p.getUniqueId())) {
                attach(p);
            }
        }
    }

    public void stop() {
        if (tickTask != null) {
            try { tickTask.cancel(); } catch (IllegalStateException ignored) { }
            tickTask = null;
        }
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && mgr != null) {
                p.setScoreboard(mgr.getMainScoreboard());
            }
        }
        boards.clear();
    }

    /**
     * Attaches the lobby sidebar to a player. Called from the player-join
     * listener and from the periodic tick when a player leaves an event.
     */
    public void attach(Player player) {
        if (player == null || !player.isOnline()) return;
        if (isInActiveEvent(player.getUniqueId())) return;
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = mgr.getNewScoreboard();
            Objective obj = board.registerNewObjective(
                    OBJ_KEY,
                    Criteria.DUMMY,
                    Component.text(SmallText.of("PointRush"), NamedTextColor.GOLD, TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            try {
                obj.numberFormat(NumberFormat.blank());
            } catch (Throwable ignored) {
                // older API - score numbers stay visible
            }
            for (int i = 0; i < LINE_IDS.length; i++) {
                Score s = obj.getScore(LINE_IDS[i]);
                s.setScore(LINE_IDS.length - i);
                s.customName(Component.empty());
            }
            boards.put(player.getUniqueId(), board);
        }

        player.setScoreboard(board);
        render(player, board);
    }

    /**
     * Removes the lobby scoreboard from this player. Called from the
     * quit listener; safe to call when the player isn't attached.
     */
    public void detach(Player player) {
        if (player == null) return;
        boards.remove(player.getUniqueId());
        if (!player.isOnline()) return;
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null && player.getScoreboard() != mgr.getMainScoreboard()) {
            // only flip back if the player still has *our* board; an active event
            // will have already replaced the scoreboard with its own.
            // We can't compare references safely so we just leave the player be —
            // the next active scoreboard wins anyway.
        }
    }

    private boolean isInActiveEvent(UUID playerId) {
        return MinigameRegistry.isPlayerInActiveEvent(plugin, playerId);
    }

    private void tick() {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean inEvent = isInActiveEvent(p.getUniqueId());
            Scoreboard ours = boards.get(p.getUniqueId());

            if (inEvent) {
                // event scoreboard owns this player; drop ours so we re-create it later
                if (ours != null) {
                    boards.remove(p.getUniqueId());
                }
                continue;
            }

            if (ours == null) {
                attach(p);
                continue;
            }

            // a previous event may have switched the player's scoreboard back to main
            if (!ours.equals(p.getScoreboard())) {
                p.setScoreboard(ours);
            }
            render(p, ours);
        }
    }

    private void render(Player viewer, Scoreboard board) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        boolean cacheLb = plugin.getUnifiedSettings().yaml()
                .getBoolean("cache.leaderboard-cache-enabled", true);
        List<Team> leaderboard = leaderboardCache.leaderboard(teamManager, cacheLb);
        Team myTeam = teamManager.getTeamOfPlayer(viewer.getUniqueId());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("top 5 teams"), NamedTextColor.GOLD, TextDecoration.BOLD));

        int limit = Math.min(5, leaderboard.size());
        for (int i = 0; i < limit; i++) {
            Team t = leaderboard.get(i);
            lines.add(leaderboardLine(i + 1, t, myTeam != null && t.getId().equals(myTeam.getId())));
        }
        for (int i = limit; i < 5; i++) {
            lines.add(Component.text(SmallText.of("#" + (i + 1) + " -"), NamedTextColor.DARK_GRAY));
        }

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("jouw team"), NamedTextColor.GRAY));
        if (myTeam == null) {
            lines.add(Component.text(SmallText.of("(geen team)"), NamedTextColor.DARK_GRAY));
        } else {
            lines.add(Component.text()
                    .append(Component.text(truncate(myTeam.getName(), 12), myTeam.getColor()))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(myTeam.getPoints() + " pts", NamedTextColor.GOLD))
                    .build());
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("/pointrush"), NamedTextColor.GOLD));
        lines.add(Component.text(SmallText.of("cloudito.cloud"), NamedTextColor.DARK_GRAY));

        int applied = Math.min(lines.size(), LINE_IDS.length);
        for (int i = 0; i < applied; i++) {
            obj.getScore(LINE_IDS[i]).customName(lines.get(i));
        }
        for (int i = applied; i < LINE_IDS.length; i++) {
            obj.getScore(LINE_IDS[i]).customName(Component.empty());
        }
    }

    private Component leaderboardLine(int rank, Team team, boolean isOwn) {
        TextColor rankColor = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.DARK_GRAY;
        };
        Component name = Component.text(truncate(team.getName(), 10), team.getColor());
        if (isOwn) {
            name = name.decoration(TextDecoration.BOLD, true);
        }
        return Component.text()
                .append(Component.text("#" + rank + " ", rankColor))
                .append(name)
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.text(team.getPoints() + "p", NamedTextColor.GOLD))
                .build();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
