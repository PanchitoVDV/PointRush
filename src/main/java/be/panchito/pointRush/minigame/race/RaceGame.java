package be.panchito.pointRush.minigame.race;

import be.panchito.pointRush.PointRush;
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
import nl.mtvehicles.core.infrastructure.vehicle.VehicleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core engine for the Race minigame.
 *
 * <p>Lifecycle:
 * <ul>
 *     <li>{@link State#IDLE} — no event running.</li>
 *     <li>{@link State#STARTING} — grid: geen rijden tot GO. Met
 *         {@link RaceConfig#getStartLightsGantry()} een F1-achtige rood→groen
 *         lampjes-sequentie; zonder gantry een {@value #COUNTDOWN_SECONDS}s
 *         afteltimer.</li>
 *     <li>{@link State#RUNNING} — drivers can race; lap detection is active.</li>
 * </ul>
 *
 * <p>A lap requires touching every configured checkpoint in order; the next
 * pass over the finish line then counts as one completed lap. After
 * {@link RaceConfig#getLaps()} laps the player finishes and is placed in the
 * leaderboard.
 *
 * <p>Players are physically locked to their vehicle: any attempt to dismount
 * is cancelled in {@link RaceListener#onVehicleLeave}. The vehicle is despawned
 * once the player is restored.
 */
public final class RaceGame {

    public enum State { IDLE, STARTING, RUNNING }

    /** Points awarded for placements 1..10 (same scale as the other minigames). */
    public static final int[] PLACEMENT_POINTS = { 100, 80, 60, 50, 40, 30, 25, 20, 15, 10 };

    public static final int COUNTDOWN_SECONDS = 10;
    /** Hard cap so a race never hangs forever (15 minutes). */
    public static final long EVENT_TIMEOUT_TICKS = 15L * 60L * 20L;
    /**
     * Squared distance at which a vehicle is considered to have "touched" a
     * checkpoint / finish line. Cars are fast, so the radius is intentionally
     * generous (~5 blocks) to avoid missing on a quick polling tick.
     */
    public static final double TOUCH_RADIUS_SQ = 25.0;
    /** Polling interval (ticks) for checkpoint detection while running. */
    public static final long POLL_INTERVAL_TICKS = 3L;
    /**
     * Interval (ticks) at which every participant's vehicle is force-topped-up
     * to {@link #FUEL_FULL}. MTVehicles does not expose a "fuel consumption"
     * event we could cancel, so the only reliable way to keep tanks at 100%
     * is to set the value on a tight schedule.
     */
    public static final long FUEL_REFILL_INTERVAL_TICKS = 10L;
    public static final double FUEL_FULL = 100.0;

    /** Client-only raceline (gold dust) + navigatie-hints naar het volgende doel. */
    public static final long RACELINE_INTERVAL_TICKS = 15L;
    private static final double RACELINE_STEP_BLOCKS = 2.5;
    private static final int RACELINE_MAX_POINTS = 40;
    private static final double RACELINE_START_Y_OFFSET = 1.2;
    private static final double RACELINE_TARGET_Y_OFFSET = 1.5;

    /** Formula-one style tower: five red lamps step on, random pause, then all five green before GO. */
    private static final int F1_LIGHT_COUNT = 5;
    private static final long F1_RED_STAGGER_TICKS = 10L;
    private static final long F1_PAUSE_AFTER_RED_MIN_TICKS = 20L;
    private static final long F1_PAUSE_AFTER_RED_EXTRA_TICKS = 51L; // 20–70 total random pause
    private static final long F1_GREEN_HOLD_TICKS = 18L;
    private static final double F1_LIGHT_SPACING = 0.42;
    private static final Particle.DustOptions F1_RED_DUST = new Particle.DustOptions(Color.fromRGB(220, 25, 30), 1.25f);
    private static final Particle.DustOptions F1_GREEN_DUST = new Particle.DustOptions(Color.fromRGB(35, 210, 65), 1.25f);

    private final PointRush plugin;
    private final RaceConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final RaceScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, RacePlayerState> players = new HashMap<>();
    /** Vehicle plates we are currently using; cleared on stop. */
    private final Set<String> spawnedPlates = new HashSet<>();
    private int nextPlacement = 1;
    private long eventStartedAtMs = 0L;
    private long raceStartedAtMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask countdownTask;
    private BukkitTask pollTask;
    private BukkitTask fuelTask;
    private BukkitTask raceLineTask;
    private BukkitTask timeoutTask;

    private BukkitTask f1VisualTask;
    private volatile int f1RedCount;
    private volatile boolean f1GreenPhase;

    public RaceGame(PointRush plugin, RaceConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new RaceScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public RaceConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public RacePlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<RacePlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public long getRaceStartedAtMs() {
        return raceStartedAtMs;
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        raceStartedAtMs = 0L;
        nextPlacement = 1;
        historyRecorded = false;

        // Gather candidate participants (skip creative / spectator).
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan de race (creative/spectator)."));
                continue;
            }
            candidates.add(online);
        }

        int capacity = config.getCapacity();
        if (candidates.isEmpty() || capacity < 1) {
            cleanupAfterStop();
            return false;
        }

        int seated = 0;
        for (Player player : candidates) {
            if (seated >= capacity) {
                player.sendMessage(Messages.warn("Race is vol — geen grid slot meer beschikbaar."));
                continue;
            }
            if (joinPlayer(player, seated)) {
                seated++;
            }
        }

        if (players.isEmpty()) {
            Bukkit.broadcast(Messages.error("Race kon niet starten — geen spelers in een voertuig gekregen."));
            cleanupAfterStop();
            return false;
        }

        scoreboard.start();
        startFuelRefillTask();
        startRaceLineTask();
        broadcastTitle(
                Component.text(SmallText.of("RACE"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of(config.getLaps() + " laps · maak je klaar"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
        Location tower = config.getStartLightsGantry();
        if (tower != null && tower.getWorld() != null) {
            startFormulaOneLightSequence(tower);
        } else {
            startCountdown();
        }
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, EVENT_TIMEOUT_TICKS);
        return true;
    }

    /**
     * Force every participant's tank back to 100% every
     * {@link #FUEL_REFILL_INTERVAL_TICKS} ticks. Finished and quit players are
     * skipped — their vehicle has already been despawned.
     */
    private void startFuelRefillTask() {
        if (fuelTask != null) return;
        fuelTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == State.IDLE) return;
            for (RacePlayerState ps : players.values()) {
                if (ps.isFinished()) continue;
                String plate = ps.getLicensePlate();
                if (plate == null || plate.isBlank()) continue;
                try {
                    VehicleUtils.setFuel(plate, FUEL_FULL);
                } catch (Throwable t) {
                    plugin.getLogger().fine("Kon fuel niet topup voor '" + plate + "': " + t.getMessage());
                }
            }
        }, FUEL_REFILL_INTERVAL_TICKS, FUEL_REFILL_INTERVAL_TICKS);
    }

    private void cancelF1VisualsAndReset() {
        cancelTask(f1VisualTask);
        f1VisualTask = null;
        f1RedCount = 0;
        f1GreenPhase = false;
    }

    /**
     * Formule 1–achtig startpaneel: vijf rode lampjes achter elkaar, korte willekeurige
     * pauze, dan vijf groene lampjes; pas daarna {@link #beginRace()}.
     * De zichtbare rij staat loodrecht op je {@linkplain RaceConfig#getStartLightsGantry
     * yaw} (ga met je gezicht naar het veld en zet het midden met {@code /race setlights}).
     */
    private void startFormulaOneLightSequence(Location gantry) {
        cancelF1VisualsAndReset();
        if (gantry.getWorld() == null) {
            startCountdown();
            return;
        }
        Location[] slots = computeFormulaOneLightSlots(gantry);
        f1RedCount = 0;
        f1GreenPhase = false;

        f1VisualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.STARTING) return;
            World w = gantry.getWorld();
            if (w == null) return;
            if (f1GreenPhase) {
                for (Location s : slots) {
                    w.spawnParticle(Particle.DUST, s, 22, 0.08, 0.08, 0.08, 0, F1_GREEN_DUST);
                }
            } else {
                for (int i = 0; i < f1RedCount; i++) {
                    w.spawnParticle(Particle.DUST, slots[i], 22, 0.08, 0.08, 0.08, 0, F1_RED_DUST);
                }
            }
        }, 0L, 2L);

        long delay = F1_RED_STAGGER_TICKS;
        for (int k = 1; k <= F1_LIGHT_COUNT; k++) {
            int step = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state != State.STARTING) return;
                f1RedCount = step;
                playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 0.55f + 0.08f * step);
            }, delay);
            delay += F1_RED_STAGGER_TICKS;
        }

        long randomPause = F1_PAUSE_AFTER_RED_MIN_TICKS
                + (long) (Math.random() * (double) F1_PAUSE_AFTER_RED_EXTRA_TICKS);
        long greenStartTick = delay + randomPause;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.STARTING) return;
            f1GreenPhase = true;
            broadcastActionBar("alle lampen groen — wacht op GO");
            playSoundAll(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.85f);
        }, greenStartTick);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.STARTING) return;
            beginRace();
        }, greenStartTick + F1_GREEN_HOLD_TICKS);
    }

    private static Location[] computeFormulaOneLightSlots(Location center) {
        Location[] slots = new Location[F1_LIGHT_COUNT];
        if (center.getWorld() == null) {
            return slots;
        }
        double yawRad = Math.toRadians(center.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        Vector forward = new Vector(fx, 0, fz).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        double startOffset = -((F1_LIGHT_COUNT - 1) / 2.0) * F1_LIGHT_SPACING;
        for (int i = 0; i < F1_LIGHT_COUNT; i++) {
            Vector off = right.clone().multiply(startOffset + i * F1_LIGHT_SPACING);
            slots[i] = center.clone().add(off).add(0, 0.05, 0);
        }
        return slots;
    }

    /**
     * Gouden stipjes langs een lijn van de speler naar het volgende doel
     * (checkpoint of finish). Alleen zichtbaar voor die speler via
     * {@link Player#spawnParticle} tijdens {@link State#RUNNING}.
     */
    private void startRaceLineTask() {
        if (raceLineTask != null) return;
        raceLineTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRaceLines,
                RACELINE_INTERVAL_TICKS, RACELINE_INTERVAL_TICKS);
    }

    private void tickRaceLines() {
        if (state != State.RUNNING) return;

        List<Location> cps = config.getCheckpoints();
        int totalCp = cps.size();

        for (Map.Entry<UUID, RacePlayerState> e : players.entrySet()) {
            RacePlayerState ps = e.getValue();
            if (ps.isFinished()) continue;
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) continue;

            Location target = resolveNavigationTarget(ps);
            if (target == null) continue;

            Location from = player.getLocation().clone().add(0, RACELINE_START_Y_OFFSET, 0);
            if (from.getWorld() == null || target.getWorld() == null
                    || from.getWorld() != target.getWorld()) {
                continue;
            }

            Vector delta = target.toVector().subtract(from.toVector());
            double distance = delta.length();
            if (distance < 0.75) {
                continue;
            }
            Vector direction = delta.clone().normalize();

            Particle.DustOptions goldDust = new Particle.DustOptions(
                    Color.fromRGB(255, 215, 0), 1.1f);

            int drawn = 0;
            for (double d = RACELINE_STEP_BLOCKS;
                    d < distance - 0.35 && drawn < RACELINE_MAX_POINTS;
                    d += RACELINE_STEP_BLOCKS) {
                Vector pos = from.toVector().add(direction.clone().multiply(d));
                Location p = new Location(from.getWorld(), pos.getX(), pos.getY(), pos.getZ());
                player.spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, goldDust);
                drawn++;
            }
            player.spawnParticle(Particle.END_ROD, target, 4, 0.2, 0.35, 0.2, 0.015);

            double dx = target.getX() - from.getX();
            double dz = target.getZ() - from.getZ();
            String brg = bearing8Short(dx, dz);
            int meters = Math.max(1, (int) Math.ceil(distance));
            String goal = ps.isFinishLineArmed()
                    ? SmallText.of("finish · laatste strook")
                    : SmallText.of("cp " + (ps.getNextCheckpoint() + 1) + "/" + totalCp);
            Component bar = Component.text()
                    .append(Component.text("→ ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(goal, NamedTextColor.YELLOW))
                    .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(meters + "m", NamedTextColor.WHITE))
                    .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(SmallText.of(brg), NamedTextColor.GRAY))
                    .build();
            player.sendActionBar(bar);
        }
    }

    /** Volgende navigatiepunt: eerstvolgend checkpoint, anders de finishlijn. */
    private Location resolveNavigationTarget(RacePlayerState ps) {
        if (ps.isFinished()) return null;
        List<Location> checkpoints = config.getCheckpoints();
        Location finish = config.getFinish();

        if (ps.isFinishLineArmed()) {
            if (finish == null || finish.getWorld() == null) return null;
            return finish.clone().add(0, RACELINE_TARGET_Y_OFFSET, 0);
        }
        int idx = ps.getNextCheckpoint();
        if (idx >= checkpoints.size()) {
            if (finish == null || finish.getWorld() == null) return null;
            return finish.clone().add(0, RACELINE_TARGET_Y_OFFSET, 0);
        }
        Location cp = checkpoints.get(idx);
        if (cp == null || cp.getWorld() == null) return null;
        return cp.clone().add(0, RACELINE_TARGET_Y_OFFSET, 0);
    }

    /**
     * Acht windrichtingen in het kort (N/O/Z/W), relatief aan de wereld:
     * N = dalende Z (Minecraft-noord).
     */
    private static String bearing8Short(double dx, double dz) {
        if (dx * dx + dz * dz < 1e-6) {
            return "•";
        }
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360.0;
        int oct = (int) ((deg + 22.5) / 45.0) & 7;
        return switch (oct) {
            case 0 -> "n";
            case 1 -> "no";
            case 2 -> "o";
            case 3 -> "zo";
            case 4 -> "z";
            case 5 -> "zw";
            case 6 -> "w";
            default -> "nw";
        };
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Race event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(countdownTask); countdownTask = null;
        cancelTask(pollTask); pollTask = null;
        cancelTask(fuelTask); fuelTask = null;
        cancelTask(raceLineTask); raceLineTask = null;
        cancelF1VisualsAndReset();
        cancelTask(timeoutTask); timeoutTask = null;
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, RacePlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            } else {
                // even when the player is offline, despawn their assigned vehicle
                String plate = entry.getValue().getLicensePlate();
                if (plate != null) safeDespawn(plate);
            }
        }
        players.clear();

        // Defensive: catch any plate we tracked but didn't despawn yet.
        for (String plate : new ArrayList<>(spawnedPlates)) {
            safeDespawn(plate);
        }
        spawnedPlates.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) { }
        }
    }

    /**
     * Snapshots the player's inventory/gamemode/location, teleports them to their
     * grid slot, then spawns their MTVehicles vehicle and seats them inside.
     * Returns true if the player ended up seated.
     */
    private boolean joinPlayer(Player player, int slotIndex) {
        List<Location> grid = config.getGrid();
        List<String> plates = config.getVehiclePlates();
        if (slotIndex >= grid.size() || slotIndex >= plates.size()) {
            return false;
        }
        Location slot = grid.get(slotIndex);
        String plate = plates.get(slotIndex);
        if (slot == null || slot.getWorld() == null || plate == null || plate.isBlank()) {
            return false;
        }

        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }

        RacePlayerState ps = new RacePlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved,
                slotIndex,
                plate
        );
        players.put(player.getUniqueId(), ps);
        MinigameShopHook.applyRaceJoin(plugin, player, ps);

        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.teleport(slot);

        // Make sure no stale copy of this plate exists in the world, then spawn fresh.
        try {
            safeDespawn(plate);
            VehicleUtils.spawnVehicle(plate, slot);
            spawnedPlates.add(plate);
        } catch (Throwable t) {
            plugin.getLogger().warning("Kon vehicle '" + plate + "' niet spawnen voor "
                    + player.getName() + ": " + t.getMessage());
            players.remove(player.getUniqueId());
            return false;
        }

        // Seat the player a tick later — the armor stands need a tick to settle
        // after spawn before MTVehicles can find them for enterVehicle().
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state == State.IDLE) return;
            if (!player.isOnline()) return;
            try {
                VehicleUtils.enterVehicle(plate, player);
            } catch (Throwable t) {
                plugin.getLogger().warning("Kon " + player.getName()
                        + " niet in vehicle '" + plate + "' zetten: " + t.getMessage());
            }
        }, 5L);

        scoreboard.attach(player);
        player.sendMessage(Messages.info("Race start binnenkort — " + config.getLaps()
                + " laps. Je zit vast in je voertuig tot je finisht of leave doet."));
        return true;
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
                    beginRace();
                    cancelTask(countdownTask);
                    countdownTask = null;
                }
            }
        }, 0L, 20L);
    }

    private void beginRace() {
        if (state != State.STARTING) return;
        cancelF1VisualsAndReset();
        state = State.RUNNING;
        raceStartedAtMs = System.currentTimeMillis();
        scoreboard.markRaceStart();
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(Title.title(
                        MinigameText.goTitle(),
                        Component.text(SmallText.of("race naar de finish · " + config.getLaps() + " laps"),
                                NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
                ));
            }
        }
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            RacePlayerState rps = players.get(id);
            if (p != null && rps != null) {
                if (rps.hasShopRaceNitro()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 180, 1, false, false, true));
                }
                if (rps.hasShopRaceCage()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1, false, false, true));
                }
                if (rps.hasShopRacePit()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, false, false, true));
                }
            }
        }
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollProgress, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    /**
     * Periodic checkpoint / finish-line detector. Runs every {@link #POLL_INTERVAL_TICKS}
     * ticks because the {@link org.bukkit.event.player.PlayerMoveEvent} is unreliable
     * for players riding a non-vanilla mount (the MTVehicles armor stand).
     */
    private void pollProgress() {
        if (state != State.RUNNING) return;
        List<Location> checkpoints = config.getCheckpoints();
        Location finish = config.getFinish();
        if (finish == null) return;

        for (Map.Entry<UUID, RacePlayerState> entry : players.entrySet()) {
            RacePlayerState ps = entry.getValue();
            if (ps.isFinished()) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Location loc = player.getLocation();

            // Advance through *every* checkpoint within reach this tick — a fast
            // car can blow past two close-together checkpoints in one poll.
            while (ps.getNextCheckpoint() < checkpoints.size()) {
                Location cp = checkpoints.get(ps.getNextCheckpoint());
                if (cp == null || cp.getWorld() == null || cp.getWorld() != loc.getWorld()) break;
                if (cp.distanceSquared(loc) > TOUCH_RADIUS_SQ) break;
                onCheckpointReached(player, ps);
            }

            // The finish line is armed once all checkpoints in this lap are hit.
            // Hitting it again closes the lap.
            if (ps.isFinishLineArmed()
                    && finish.getWorld() == loc.getWorld()
                    && finish.distanceSquared(loc) <= TOUCH_RADIUS_SQ) {
                onLapCompleted(player, ps);
                if (ps.isFinished()) {
                    // skip further checks for this player this tick
                    continue;
                }
            }
        }
    }

    private void onCheckpointReached(Player player, RacePlayerState ps) {
        int idx = ps.getNextCheckpoint();
        ps.setNextCheckpoint(idx + 1);
        ps.setLastProgressTimeMs(System.currentTimeMillis());

        int total = config.getCheckpoints().size();
        if (ps.getNextCheckpoint() >= total) {
            ps.setFinishLineArmed(true);
            player.sendActionBar(Messages.success("Alle checkpoints · ga naar de finish om lap "
                    + (ps.getCurrentLap() + 1) + " af te ronden"));
        } else {
            player.sendActionBar(Messages.success("Checkpoint " + (idx + 1) + " / " + total));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    private void onLapCompleted(Player player, RacePlayerState ps) {
        int lap = ps.getCurrentLap() + 1;
        ps.setCurrentLap(lap);
        ps.setFinishLineArmed(false);
        ps.setNextCheckpoint(0);
        ps.setLastProgressTimeMs(System.currentTimeMillis());

        if (lap >= config.getLaps()) {
            finishPlayer(player, ps);
            return;
        }

        player.sendActionBar(Messages.success("Lap " + lap + " · nog " + (config.getLaps() - lap)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    private void finishPlayer(Player player, RacePlayerState ps) {
        int placement = nextPlacement++;
        ps.setPlacement(placement);
        ps.setLastProgressTimeMs(System.currentTimeMillis());

        int points = placement <= PLACEMENT_POINTS.length ? PLACEMENT_POINTS[placement - 1] : 0;
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null && points > 0) {
            team.addPoints(points);
            dataManager.save();
        }

        Component msg;
        if (team != null) {
            msg = Messages.PREFIX
                    .append(Component.text(SmallText.of(player.getName()), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" finishte als #" + placement
                            + " (+" + points + " pts) — team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()));
        } else {
            msg = Messages.PREFIX
                    .append(Component.text(SmallText.of(player.getName()), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" finishte als #" + placement
                            + " (geen team, geen punten)"), NamedTextColor.GRAY));
        }
        Bukkit.broadcast(msg);

        player.showTitle(Title.title(
                Component.text(SmallText.of("FINISH"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("#" + placement + " · +" + points + " pts"),
                        NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Kick the player out of the vehicle and despawn it, then send them
        // into spectator next to the finish line.
        kickAndDespawn(player, ps);
        player.setGameMode(GameMode.SPECTATOR);
        if (config.getFinish() != null) {
            try { player.teleport(config.getFinish()); } catch (Exception ignored) { }
        } else if (config.getLobbySpawn() != null) {
            try { player.teleport(config.getLobbySpawn()); } catch (Exception ignored) { }
        }

        boolean anyStillRacing = false;
        for (RacePlayerState s : players.values()) {
            if (!s.isFinished()) {
                anyStillRacing = true;
                break;
            }
        }
        if (!anyStillRacing) {
            stop();
        }
    }

    /**
     * Removes a player from the race (quit or /race leave). When the race is
     * already running, surviving players continue.
     */
    public void removeParticipant(Player player, boolean teleport) {
        RacePlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            stop();
            return;
        }
        // If only finished players remain, end the race.
        boolean anyStillRacing = false;
        for (RacePlayerState s : players.values()) {
            if (!s.isFinished()) {
                anyStillRacing = true;
                break;
            }
        }
        if (state != State.IDLE && !anyStillRacing) {
            stop();
        }
    }

    private void restorePlayer(Player player, RacePlayerState ps, boolean teleport) {
        kickAndDespawn(player, ps);

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
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);

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

    /**
     * Force-eject the player from the vehicle (bypassing our own
     * cancel-VehicleLeaveEvent guard, which only fires for player-initiated
     * dismounts) and despawn the vehicle from the world.
     */
    private void kickAndDespawn(Player player, RacePlayerState ps) {
        try { VehicleUtils.kickOut(player); } catch (Throwable ignored) { }
        String plate = ps.getLicensePlate();
        if (plate != null) {
            safeDespawn(plate);
            spawnedPlates.remove(plate);
        }
    }

    private void safeDespawn(String plate) {
        try {
            VehicleUtils.despawnVehicle(plate);
        } catch (Throwable t) {
            plugin.getLogger().fine("Despawn van '" + plate + "' faalde: " + t.getMessage());
        }
    }

    /**
     * Writes the finished race into {@link EventHistoryManager}. Called from
     * {@link #stop()} before player state is wiped. Finishers come first
     * (placement asc), drop-outs without a placement are omitted.
     */
    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<RacePlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator
                .comparing(RacePlayerState::isFinished).reversed()
                .thenComparingInt(RacePlayerState::getPlacement));

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (RacePlayerState ps : sorted) {
            if (!ps.isFinished()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int score = ps.getPlacement() >= 1 && ps.getPlacement() <= PLACEMENT_POINTS.length
                    ? PLACEMENT_POINTS[ps.getPlacement() - 1]
                    : 0;
            String detail = config.getLaps() + " laps · " + (ps.getCurrentLap()) + " gereden";
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    score,
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "race",
                started,
                System.currentTimeMillis(),
                placements
        ));
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
