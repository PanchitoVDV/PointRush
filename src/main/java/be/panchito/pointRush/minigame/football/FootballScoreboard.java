package be.panchito.pointRush.minigame.football;

import be.panchito.pointRush.PointRush;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + gedeelde boss bar voor het Voetbal-event.
 */
public final class FootballScoreboard {

    private static final String OBJ_KEY = "pr_football";

    private static final String[] LINE_IDS = {
            "§0", "§1", "§2", "§3",
            "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b"
    };

    private final PointRush plugin;
    private final FootballGame game;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public FootballScoreboard(PointRush plugin, FootballGame game) {
        this.plugin = plugin;
        this.game = game;
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
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        for (UUID id : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bossBar);
                if (mgr != null) {
                    p.setScoreboard(mgr.getMainScoreboard());
                }
            }
        }
        boards.clear();
    }

    public void updateBossBar(String label, float progress, BossBar.Color color) {
        progress = Math.max(0f, Math.min(1f, progress));
        bossBar.name(Component.text(SmallText.of(label), NamedTextColor.WHITE));
        bossBar.progress(progress);
        bossBar.color(color);
    }

    private void tick() {
        FootballGame.State state = game.getState();
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), state);
        }
    }

    private void render(Player viewer, Scoreboard board, FootballGame.State state) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        FootballPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("voetbal"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (state == FootballGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("aftrap over ") + game.formatTime(game.getCountdownTimeLeftMs()),
                    NamedTextColor.GRAY));
        } else if (state == FootballGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getMatchTimeLeftMs()), NamedTextColor.GOLD))
                    .build());
        } else {
            lines.add(Component.text(SmallText.of("wachtend"), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("stand"), NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(scoreLine(FootballSide.RED, game.getRedScore()));
        lines.add(scoreLine(FootballSide.BLUE, game.getBlueScore()));

        if (me != null && me.getSide() != null) {
            lines.add(Component.empty());
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("jij: "), NamedTextColor.GRAY))
                    .append(Component.text(me.getSide().getDisplayName(), me.getSide().getTextColor(), TextDecoration.BOLD))
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

    private Component scoreLine(FootballSide side, int score) {
        return Component.text()
                .append(Component.text(side.getDisplayName(), side.getTextColor()))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(score), NamedTextColor.WHITE, TextDecoration.BOLD))
                .build();
    }
}
