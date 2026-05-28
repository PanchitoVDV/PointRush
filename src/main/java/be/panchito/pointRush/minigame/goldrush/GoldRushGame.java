package be.panchito.pointRush.minigame.goldrush;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gold Rush: 1 punt per geminde gouderts, meeste punten in 1 uur wint.
 */
public final class GoldRushGame {

    public enum State { IDLE, RUNNING }

    public static final int WIN_BONUS_POINTS = 100;

    private final PointRush plugin;
    private final GoldRushConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;

    private State state = State.IDLE;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private long nextLeaderboardAtMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public GoldRushGame(PointRush plugin, GoldRushConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
    }

    public State getState() {
        return state;
    }

    public GoldRushConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Map<UUID, Integer> getScores() {
        return Map.copyOf(scores);
    }

    public int getScore(UUID playerId) {
        return scores.getOrDefault(playerId, 0);
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public boolean start() {
        if (state != State.IDLE) return false;

        state = State.RUNNING;
        eventStartedAtMs = System.currentTimeMillis();
        runEndsAtMs = eventStartedAtMs + config.getDurationMs();
        nextLeaderboardAtMs = eventStartedAtMs + config.getLeaderboardIntervalMs();
        historyRecorded = false;
        scores.clear();
        names.clear();

        Title title = Title.title(
                Component.text(SmallText.of("GOLD RUSH"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("mine goud · 1 pt per erts · "
                        + config.getDurationMinutes() + " min"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400))
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        }

        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of("Gold Rush gestart! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("1 punt per gouderts · geen goud-blokken plaatsen · "
                        + config.getDurationMinutes() + " min"), NamedTextColor.GRAY))
                .build()));

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        endEvent(true);
        return true;
    }

    private void endEvent(boolean forced) {
        if (state == State.IDLE) return;

        List<UUID> winnerIds = findWinnerIds();
        announceWinners(winnerIds, forced);
        recordHistory(winnerIds);

        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        scores.clear();
        names.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void tick() {
        if (state != State.RUNNING) return;
        long now = System.currentTimeMillis();

        if (now >= runEndsAtMs) {
            endEvent(false);
            return;
        }

        if (now >= nextLeaderboardAtMs) {
            broadcastLeaderboard(false);
            nextLeaderboardAtMs = now + config.getLeaderboardIntervalMs();
        }
    }

    public void addPoint(Player player) {
        if (state != State.RUNNING) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        UUID id = player.getUniqueId();
        int score = scores.getOrDefault(id, 0) + 1;
        scores.put(id, score);
        names.put(id, player.getName());

        player.sendActionBar(Component.text()
                .append(Component.text(SmallText.of("+1 goud · "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("totaal " + score), NamedTextColor.WHITE))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of("rest " + formatTime(getRunTimeLeftMs())), NamedTextColor.GRAY))
                .build());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    private List<UUID> findWinnerIds() {
        int bestScore = 0;
        for (int score : scores.values()) {
            if (score > bestScore) {
                bestScore = score;
            }
        }
        if (bestScore <= 0) {
            return List.of();
        }
        List<UUID> winners = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == bestScore) {
                winners.add(entry.getKey());
            }
        }
        return winners;
    }

    private void announceWinners(List<UUID> winnerIds, boolean forced) {
        Component line;
        if (winnerIds.isEmpty()) {
            line = Component.text(SmallText.of("geen goud gemined — gelijkspel"), NamedTextColor.GRAY);
        } else if (winnerIds.size() == 1) {
            UUID winnerId = winnerIds.get(0);
            String name = names.getOrDefault(winnerId, winnerId.toString().substring(0, 8));
            int pts = scores.getOrDefault(winnerId, 0);
            Team team = teamManager.getTeamOfPlayer(winnerId);
            if (team != null) {
                team.addPoints(WIN_BONUS_POINTS);
                dataManager.save();
            }
            var builder = Component.text()
                    .append(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" wint met " + pts + " goud"), NamedTextColor.GRAY));
            if (team != null) {
                builder.append(Component.text(SmallText.of(" · team "), NamedTextColor.DARK_GRAY))
                        .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                        .append(Component.text(SmallText.of(" +" + WIN_BONUS_POINTS), NamedTextColor.GREEN));
            }
            line = builder.build();
            broadcastLeaderboard(true);
        } else {
            awardWinBonusToTeams(winnerIds);
            int topScore = scores.getOrDefault(winnerIds.get(0), 0);
            var builder = Component.text()
                    .append(Component.text(SmallText.of("gelijkspel · "), NamedTextColor.GOLD, TextDecoration.BOLD));
            for (int i = 0; i < winnerIds.size(); i++) {
                if (i > 0) {
                    builder.append(Component.text(SmallText.of(" & "), NamedTextColor.DARK_GRAY));
                }
                UUID id = winnerIds.get(i);
                String name = names.getOrDefault(id, id.toString().substring(0, 8));
                Team team = teamManager.getTeamOfPlayer(id);
                builder.append(Component.text(name, team != null ? team.getColor() : NamedTextColor.WHITE, TextDecoration.BOLD));
            }
            builder.append(Component.text(SmallText.of(" · " + topScore + " goud"), NamedTextColor.GRAY));
            line = builder.build();
            broadcastLeaderboard(true);
        }

        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of(forced ? "Gold Rush gestopt · " : "Gold Rush afgelopen · "),
                        NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(line)
                .build()));
    }

    private void awardWinBonusToTeams(List<UUID> winnerIds) {
        java.util.Set<UUID> rewardedTeams = new java.util.HashSet<>();
        for (UUID winnerId : winnerIds) {
            Team team = teamManager.getTeamOfPlayer(winnerId);
            if (team != null && rewardedTeams.add(team.getId())) {
                team.addPoints(WIN_BONUS_POINTS);
            }
        }
        if (!rewardedTeams.isEmpty()) {
            dataManager.save();
        }
    }

    public void broadcastLeaderboard(boolean finalBoard) {
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());

        if (sorted.isEmpty()) {
            if (finalBoard) return;
            Bukkit.broadcast(Messages.info("Gold Rush stand: nog geen punten."));
            return;
        }

        var builder = Component.text()
                .append(Component.text(SmallText.of(finalBoard ? "eindstand: " : "stand: "), NamedTextColor.GOLD));
        int shown = 0;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (shown >= 5) break;
            if (shown > 0) builder.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
            String name = names.getOrDefault(entry.getKey(), "?");
            Team team = teamManager.getTeamOfPlayer(entry.getKey());
            builder.append(Component.text(name, team != null ? team.getColor() : NamedTextColor.WHITE))
                    .append(Component.text(" " + entry.getValue(), NamedTextColor.GOLD));
            shown++;
        }
        if (!finalBoard) {
            builder.append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(SmallText.of("rest " + formatTime(getRunTimeLeftMs())), NamedTextColor.GRAY));
        }
        Bukkit.broadcast(Messages.PREFIX.append(builder.build()));
    }

    private void recordHistory(List<UUID> winnerIds) {
        if (historyManager == null || historyRecorded) return;
        historyRecorded = true;

        java.util.Set<UUID> winnerSet = new java.util.HashSet<>(winnerIds);

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            UUID id = entry.getKey();
            Team team = teamManager.getTeamOfPlayer(id);
            boolean winner = winnerSet.contains(id);
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    id,
                    names.getOrDefault(id, id.toString().substring(0, 8)),
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    winner && team != null ? WIN_BONUS_POINTS : 0,
                    entry.getValue() + " goud erts" + (winner && winnerIds.size() > 1 ? " · gelijkspel" : "")
            ));
        }
        if (placements.isEmpty()) {
            placements.add(new EventHistoryEntry.Placement(
                    1, UUID.randomUUID(), "-", null, null, null, 0, "geen mining"));
        }

        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "goldrush",
                eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis(),
                System.currentTimeMillis(),
                placements
        ));
    }

    public String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
