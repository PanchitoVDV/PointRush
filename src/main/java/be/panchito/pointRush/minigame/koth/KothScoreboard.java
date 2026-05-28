package be.panchito.pointRush.minigame.koth;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetEngine;
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
 * Per-player sidebar + shared boss bar voor Wipeout KOTH.
 */
public final class KothScoreboard {

    private static final String OBJ_KEY = "pr_koth";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final KothGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public KothScoreboard(PointRush plugin, KothGame game) {
        this.plugin = plugin;
        this.game = game;
        this.teamManager = plugin.getTeamManager();
        this.bossBar = BossBar.bossBar(
                Component.text(SmallText.of("wachten..."), NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.GREEN,
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
        KothGame.State state = game.getState();
        Map<UUID, TeamProgress> teamProgress = computeTeamProgress();
        long pointInterval = game.getConfig().getPointIntervalMs();

        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), state, teamProgress, pointInterval);
        }
    }

    private void render(Player viewer, Scoreboard board, KothGame.State state,
                        Map<UUID, TeamProgress> teamProgress, long pointInterval) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        KothPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("king of the hill"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (state == KothGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown ") + game.formatTime(game.getCountdownTimeLeftMs()),
                    NamedTextColor.GRAY));
        } else if (state == KothGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.GOLD))
                    .build());

            UUID controlling = game.getControllingBucket();
            if (controlling != null) {
                long ms = game.getHillProgressMs();
                lines.add(Component.text()
                        .append(Component.text(SmallText.of("bezetting "), NamedTextColor.GREEN))
                        .append(Component.text(truncate(game.bucketName(controlling), 12), teamColor(controlling)))
                        .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(game.formatTime(Math.max(0L, pointInterval - ms)), NamedTextColor.WHITE))
                        .build());
            } else {
                lines.add(Component.text(SmallText.of("hill omstreden"), NamedTextColor.RED));
            }
        } else {
            lines.add(Component.text(SmallText.of("wachtend"), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());
        lines.add(statusLine(me));

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("hill punten"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (teamProgress.isEmpty()) {
            lines.add(Component.text(SmallText.of("(nog geen punten)"), NamedTextColor.DARK_GRAY));
        } else {
            int shown = 0;
            for (Map.Entry<UUID, TeamProgress> entry : teamProgress.entrySet()) {
                if (shown >= 6) break;
                Team t = teamManager.getTeam(entry.getKey());
                TeamProgress tp = entry.getValue();
                if (t != null) {
                    lines.add(Component.text()
                            .append(Component.text(truncate(t.getName(), 10), t.getColor()))
                            .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(SmallText.of(tp.hillPoints + " pts · " + tp.alive + " alive"),
                                    tp.alive > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                            .build());
                } else {
                    lines.add(Component.text()
                            .append(Component.text(truncate(game.bucketName(entry.getKey()), 10), NamedTextColor.WHITE))
                            .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(SmallText.of(tp.hillPoints + " pts"), NamedTextColor.GRAY))
                            .build());
                }
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

    private Component statusLine(KothPlayerState me) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            long left = Math.max(0L, me.getRespawnAtMs() - System.currentTimeMillis());
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("spectator "), NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text("(" + game.formatTime(left) + ")", NamedTextColor.GOLD))
                    .build();
        }
        Player p = Bukkit.getPlayer(me.getUuid());
        if (p != null && game.getConfig().contains(p.getLocation())) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("op hill"), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("vechten"), NamedTextColor.WHITE))
                .build();
    }

    private Map<UUID, TeamProgress> computeTeamProgress() {
        Map<UUID, TeamProgress> result = new HashMap<>();

        for (KothPlayerState ps : game.getAllPlayerStates()) {
            UUID bucket = game.bucketFor(ps.getUuid());
            TeamProgress tp = result.computeIfAbsent(bucket, k -> new TeamProgress());
            if (ps.isAlive()) tp.alive++;
        }

        for (Map.Entry<UUID, Integer> e : game.getHillScores().entrySet()) {
            TeamProgress tp = result.computeIfAbsent(e.getKey(), k -> new TeamProgress());
            tp.hillPoints = e.getValue();
        }

        List<Map.Entry<UUID, TeamProgress>> list = new ArrayList<>(result.entrySet());
        list.sort(Comparator.<Map.Entry<UUID, TeamProgress>>comparingInt(e -> e.getValue().hillPoints).reversed());
        Map<UUID, TeamProgress> sorted = new LinkedHashMap<>();
        for (Map.Entry<UUID, TeamProgress> e : list) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    private NamedTextColor teamColor(UUID bucketId) {
        Team t = teamManager.getTeam(bucketId);
        if (t == null) return NamedTextColor.WHITE;
        return t.getColor();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static final class TeamProgress {
        int alive;
        int hillPoints;
    }
}
