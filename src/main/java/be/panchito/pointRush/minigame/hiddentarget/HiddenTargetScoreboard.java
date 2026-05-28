package be.panchito.pointRush.minigame.hiddentarget;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + shared boss bar voor Hidden Target.
 */
public final class HiddenTargetScoreboard {

    private static final String OBJ_KEY = "pr_ht";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final HiddenTargetGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public HiddenTargetScoreboard(PointRush plugin, HiddenTargetGame game) {
        this.plugin = plugin;
        this.game = game;
        this.teamManager = plugin.getTeamManager();
        this.bossBar = BossBar.bossBar(
                Component.text(SmallText.of("wachten..."), NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
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
        if (updateTask != null) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (updateTask != null) {
            try {
                updateTask.cancel();
            } catch (IllegalStateException ignored) {
            }
            updateTask = null;
        }
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bossBar);
            }
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

    public void updateBossBar(String phaseLabel, float progress, BossBar.Color color) {
        progress = Math.max(0f, Math.min(1f, progress));
        bossBar.name(Component.text(SmallText.of(phaseLabel), NamedTextColor.WHITE));
        bossBar.progress(progress);
        bossBar.color(color);
    }

    private void tick() {
        HiddenTargetGame.State state = game.getState();
        List<HiddenTargetPlayerState> top = computeTopKillers();

        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), state, top);
        }
    }

    private void render(Player viewer, Scoreboard board, HiddenTargetGame.State state,
                        List<HiddenTargetPlayerState> top) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        HiddenTargetPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("hidden target"), NamedTextColor.DARK_RED, TextDecoration.BOLD));

        if (state == HiddenTargetGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown ") + game.formatTime(game.getCountdownTimeLeftMs()),
                    NamedTextColor.GRAY));
        } else if (state == HiddenTargetGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.GOLD))
                    .build());
        } else {
            lines.add(Component.text(SmallText.of("wachtend"), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());
        lines.add(statusLine(me));

        if (me != null && state == HiddenTargetGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("jouw eliminaties "), NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(me.getTargetKills()), NamedTextColor.WHITE))
                    .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(SmallText.of("gejaagd "), NamedTextColor.RED))
                    .append(Component.text(String.valueOf(me.getHuntedDeaths()), NamedTextColor.WHITE))
                    .build());
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("top hunters"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (top.isEmpty()) {
            lines.add(Component.text(SmallText.of("(nog geen eliminaties)"), NamedTextColor.DARK_GRAY));
        } else {
            int shown = 0;
            for (HiddenTargetPlayerState ps : top) {
                if (shown >= 5) break;
                Player p = Bukkit.getPlayer(ps.getUuid());
                String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
                Team team = teamManager.getTeamOfPlayer(ps.getUuid());
                lines.add(Component.text()
                        .append(Component.text(truncate(name, 10), team != null ? team.getColor() : NamedTextColor.WHITE))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(ps.getTargetKills() + " elim.", NamedTextColor.GREEN))
                        .build());
                shown++;
            }
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

    private Component statusLine(HiddenTargetPlayerState me) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            long left = Math.max(0L, me.getRespawnAtMs() - System.currentTimeMillis());
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("dood "), NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text("(" + game.formatTime(left) + ")", NamedTextColor.GOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("jacht"), NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    private List<HiddenTargetPlayerState> computeTopKillers() {
        List<HiddenTargetPlayerState> list = new ArrayList<>(game.getAllPlayerStates());
        list.sort(Comparator.comparingInt(HiddenTargetPlayerState::getTargetKills).reversed()
                .thenComparingInt(HiddenTargetPlayerState::netScore).reversed());
        return list;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
