package be.panchito.pointRush.minigame.tntrun;

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
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel-inspired TNT Run minigame.
 *
 * <p>Mechanics:
 * <ul>
 *     <li>Players spawn on a floor of regular blocks inside the configured arena.</li>
 *     <li>When a participant stands on a block, that block briefly turns into a
 *         "warning" state (red wool) and then vanishes a few ticks later.</li>
 *     <li>Once a participant drops below {@link TntRunConfig#getDeathY()} they
 *         are eliminated and switched to spectator.</li>
 *     <li>The last team (or solo player) alive wins; placements are stored in
 *         elimination order so the global event history can render them.</li>
 * </ul>
 *
 * <p>All blocks we mutate are recorded in {@link #decayedBlocks} so the arena
 * is restored to its original state when the event ends or the plugin disables.
 */
public final class TntRunGame {

    public enum State { IDLE, STARTING, RUNNING }

    /** Points awarded for placements 1..10 (same scale as parkour). */
    public static final int[] PLACEMENT_POINTS = { 100, 80, 60, 50, 40, 30, 25, 20, 15, 10 };

    public static final int COUNTDOWN_SECONDS = 10;
    /** Hard cap so an event never hangs forever (15 min). */
    public static final long EVENT_TIMEOUT_TICKS = 15L * 60L * 20L;

    /** Ticks between stepping on a block and the warning state. */
    public static final long DECAY_WARNING_TICKS = 5L;
    /** Ticks between stepping on a block and it vanishing entirely. */
    public static final long DECAY_REMOVE_TICKS = 10L;
    /**
     * Maximum number of "support" layers we will pop together with the floor
     * tile (e.g. the TNT placed below the sand). Limits how deep one footstep
     * can chew through arbitrary structures.
     */
    public static final int MAX_SUPPORT_DEPTH = 4;

    private final PointRush plugin;
    private final TntRunConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final TntRunScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, TntRunPlayerState> players = new HashMap<>();
    private final Map<UUID, Long> gadgetCooldownUntilMs = new ConcurrentHashMap<>();
    /** Blocks queued for decay this tick; prevents double-scheduling. */
    private final Set<Block> scheduledBlocks = new HashSet<>();
    /** Original block data so the arena can be restored on stop. */
    private final Map<Block, BlockData> decayedBlocks = new LinkedHashMap<>();
    private long eventStartedAtMs = 0L;
    private int nextEliminationPlacement = 0;
    private boolean historyRecorded = false;

    private BukkitTask countdownTask;
    private BukkitTask timeoutTask;

    public TntRunGame(PointRush plugin, TntRunConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new TntRunScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Map<UUID, Long> getGadgetCooldownMap() {
        return gadgetCooldownUntilMs;
    }

    public TntRunConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public TntRunPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<TntRunPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int aliveCount() {
        int n = 0;
        for (TntRunPlayerState ps : players.values()) {
            if (ps.isAlive()) n++;
        }
        return n;
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        nextEliminationPlacement = 0;
        historyRecorded = false;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan TNT Run (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("TNT Run heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        // initial placement count = number of players; first elimination gets bottom rank
        nextEliminationPlacement = players.size();
        scoreboard.start();
        broadcastTitle(
                Component.text(SmallText.of("TNT RUN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("blijf bewegen of val!"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        startCountdown();
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, EVENT_TIMEOUT_TICKS);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("TNT Run event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(countdownTask); countdownTask = null;
        cancelTask(timeoutTask); timeoutTask = null;
        scoreboard.stop();

        restoreFloor();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, TntRunPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        gadgetCooldownUntilMs.clear();
        scheduledBlocks.clear();
        nextEliminationPlacement = 0;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    private void joinPlayer(Player player) {
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }
        TntRunPlayerState ps = new TntRunPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved
        );
        players.put(player.getUniqueId(), ps);
        MinigameShopHook.applyTntRunJoin(plugin, player, ps);

        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        Location spawn = config.getSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        MinigameGadgetItems.giveGadgetRow(plugin, player.getInventory(), MinigameGadgetMode.TNT_RUN);
        scoreboard.attach(player);
        player.sendMessage(Messages.info("TNT Run start binnenkort - maak je klaar!"));
    }

    private void startCountdown() {
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancelTask(countdownTask);
                    countdownTask = null;
                    return;
                }
                if (remaining > 0) {
                    broadcastActionBar("start in " + remaining + "s");
                    if (remaining <= 5) {
                        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    remaining--;
                } else {
                    beginRun();
                    cancelTask(countdownTask);
                    countdownTask = null;
                }
            }
        }, 0L, 20L);
    }

    private void beginRun() {
        state = State.RUNNING;
        int dur = 60000;
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            TntRunPlayerState ps = players.get(id);
            if (p != null && ps != null) {
                if (ps.hasShopRunFeather()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 0, false, false, true));
                }
                if (ps.hasShopRunResist()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 0, false, false, true));
                }
                p.showTitle(Title.title(
                        MinigameText.goTitle(),
                        Component.text(SmallText.of("blijf bewegen"), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
                ));
            }
        }
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
    }

    /**
     * Called from the listener on every move event. Handles the death-plane
     * check first (cheap) and then schedules the block below the player for
     * decay. Re-scheduling the same block is a no-op via {@link #scheduledBlocks}.
     */
    public void handleMove(Player player) {
        if (state != State.RUNNING) return;
        TntRunPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        Location loc = player.getLocation();
        if (loc.getY() < config.getDeathY()) {
            eliminate(player);
            return;
        }

        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();
        if (below.getType().isAir()) return;
        if (!config.contains(below.getLocation())) return;
        if (!scheduledBlocks.add(below)) return;
        if (!decayedBlocks.containsKey(below)) {
            decayedBlocks.put(below, below.getBlockData().clone());
        }
        List<Block> support = collectSupportColumn(below);
        for (Block s : support) {
            if (!decayedBlocks.containsKey(s)) {
                decayedBlocks.put(s, s.getBlockData().clone());
            }
        }
        scheduleDecay(player, below, support);
    }

    /**
     * Returns the stack of solid blocks immediately under {@code top} so the
     * "support" layers (e.g. TNT placed below the sand) can be popped together
     * with the floor tile. Walks downward until air, the world floor, or
     * {@link #MAX_SUPPORT_DEPTH} is reached. Blocks already queued for another
     * column are skipped to avoid double scheduling.
     */
    private List<Block> collectSupportColumn(Block top) {
        List<Block> support = new ArrayList<>();
        int worldMin = top.getWorld().getMinHeight();
        Block cursor = top.getRelative(0, -1, 0);
        int depth = 0;
        while (depth < MAX_SUPPORT_DEPTH
                && !cursor.getType().isAir()
                && cursor.getY() >= worldMin) {
            if (scheduledBlocks.add(cursor)) {
                support.add(cursor);
            }
            cursor = cursor.getRelative(0, -1, 0);
            depth++;
        }
        return support;
    }

    private void scheduleDecay(Player runner, Block topBlock, List<Block> support) {
        TntRunPlayerState ps = players.get(runner.getUniqueId());
        long removeDelay = DECAY_REMOVE_TICKS + (ps != null ? ps.getShopRunDecayBonusTicks() : 0);
        Location particleLoc = topBlock.getLocation().add(0.5, 0.5, 0.5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.RUNNING) return;
            if (topBlock.getType().isAir()) return;
            topBlock.setType(Material.RED_WOOL, false);
            topBlock.getWorld().spawnParticle(Particle.CRIT, particleLoc, 4, 0.2, 0.1, 0.2, 0.0);
            topBlock.getWorld().playSound(particleLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.8f);
        }, DECAY_WARNING_TICKS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.RUNNING) return;
            topBlock.setType(Material.AIR, false);
            topBlock.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 8, 0.3, 0.1, 0.3, 0.01);
            topBlock.getWorld().playSound(particleLoc, Sound.BLOCK_SAND_BREAK, 0.6f, 1.2f);
            for (Block s : support) {
                if (s.getType().isAir()) continue;
                Location sLoc = s.getLocation().add(0.5, 0.5, 0.5);
                s.setType(Material.AIR, false);
                s.getWorld().spawnParticle(Particle.SMOKE, sLoc, 6, 0.3, 0.1, 0.3, 0.01);
            }
        }, removeDelay);
    }

    /** Called from the listener for every participant whose Y dropped under the death plane. */
    public void eliminate(Player player) {
        TntRunPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        ps.setAlive(false);
        ps.setEliminatedAtMs(System.currentTimeMillis());
        ps.setPlacement(nextEliminationPlacement);
        if (nextEliminationPlacement > 0) nextEliminationPlacement--;

        int pts = pointsForPlacement(ps.getPlacement());
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null && pts > 0) {
            team.addPoints(pts);
            dataManager.save();
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.showTitle(Title.title(
                Component.text(SmallText.of("uitgeschakeld"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("#" + ps.getPlacement() + " · +" + pts + " pts"),
                        NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
        if (config.getSpawn() != null) {
            try { player.teleport(config.getSpawn()); } catch (Exception ignored) { }
        }

        Component msg;
        if (team != null) {
            msg = Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" valt als #" + ps.getPlacement()
                            + " (+" + pts + " pts) - team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                    .build();
        } else {
            msg = Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" valt als #" + ps.getPlacement()
                            + " (geen team, geen punten)"), NamedTextColor.GRAY))
                    .build();
        }
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.5f);

        checkWinCondition();
    }

    private int pointsForPlacement(int placement) {
        if (placement < 1) return 0;
        if (placement > PLACEMENT_POINTS.length) return 0;
        return PLACEMENT_POINTS[placement - 1];
    }

    private void checkWinCondition() {
        List<UUID> alive = new ArrayList<>();
        Set<UUID> aliveTeamIds = new HashSet<>();
        int aliveSolo = 0;
        for (TntRunPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            alive.add(ps.getUuid());
            Team t = teamManager.getTeamOfPlayer(ps.getUuid());
            if (t != null) aliveTeamIds.add(t.getId());
            else aliveSolo++;
        }

        boolean teamWin = aliveTeamIds.size() == 1 && aliveSolo == 0;
        boolean soloWin = alive.size() == 1;
        boolean noOne = alive.isEmpty();
        if (!noOne && !teamWin && !soloWin) {
            return;
        }

        // assign placement #1 to every surviving player (ties at the top)
        for (UUID id : alive) {
            TntRunPlayerState ps = players.get(id);
            if (ps == null) continue;
            ps.setPlacement(1);
            int pts = pointsForPlacement(1);
            Team team = teamManager.getTeamOfPlayer(id);
            if (team != null && pts > 0) {
                team.addPoints(pts);
            }
        }
        if (!alive.isEmpty()) {
            dataManager.save();
        }

        announceWinner(alive);
        recordHistoryEntry();
        // give the title a chance to play before tearing down
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("TNT Run event afgelopen."));
            }
        }, 60L);
        state = State.STARTING; // freeze any further moves from triggering decay
    }

    private void announceWinner(List<UUID> alive) {
        Component winnerLine;
        if (alive.isEmpty()) {
            winnerLine = Component.text(SmallText.of("iedereen viel - gelijkspel"),
                    NamedTextColor.GRAY);
        } else if (alive.size() == 1) {
            UUID solo = alive.get(0);
            Player p = Bukkit.getPlayer(solo);
            String name = p != null ? p.getName() : solo.toString().substring(0, 8);
            Team t = teamManager.getTeamOfPlayer(solo);
            var b = Component.text()
                    .append(Component.text(name, t != null ? t.getColor() : NamedTextColor.GOLD,
                            TextDecoration.BOLD));
            if (t != null) {
                b.append(Component.text(SmallText.of("  (team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(t.getName()), t.getColor()))
                        .append(Component.text(")", NamedTextColor.GRAY));
            }
            b.append(Component.text(SmallText.of(" wint!"), NamedTextColor.GRAY));
            winnerLine = b.build();
        } else {
            // team win: every alive player belongs to one team
            Team t = teamManager.getTeamOfPlayer(alive.get(0));
            winnerLine = Component.text()
                    .append(Component.text(SmallText.of("team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(t != null ? t.getName() : "?"),
                            t != null ? t.getColor() : NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" wint! (" + alive.size()
                            + " overlevenden)"), NamedTextColor.GRAY))
                    .build();
        }

        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text(SmallText.of("EVENT VOORBIJ"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    winnerLine
            ));
        }
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
    }

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<TntRunPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            // alive first (placement == 1 for winners), then by placement asc
            int ap = a.getPlacement();
            int bp = b.getPlacement();
            if (ap == 0 && bp == 0) return 0;
            if (ap == 0) return 1;
            if (bp == 0) return -1;
            return Integer.compare(ap, bp);
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (TntRunPlayerState ps : sorted) {
            if (ps.getPlacement() <= 0) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            String detail = ps.isAlive() ? "overleefd" : "uitgeschakeld";
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    pointsForPlacement(ps.getPlacement()),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "tntrun",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    /** Resets every block we mutated back to its original block data. */
    private void restoreFloor() {
        for (Map.Entry<Block, BlockData> e : decayedBlocks.entrySet()) {
            try {
                e.getKey().setBlockData(e.getValue(), false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon block niet restoren: " + ex.getMessage());
            }
        }
        decayedBlocks.clear();
    }

    public void removeParticipant(Player player, boolean teleport) {
        gadgetCooldownUntilMs.remove(player.getUniqueId());
        TntRunPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (state == State.RUNNING) {
            checkWinCondition();
        }
        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, TntRunPlayerState ps, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (Throwable ignored) { }
        }
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }
        player.getInventory().clear();
        if (ps.getSavedInventory() != null) {
            player.getInventory().setContents(ps.getSavedInventory());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

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

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastActionBar(String text) {
        Component c = Component.text(SmallText.of(text), NamedTextColor.GOLD);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(c);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
