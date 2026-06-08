package be.panchito.pointRush.util;

import be.panchito.pointRush.PointRush;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Bepaalt welke wereld als hoofd-/lobbywereld geldt voor minigame-events. Alleen spelers in deze
 * wereld worden bij de start van een event opgepakt (en teleporteren naar de arena-spawn); na afloop
 * gaan ze terug naar hun opgeslagen plek in deze wereld.
 *
 * <p>Instelbaar via {@code lobby-world} in settings.yml (standaard {@code "world"}).
 */
public final class LobbyWorld {

    public static final String DEFAULT = "world";

    private LobbyWorld() {
    }

    /** Geconfigureerde naam van de hoofd-/lobbywereld. */
    public static String name(PointRush plugin) {
        return plugin.getUnifiedSettings().yaml().getString("lobby-world", DEFAULT);
    }

    /** True als de speler zich in de geconfigureerde hoofd-/lobbywereld bevindt. */
    public static boolean contains(PointRush plugin, Player player) {
        World world = player.getWorld();
        return world != null && world.getName().equalsIgnoreCase(name(plugin));
    }
}
