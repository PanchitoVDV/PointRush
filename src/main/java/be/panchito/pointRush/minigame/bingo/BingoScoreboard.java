package be.panchito.pointRush.minigame.bingo;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.SmallText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BingoScoreboard {

    private static final String OBJ = "pr_bingo";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final PointRush plugin;
    private final BingoGame game;
    private final TeamManager teamManager;
    private final BossBar bossBar;
    private final Map<UUID, Scoreboard> boards = new java.util.HashMap<>();
    private BukkitTask task;

    public BingoScoreboard(PointRush plugin, BingoGame game) {
        this.plugin = plugin;
        this.game = game;
        this.teamManager = plugin.getTeamManager();
        this.bossBar = BossBar.bossBar(
                Component.text(SmallText.of("bingo"), NamedTextColor.GOLD),
                1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    }

    public void attach(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;
        Scoreboard board = mgr.getNewScoreboard();
        Objective obj = board.registerNewObjective(OBJ, Criteria.DUMMY,
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
        player.showBossBar(bossBar);
    }

    public void detach(Player player) {
        boards.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.hideBossBar(bossBar);
        }
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null && player.isOnline()) {
            player.setScoreboard(mgr.getMainScoreboard());
        }
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        updateBossBar();
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
            task = null;
        }
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(bossBar);
        }
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && mgr != null) p.setScoreboard(mgr.getMainScoreboard());
        }
        boards.clear();
    }

    public void updateBossBar() {
        long left = game.getRunTimeLeftMs();
        long total = game.getConfig().getDurationMs();
        float progress = total > 0 ? Math.min(1f, left / (float) total) : 0f;
        bossBar.progress(progress);
        bossBar.name(Component.text(SmallText.of("bingo · rest " + game.formatTime(left)),
                NamedTextColor.WHITE));
    }

    private void tick() {
        if (game.getState() != BingoGame.State.RUNNING) return;
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue());
        }
    }

    private void render(Player viewer, Scoreboard board) {
        Objective obj = board.getObjective(OBJ);
        if (obj == null) return;

        UUID bucket = game.bucketFor(viewer.getUniqueId());
        BingoTeamProgress me = game.getTeamProgress(bucket);
        Team myTeam = teamManager.getTeamOfPlayer(viewer.getUniqueId());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("bingo"), NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(Component.text()
                .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.GOLD))
                .build());

        if (me != null) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("jouw team "), NamedTextColor.GRAY))
                    .append(Component.text(me.countFound() + "/" + BingoGrid.TOTAL,
                            NamedTextColor.GREEN, TextDecoration.BOLD))
                    .build());
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("top teams"), NamedTextColor.GOLD, TextDecoration.BOLD));

        int shown = 0;
        for (Map.Entry<UUID, BingoTeamProgress> entry : game.teams().sortedByFound()) {
            if (shown >= 5) break;
            Team t = teamManager.getTeam(entry.getKey());
            NamedTextColor color = t != null ? t.getColor() : NamedTextColor.WHITE;
            String name = game.bucketLabel(entry.getKey());
            lines.add(Component.text()
                    .append(Component.text(trunc(name, 10), color))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.getValue().countFound() + "/" + BingoGrid.TOTAL,
                            NamedTextColor.GREEN))
                    .build());
            shown++;
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("klik map voor kaart"), NamedTextColor.DARK_GRAY));
        lines.add(Component.text(SmallText.of("cloudito.cloud"), NamedTextColor.DARK_GRAY));

        int n = Math.min(lines.size(), LINE_IDS.length);
        for (int i = 0; i < n; i++) {
            obj.getScore(LINE_IDS[i]).customName(lines.get(i));
        }
        for (int i = n; i < LINE_IDS.length; i++) {
            obj.getScore(LINE_IDS[i]).customName(Component.empty());
        }
    }

    private String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
