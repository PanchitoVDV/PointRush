package be.panchito.pointRush.minigame.bingo;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Materialen voor scavenger-bingo (survival-vriendelijk).
 */
public final class BingoMaterialPool {

    private static final Set<Material> BLOCKED = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.STRUCTURE_VOID, Material.JIGSAW, Material.SPAWNER
    );

    private static final List<Material> DEFAULT = List.of(
            Material.COBBLESTONE, Material.STONE, Material.DEEPSLATE,
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
            Material.OAK_PLANKS, Material.BAMBOO, Material.WHEAT, Material.CARROT,
            Material.POTATO, Material.BEETROOT, Material.EGG, Material.LEATHER,
            Material.STRING, Material.FEATHER, Material.BONE, Material.GUNPOWDER,
            Material.SLIME_BALL, Material.REDSTONE, Material.LAPIS_LAZULI,
            Material.QUARTZ, Material.OBSIDIAN, Material.NETHERRACK,
            Material.GLOWSTONE_DUST, Material.BLAZE_ROD, Material.ENDER_PEARL,
            Material.PRISMARINE_SHARD, Material.HONEYCOMB, Material.COPPER_INGOT,
            Material.AMETHYST_SHARD, Material.RAW_IRON, Material.RAW_COPPER,
            Material.SAND, Material.GRAVEL, Material.CLAY_BALL, Material.OAK_SAPLING,
            Material.OAK_LEAVES, Material.CACTUS, Material.SUGAR_CANE, Material.KELP,
            Material.IRON_INGOT, Material.COAL, Material.FLINT, Material.ARROW
    );

    private BingoMaterialPool() {
    }

    public static List<Material> defaultPoolCopy() {
        return new ArrayList<>(DEFAULT);
    }

    public static List<Material> parseFromYaml(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return defaultPoolCopy();
        }
        List<Material> out = new ArrayList<>();
        for (Object o : raw) {
            if (o == null) continue;
            Material mat = Material.matchMaterial(o.toString().trim().toUpperCase(Locale.ROOT));
            if (mat == null || !mat.isItem() || BLOCKED.contains(mat)) continue;
            if (!out.contains(mat)) out.add(mat);
        }
        return out.isEmpty() ? defaultPoolCopy() : out;
    }

    public static List<String> toYamlNames(List<Material> pool) {
        List<String> names = new ArrayList<>(pool.size());
        for (Material m : pool) {
            names.add(m.name());
        }
        return Collections.unmodifiableList(names);
    }
}
