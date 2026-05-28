package be.panchito.pointRush.minigame.ctf;

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
 * Per-player sidebar + shared boss bar voor Capture the Flag.
 */
public final class CtfScoreboard {

    private static final String OBJ_KEY = "pr_ctf";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final CtfGame game;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public CtfScoreboard(PointRush plugin, CtfGame game) {
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
        CtfGame.State state = game.getState();
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), state);
        }
    }

    private void render(Player viewer, Scoreboard board, CtfGame.State state) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        CtfPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("capture the flag"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (state == CtfGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown ") + game.formatTime(game.getCountdownTimeLeftMs()),
                    NamedTextColor.GRAY));
        } else if (state == CtfGame.State.RUNNING || state == CtfGame.State.INTERMISSION) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("ronde "), NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(game.getRoundNumber()), NamedTextColor.WHITE))
                    .append(Component.text(" / " + game.getConfig().getRounds(), NamedTextColor.DARK_GRAY))
                    .build());
            if (state == CtfGame.State.RUNNING && game.getRoundPhase() == CtfGame.RoundPhase.HIDING) {
                lines.add(Component.text(SmallText.of("verstopfase ") + game.formatTime(game.getHidePhaseTimeLeftMs()),
                        NamedTextColor.YELLOW));
            }
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getRoundTimeLeftMs()), NamedTextColor.GOLD))
                    .build());
        } else {
            lines.add(Component.text(SmallText.of("wachtend"), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());
        if (me != null) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("jij: "), NamedTextColor.GRAY))
                    .append(Component.text(me.getSide().getDisplayName(), me.getSide().getTextColor(), TextDecoration.BOLD))
                    .build());

            if (state == CtfGame.State.RUNNING) {
                boolean hiding = me.getSide() == game.getHidingSide();
                lines.add(Component.text()
                        .append(Component.text(SmallText.of("rol: "), NamedTextColor.GRAY))
                        .append(Component.text(
                                hiding ? SmallText.of("verstop") : SmallText.of("zoek"),
                                hiding ? NamedTextColor.AQUA : NamedTextColor.RED,
                                TextDecoration.BOLD))
                        .build());

                if (game.getRoundPhase() == CtfGame.RoundPhase.ACTIVE) {
                    UUID carrier = game.getFlagCarrier();
                    if (carrier != null) {
                        Player cp = Bukkit.getPlayer(carrier);
                        String name = cp != null ? cp.getName() : "?";
                        lines.add(Component.text()
                                .append(Component.text(SmallText.of("vlag bij "), NamedTextColor.GRAY))
                                .append(Component.text(truncate(name, 12), NamedTextColor.GOLD))
                                .build());
                    } else if (game.isFlagPlanted()) {
                        lines.add(Component.text(SmallText.of("vlag: ergens verborgen"), NamedTextColor.YELLOW));
                    } else {
                        lines.add(Component.text(SmallText.of("vlag: nog niet geplant"), NamedTextColor.DARK_GRAY));
                    }
                } else if (hiding) {
                    lines.add(Component.text(SmallText.of("plant de vlag!"), NamedTextColor.GREEN));
                } else {
                    lines.add(Component.text(SmallText.of("wacht op zoekfase"), NamedTextColor.DARK_GRAY));
                }
            }
        }

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("ronde winsten"), NamedTextColor.GOLD, TextDecoration.BOLD));
        for (CtfSide side : CtfSide.values()) {
            int wins = game.getRoundWins().getOrDefault(side, 0);
            lines.add(Component.text()
                    .append(Component.text(side.getDisplayName(), side.getTextColor()))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.valueOf(wins), NamedTextColor.WHITE))
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

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
