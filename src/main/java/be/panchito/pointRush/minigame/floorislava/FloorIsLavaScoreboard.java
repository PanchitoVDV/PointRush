package be.panchito.pointRush.minigame.floorislava;

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
 * Per-player sidebar voor Floor is Lava.
 */
public final class FloorIsLavaScoreboard {

    private static final String OBJ_KEY = "pr_fil";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final PointRush plugin;
    private final FloorIsLavaGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask updateTask;

    public FloorIsLavaScoreboard(PointRush plugin, FloorIsLavaGame game) {
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
                Component.text(SmallText.of("PointRush"), NamedTextColor.GOLD, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            obj.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
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
            try {
                updateTask.cancel();
            } catch (IllegalStateException ignored) {
            }
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
        FloorIsLavaGame.State state = game.getState();
        int lavaY = game.getCurrentLavaY();
        long riseLeftMs = Math.max(0L, game.getNextLavaRiseMs() - System.currentTimeMillis());
        long itemLeftMs = Math.max(0L, game.getNextItemDropMs() - System.currentTimeMillis());
        String riseStr = formatTime(riseLeftMs);
        String itemStr = formatTime(itemLeftMs);

        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), alive, total, state, lavaY, riseStr, itemStr);
        }
    }

    private void render(Player viewer, Scoreboard board, int alive, int total,
                        FloorIsLavaGame.State state, int lavaY, String riseStr, String itemStr) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        FloorIsLavaPlayerState me = game.getPlayerState(viewer.getUniqueId());
        Team myTeam = teamManager.getTeamOfPlayer(viewer.getUniqueId());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("floor is lava"), NamedTextColor.RED, TextDecoration.BOLD));
        if (state == FloorIsLavaGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown..."), NamedTextColor.GRAY));
        } else {
            lines.add(statusLine(me));
        }

        lines.add(Component.empty());

        if (state == FloorIsLavaGame.State.RUNNING && lavaY > Integer.MIN_VALUE) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("lava y "), NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(lavaY), NamedTextColor.RED))
                    .build());
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("stijgt in "), NamedTextColor.GRAY))
                    .append(Component.text(riseStr, NamedTextColor.GOLD))
                    .build());
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("kit in "), NamedTextColor.GRAY))
                    .append(Component.text(itemStr, NamedTextColor.GREEN))
                    .build());
            lines.add(Component.empty());
        }

        lines.add(Component.text()
                .append(Component.text(SmallText.of("in leven "), NamedTextColor.GRAY))
                .append(Component.text(alive + " / " + total, NamedTextColor.GOLD))
                .build());

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
        lines.add(Component.text(SmallText.of("cloudito.cloud"), NamedTextColor.DARK_GRAY));

        int applied = Math.min(lines.size(), LINE_IDS.length);
        for (int i = 0; i < applied; i++) {
            obj.getScore(LINE_IDS[i]).customName(lines.get(i));
        }
        for (int i = applied; i < LINE_IDS.length; i++) {
            obj.getScore(LINE_IDS[i]).customName(Component.empty());
        }
    }

    private Component statusLine(FloorIsLavaPlayerState me) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("lava (#" + me.getPlacement() + ")"),
                            NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("in leven"), NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
