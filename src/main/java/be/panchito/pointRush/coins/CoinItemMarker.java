package be.panchito.pointRush.coins;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Marker op {@link ItemStack} zodat alleen PointRush-spawns als "bezette plek"/pickup worden gezien.
 */
public final class CoinItemMarker {

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "collector_coin_drop");
    }

    static NamespacedKey spawnSlotKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "collector_coin_spawn_idx");
    }

    /** PointRush-munt van spawn-slot {@code spawnSlotIndex} (match met {@link CoinSpawnConfig#getSpawns()} volgorde). */
    public static void stamp(JavaPlugin plugin, ItemStack stack, int spawnSlotIndex) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        pdc.set(spawnSlotKey(plugin), PersistentDataType.INTEGER, spawnSlotIndex);
        stack.setItemMeta(meta);
    }

    /** {@code -1} als niet gezet (legacy drops). */
    public static int readSpawnSlot(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return -1;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey k = spawnSlotKey(plugin);
        if (!pdc.has(k, PersistentDataType.INTEGER)) {
            return -1;
        }
        return pdc.get(k, PersistentDataType.INTEGER);
    }

    public static boolean isStamped(JavaPlugin plugin, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}
