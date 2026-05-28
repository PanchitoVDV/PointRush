package be.panchito.pointRush.minigame.tnttag;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetItems;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetMode;
import be.panchito.pointRush.shop.MinigameShopHook;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core TNT Tag minigame engine.
 *
 * <p>Loosely modelled after Hypixel's TNT Tag:
 * <ul>
 *     <li>At round start a fraction of remaining players gets the TNT (helmet + glow).</li>
 *     <li>Tagged players try to <em>melee-hit</em> another alive player to pass the TNT.</li>
 *     <li>When the round timer expires every player still tagged explodes and is eliminated.</li>
 *     <li>The last team (or solo player) standing wins; team points are awarded along the way.</li>
 * </ul>
 *
 * <p>Lifecycle: {@link State#IDLE} -> {@link State#STARTING} -> {@link State#RUNNING}
 * (loops with {@link State#INTERMISSION} between rounds) -> {@link State#IDLE}.
 */
public final class TntTagGame {

    public enum State { IDLE, STARTING, RUNNING, INTERMISSION }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final int INITIAL_ROUND_DURATION_SECONDS = 30;
    public static final int ROUND_DURATION_DECREASE = 2;
    public static final int MIN_ROUND_DURATION_SECONDS = 10;
    public static final int INTERMISSION_SECONDS = 4;
    /** Hard cap so an event never hangs forever (15 min). */
    public static final long EVENT_TIMEOUT_TICKS = 15L * 60L * 20L;
    public static final double TAG_REACH = 3.0;
    public static final double TAG_REACH_SQ = TAG_REACH * TAG_REACH;
    public static final long TAG_COOLDOWN_MS = 1500L;
    public static final double INITIAL_TAG_FRACTION = 0.33;

    /** Points granted per round survived (per team, per surviving member). */
    public static final int POINTS_PER_ROUND_SURVIVED = 10;
    /** Bonus to the last team alive (split per surviving member). */
    public static final int POINTS_LAST_TEAM_BONUS = 200;
    /** Bonus to a solo survivor (no team). */
    public static final int POINTS_LAST_PLAYER_BONUS = 75;

    private final PointRush plugin;
    private final TntTagConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final TntTagScoreboard scoreboard;
    private final Random random = ThreadLocalRandom.current();

    private State state = State.IDLE;
    private final Map<UUID, TntTagPlayerState> players = new HashMap<>();
    private final Map<UUID, Long> gadgetCooldownUntilMs = new ConcurrentHashMap<>();
    private int roundNumber = 0;
    private long roundStartMs = 0L;
    private long roundDurationMs = 0L;
    private long intermissionEndsMs = 0L;
    private long countdownEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;
    private BukkitTask timeoutTask;

    public TntTagGame(PointRush plugin, TntTagConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new TntTagScoreboard(plugin, this);
    }

    public State getState() { return state; }
    public TntTagConfig getConfig() { return config; }
    public int getRoundNumber() { return roundNumber; }

    public PointRush getPlugin() {
        return plugin;
    }

    public Map<UUID, Long> getGadgetCooldownMap() {
        return gadgetCooldownUntilMs;
    }

    public Collection<TntTagPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public TntTagPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    /** Milliseconds remaining in the current round / countdown / intermission. */
    public long getRoundTimeLeftMs() {
        long now = System.currentTimeMillis();
        return switch (state) {
            case STARTING -> Math.max(0L, countdownEndsMs - now);
            case RUNNING -> Math.max(0L, (roundStartMs + roundDurationMs) - now);
            case INTERMISSION -> Math.max(0L, intermissionEndsMs - now);
            default -> 0L;
        };
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        roundNumber = 0;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        historyRecorded = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan TNT Tag (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("TNT Tag heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        scoreboard.start();
        scoreboard.updateBossBar("countdown", 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("TNT TAG"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("blijf zo lang mogelijk in leven"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, EVENT_TIMEOUT_TICKS);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;

        announceFinalStandings();
        recordHistoryEntry(collectAlive());
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("TNT Tag event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(tickTask); tickTask = null;
        cancelTask(timeoutTask); timeoutTask = null;

        for (Map.Entry<UUID, TntTagPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        gadgetCooldownUntilMs.clear();
        scoreboard.stop();
        roundNumber = 0;
        roundDurationMs = 0L;
        roundStartMs = 0L;
        intermissionEndsMs = 0L;
        countdownEndsMs = 0L;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    /**
     * Persists the current event to history. Ranks survivors first, then the
     * eliminated players ordered by latest-elimination-round descending so
     * "1st" matches the last team/player alive. Guarded by {@link #historyRecorded}
     * because endEventWithWinners() and stop() can both fire for a single event.
     */
    private void recordHistoryEntry(List<UUID> survivors) {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        Set<UUID> winnerSet = new HashSet<>(survivors);

        List<TntTagPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            boolean aw = winnerSet.contains(a.getUuid());
            boolean bw = winnerSet.contains(b.getUuid());
            if (aw != bw) return aw ? -1 : 1;
            int byRound = Integer.compare(b.getEliminatedInRound(), a.getEliminatedInRound());
            if (byRound != 0) return byRound;
            return Integer.compare(b.getRoundsSurvived(), a.getRoundsSurvived());
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (TntTagPlayerState ps : sorted) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            String detail;
            if (winnerSet.contains(ps.getUuid())) {
                detail = "winnaar · " + ps.getRoundsSurvived() + " rondes";
            } else {
                detail = "ronde " + ps.getEliminatedInRound() + " · "
                        + ps.getRoundsSurvived() + " overleefd";
            }
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    ps.getPointsEarned(),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "tnttag",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void joinPlayer(Player player) {
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }
        ItemStack savedHelmet = player.getInventory().getHelmet();
        if (savedHelmet != null) savedHelmet = savedHelmet.clone();

        TntTagPlayerState ps = new TntTagPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved,
                savedHelmet
        );
        players.put(player.getUniqueId(), ps);
        MinigameShopHook.applyTagJoin(plugin, player, ps);

        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);

        Location spawn = config.getSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        MinigameGadgetItems.giveGadgetRow(plugin, player.getInventory(), MinigameGadgetMode.TNT_TAG);
        scoreboard.attach(player);

        player.sendMessage(Messages.info("TNT Tag start binnenkort - maak je klaar!"));
    }

    private void clearPotionEffects(Player player) {
        for (PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(eff.getType());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case STARTING -> tickStarting(now);
            case RUNNING -> tickRunning(now);
            case INTERMISSION -> tickIntermission(now);
            default -> { }
        }
    }

    private void tickStarting(long now) {
        long left = Math.max(0L, countdownEndsMs - now);
        int secondsLeft = (int) ((left + 999) / 1000);

        float progress = Math.min(1f, (float) left / (COUNTDOWN_SECONDS * 1000f));
        scoreboard.updateBossBar("event start in " + secondsLeft + "s", progress, BossBar.Color.YELLOW);

        if (left == 0) {
            beginRound(1);
            return;
        }

        if (secondsLeft <= 5 && now % 1000L < 100L) {
            playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        }

        broadcastActionBar(Component.text()
                .append(Component.text(SmallText.of("start in "), NamedTextColor.GRAY))
                .append(Component.text(secondsLeft + "s", NamedTextColor.GOLD, TextDecoration.BOLD))
                .build());

        Location spawn = config.getSpawn();
        if (spawn != null) {
            for (UUID id : players.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.getLocation().distanceSquared(spawn) > 100.0) {
                    p.teleport(spawn);
                }
            }
        }
    }

    private void tickRunning(long now) {
        long left = Math.max(0L, (roundStartMs + roundDurationMs) - now);
        int secondsLeft = (int) ((left + 999) / 1000);
        float progress = Math.min(1f, roundDurationMs == 0 ? 0f : (float) left / (float) roundDurationMs);
        scoreboard.updateBossBar("ronde " + roundNumber + "  ·  " + formatTime(left),
                progress, BossBar.Color.RED);

        spawnTaggedParticles();
        broadcastActionBars(secondsLeft);

        if (now % 1000L < 50L && secondsLeft <= 5 && secondsLeft > 0) {
            playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 2.0f);
        }
        if (now % 1000L < 50L && secondsLeft > 5) {
            playSoundForTagged(Sound.ENTITY_CREEPER_PRIMED, 0.4f, 1.0f);
        }

        if (left == 0) {
            endRound();
        }
    }

    private void tickIntermission(long now) {
        long left = Math.max(0L, intermissionEndsMs - now);
        int secondsLeft = (int) ((left + 999) / 1000);
        float progress = Math.min(1f, (float) left / (INTERMISSION_SECONDS * 1000f));
        scoreboard.updateBossBar("ronde " + (roundNumber + 1) + " in " + secondsLeft + "s",
                progress, BossBar.Color.BLUE);

        broadcastActionBar(Component.text()
                .append(Component.text(SmallText.of("ronde " + (roundNumber + 1) + " in "), NamedTextColor.GRAY))
                .append(Component.text(secondsLeft + "s", NamedTextColor.AQUA, TextDecoration.BOLD))
                .build());

        if (left == 0) {
            beginRound(roundNumber + 1);
        }
    }

    private void beginRound(int round) {
        this.roundNumber = round;
        this.roundDurationMs = Math.max(MIN_ROUND_DURATION_SECONDS,
                INITIAL_ROUND_DURATION_SECONDS - (round - 1) * ROUND_DURATION_DECREASE) * 1000L;
        this.roundStartMs = System.currentTimeMillis();
        this.state = State.RUNNING;

        clearTagsAndCooldowns();

        List<UUID> alive = collectAlive();
        if (alive.size() <= 1) {
            // edge case: started a round but no one to play against
            endEventWithWinners(alive);
            return;
        }

        int tagCount = Math.max(1, (int) Math.ceil(alive.size() * INITIAL_TAG_FRACTION));
        if (tagCount >= alive.size()) tagCount = alive.size() - 1;

        Collections.shuffle(alive, random);
        Set<UUID> chosen = new HashSet<>(alive.subList(0, tagCount));
        for (UUID id : chosen) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                applyTagged(p, true);
            }
        }

        broadcastTitle(
                Component.text(SmallText.of("ronde " + round), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of(tagCount + " spelers zijn gemarkeerd"), NamedTextColor.RED)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE, Color.RED, Color.ORANGE);

        for (UUID id : chosen) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text(SmallText.of("TNT!"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("pass de tnt voor de tijd op is"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(300))
            ));
        }

        for (UUID id : collectAlive()) {
            Player p = Bukkit.getPlayer(id);
            TntTagPlayerState ps = players.get(id);
            if (p == null || ps == null) {
                continue;
            }
            if (ps.hasShopTagIronRush()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 220, 0, false, false, true));
            }
            if (ps.hasShopTagSecondWind()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, false, true));
            }
        }

        for (UUID gid : collectAlive()) {
            Player gp = Bukkit.getPlayer(gid);
            if (gp != null) {
                MinigameGadgetItems.refillTagGadgets(plugin, gp.getInventory());
            }
        }
    }

    private void endRound() {
        // every player still tagged explodes
        List<UUID> exploded = new ArrayList<>();
        for (TntTagPlayerState ps : players.values()) {
            if (ps.isAlive() && ps.isTagged()) {
                exploded.add(ps.getUuid());
            }
        }
        for (UUID id : exploded) {
            eliminate(id);
        }

        // remaining alive players survived the round - award points
        List<UUID> survivors = collectAlive();
        for (UUID id : survivors) {
            TntTagPlayerState ps = players.get(id);
            if (ps == null) continue;
            ps.incrementRoundsSurvived();
            Team team = teamManager.getTeamOfPlayer(id);
            if (team != null && POINTS_PER_ROUND_SURVIVED > 0) {
                team.addPoints(POINTS_PER_ROUND_SURVIVED);
                ps.addPointsEarned(POINTS_PER_ROUND_SURVIVED);
            }
        }
        if (!survivors.isEmpty()) {
            dataManager.save();
        }

        // win condition: 0 or 1 team left alive (or <=1 player left)
        Set<UUID> aliveTeamIds = new HashSet<>();
        int aliveSolo = 0;
        for (UUID id : survivors) {
            Team t = teamManager.getTeamOfPlayer(id);
            if (t != null) aliveTeamIds.add(t.getId());
            else aliveSolo++;
        }
        boolean teamWin = aliveTeamIds.size() <= 1 && aliveSolo == 0 && !aliveTeamIds.isEmpty();
        boolean soloWin = survivors.size() == 1;
        boolean noOne = survivors.isEmpty();

        if (noOne || teamWin || soloWin) {
            endEventWithWinners(survivors);
            return;
        }

        // intermission then next round
        state = State.INTERMISSION;
        intermissionEndsMs = System.currentTimeMillis() + INTERMISSION_SECONDS * 1000L;
        broadcastTitle(
                Component.text(SmallText.of("ronde " + roundNumber + " voorbij"),
                        NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of(survivors.size() + " spelers nog in leven"),
                        NamedTextColor.GRAY)
        );
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
    }

    private void endEventWithWinners(List<UUID> survivors) {
        // figure out winning team / player
        Map<UUID, Integer> teamCount = new HashMap<>();
        List<UUID> soloWinners = new ArrayList<>();
        for (UUID id : survivors) {
            Team t = teamManager.getTeamOfPlayer(id);
            if (t != null) {
                teamCount.merge(t.getId(), 1, Integer::sum);
            } else {
                soloWinners.add(id);
            }
        }

        Component winnerLine;
        if (!teamCount.isEmpty() && soloWinners.isEmpty() && teamCount.size() == 1) {
            UUID winningTeamId = teamCount.keySet().iterator().next();
            Team t = teamManager.getTeam(winningTeamId);
            int memberCount = teamCount.get(winningTeamId);
            int bonus = POINTS_LAST_TEAM_BONUS;
            if (t != null) {
                t.addPoints(bonus);
                dataManager.save();
                int share = memberCount > 0 ? bonus / memberCount : bonus;
                for (UUID id : survivors) {
                    TntTagPlayerState ps = players.get(id);
                    if (ps != null) ps.addPointsEarned(share);
                }
                winnerLine = Component.text()
                        .append(Component.text(SmallText.of("team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(t.getName()), t.getColor(), TextDecoration.BOLD))
                        .append(Component.text(SmallText.of(" wint! (+" + bonus + " punten, "
                                + memberCount + " overlevenden)"), NamedTextColor.GRAY))
                        .build();
            } else {
                winnerLine = Component.text(SmallText.of("team winnaar"), NamedTextColor.GOLD);
            }
            launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE,
                    Color.fromRGB(255, 215, 0), Color.fromRGB(255, 80, 80));
            launchFireworkAtSpawn(FireworkEffect.Type.STAR,
                    Color.fromRGB(80, 255, 80), Color.fromRGB(255, 215, 0));
        } else if (soloWinners.size() == 1 && teamCount.isEmpty()) {
            UUID solo = soloWinners.get(0);
            Player p = Bukkit.getPlayer(solo);
            String name = p != null ? p.getName() : solo.toString().substring(0, 8);
            winnerLine = Component.text()
                    .append(Component.text(SmallText.of(name), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" wint solo! (+" + POINTS_LAST_PLAYER_BONUS
                            + " punten naar diens team)"), NamedTextColor.GRAY))
                    .build();
            Team t = teamManager.getTeamOfPlayer(solo);
            if (t != null) {
                t.addPoints(POINTS_LAST_PLAYER_BONUS);
                dataManager.save();
            }
            TntTagPlayerState soloPs = players.get(solo);
            if (soloPs != null) soloPs.addPointsEarned(POINTS_LAST_PLAYER_BONUS);
            launchFireworkAtSpawn(FireworkEffect.Type.STAR, Color.YELLOW, Color.WHITE);
        } else if (survivors.isEmpty()) {
            winnerLine = Component.text(SmallText.of("geen overlevenden - gelijkspel"),
                    NamedTextColor.GRAY);
        } else {
            // mixed solo + team scenario: just announce, no big bonus
            winnerLine = Component.text(SmallText.of(survivors.size()
                    + " overlevenden - event afgerond"), NamedTextColor.GOLD);
        }

        broadcastTitle(
                Component.text(SmallText.of("EVENT VOORBIJ"), NamedTextColor.GOLD, TextDecoration.BOLD),
                winnerLine
        );
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);

        recordHistoryEntry(survivors);

        // stop after a short delay so the title can play
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("TNT Tag event afgelopen."));
            }
        }, 60L);
        state = State.INTERMISSION; // freeze further round triggers
        intermissionEndsMs = Long.MAX_VALUE;
    }

    private void announceFinalStandings() {
        // called from stop() (manual stop) - just emit a summary
        Component header = Component.text(SmallText.of("eind stand tnt tag"),
                NamedTextColor.GOLD, TextDecoration.BOLD);
        Bukkit.broadcast(Messages.PREFIX.append(header));
        List<TntTagPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            if (a.isAlive() != b.isAlive()) return a.isAlive() ? -1 : 1;
            return Integer.compare(b.getRoundsSurvived(), a.getRoundsSurvived());
        });
        int shown = 0;
        for (TntTagPlayerState ps : sorted) {
            if (shown >= 5) break;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team t = teamManager.getTeamOfPlayer(ps.getUuid());
            Component nameC = Component.text(name, t != null ? t.getColor() : NamedTextColor.WHITE);
            Component line = Component.text()
                    .append(Component.text((shown + 1) + ". ", NamedTextColor.GRAY))
                    .append(nameC)
                    .append(Component.text(SmallText.of("  rondes overleefd "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(String.valueOf(ps.getRoundsSurvived()), NamedTextColor.GOLD))
                    .build();
            Bukkit.broadcast(Messages.PREFIX.append(line));
            shown++;
        }
    }

    private void clearTagsAndCooldowns() {
        for (TntTagPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null && ps.isTagged()) {
                applyTagged(p, false);
            }
            ps.setTagCooldownExpiresMs(0L);
        }
    }

    private List<UUID> collectAlive() {
        List<UUID> alive = new ArrayList<>();
        for (TntTagPlayerState ps : players.values()) {
            if (ps.isAlive()) alive.add(ps.getUuid());
        }
        return alive;
    }

    /**
     * Apply or remove the visual + mechanical "tagged" state.
     */
    private void applyTagged(Player player, boolean tagged) {
        TntTagPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;
        ps.setTagged(tagged);

        if (tagged) {
            ItemStack tntHelmet = new ItemStack(Material.TNT);
            player.getInventory().setHelmet(tntHelmet);
            player.setGlowing(true);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, 1, false, false, false));
        } else {
            player.getInventory().setHelmet(null);
            player.setGlowing(false);
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    /**
     * Called by the listener when a tagged participant melee-hits a non-tagged participant.
     * Validates the pass and transfers the tag.
     */
    public boolean tryPassTag(Player attacker, Player victim) {
        TntTagPlayerState aState = players.get(attacker.getUniqueId());
        TntTagPlayerState vState = players.get(victim.getUniqueId());
        if (aState == null || vState == null) return false;
        if (state != State.RUNNING) return false;
        if (!aState.isAlive() || !vState.isAlive()) return false;
        if (!aState.isTagged() || vState.isTagged()) return false;

        long now = System.currentTimeMillis();
        if (now < aState.getTagCooldownExpiresMs() || now < vState.getTagCooldownExpiresMs()) {
            return false;
        }
        if (attacker.getLocation().distanceSquared(victim.getLocation()) > TAG_REACH_SQ) {
            return false;
        }

        if (vState.getShopTagGhostPasses() > 0) {
            vState.setShopTagGhostPasses(vState.getShopTagGhostPasses() - 1);
            victim.sendMessage(Messages.info("Rush-voordeel: deze tag ketste af - ren!"));
            attacker.sendMessage(Messages.warn("Je tag werd geblokkeerd (Rush-winkel)."));
            Location vl = victim.getLocation();
            vl.getWorld().spawnParticle(Particle.WITCH, vl, 12, 0.4, 0.8, 0.4, 0.05);
            return false;
        }

        applyTagged(attacker, false);
        applyTagged(victim, true);
        aState.incrementPasses();

        long cd = now + TAG_COOLDOWN_MS;
        aState.setTagCooldownExpiresMs(cd);
        vState.setTagCooldownExpiresMs(cd);

        Location vl = victim.getLocation();
        vl.getWorld().spawnParticle(Particle.EXPLOSION, vl, 1, 0, 0, 0, 0);
        vl.getWorld().playSound(vl, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);

        attacker.sendActionBar(Component.text()
                .append(Component.text(SmallText.of("doorgegeven aan "), NamedTextColor.GREEN))
                .append(Component.text(victim.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .build());
        victim.showTitle(Title.title(
                Component.text(SmallText.of("GETAGD!"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("pass snel weer door"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
        ));
        return true;
    }

    private void eliminate(UUID id) {
        TntTagPlayerState ps = players.get(id);
        if (ps == null || !ps.isAlive()) return;
        ps.setAlive(false);
        ps.setEliminatedInRound(roundNumber);

        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            Location loc = p.getLocation();
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

            applyTagged(p, false);
            p.setGameMode(GameMode.SPECTATOR);
            clearPotionEffects(p);

            p.showTitle(Title.title(
                    Component.text(SmallText.of("BOOM!"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("je bent uitgeschakeld - kijk lekker mee"),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
            ));

            Team team = teamManager.getTeamOfPlayer(id);
            Component announce;
            if (team != null) {
                announce = Component.text()
                        .append(Component.text(p.getName(), NamedTextColor.WHITE))
                        .append(Component.text(SmallText.of(" ontplofte (team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                        .append(Component.text(")", NamedTextColor.GRAY))
                        .build();
            } else {
                announce = Component.text()
                        .append(Component.text(p.getName(), NamedTextColor.WHITE))
                        .append(Component.text(SmallText.of(" ontplofte"), NamedTextColor.GRAY))
                        .build();
            }
            Bukkit.broadcast(Messages.PREFIX.append(announce));
        }
    }

    /** Called by /tnttag leave or on player quit. */
    public void removeParticipant(Player player, boolean teleport) {
        gadgetCooldownUntilMs.remove(player.getUniqueId());
        TntTagPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (state != State.IDLE && state != State.STARTING) {
            // check if event should end early
            List<UUID> survivors = collectAlive();
            Set<UUID> aliveTeamIds = new HashSet<>();
            int aliveSolo = 0;
            for (UUID id : survivors) {
                Team t = teamManager.getTeamOfPlayer(id);
                if (t != null) aliveTeamIds.add(t.getId());
                else aliveSolo++;
            }
            boolean teamWin = aliveTeamIds.size() == 1 && aliveSolo == 0;
            boolean soloWin = survivors.size() == 1;
            if (survivors.size() <= 1 || teamWin || soloWin) {
                endEventWithWinners(survivors);
            }
        }
        if (players.isEmpty() && state != State.IDLE) {
            cleanupAfterStop();
        }
    }

    private void restorePlayer(Player player, TntTagPlayerState ps, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (Throwable ignored) { }
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, other);
        }
        player.setGlowing(false);
        clearPotionEffects(player);

        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }

        player.getInventory().clear();
        if (ps.getSavedInventory() != null) {
            player.getInventory().setContents(ps.getSavedInventory());
        }
        player.getInventory().setHelmet(ps.getSavedHelmet());

        player.setFireTicks(0);
        player.setFallDistance(0f);

        if (!teleport || ps.getSavedLocation() == null || ps.getSavedLocation().getWorld() == null) {
            return;
        }
        try {
            player.teleport(ps.getSavedLocation());
            player.setFallDistance(0f);
            player.sendActionBar(Messages.info("Terug naar je startlocatie."));
            player.playSound(ps.getSavedLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Kon speler " + player.getName()
                    + " niet terugteleporteren: " + ex.getMessage());
        }
    }

    private void spawnTaggedParticles() {
        for (TntTagPlayerState ps : players.values()) {
            if (!ps.isAlive() || !ps.isTagged()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            Location feet = p.getLocation();
            feet.getWorld().spawnParticle(Particle.SMOKE, feet.clone().add(0, 0.2, 0),
                    4, 0.3, 0.1, 0.3, 0.01);
            feet.getWorld().spawnParticle(Particle.FLAME, feet.clone().add(0, 2.1, 0),
                    2, 0.2, 0.1, 0.2, 0.0);
        }
    }

    private void broadcastActionBars(int secondsLeft) {
        for (TntTagPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            Component msg;
            if (!ps.isAlive()) {
                msg = Component.text(SmallText.of("je kijkt mee - wacht tot het event eindigt"),
                        NamedTextColor.GRAY);
            } else if (ps.isTagged()) {
                msg = Component.text()
                        .append(Component.text(SmallText.of("TNT! "), NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(SmallText.of("pak iemand voor "), NamedTextColor.GRAY))
                        .append(Component.text(secondsLeft + "s", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .build();
            } else {
                msg = Component.text()
                        .append(Component.text(SmallText.of("safe "), NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text(SmallText.of("· "), NamedTextColor.DARK_GRAY))
                        .append(Component.text(SmallText.of("blijf weg van de gemarkeerden ("), NamedTextColor.GRAY))
                        .append(Component.text(secondsLeft + "s", NamedTextColor.WHITE))
                        .append(Component.text(")", NamedTextColor.GRAY))
                        .build();
            }
            p.sendActionBar(msg);
        }
    }

    private void launchFireworkAtSpawn(FireworkEffect.Type type, Color a, Color b) {
        Location loc = config.getSpawn();
        if (loc == null || loc.getWorld() == null) return;
        Firework firework = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(type)
                .withColor(a)
                .withFade(b)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastActionBar(Component component) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(component);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private void playSoundForTagged(Sound sound, float volume, float pitch) {
        for (TntTagPlayerState ps : players.values()) {
            if (!ps.isAlive() || !ps.isTagged()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
