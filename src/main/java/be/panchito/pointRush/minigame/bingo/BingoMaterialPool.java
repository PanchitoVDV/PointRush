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
            // Steen & grond
            Material.COBBLESTONE, Material.STONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF,
            Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.MUD, Material.SAND,
            Material.RED_SAND, Material.GRAVEL, Material.CLAY, Material.CLAY_BALL,
            Material.SANDSTONE, Material.SNOWBALL, Material.ICE,
            // Ertsen & mineralen
            Material.COAL, Material.CHARCOAL, Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.DIAMOND, Material.EMERALD,
            Material.AMETHYST_SHARD, Material.FLINT, Material.IRON_NUGGET, Material.GOLD_NUGGET,
            // Hout & bladeren
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.OAK_PLANKS, Material.OAK_SAPLING, Material.OAK_LEAVES, Material.STICK,
            Material.BAMBOO,
            // Gewassen, planten & plantaardig voedsel
            Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROT, Material.POTATO,
            Material.BEETROOT, Material.PUMPKIN, Material.MELON_SLICE, Material.SUGAR_CANE,
            Material.CACTUS, Material.KELP, Material.SWEET_BERRIES, Material.GLOW_BERRIES,
            Material.APPLE, Material.COCOA_BEANS, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM,
            Material.SEAGRASS, Material.LILY_PAD,
            // Mob-drops (overworld)
            Material.LEATHER, Material.STRING, Material.FEATHER, Material.BONE,
            Material.EGG, Material.GUNPOWDER, Material.SLIME_BALL, Material.SPIDER_EYE,
            Material.ROTTEN_FLESH, Material.INK_SAC, Material.GLOW_INK_SAC, Material.RABBIT_HIDE,
            Material.RABBIT_FOOT, Material.HONEYCOMB, Material.HONEY_BOTTLE, Material.PHANTOM_MEMBRANE,
            Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.NAUTILUS_SHELL,
            Material.TURTLE_SCUTE, Material.ARMADILLO_SCUTE,
            // Vlees & vis
            Material.BEEF, Material.PORKCHOP, Material.CHICKEN, Material.MUTTON,
            Material.RABBIT, Material.COD, Material.SALMON, Material.TROPICAL_FISH,
            Material.PUFFERFISH, Material.BREAD,
            // Gemaakte/diverse overworld-items
            Material.ARROW, Material.TORCH, Material.PAPER, Material.BOOK,
            Material.BUCKET, Material.SHEARS, Material.COMPASS, Material.CLOCK,
            Material.FISHING_ROD, Material.WHITE_WOOL, Material.BRICK
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
