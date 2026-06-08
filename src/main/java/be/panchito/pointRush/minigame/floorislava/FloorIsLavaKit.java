package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random kit-roll voor Floor is Lava: vooral bouwblokken (1–3 stuks), zelden knock-items.
 */
public final class FloorIsLavaKit {

    public static final int MAX_BLOCKS_PER_TYPE = 3;
    public static final int MAX_UTIL_PER_TYPE = 2;

    /** Kans op knock i.p.v. bouwblok (procent). */
    private static final int KNOCK_CHANCE_PERCENT = 12;

    /** Kans op een gadget (grijphaak/wisselbal/kikkersprong) i.p.v. bouwblok (procent). */
    private static final int GADGET_CHANCE_PERCENT = 9;

    /** Duur (seconden) en sterkte (amplifier) van de jump boost van het kikkersprong-drankje. */
    public static final int FROG_JUMP_SECONDS = 8;
    public static final int FROG_JUMP_AMPLIFIER = 2;

    public enum Type { BUILD, KNOCK, GADGET }

    public record Roll(Type type, ItemStack item, String label) {
    }

    private static final List<Material> BUILD_MATERIALS = List.of(
            Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE,
            Material.STONE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.DEEPSLATE,
            Material.OAK_PLANKS,
            Material.BIRCH_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.WHITE_WOOL,
            Material.ORANGE_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL,
            Material.PINK_WOOL,
            Material.OAK_SLAB,
            Material.COBBLESTONE_SLAB,
            Material.STONE_BRICK_SLAB,
            Material.OAK_STAIRS,
            Material.COBBLESTONE_STAIRS,
            Material.BRICKS,
            Material.STONE_BRICKS,
            Material.MUD_BRICKS,
            Material.SCAFFOLDING,
            Material.LADDER,
            Material.GLASS,
            Material.TERRACOTTA,
            Material.ORANGE_TERRACOTTA,
            Material.NETHERRACK,
            Material.BLACKSTONE,
            Material.BAMBOO_BLOCK,
            Material.CRIMSON_PLANKS,
            Material.WARPED_PLANKS
    );

    private static final List<UtilDrop> UTIL_DROPS = List.of(
            new UtilDrop(Material.SNOWBALL, 1, 2, "sneeuwbal"),
            new UtilDrop(Material.EGG, 1, 2, "ei")
    );

    private static final List<GadgetDrop> GADGET_DROPS = List.of(
            new GadgetDrop(Material.FISHING_ROD, "grijphaak (1x)"),
            new GadgetDrop(Material.ENDER_PEARL, "wisselbal"),
            new GadgetDrop(Material.POTION, "kikkersprong")
    );

    private FloorIsLavaKit() {
    }

    public static Roll randomRoll() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);
        if (roll < GADGET_CHANCE_PERCENT) {
            GadgetDrop gadget = GADGET_DROPS.get(random.nextInt(GADGET_DROPS.size()));
            return gadgetStack(gadget);
        }
        if (roll < GADGET_CHANCE_PERCENT + KNOCK_CHANCE_PERCENT) {
            UtilDrop drop = UTIL_DROPS.get(random.nextInt(UTIL_DROPS.size()));
            int amount = random.nextInt(drop.minAmount(), drop.maxAmount() + 1);
            return stack(drop.material(), amount, Type.KNOCK, drop.label());
        }

        Material material = BUILD_MATERIALS.get(random.nextInt(BUILD_MATERIALS.size()));
        int amount = random.nextInt(1, MAX_BLOCKS_PER_TYPE + 1);
        String label = formatBlockLabel(material);
        return stack(material, amount, Type.BUILD, label);
    }

    public static boolean isBlockMaterial(Material material) {
        return material.isBlock() && material.isItem();
    }

    public static int maxAllowed(Material material, Type type) {
        if (type == Type.GADGET) {
            return 1;
        }
        if (type == Type.BUILD && isBlockMaterial(material)) {
            return MAX_BLOCKS_PER_TYPE;
        }
        return MAX_UTIL_PER_TYPE;
    }

    private static String formatBlockLabel(Material material) {
        String raw = material.name().toLowerCase().replace('_', ' ');
        return raw.length() > 18 ? raw.substring(0, 18) : raw;
    }

    private static Roll stack(Material material, int amount, Type type, String label) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        item.editMeta(meta -> meta.displayName(displayName(type, label)));
        return new Roll(type, item, label);
    }

    private static Roll gadgetStack(GadgetDrop gadget) {
        ItemStack item = new ItemStack(gadget.material(), 1);
        if (gadget.material() == Material.POTION) {
            item.editMeta(PotionMeta.class, meta -> {
                meta.setBasePotionType(PotionType.WATER);
                meta.setColor(Color.fromRGB(95, 215, 90));
                meta.displayName(displayName(Type.GADGET, gadget.label()));
                meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            });
        } else {
            item.editMeta(meta -> {
                meta.displayName(displayName(Type.GADGET, gadget.label()));
                if (gadget.material() == Material.FISHING_ROD) {
                    meta.setUnbreakable(true);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                }
            });
        }
        return new Roll(Type.GADGET, item, gadget.label());
    }

    private static Component displayName(Type type, String label) {
        NamedTextColor color = switch (type) {
            case BUILD -> NamedTextColor.GREEN;
            case KNOCK -> NamedTextColor.RED;
            case GADGET -> NamedTextColor.AQUA;
        };
        return Component.text(SmallText.of(label), color, TextDecoration.BOLD);
    }

    private record UtilDrop(Material material, int minAmount, int maxAmount, String label) {
    }

    private record GadgetDrop(Material material, String label) {
    }
}
