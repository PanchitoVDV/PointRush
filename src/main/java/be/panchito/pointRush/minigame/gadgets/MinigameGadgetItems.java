package be.panchito.pointRush.minigame.gadgets;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Item stacks + PDC voor Rush-trucs tijdens minigames.
 */
public final class MinigameGadgetItems {

    private static final String KEY_ID = "minigame_gadget";

    private MinigameGadgetItems() {
    }

    public static NamespacedKey gadgetKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY_ID);
    }

    public static ItemStack createStack(PointRush plugin, MinigameGadgetType type) {
        ItemStack stack = new ItemStack(type.getMaterial(), type.getDefaultAmount());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(SmallText.of(type.getDisplayName()), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(SmallText.of(type.getLoreLine()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(SmallText.of("Rechtsklik om te gebruiken."), NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(gadgetKey(plugin), PersistentDataType.STRING, type.name());
        stack.setItemMeta(meta);
        return stack;
    }

    public static MinigameGadgetType parse(PointRush plugin, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(gadgetKey(plugin), PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return MinigameGadgetType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Hotbar slots 1–5 (index 1..5), feather/checkpoint tools stay on 0 / eye on 8 for parkour.
     */
    public static void giveGadgetRow(PointRush plugin, PlayerInventory inv, MinigameGadgetMode mode) {
        int slot = 1;
        for (MinigameGadgetType t : MinigameGadgetType.values()) {
            if (!t.allowedIn(mode)) continue;
            if (slot > 5) break;
            inv.setItem(slot++, createStack(plugin, t));
        }
    }

    /** Full refill for TNT Tag each round so gadgets stay chaotic. */
    public static void refillTagGadgets(PointRush plugin, PlayerInventory inv) {
        giveGadgetRow(plugin, inv, MinigameGadgetMode.TNT_TAG);
    }
}
