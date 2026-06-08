package be.panchito.pointRush.minigame.holdthecrown;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.ctf.CtfSide;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player sidebar + shared boss bar voor Hold the Crown.
 */
public final class HoldTheCrownScoreboard {

    private static final String OBJ_KEY = "pr_htc";

    private static final String[] LINE_IDS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73",
            "\u00A74", "\u00A75", "\u00A76", "\u00A77",
            "\u00A78", "\u00A79", "\u00A7a", "\u00A7b",
            "\u00A7c", "\u00A7d", "\u00A7e", "\u00A7f"
    };

    private final PointRush plugin;
    private final HoldTheCrownGame game;

    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final BossBar bossBar;
    private BukkitTask updateTask;

    public HoldTheCrownScoreboard(PointRush plugin, HoldTheCrownGame game) {
        this.plugin = plugin;
        this.game = game;
        this.bossBar = BossBar.bossBar(
                Component.text(SmallText.of("wachten..."), NamedTextColor.GRAY),
                1.0f,
                BossBar.Color.YELLOW,
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
        HoldTheCrownGame.State state = game.getState();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            render(p, e.getValue(), state, now);
        }
    }

    private void render(Player viewer, Scoreboard board, HoldTheCrownGame.State state, long now) {
        Objective obj = board.getObjective(OBJ_KEY);
        if (obj == null) return;

        HoldTheCrownPlayerState me = game.getPlayerState(viewer.getUniqueId());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("hold the crown"), NamedTextColor.GOLD, TextDecoration.BOLD));

        if (state == HoldTheCrownGame.State.STARTING) {
            lines.add(Component.text(SmallText.of("countdown ") + game.formatTime(game.getCountdownTimeLeftMs()),
                    NamedTextColor.GRAY));
        } else if (state == HoldTheCrownGame.State.RUNNING) {
            lines.add(Component.text()
                    .append(Component.text(SmallText.of("rest "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.GOLD))
                    .build());

            UUID carrierId = game.getCrownCarrier();
            if (carrierId != null) {
                Player carrier = Bukkit.getPlayer(carrierId);
                HoldTheCrownPlayerState cps = game.getPlayerState(carrierId);
                CtfSide side = cps != null ? cps.getSide() : CtfSide.RED;
                String name = carrier != null ? truncate(carrier.getName(), 10) : "?";
                long interval = game.getConfig().getPointIntervalMs();
                long ms = game.getCrownProgressMs();
                lines.add(Component.text()
                        .append(Component.text(SmallText.of("kroon "), NamedTextColor.YELLOW))
                        .append(Component.text(name, side.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(game.formatTime(Math.max(0L, interval - ms)), NamedTextColor.WHITE))
                        .build());
            } else if (game.isCrownAtCenter()) {
                lines.add(Component.text(SmallText.of("kroon in het midden!"), NamedTextColor.YELLOW));
            } else {
                lines.add(Component.text(SmallText.of("kroon op de grond"), NamedTextColor.RED));
            }
        } else {
            lines.add(Component.text(SmallText.of("wachtend"), NamedTextColor.GRAY));
        }

        lines.add(Component.empty());
        lines.add(statusLine(me, now));

        lines.add(Component.empty());
        lines.add(Component.text()
                .append(Component.text(SmallText.of("R "), NamedTextColor.RED))
                .append(Component.text(String.valueOf(game.getSidePoints().get(CtfSide.RED)), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of("  ·  B "), NamedTextColor.BLUE))
                .append(Component.text(String.valueOf(game.getSidePoints().get(CtfSide.BLUE)), NamedTextColor.WHITE))
                .build());

        lines.add(Component.empty());
        lines.add(Component.text(SmallText.of("kroon tijd"), NamedTextColor.GOLD, TextDecoration.BOLD));

        List<HoldTheCrownPlayerState> top = topHolders(now, 4);
        if (top.isEmpty()) {
            lines.add(Component.text(SmallText.of("(nog niemand)"), NamedTextColor.DARK_GRAY));
        } else {
            for (HoldTheCrownPlayerState ps : top) {
                Player p = Bukkit.getPlayer(ps.getUuid());
                String name = p != null ? truncate(p.getName(), 10) : "?";
                lines.add(Component.text()
                        .append(Component.text(name, ps.getSide().getTextColor()))
                        .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(game.formatTime(ps.totalHoldTimeMs(now)), NamedTextColor.GRAY))
                        .build());
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

    private List<HoldTheCrownPlayerState> topHolders(long now, int limit) {
        List<HoldTheCrownPlayerState> list = new ArrayList<>(game.getAllPlayerStates());
        list.sort(Comparator.comparingLong((HoldTheCrownPlayerState ps) -> ps.totalHoldTimeMs(now)).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    private Component statusLine(HoldTheCrownPlayerState me, long now) {
        if (me == null) {
            return Component.text(SmallText.of("status: spectator"), NamedTextColor.GRAY);
        }
        if (!me.isAlive()) {
            long left = Math.max(0L, me.getRespawnAtMs() - now);
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("spectator "), NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text("(" + game.formatTime(left) + ")", NamedTextColor.GOLD))
                    .build();
        }
        if (game.getCrownCarrier() != null && game.getCrownCarrier().equals(me.getUuid())) {
            return Component.text()
                    .append(Component.text(SmallText.of("status: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of("kroondrager!"), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .build();
        }
        return Component.text()
                .append(Component.text(SmallText.of("team: "), NamedTextColor.GRAY))
                .append(Component.text(me.getSide().getDisplayName(), me.getSide().getTextColor(), TextDecoration.BOLD))
                .build();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
