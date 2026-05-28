package be.panchito.pointRush.minigame.bingo;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * PDC-tags voor bingo-kaart items.
 */
public final class BingoKeys {

    private BingoKeys() {
    }

    public static NamespacedKey mapKey(Plugin plugin) {
        return new NamespacedKey(plugin, "bingo_map");
    }

    public static boolean isBingoMap(Plugin plugin, ItemMeta meta) {
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(mapKey(plugin), PersistentDataType.BYTE);
    }
}
