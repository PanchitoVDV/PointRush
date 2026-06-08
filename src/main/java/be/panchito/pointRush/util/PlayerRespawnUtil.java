package be.panchito.pointRush.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Helpers for minigame death/respawn flows. Event handlers must not change
 * gamemode, health, or inventory while {@link Player#isDead()} — apply those
 * after a real respawn or via {@link #prepareForRestore(Player)} when cleaning up.
 */
public final class PlayerRespawnUtil {

    private PlayerRespawnUtil() {
    }

    /**
     * Skips the client death screen so {@link org.bukkit.event.player.PlayerRespawnEvent}
     * fires and follow-up logic can run on a living player.
     */
    public static void forceRespawnIfDead(Player player) {
        if (player == null || !player.isDead()) {
            return;
        }
        try {
            player.spigot().respawn();
        } catch (Throwable ignored) {
        }
    }

    public static void clearSpectatorState(Player player) {
        if (player == null || player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Call at the start of minigame {@code restorePlayer} so offline-death and
     * spectator states do not block inventory/gamemode restore.
     */
    public static void prepareForRestore(Player player) {
        forceRespawnIfDead(player);
        clearSpectatorState(player);
    }
}
