package be.panchito.pointRush.minigame.tnttag;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.SmallText;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + shared boss bar for the TNT Tag minigame.
 *
 * <p>Sidebar layout:
 * <pre>
 *     ᴘᴏɪɴᴛʀᴜѕʜ                    (title)
 *
 *     ᴛɴᴛ ᴛᴀɢ
 *     ronde 2  ·  0:24
 *
 *     status: tagged / safe / out
 *
 *     ᴛᴇᴀᴍѕ
 *     Red    3 alive
 *     Blue   2 alive
 *
 *     cloudito.cloud
 * </pre>
 *
 * <p>The boss bar is a shared red bar that ticks down the round timer.
 */
public final class TntTagScoreboard {

    private static final String OBJ_KEY = "pr_tnttag";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final TntTagGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public TntTagScoreboard(PointRush plugin, TntTagGame game) {
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
            // older API - score numbers stay visible
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
            try { updateTask.cancel(); } catch (IllegalStateException ignored) { }
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
        int round = game.getRoundNumber();
        long timeLeftMs = game.getRoundTimeLeftMs();
        String timeStr = formatTime(timeLeftMs);
        TntTagGame.State state = game.getState();

        Map<UUID, Integer> teamAlive = computeTeamAliveCounts();

        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), round, timeStr, state, teamAlive);
        }
    }

    private void render(Player viewer, Scoreboard board, int round, String timeStr,
                        TntTagGame.State state, Map<UUID, Integer> teamAlive) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        TntTagPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("tnt tag"), NamedTextColor.RED, TextDecoration.BOLD));
        if (state == TntTagGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown ") + timeStr, NamedTextColor.GRAY));
        } else if (state == TntTagGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("ronde "), NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(round), NamedTextColor.GOLD))
                    .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(timeStr, NamedTextColor.WHITE))
                    .build());
        } else {
            lines.add(Component.text(SmallText.of(state.name().toLowerCase()), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());

        lines.add(statusLine(me));

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("teams"), NamedTextColor.GOLD, TextDecoration.BOLD));
        if (teamAlive.isEmpty()) {
            lines.add(Component.text(SmallText.of("(geen teams)"), NamedTextColor.DARK_GRAY));
        } else {
            int shown = 0;
            for (Map.Entry<UUID, Integer> entry : teamAlive.entrySet()) {
                if (shown >= 6) break;
                Team t = teamManager.getTeam(entry.getKey());
                if (t == null) continue;
                int alive = entry.getValue();
                lines.add(Component.text()
                        .append(Component.text(truncate(t.getName(), 10), t.getColor()))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(SmallText.of(alive + " in leven"),
                                alive > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
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

    private Component statusLine(TntTagPlayerState me) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("uitgeschakeld"), NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .build();
        }
        if (me.isTagged()) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("getagd"), NamedTextColor.RED, TextDecoration.BOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("veilig"), NamedTextColor.GREEN, TextDecoration.BOLD))
                .build();
    }

    /**
     * Returns alive counts per team-id, sorted by alive count desc.
     * Teams with zero remaining members are still included (so players see who got knocked out).
     */
    private Map<UUID, Integer> computeTeamAliveCounts() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (TntTagPlayerState ps : game.getAllPlayerStates()) {
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team == null) continue;
            counts.putIfAbsent(team.getId(), 0);
            if (ps.isAlive()) {
                counts.merge(team.getId(), 1, Integer::sum);
            }
        }
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        Map<UUID, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> e : list) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
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
