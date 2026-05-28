package be.panchito.pointRush.coins;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Drops Nexo coins aan spawnpunten tijdens actieve minigames; na pickup per plek cooldown (configureerbaar).
 */
public final class NexoCoinSpawner implements Listener {

    private final JavaPlugin plugin;
    private final CoinSpawnConfig coinConfig;
    private final BooleanSupplier minigamesActiveGate;
    private final Random random;

    /** Spawn-slot index → epoch-ms waarna opnieuw spawnen mag. */
    private final ConcurrentHashMap<Integer, Long> pickupCooldownUntilMs = new ConcurrentHashMap<>();

    private volatile boolean nexoItemsReady;

    private BukkitTask ticking;

    public NexoCoinSpawner(JavaPlugin plugin, CoinSpawnConfig coinConfig, BooleanSupplier minigamesActiveGate) {
        this.plugin = plugin;
        this.coinConfig = coinConfig;
        this.minigamesActiveGate = minigamesActiveGate;
        this.random = ThreadLocalRandom.current();
    }

    /** Starts or restarts periodic refill safely. */
    public void startScheduling() {
        if (ticking != null && !ticking.isCancelled()) {
            ticking.cancel();
        }
        long period = coinConfig.getRefillIntervalTicks();
        ticking = new BukkitRunnable() {
            @Override
            public void run() {
                refillEmptySpawns();
            }
        }.runTaskTimer(plugin, period, period);
    }

    public void shutdown() {
        if (ticking != null) {
            ticking.cancel();
            ticking = null;
        }
    }

    @EventHandler
    public void onNexoItemsLoaded(NexoItemsLoadedEvent event) {
        nexoItemsReady = true;
        plugin.getLogger().info("Nexo items geladen - collectible coin spawner actief ("
                + coinConfig.getSpawns().size() + " spawnplekken).");
        refillEmptySpawns();
    }

    public void reloadConfig() {
        startScheduling();
    }

    public boolean isNexoItemsReady() {
        return nexoItemsReady;
    }

    /** Of er nu een PointRush-minigame loopt (parkour/tnttag/tntrun/race ≠ IDLE). */
    public boolean isMinigameGateOpen() {
        return minigamesActiveGate.getAsBoolean();
    }

    /**
     * If Nexo's load event fired before PointRush registered listeners, detect items and activate.
     */
    public void tryAssumeNexoReadyAfterBoot() {
        if (nexoItemsReady || !coinConfig.isEnabled()) {
            return;
        }
        for (String id : coinConfig.getResolvedPoolIds()) {
            if (Boolean.TRUE.equals(NexoCoinFactory.exists(id))) {
                nexoItemsReady = true;
                refillEmptySpawns();
                plugin.getLogger().info("Nexo coins: items waren al geladen bij late koppeling; spawner actief.");
                return;
            }
        }
    }

    public void refillEmptySpawns() {
        if (!coinConfig.isEnabled() || !nexoItemsReady) {
            return;
        }
        if (!minigamesActiveGate.getAsBoolean()) {
            return;
        }
        List<String> pool = coinConfig.getResolvedPoolIds();
        List<Location> spawnList = coinConfig.getSpawns();
        if (pool.isEmpty() || spawnList.isEmpty()) {
            return;
        }
        for (int i = 0; i < spawnList.size(); i++) {
            try {
                trySpawnAt(spawnList.get(i), pool, i);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Coin-spawn mislukt op " + spawnList.get(i), ex);
            }
        }
    }

    /**
     * Na succesvolle pickup: deze spawn-slot mag pas weer een coin tonen na {@link CoinSpawnConfig#getPickupRespawnDelayMillisApprox()}.
     */
    public void registerPickupCooldown(int spawnSlotIndex) {
        if (spawnSlotIndex < 0) {
            return;
        }
        long until = System.currentTimeMillis() + coinConfig.getPickupRespawnDelayMillisApprox();
        pickupCooldownUntilMs.put(spawnSlotIndex, until);
    }

    private void trySpawnAt(Location raw, List<String> pool, int spawnSlotIndex) {
        long until = pickupCooldownUntilMs.getOrDefault(spawnSlotIndex, 0L);
        if (System.currentTimeMillis() < until) {
            return;
        }
        World world = raw.getWorld();
        if (world == null) {
            return;
        }
        Location center = raw.clone().add(0.5D, 0.2D, 0.5D);
        double r = coinConfig.getProximityRadius();
        if (hasStampedCoinNearby(world, center, r)) {
            return;
        }
        List<String> order = new ArrayList<>(pool);
        Collections.shuffle(order, random);
        ItemStack stack = null;
        for (String id : order) {
            ItemStack trial = NexoCoinFactory.stackForId(id);
            if (trial != null && !trial.isEmpty()) {
                stack = trial;
                break;
            }
        }
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CoinItemMarker.stamp(plugin, stack, spawnSlotIndex);
        Item dropped = world.dropItem(center, stack);
        dropped.setPickupDelay(10);
        dropped.setVelocity(new Vector(0D, 0D, 0D));
    }

    private boolean hasStampedCoinNearby(World world, Location center, double radius) {
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Item item) {
                if (CoinItemMarker.isStamped(plugin, item.getItemStack())) {
                    return true;
                }
            }
        }
        return false;
    }
}
