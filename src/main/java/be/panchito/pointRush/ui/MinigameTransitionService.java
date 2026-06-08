package be.panchito.pointRush.ui;

import be.panchito.pointRush.PointRush;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.Locale;

/**
 * Speelt de Ultimate UI {@code transition}-overlay af wanneer een minigame start.
 */
public final class MinigameTransitionService implements Listener {

    private final PointRush plugin;
    private final UltimateUiBridge bridge;
    private String pageId = "transition";
    private boolean enabled = true;
    private boolean autoClose = true;

    public MinigameTransitionService(PointRush plugin) {
        this.plugin = plugin;
        this.bridge = new UltimateUiBridge(plugin);
    }

    public void init() {
        reloadSettings();
        bridge.init();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        scheduleInitRetries();
    }

    private void scheduleInitRetries() {
        for (long delay : new long[]{20L, 60L, 120L, 200L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!bridge.isAvailable()) {
                    bridge.init();
                }
            }, delay);
        }
    }

    public void reloadSettings() {
        enabled = plugin.getUnifiedSettings().yaml().getBoolean("minigame-transition.enabled", true);
        pageId = plugin.getUnifiedSettings().yaml().getString("minigame-transition.page", "transition");
        autoClose = plugin.getUnifiedSettings().yaml().getBoolean("minigame-transition.auto-close", true);
        bridge.setConfiguredPluginName(
                plugin.getUnifiedSettings().yaml().getString("minigame-transition.plugin-name", ""));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        String normalized = event.getPlugin().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.contains("ultimateui")) {
            bridge.init();
        }
    }

    /** Gedeelde bridge zodat de HUD-service dezelfde Ultimate UI binding hergebruikt. */
    public UltimateUiBridge getBridge() {
        return bridge;
    }

    public void playForOnlinePlayers() {
        if (!enabled) {
            return;
        }
        bridge.openHudOverlay(Bukkit.getOnlinePlayers(), pageId, autoClose);
    }
}
