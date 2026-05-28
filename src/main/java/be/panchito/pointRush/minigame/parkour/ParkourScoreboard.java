package be.panchito.pointRush.minigame.parkour;

import be.panchito.pointRush.PointRush;
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
 * Per-player sidebar scoreboard for the parkour event.
 *
 * <p>Layout:
 * <pre>
 *     ᴘᴏɪɴᴛʀᴜѕʜ        (title)
 *
 *     ᴊᴏᴜᴡ ᴘʀᴏɢʀᴇѕѕ
 *     cp 3/8
 *
 *     ᴛɪᴊᴅ 1:23
 *
 *     ᴛᴏᴘ 5
 *     1. Player · cp 5
 *     2. Player · cp 4
 *     ...
 *
 *     cloudito.cloud
 * </pre>
 *
 * <p>Uses fixed slot IDs + {@link Score#customName(Component)} so the sidebar updates
 * without flicker and without resetting scores every tick.
 */
public final class ParkourScoreboard {

    private static final String OBJ_KEY = "pr_parkour";

    /** Invisible single-color-code entries used purely as unique slot ids. */
    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d"
    };

    private final PointRush plugin;
    private final ParkourGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private long raceStartMs;
    private BukkitTask updateTask;

    public ParkourScoreboard(PointRush plugin, ParkourGame game) {
        this.plugin = plugin;
        this.game = game;
        this.teamManager = plugin.getTeamManager();
    }

    /** Creates and assigns a sidebar to the given player. */
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
            // Older API - score numbers stay visible.
        }

        for (int i = 0; i < LINE_IDS.length; i++) {
            Score s = obj.getScore(LINE_IDS[i]);
            s.setScore(LINE_IDS.length - i);
            s.customName(Component.empty());
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    /** Removes the parkour sidebar from this player and restores the main scoreboard. */
    public void detach(Player player) {
        boards.remove(player.getUniqueId());
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null && player.isOnline()) {
            player.setScoreboard(mgr.getMainScoreboard());
        }
    }

    /** Starts the periodic update task. Call after all initial participants are attached. */
    public void start() {
        if (updateTask != null) return;
        raceStartMs = System.currentTimeMillis();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 10L);
    }

    /** Resets the race clock to "now". Called when the actual race begins (after countdown). */
    public void markRaceStart() {
        this.raceStartMs = System.currentTimeMillis();
    }

    /** Stops the update task and clears all sidebars from all participants. */
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
        List<ParkourPlayerState> ranked = computeLeaderboard();
        String timeStr = formatTime(System.currentTimeMillis() - raceStartMs);
        int totalCps = game.getConfig().getCheckpoints().size();

        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), ranked, timeStr, totalCps);
        }
    }

    private void render(Player viewer, Scoreboard board, List<ParkourPlayerState> ranked,
                        String timeStr, int totalCps) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        ParkourPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("jouw progress"), NamedTextColor.GRAY));
        lines.add(meProgressLine(me, totalCps));

        lines.add(Component.empty());

        lines.add(Component.text()
                .append(Component.text(SmallText.of("tijd "), NamedTextColor.GRAY))
                .append(Component.text(timeStr, NamedTextColor.WHITE))
                .build());

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("top 5"), NamedTextColor.GOLD, TextDecoration.BOLD));

        int limit = Math.min(5, ranked.size());
        for (int i = 0; i < limit; i++) {
            lines.add(formatLeaderboardLine(viewer.getUniqueId(), i + 1, ranked.get(i)));
        }
        for (int i = limit; i < 5; i++) {
            lines.add(Component.text(SmallText.of("#" + (i + 1) + " -"), NamedTextColor.DARK_GRAY));
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

    private Component meProgressLine(ParkourPlayerState me, int totalCps) {
        if (me == null) {
            return Component.text("-", NamedTextColor.WHITE);
        }
        if (me.isFinished()) {
            return Component.text()
                    .append(Component.text(SmallText.of("gefinisht "), NamedTextColor.GREEN))
                    .append(Component.text("#" + me.getPlacement(), NamedTextColor.GOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("cp "), NamedTextColor.WHITE))
                .append(Component.text(
                        (me.getCheckpointIndex() + 1) + "/" + totalCps,
                        NamedTextColor.GOLD))
                .build();
    }

    private Component formatLeaderboardLine(UUID viewerId, int rank, ParkourPlayerState ps) {
        Player p = Bukkit.getPlayer(ps.getUuid());
        String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
        name = truncate(name, 12);

        Team team = teamManager.getTeamOfPlayer(ps.getUuid());
        TextColor nameColor = team != null ? team.getColor() : NamedTextColor.WHITE;

        String status;
        TextColor statusColor;
        if (ps.isFinished()) {
            status = "#" + ps.getPlacement();
            statusColor = NamedTextColor.GREEN;
        } else {
            status = "cp " + (ps.getCheckpointIndex() + 1);
            statusColor = NamedTextColor.GOLD;
        }

        boolean isSelf = ps.getUuid().equals(viewerId);
        Component nameComp = Component.text(name, nameColor);
        if (isSelf) {
            nameComp = nameComp.decoration(TextDecoration.BOLD, true);
        }

        return Component.text()
                .append(Component.text(rank + ". ", NamedTextColor.GRAY))
                .append(nameComp)
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(status), statusColor))
                .build();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private List<ParkourPlayerState> computeLeaderboard() {
        List<ParkourPlayerState> all = new ArrayList<>(game.getAllPlayerStates());
        all.sort((a, b) -> {
            if (a.isFinished() && b.isFinished()) {
                return Integer.compare(a.getPlacement(), b.getPlacement());
            }
            if (a.isFinished()) return -1;
            if (b.isFinished()) return 1;
            int cpCmp = Integer.compare(b.getCheckpointIndex(), a.getCheckpointIndex());
            if (cpCmp != 0) return cpCmp;
            return Long.compare(a.getLastProgressTimeMs(), b.getLastProgressTimeMs());
        });
        return all;
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
