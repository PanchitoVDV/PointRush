package be.panchito.pointRush.ui;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Beheert de live Ultimate UI HUD ({@code stats_display}) tijdens minigames.
 *
 * <p>De inhoud (tijd, score, spelers, ranking, event en objective) wordt via
 * PlaceholderAPI ({@code %pointrush_hud_...%}) ingevuld — exact zoals het profielmenu.
 * Deze service opent/sluit de page en herlaadt hem periodiek zodat de waardes live blijven.
 */
public final class MinigameHudService {

    private final PointRush plugin;
    private final UltimateUiBridge bridge;

    private String pageId = "stats_display";
    private boolean enabled = true;
    private long refreshTicks = 300L;

    private BukkitTask tickTask;
    private final Set<UUID> shown = new HashSet<>();

    private volatile long startMs;
    private volatile String activeId;
    private volatile String eventMessage = "";
    private volatile int playerCount;

    public MinigameHudService(PointRush plugin, UltimateUiBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void init() {
        reloadSettings();
    }

    public void reloadSettings() {
        enabled = plugin.getUnifiedSettings().yaml().getBoolean("minigame-hud.enabled", true);
        pageId = plugin.getUnifiedSettings().yaml().getString("minigame-hud.page", "stats_display");
        refreshTicks = plugin.getUnifiedSettings().yaml().getLong("minigame-hud.refresh-ticks", 300L);
    }

    /** Aangeroepen wanneer een minigame succesvol start. */
    public void onMinigameStarted(String minigameId) {
        if (!enabled) {
            return;
        }
        this.activeId = minigameId;
        this.startMs = System.currentTimeMillis();
        this.eventMessage = MinigameRegistry.displayName(minigameId) + " gestart!";
        ensureTicking();
    }

    /** Toon een tijdelijk event-bericht in de HUD (zonder kleurcodes). */
    public void setEventMessage(String message) {
        this.eventMessage = message == null ? "" : message;
    }

    // ---- Placeholder-bronnen (gelezen door PointRushExpansion) ----

    public boolean isActive() {
        return activeId != null;
    }

    public String formattedTimer() {
        if (startMs == 0L) {
            return "00:00";
        }
        return formatTime(System.currentTimeMillis() - startMs);
    }

    public String activeDisplayName() {
        return activeId == null ? "PointRush" : MinigameRegistry.displayName(activeId);
    }

    public String eventMessage() {
        return eventMessage;
    }

    public int playerCount() {
        return playerCount;
    }

    // ---- Lifecycle ----

    private void ensureTicking() {
        if (tickTask != null) {
            return;
        }
        long period = refreshTicks > 0 ? refreshTicks : 20L;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, period);
    }

    private void tick() {
        String currentActive = MinigameRegistry.activeMinigameId(plugin);
        if (currentActive == null) {
            closeAll();
            stopTicking();
            return;
        }
        if (activeId == null || !activeId.equals(currentActive)) {
            activeId = currentActive;
            if (startMs == 0L) {
                startMs = System.currentTimeMillis();
            }
        }

        List<Player> viewers = collectViewers();
        playerCount = viewers.size();

        Set<UUID> stillVisible = new HashSet<>();
        for (Player viewer : viewers) {
            UUID id = viewer.getUniqueId();
            stillVisible.add(id);
            boolean isNew = shown.add(id);
            // Open de page bij nieuwe kijkers en herlaad hem bij elke tick (refresh placeholders).
            if (isNew || refreshTicks > 0) {
                bridge.openPersistentHud(viewer, pageId);
            }
        }

        for (UUID id : new ArrayList<>(shown)) {
            if (!stillVisible.contains(id)) {
                Player gone = Bukkit.getPlayer(id);
                if (gone != null) {
                    bridge.close(gone);
                }
                shown.remove(id);
            }
        }
    }

    private List<Player> collectViewers() {
        List<Player> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private void closeAll() {
        for (UUID id : new ArrayList<>(shown)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                bridge.close(player);
            }
        }
        shown.clear();
        activeId = null;
        startMs = 0L;
        eventMessage = "";
        playerCount = 0;
    }

    private void stopTicking() {
        if (tickTask != null) {
            try {
                tickTask.cancel();
            } catch (IllegalStateException ignored) {
            }
            tickTask = null;
        }
    }

    public void shutdown() {
        stopTicking();
        closeAll();
    }

    private static String formatTime(long ms) {
        if (ms < 0L) {
            ms = 0L;
        }
        long totalSeconds = ms / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
