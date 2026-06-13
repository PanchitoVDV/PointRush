package be.panchito.pointRush.minigame.goldrush;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live sidebar voor Gold Rush: resttijd, top-miners (jouw regel gemarkeerd) en jouw eigen goud.
 */
public final class GoldRushScoreboard {

    private static final String OBJ_KEY = "pr_gr";
    private static final int TOP_COUNT = 5;

    private static final String[] LINE_IDS = {
            "§0", "§1", "§2", "§3",
            "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b",
            "§c", "§d", "§e", "§f"
    };

    private final PointRush plugin;
    private final GoldRushGame game;
    private final TeamManager teamManager;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask updateTask;

    public GoldRushScoreboard(PointRush plugin, GoldRushGame game) {
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
                Component.text(SmallText.of("GOLD RUSH"), NamedTextColor.GOLD, TextDecoration.BOLD));
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
        if (boards.isEmpty()) return;
        List<Map.Entry<UUID, Integer>> top = computeTop();
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), top);
        }
    }

    private void render(Player viewer, Scoreboard board, List<Map.Entry<UUID, Integer>> top) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        UUID viewerId = viewer.getUniqueId();
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text()
                .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.GOLD))
                .build());

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("top miners"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (top.isEmpty()) {
            lines.add(Component.text(SmallText.of("(nog geen goud)"), NamedTextColor.DARK_GRAY));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                if (rank > TOP_COUNT) break;
                UUID id = entry.getKey();
                boolean self = id.equals(viewerId);
                Team team = teamManager.getTeamOfPlayer(id);
                NamedTextColor nameColor = self ? NamedTextColor.YELLOW
                        : (team != null ? team.getColor() : NamedTextColor.WHITE);
                Component nameComp = Component.text(truncate(game.nameOf(id), 10), nameColor);
                if (self) {
                    nameComp = nameComp.decorate(TextDecoration.BOLD);
                }
                lines.add(Component.text()
                        .append(Component.text(rank + ". ", NamedTextColor.DARK_GRAY))
                        .append(nameComp)
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.valueOf(entry.getValue()), NamedTextColor.GOLD))
                        .build());
                rank++;
            }
        }

        lines.add(Component.empty());
        lines.add(Component.text()
                .append(Component.text(SmallText.of("jouw goud "), NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(game.getScore(viewerId)),
                        NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());

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

    private List<Map.Entry<UUID, Integer>> computeTop() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(game.getScores().entrySet());
        list.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
        return list;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
