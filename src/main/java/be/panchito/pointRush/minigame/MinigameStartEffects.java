package be.panchito.pointRush.minigame;

import be.panchito.pointRush.PointRush;

/**
 * Visuele effecten bij een geslaagde minigame-start (Ultimate UI transitie).
 */
public final class MinigameStartEffects {

    private MinigameStartEffects() {
    }

    public static void onStarted(PointRush plugin) {
        onStarted(plugin, null);
    }

    public static void onStarted(PointRush plugin, String minigameId) {
        if (plugin == null) {
            return;
        }
        plugin.getMinigameTransitionService().playForOnlinePlayers();
        if (plugin.getMinigameHudService() != null) {
            String id = minigameId != null
                    ? minigameId
                    : MinigameRegistry.activeMinigameId(plugin);
            if (id != null) {
                plugin.getMinigameHudService().onMinigameStarted(id);
            }
        }
    }
}
