package be.panchito.pointRush.minigame.goldrush;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Goud-gerelateerde blokken voor Gold Rush scoring en anti-cheat.
 */
public final class GoldRushMaterials {

    private static final Set<Material> COUNTED_ORES = EnumSet.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE
    );

    private static final Set<Material> PLACE_BLOCKED = EnumSet.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.GOLD_BLOCK,
            Material.RAW_GOLD_BLOCK
    );

    private GoldRushMaterials() {
    }

    public static boolean isCountedOre(Material material) {
        return COUNTED_ORES.contains(material);
    }

    public static boolean isPlaceBlocked(Material material) {
        return PLACE_BLOCKED.contains(material);
    }
}
