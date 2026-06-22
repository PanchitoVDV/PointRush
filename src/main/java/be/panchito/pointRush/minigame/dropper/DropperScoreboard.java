package be.panchito.pointRush.minigame.dropper;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.SmallText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Per-speler sidebar voor de Dropper-minigame.
 *
 * <pre>
 *     ᴘᴏɪɴᴛʀᴜѕʜ
 *
 *     ᴅʀᴏᴘᴘᴇʀ
 *     status: voltooid / in leven / uit
 *
 *     ronde 2 / 5
 *     tijd  1:34
 *
 *     in leven 4 / 8
 *
 *     jouw team
 *     Red    150 pts
 *
 *     cloudito.cloud
 * </pre>
 */
public final class DropperScoreboard {

    private static final String OBJ_KEY = "pr_dropper";

    private static final String[] LINE_IDS = {
            "§0", "§1", "§2", "§3",
            "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b",
            "§c", "§d", "§e", "§f"
    };

    private final PointRush plugin;
    private final DropperGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask updateTask;

    public DropperScoreboard(PointRush plugin, DropperGame game) {
        this.plugin = plugin;
        this.game = game;
        this.teamManager = plugin.getTeamManager();
    }

    public void attach(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard board = mgr.getNewScoreboard();
        Objective obj = board.registerNewObjective(
                OBJ_KEY,
                Criteria.DUMMY,
                Component.text(SmallText.of("PointRush"), NamedTextColor.AQUA, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            obj.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
            // oudere API - score-nummers blijven zichtbaar
        }
        for (int i = 0; i < LINE_IDS.length; i++) {
            Score s = obj.getScore(LINE_IDS[i]);
            s.setScore(LINE_IDS.length - i);
            s.customName(Component.empty());
        }
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    public void detach(Player player) {
        boards.remove(player.getUniqueId());
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null && player.isOnline()) {
            player.setScoreboard(mgr.getMainScoreboard());
        }
    }

    public void start() {
        if (updateTask != null) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (updateTask != null) {
            try { updateTask.cancel(); } catch (IllegalStateException ignored) { }
            updateTask = null;
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

    private void tick() {
        int alive = game.aliveCount();
        int total = game.getAllPlayerStates().size();
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), alive, total);
        }
    }

    private void render(Player viewer, Scoreboard board, int alive, int total) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        DropperPlayerState me = game.getPlayerState(viewer.getUniqueId());
        Team myTeam = teamManager.getTeamOfPlayer(viewer.getUniqueId());
        DropperGame.State state = game.getState();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("dropper"), NamedTextColor.AQUA, TextDecoration.BOLD));
        if (state == DropperGame.State.STARTING && game.getCurrentRoundNumber() == 0) {
            lines.add(Component.text(SmallText.of("countdown..."), NamedTextColor.GRAY));
        } else {
            lines.add(statusLine(me));
        }

        lines.add(Component.empty());

        int total_rounds = game.getTotalRounds();
        int roundNr = Math.max(1, game.getCurrentRoundNumber());
        lines.add(Component.text()
                .append(Component.text(SmallText.of("ronde "), NamedTextColor.GRAY))
                .append(Component.text(roundNr + " / " + total_rounds, NamedTextColor.AQUA))
                .build());
        if (state == DropperGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("tijd "), NamedTextColor.GRAY))
                    .append(Component.text(formatTime(game.getRoundSecondsLeft()), NamedTextColor.YELLOW))
                    .build());
        }

        lines.add(Component.empty());

        lines.add(Component.text()
                .append(Component.text(SmallText.of("in leven "), NamedTextColor.GRAY))
                .append(Component.text(alive + " / " + total, NamedTextColor.AQUA))
                .build());

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("jouw team"), NamedTextColor.GRAY));
        if (myTeam == null) {
            lines.add(Component.text(SmallText.of("(geen team)"), NamedTextColor.DARK_GRAY));
        } else {
            lines.add(Component.text()
                    .append(Component.text(truncate(myTeam.getName(), 12), myTeam.getColor()))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(myTeam.getPoints() + " pts", NamedTextColor.AQUA))
                    .build());
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("cloudito.cloud"), NamedTextColor.DARK_GRAY));

        int applied = Math.min(lines.size(), LINE_IDS.length);
        for (int i = 0; i < applied; i++) {
            obj.getScore(LINE_IDS[i]).customName(lines.get(i));
        }
        for (int i = applied; i < LINE_IDS.length; i++) {
            obj.getScore(LINE_IDS[i]).customName(Component.empty());
        }
    }

    private Component statusLine(DropperPlayerState me) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("uit (#" + me.getPlacement() + ")"),
                            NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .build();
        }
        if (me.hasCompletedCurrentRound()) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("voltooid"), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("in leven"), NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
