package be.panchito.pointRush.minigame.boatrace;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar for the Boat Race minigame.
 */
public final class BoatRaceScoreboard {

    private static final String OBJ_KEY = "pr_boatrace";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d"
    };

    private final PointRush plugin;
    private final BoatRaceGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private long raceStartMs;
    private BukkitTask updateTask;

    public BoatRaceScoreboard(PointRush plugin, BoatRaceGame game) {
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
        raceStartMs = System.currentTimeMillis();
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 10L);
    }

    public void markRaceStart() {
        this.raceStartMs = System.currentTimeMillis();
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
        List<BoatRacePlayerState> ranked = computeLeaderboard();
        String timeStr = formatTime(System.currentTimeMillis() - raceStartMs);
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), ranked, timeStr);
        }
    }

    private void render(Player viewer, Scoreboard board, List<BoatRacePlayerState> ranked, String timeStr) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        BoatRacePlayerState me = game.getPlayerState(viewer.getUniqueId());
        int totalLaps = game.getConfig().getLaps();
        int totalCps = game.getConfig().getCheckpoints().size();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("bootrace"), NamedTextColor.AQUA, TextDecoration.BOLD));
        lines.add(meProgressLine(me, totalLaps, totalCps));

        lines.add(Component.empty());

        lines.add(Component.text()
                .append(Component.text(SmallText.of("tijd "), NamedTextColor.GRAY))
                .append(Component.text(timeStr, NamedTextColor.WHITE))
                .build());

        lines.add(Component.empty());

        lines.add(Component.text(SmallText.of("top 5"), NamedTextColor.GOLD, TextDecoration.BOLD));
        int limit = Math.min(5, ranked.size());
        for (int i = 0; i < limit; i++) {
            lines.add(formatLeaderboardLine(viewer.getUniqueId(), i + 1, ranked.get(i), totalLaps));
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

    private Component meProgressLine(BoatRacePlayerState me, int totalLaps, int totalCps) {
        if (me == null) {
            return Component.text("-", NamedTextColor.WHITE);
        }
        if (me.isFinished()) {
            return Component.text()
                    .append(Component.text(SmallText.of("gefinisht "), NamedTextColor.GREEN))
                    .append(Component.text("#" + me.getPlacement(), NamedTextColor.GOLD))
                    .build();
        }
        int displayLap = Math.min(totalLaps, me.getCurrentLap() + 1);
        String cpStr = totalCps == 0 ? "-" : (me.getNextCheckpoint() + "/" + totalCps);
        return Component.text()
                .append(Component.text(SmallText.of("lap "), NamedTextColor.WHITE))
                .append(Component.text(displayLap + "/" + totalLaps, NamedTextColor.AQUA))
                .append(Component.text(SmallText.of(" · cp "), NamedTextColor.DARK_GRAY))
                .append(Component.text(cpStr, NamedTextColor.WHITE))
                .build();
    }

    private Component formatLeaderboardLine(UUID viewerId, int rank, BoatRacePlayerState ps, int totalLaps) {
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
            int displayLap = Math.min(totalLaps, ps.getCurrentLap() + 1);
            status = "lap " + displayLap;
            statusColor = NamedTextColor.AQUA;
        }

        boolean isSelf = ps.getUuid().equals(viewerId);
        Component nameComp = Component.text(name, nameColor);
        if (isSelf) nameComp = nameComp.decoration(TextDecoration.BOLD, true);

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

    private List<BoatRacePlayerState> computeLeaderboard() {
        List<BoatRacePlayerState> all = new ArrayList<>(game.getAllPlayerStates());
        all.sort(Comparator
                .comparing(BoatRacePlayerState::isFinished).reversed()
                .thenComparingInt(BoatRacePlayerState::getPlacement)
                .thenComparing(Comparator.comparingInt(BoatRacePlayerState::getCurrentLap).reversed())
                .thenComparing(Comparator.comparingInt(BoatRacePlayerState::getNextCheckpoint).reversed())
                .thenComparingLong(BoatRacePlayerState::getLastProgressTimeMs));
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
