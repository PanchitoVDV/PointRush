package be.panchito.pointRush.minigame.boss;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + shared boss bar voor Boss Event.
 */
public final class BossEventScoreboard {

    private static final String OBJ_KEY = "pr_bossevent";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b"
    };

    private final be.panchito.pointRush.PointRush plugin;
    private final BossEventGame game;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public BossEventScoreboard(be.panchito.pointRush.PointRush plugin, BossEventGame game) {
        this.plugin = plugin;
        this.game = game;
        this.bossBar = BossBar.bossBar(
                Component.text(SmallText.of("wachten..."), NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
    }

    public void attach(Player player) {
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) {
            return;
        }

        Scoreboard board = mgr.getNewScoreboard();
        Objective obj = board.registerNewObjective(
                OBJ_KEY,
                Criteria.DUMMY,
                Component.text(SmallText.of("Boss Event"), NamedTextColor.DARK_RED, TextDecoration.BOLD));
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
        player.hideBossBar(bossBar);
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null) {
            player.setScoreboard(mgr.getMainScoreboard());
        }
    }

    public void start() {
        if (updateTask != null) {
            return;
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 10L, 10L);
    }

    public void stop() {
        if (updateTask != null) {
            try {
                updateTask.cancel();
            } catch (IllegalStateException ignored) {
            }
            updateTask = null;
        }
        for (UUID id : new HashMap<>(boards).keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                detach(p);
            }
        }
        boards.clear();
    }

    public void updateBossBar(String text, float progress, BossBar.Color color) {
        bossBar.name(Component.text(SmallText.of(text), NamedTextColor.WHITE));
        bossBar.progress(Math.max(0f, Math.min(1f, progress)));
        bossBar.color(color);
    }

    private void refreshAll() {
        for (Map.Entry<UUID, Scoreboard> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            refresh(player, entry.getValue());
        }
    }

    private void refresh(Player player, Scoreboard board) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) {
            return;
        }

        BossEventPlayerState ps = game.getPlayerState(player.getUniqueId());
        if (ps == null) {
            return;
        }

        BossEventGame.Phase phase = game.getPhase();
        String phaseLabel = switch (phase) {
            case COUNTDOWN -> "countdown";
            case ARENA_ROUND -> "arena ronde " + game.getCurrentRound();
            case ARENA_INTERMISSION -> "pauze";
            case FINAL_COUNTDOWN -> "finaal countdown";
            case FINAL_ROUND -> "finaalronde";
            default -> "-";
        };

        String status = ps.isSpectatingInPhase(phase) ? "uitgeschakeld" : "actief";
        if (phase == BossEventGame.Phase.FINAL_ROUND || phase == BossEventGame.Phase.FINAL_COUNTDOWN) {
            status = ps.isFinalAlive() ? "actief" : "uitgeschakeld";
        }

        setLine(obj, 0, Component.text(SmallText.of("fase: " + phaseLabel), NamedTextColor.GRAY));
        setLine(obj, 1, Component.text(SmallText.of("arena: " + (ps.getArenaId() != null ? ps.getArenaId() : "-")),
                NamedTextColor.AQUA));
        setLine(obj, 2, Component.text(SmallText.of("status: " + status),
                ps.isSpectatingInPhase(phase) ? NamedTextColor.RED : NamedTextColor.GREEN));
        setLine(obj, 3, Component.text(SmallText.of("tijd: " + game.formatTime(game.getPhaseTimeLeftMs())),
                NamedTextColor.YELLOW));
        setLine(obj, 4, Component.empty());
        setLine(obj, 5, Component.text(SmallText.of("punten: " + ps.getPointsEarned()), NamedTextColor.GOLD));

        for (int i = 6; i < LINE_IDS.length; i++) {
            setLine(obj, i, Component.empty());
        }
    }

    private void setLine(Objective obj, int index, Component text) {
        if (index < 0 || index >= LINE_IDS.length) {
            return;
        }
        obj.getScore(LINE_IDS[index]).customName(text);
    }
}
