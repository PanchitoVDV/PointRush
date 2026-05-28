package be.panchito.pointRush.minigame.bingo;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class BingoItems {

    private BingoItems() {
    }

    public static ItemStack createMap(PointRush plugin) {
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        map.editMeta(ItemMeta.class, meta -> {
            meta.displayName(Component.text(SmallText.of("Bingo kaart"),
                    NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text(SmallText.of("Klik om je team-bingo te openen."), NamedTextColor.GRAY),
                    Component.text(SmallText.of("Verzamel items met je team!"), NamedTextColor.DARK_GRAY)
            ));
            meta.getPersistentDataContainer().set(BingoKeys.mapKey(plugin), PersistentDataType.BYTE, (byte) 1);
        });
        return map;
    }

    public static boolean isBingoMap(PointRush plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.FILLED_MAP) return false;
        ItemMeta meta = stack.getItemMeta();
        return BingoKeys.isBingoMap(plugin, meta);
    }
}
