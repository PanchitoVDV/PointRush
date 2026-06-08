package be.panchito.pointRush.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.mvplugins.multiverse.core.MultiverseCoreApi;

import java.util.logging.Level;

/**
 * Cross-world teleport voor minigame-events. Op servers met Multiverse-Core gebruikt dit de
 * {@code AsyncSafetyTeleporter} (laadt de doel-chunk async en respecteert MV's wereldlogica); een
 * gewone synchrone {@code Player#teleport} over werelden heen wordt door Multiverse soms genegeerd,
 * waardoor spelers "blijven staan". Zonder Multiverse valt dit terug op Paper's async teleport.
 *
 * <p>Alle Multiverse-types worden alleen aangeraakt vanuit {@link #teleportViaMultiverse}, die enkel
 * wordt aangeroepen wanneer de plugin aanwezig is — zo treedt er geen {@code NoClassDefFoundError}
 * op servers zonder Multiverse.
 */
public final class MinigameTeleporter {

    private final Plugin plugin;
    private final boolean multiverseAvailable;

    public MinigameTeleporter(Plugin plugin) {
        this.plugin = plugin;
        this.multiverseAvailable = plugin.getServer().getPluginManager().getPlugin("Multiverse-Core") != null;
        if (multiverseAvailable) {
            plugin.getLogger().info("Multiverse-Core gevonden: minigame-teleports gebruiken de veilige async teleporter.");
        }
    }

    public boolean isMultiverseAvailable() {
        return multiverseAvailable;
    }

    /** Teleporteert exact naar {@code target} (geen veiligheidsverplaatsing — de locatie is bekend/veilig). */
    public void teleport(Player player, Location target) {
        teleport(player, target, false);
    }

    /**
     * Teleporteert {@code player} naar {@code target}. Met {@code checkSafety} laat je Multiverse de
     * speler eventueel naar een veilige nabije plek verplaatsen; voor vaste arena-spawns en de
     * opgeslagen startlocatie wil je dit meestal {@code false}.
     */
    public void teleport(Player player, Location target, boolean checkSafety) {
        if (player == null || target == null || target.getWorld() == null) {
            return;
        }
        if (multiverseAvailable && teleportViaMultiverse(player, target, checkSafety)) {
            return;
        }
        // Paper laadt de doel-chunk async en teleporteert daarna betrouwbaar over werelden heen.
        player.teleportAsync(target);
    }

    private boolean teleportViaMultiverse(Player player, Location target, boolean checkSafety) {
        try {
            MultiverseCoreApi.get()
                    .getSafetyTeleporter()
                    .to(target)
                    .checkSafety(checkSafety)
                    .teleportSingle(player);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Multiverse-teleport mislukt, val terug op Bukkit-teleport.", ex);
            return false;
        }
    }
}
