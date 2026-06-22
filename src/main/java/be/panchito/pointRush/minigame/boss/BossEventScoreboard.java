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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + boss bars voor Boss Event.
 * Tijdens arena-rondes ziet elke speler de live HP-bar van zijn eigen arena-boss;
 * in countdowns/pauze/finaal een gedeelde info-bar.
 */
public final class BossEventScoreboard {

    private static final String OBJ_KEY = "pr_bossevent";

    private static final String[] LINE_IDS = {
            "§0", "§1", "§2", "§3",
            "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b"
    };

    private final be.panchito.pointRush.PointRush plugin;
    private final BossEventGame game;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar infoBar;
    private final Map<String, BossBar> arenaBars = new LinkedHashMap<>();
    private final Map<UUID, BossBar> shownBar = new HashMap<>();
    private BukkitTask updateTask;

    public BossEventScoreboard(be.panchito.pointRush.PointRush plugin, BossEventGame game) {
        this.plugin = plugin;
        this.game = game;
        this.infoBar = BossBar.bossBar(
                Component.text(SmallText.of("wachten..."), NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
    }

    /** Maak één live HP-bar per actieve arena aan (bij event-start). */
    public void initArenaBars(Collection<String> arenaIds) {
        clearArenaBars();
        for (String id : arenaIds) {
            arenaBars.put(id, BossBar.bossBar(
                    Component.text(SmallText.of("boss"), NamedTextColor.WHITE),
                    1.0f,
                    BossBar.Color.RED,
                    BossBar.Overlay.NOTCHED_20));
        }
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
        applyBossBar(player);
    }

    public void detach(Player player) {
        boards.remove(player.getUniqueId());
        shownBar.remove(player.getUniqueId());
        player.hideBossBar(infoBar);
        for (BossBar bar : arenaBars.values()) {
            player.hideBossBar(bar);
        }
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
        shownBar.clear();
        arenaBars.clear();
    }

    /** Gedeelde info-bar (countdown / pauze / finaal). */
    public void updateInfoBar(String text, float progress, BossBar.Color color) {
        infoBar.name(Component.text(SmallText.of(text), NamedTextColor.WHITE));
        infoBar.progress(clamp(progress));
        infoBar.color(color);
    }

    /** Live boss-HP-bar voor één specifieke arena. */
    public void updateArenaBar(String arenaId, String text, float progress, BossBar.Color color) {
        BossBar bar = arenaBars.get(arenaId);
        if (bar == null) {
            return;
        }
        bar.name(Component.text(SmallText.of(text), NamedTextColor.WHITE));
        bar.progress(clamp(progress));
        bar.color(color);
    }

    private void clearArenaBars() {
        for (BossBar bar : arenaBars.values()) {
            for (UUID id : new ArrayList<>(boards.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        arenaBars.clear();
    }

    private float clamp(float progress) {
        return Math.max(0f, Math.min(1f, progress));
    }

    private void refreshAll() {
        for (Map.Entry<UUID, Scoreboard> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            refresh(player, entry.getValue());
            applyBossBar(player);
        }
    }

    /** Toon de speler de juiste bar voor de huidige fase (arena-HP of gedeelde info). */
    private void applyBossBar(Player player) {
        BossBar target = targetBar(player);
        BossBar current = shownBar.get(player.getUniqueId());
        if (current == target) {
            return;
        }
        if (current != null) {
            player.hideBossBar(current);
        }
        if (target != null) {
            player.showBossBar(target);
        }
        shownBar.put(player.getUniqueId(), target);
    }

    private BossBar targetBar(Player player) {
        if (game.getPhase() == BossEventGame.Phase.ARENA_ROUND) {
            BossEventPlayerState ps = game.getPlayerState(player.getUniqueId());
            if (ps != null && ps.getArenaId() != null) {
                BossBar bar = arenaBars.get(ps.getArenaId());
                if (bar != null) {
                    return bar;
                }
            }
        }
        return infoBar;
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

        boolean eliminated;
        if (phase == BossEventGame.Phase.FINAL_ROUND || phase == BossEventGame.Phase.FINAL_COUNTDOWN) {
            eliminated = !ps.isFinalAlive();
        } else {
            eliminated = ps.isSpectatingInPhase(phase);
        }
        String status = eliminated ? "uitgeschakeld" : "actief";

        setLine(obj, 0, Component.text(SmallText.of("fase: " + phaseLabel), NamedTextColor.GRAY));
        setLine(obj, 1, Component.text(SmallText.of("arena: " + (ps.getArenaId() != null ? ps.getArenaId() : "-")),
                NamedTextColor.AQUA));
        setLine(obj, 2, Component.text(SmallText.of("status: " + status),
                eliminated ? NamedTextColor.RED : NamedTextColor.GREEN));
        setLine(obj, 3, Component.text(SmallText.of("tijd: " + game.formatTime(game.getPhaseTimeLeftMs())),
                NamedTextColor.YELLOW));
        setLine(obj, 4, Component.empty());
        setLine(obj, 5, Component.text(SmallText.of("punten: " + ps.getPointsEarned()), NamedTextColor.GOLD));
        setLine(obj, 6, Component.text(SmallText.of("schade: " + (long) ps.getBossDamage()), NamedTextColor.LIGHT_PURPLE));

        for (int i = 7; i < LINE_IDS.length; i++) {
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
