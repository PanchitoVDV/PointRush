package be.panchito.pointRush.minigame.treasurehunt;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Survival Treasure Hunt: hints met coords in chat, schatten claimen voor random PvP/healing loot.
 * Geen team points — restock/gear event.
 */
public final class TreasureHuntGame {

    public enum State { IDLE, RUNNING }

    private final PointRush plugin;
    private final TreasureHuntConfig config;
    private final EventHistoryManager historyManager;

    private State state = State.IDLE;
    /** treasureId -> spelers die deze schat al geclaimd hebben. */
    private final Map<String, Set<UUID>> claims = new HashMap<>();
    /** speler -> aantal schatten geclaimd. */
    private final Map<UUID, Integer> finderCounts = new HashMap<>();
    private final Map<String, UUID> markerIds = new HashMap<>();

    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private long nextHintAtMs = 0L;
    private int hintRotationIndex = 0;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public TreasureHuntGame(PointRush plugin, TreasureHuntConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.historyManager = plugin.getEventHistoryManager();
    }

    public State getState() {
        return state;
    }

    public TreasureHuntConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public int getClaimCount(UUID playerId) {
        return finderCounts.getOrDefault(playerId, 0);
    }

    public boolean hasClaimed(UUID playerId, String treasureId) {
        Set<UUID> set = claims.get(treasureId);
        return set != null && set.contains(playerId);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.RUNNING;
        eventStartedAtMs = System.currentTimeMillis();
        runEndsAtMs = eventStartedAtMs + config.getDurationMs();
        nextHintAtMs = eventStartedAtMs;
        hintRotationIndex = 0;
        historyRecorded = false;
        claims.clear();
        finderCounts.clear();

        spawnMarkers();
        broadcastStart();

        for (TreasureLocation treasure : config.getTreasures()) {
            broadcastHint(treasure, true);
        }

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
        recordHistory();
        removeMarkers();

        Component msg = Component.text()
                .append(Component.text(SmallText.of("Treasure Hunt afgelopen! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(forced ? "event gestopt." : "tijd is op."), NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));

        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        claims.clear();
        finderCounts.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void broadcastStart() {
        Title title = Title.title(
                Component.text(SmallText.of("TREASURE HUNT"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("survival restock · volg de hints"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400))
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        }
        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of("Treasure Hunt gestart! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(config.getTreasures().size() + " schatten · "
                        + config.getDurationMinutes() + " min · hints elke "
                        + config.getHintIntervalSeconds() + "s"), NamedTextColor.GRAY))
                .build()));
    }

    private void tick() {
        if (state != State.RUNNING) return;
        long now = System.currentTimeMillis();

        if (now >= runEndsAtMs) {
            endEvent(false);
            return;
        }

        if (now >= nextHintAtMs) {
            broadcastRotatingHint();
            nextHintAtMs = now + config.getHintIntervalMs();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            tryClaimNearby(player);
        }

        pulseMarkers();
    }

    private void pulseMarkers() {
        for (TreasureLocation treasure : config.getTreasures()) {
            Location loc = treasure.getLocation();
            if (loc.getWorld() == null) continue;
            loc.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    loc.clone().add(0, 0.8, 0),
                    1, 0.2, 0.1, 0.2, 0.01
            );
        }
    }

    private void broadcastRotatingHint() {
        List<TreasureLocation> list = config.getTreasures();
        if (list.isEmpty()) return;
        if (hintRotationIndex >= list.size()) hintRotationIndex = 0;
        broadcastHint(list.get(hintRotationIndex), false);
        hintRotationIndex++;
    }

    public void broadcastHint(TreasureLocation treasure, boolean initial) {
        TreasureTier tier = treasure.getTier();
        Component line = Component.text()
                .append(Component.text(SmallText.of(initial ? "schat hint: " : "hint: "), NamedTextColor.GOLD))
                .append(Component.text(SmallText.of(treasure.getHint()), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tier.getDisplayName(), tier.getColor(), TextDecoration.BOLD))
                .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(treasure.formatCoords(), NamedTextColor.AQUA))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(line));
    }

    public void broadcastHintById(String id) {
        TreasureLocation treasure = config.getTreasure(id);
        if (treasure == null) {
            return;
        }
        broadcastHint(treasure, false);
    }

    public void tryClaimNearby(Player player) {
        if (state != State.RUNNING) return;

        Location playerLoc = player.getLocation();
        double radius = config.getClaimRadius();

        for (TreasureLocation treasure : config.getTreasures()) {
            if (hasClaimed(player.getUniqueId(), treasure.getId())) continue;

            Location loc = treasure.getLocation();
            if (loc.getWorld() != playerLoc.getWorld()) continue;
            if (loc.distanceSquared(playerLoc) > radius * radius) continue;

            claimTreasure(player, treasure);
            return;
        }
    }

    public void claimTreasure(Player player, TreasureLocation treasure) {
        if (state != State.RUNNING) return;
        if (hasClaimed(player.getUniqueId(), treasure.getId())) return;

        claims.computeIfAbsent(treasure.getId(), k -> new HashSet<>()).add(player.getUniqueId());
        finderCounts.merge(player.getUniqueId(), 1, Integer::sum);

        List<ItemStack> loot = TreasureLootTable.rollLoot(treasure.getTier());
        for (ItemStack item : loot) {
            giveItem(player, item);
        }

        TreasureTier tier = treasure.getTier();
        player.sendActionBar(Component.text()
                .append(Component.text(SmallText.of(tier.getDisplayName() + " geclaimd! "), tier.getColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(loot.size() + " items"), NamedTextColor.GRAY))
                .build());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);

        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" vond een "), NamedTextColor.GRAY))
                .append(Component.text(tier.getDisplayName(), tier.getColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of("!"), NamedTextColor.GRAY))
                .build()));

        Location loc = treasure.getLocation();
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private void spawnMarkers() {
        removeMarkers();
        for (TreasureLocation treasure : config.getTreasures()) {
            spawnMarker(treasure);
        }
    }

    private void spawnMarker(TreasureLocation treasure) {
        Location loc = treasure.getLocation().clone().add(0, -0.35, 0);
        if (loc.getWorld() == null) return;

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setSmall(true);
            as.getEquipment().setHelmet(new ItemStack(treasure.getTier().getMarkerMaterial()));
        });
        markerIds.put(treasure.getId(), stand.getUniqueId());
    }

    private void removeMarkers() {
        for (String id : new ArrayList<>(markerIds.keySet())) {
            UUID entityId = markerIds.remove(id);
            if (entityId == null) continue;
            for (World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                    break;
                }
            }
        }
        markerIds.clear();
    }

    private void recordHistory() {
        if (historyManager == null || historyRecorded) return;
        historyRecorded = true;

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(finderCounts.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = p != null ? p.getName() : entry.getKey().toString().substring(0, 8);
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    entry.getKey(),
                    name,
                    null,
                    null,
                    null,
                    0,
                    entry.getValue() + " schatten gevonden"
            ));
        }

        if (placements.isEmpty()) {
            placements.add(new EventHistoryEntry.Placement(
                    1, UUID.randomUUID(), "-", null, null, null, 0, "geen claims"));
        }

        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "treasurehunt",
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
