package be.panchito.pointRush.minigame.boss;

import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * Full diamond PvE kit voor Boss Event arena- en finaalrondes.
 */
public final class BossEventKit {

    private BossEventKit() {
    }

    public static void give(Player player) {
        player.getInventory().clear();

        player.getInventory().setHelmet(enchantedArmor(Material.DIAMOND_HELMET));
        player.getInventory().setChestplate(enchantedArmor(Material.DIAMOND_CHESTPLATE));
        player.getInventory().setLeggings(enchantedArmor(Material.DIAMOND_LEGGINGS));
        player.getInventory().setBoots(enchantedArmor(Material.DIAMOND_BOOTS));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Boss zwaard"), NamedTextColor.AQUA, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.SHARPNESS, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        ItemStack bow = new ItemStack(Material.BOW);
        bow.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Boss boog"), NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.POWER, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        totem.editMeta(meta -> meta.displayName(
                Component.text(SmallText.of("Boss totem"), NamedTextColor.YELLOW, TextDecoration.BOLD)));

        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 64));
        player.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 16));
        player.getInventory().setItem(4, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        player.getInventory().setItem(5, new ItemStack(Material.COOKED_BEEF, 32));
        player.getInventory().setItem(8, totem);

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    private static ItemStack enchantedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.addEnchant(Enchantment.PROTECTION, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });
        return item;
    }
}
