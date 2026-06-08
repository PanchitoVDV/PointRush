package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.coins.NexoCoinFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Nexo vlag ({@code bl_flag_1}) voor Capture the Flag.
 */
public final class CtfFlagItem {

    public static final String NEXO_ID = "bl_flag_1";

    private CtfFlagItem() {
    }

    public static boolean isAvailable() {
        return Boolean.TRUE.equals(NexoCoinFactory.exists(NEXO_ID));
    }

    public static ItemStack create() {
        ItemStack nexo = NexoCoinFactory.stackForId(NEXO_ID);
        if (nexo != null && !nexo.getType().isAir()) {
            return nexo.clone();
        }
        ItemStack fallback = new ItemStack(Material.PAPER);
        fallback.editMeta(ItemMeta.class, meta -> {
            meta.displayName(Component.text("Vlag", NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.setUnbreakable(true);
        });
        return fallback;
    }

    public static boolean isFlag(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = NexoCoinFactory.idFromStack(stack);
        if (NEXO_ID.equals(id)) {
            return true;
        }
        if (stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return false;
        }
        if (stack.getItemMeta().displayName() == null) {
            return false;
        }
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
        return "Vlag".equalsIgnoreCase(plain);
    }
}
