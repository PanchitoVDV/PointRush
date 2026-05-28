package be.panchito.pointRush.minigame.treasurehunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Random PvP/healing loot voor Treasure Hunt (1.21+).
 */
public final class TreasureLootTable {

    @FunctionalInterface
    private interface LootFactory {
        ItemStack create();
    }

    private static final List<LootFactory> NORMAL = List.of(
            () -> stack(Material.GOLDEN_APPLE, 3),
            () -> stack(Material.COOKED_BEEF, 8),
            () -> stack(Material.ARROW, 32),
            () -> stack(Material.SNOWBALL, 12),
            () -> stack(Material.WIND_CHARGE, 2),
            () -> stack(Material.SHIELD),
            () -> stack(Material.BOW),
            () -> enchanted(Material.IRON_SWORD, Enchantment.SHARPNESS, 1),
            () -> enchanted(Material.BOW, Enchantment.POWER, 1),
            () -> stack(Material.CROSSBOW),
            () -> stack(Material.IRON_HELMET),
            () -> stack(Material.CHAINMAIL_CHESTPLATE),
            () -> splash(PotionEffectType.INSTANT_HEALTH, 1, 1),
            () -> splash(PotionEffectType.SPEED, 20 * 45, 0),
            () -> stack(Material.ENDER_PEARL, 1)
    );

    private static final List<LootFactory> EPIC = List.of(
            () -> stack(Material.GOLDEN_APPLE, 6),
            () -> stack(Material.ENCHANTED_GOLDEN_APPLE, 1),
            () -> stack(Material.ENDER_PEARL, 2),
            () -> stack(Material.WIND_CHARGE, 4),
            () -> stack(Material.SNOWBALL, 16),
            () -> trident(2),
            () -> mace(1, 1),
            () -> enchanted(Material.IRON_CHESTPLATE, Enchantment.PROTECTION, 2),
            () -> enchanted(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2),
            () -> enchanted(Material.CROSSBOW, Enchantment.MULTISHOT, 1),
            () -> splash(PotionEffectType.STRENGTH, 20 * 45, 0),
            () -> splash(PotionEffectType.REGENERATION, 20 * 30, 1),
            () -> splash(PotionEffectType.INSTANT_HEALTH, 1, 1),
            () -> splash(PotionEffectType.RESISTANCE, 20 * 20, 0)
    );

    private static final List<LootFactory> LEGENDARY = List.of(
            () -> stack(Material.ENCHANTED_GOLDEN_APPLE, 2),
            () -> stack(Material.GOLDEN_APPLE, 10),
            () -> stack(Material.ENDER_PEARL, 4),
            () -> stack(Material.WIND_CHARGE, 8),
            () -> trident(3),
            () -> mace(3, 2),
            () -> enchanted(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 3),
            () -> enchanted(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3),
            () -> enchanted(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 2),
            () -> splash(PotionEffectType.STRENGTH, 20 * 45, 1),
            () -> splash(PotionEffectType.INSTANT_HEALTH, 1, 2),
            () -> splash(PotionEffectType.REGENERATION, 20 * 45, 1),
            () -> splash(PotionEffectType.SPEED, 20 * 60, 1),
            () -> stack(Material.TOTEM_OF_UNDYING, 1)
    );

    private TreasureLootTable() {
    }

    public static List<ItemStack> rollLoot(TreasureTier tier) {
        List<LootFactory> pool = switch (tier) {
            case NORMAL -> NORMAL;
            case EPIC -> EPIC;
            case LEGENDARY -> LEGENDARY;
        };
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> out = new ArrayList<>();
        int rolls = tier.getRollCount();
        for (int i = 0; i < rolls; i++) {
            ItemStack item = pool.get(random.nextInt(pool.size())).create().clone();
            out.add(item);
        }
        return out;
    }

    private static ItemStack stack(Material material) {
        return new ItemStack(material);
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    private static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.addEnchant(enchantment, level, true);
            meta.displayName(Component.text(material.name().toLowerCase().replace('_', ' '),
                    NamedTextColor.AQUA, TextDecoration.BOLD));
        });
        return item;
    }

    private static ItemStack trident(int loyalty) {
        ItemStack item = new ItemStack(Material.TRIDENT);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Treasure Trident", NamedTextColor.AQUA, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.LOYALTY, loyalty, true);
            meta.setUnbreakable(true);
        });
        return item;
    }

    private static ItemStack mace(int density, int breach) {
        ItemStack item = new ItemStack(Material.MACE);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Treasure Mace", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.DENSITY, density, true);
            meta.addEnchant(Enchantment.BREACH, breach, true);
            meta.setUnbreakable(true);
        });
        return item;
    }

    private static ItemStack splash(PotionEffectType type, int duration, int amplifier) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        item.editMeta(PotionMeta.class, meta -> {
            meta.addCustomEffect(new PotionEffect(type, duration, amplifier, false, true, true), true);
            meta.displayName(Component.text("Treasure Potion", NamedTextColor.LIGHT_PURPLE));
        });
        return item;
    }
}
