package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random kit-roll voor Floor is Lava: bouw-items of knock-items.
 */
public final class FloorIsLavaKit {

    public enum Type { BUILD, KNOCK }

    public record Roll(Type type, ItemStack item, String label) {
    }

    private static final List<RollFactory> BUILD = List.of(
            () -> stack(Material.COBBLESTONE, 16, Type.BUILD, "cobblestone"),
            () -> stack(Material.OAK_PLANKS, 16, Type.BUILD, "planken"),
            () -> stack(Material.WHITE_WOOL, 12, Type.BUILD, "wol"),
            () -> stack(Material.SCAFFOLDING, 8, Type.BUILD, "scaffolding"),
            () -> stack(Material.LADDER, 6, Type.BUILD, "ladder"),
            () -> stack(Material.OAK_SLAB, 12, Type.BUILD, "planken slab")
    );

    private static final List<RollFactory> KNOCK = List.of(
            FloorIsLavaKit::knockStick,
            () -> stack(Material.SNOWBALL, 8, Type.KNOCK, "sneeuwbal"),
            () -> stack(Material.EGG, 6, Type.KNOCK, "ei"),
            () -> stack(Material.FISHING_ROD, 1, Type.KNOCK, "hengel"),
            () -> stack(Material.WIND_CHARGE, 3, Type.KNOCK, "wind charge"),
            () -> stack(Material.ENDER_PEARL, 1, Type.KNOCK, "ender pearl")
    );

    private FloorIsLavaKit() {
    }

    public static Roll randomRoll() {
        var random = ThreadLocalRandom.current();
        List<RollFactory> pool = random.nextBoolean() ? BUILD : KNOCK;
        return pool.get(random.nextInt(pool.size())).create();
    }

    private static Roll knockStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        stick.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Duw stok"), NamedTextColor.RED, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
            meta.setUnbreakable(true);
        });
        return new Roll(Type.KNOCK, stick, "knock stick");
    }

    private static Roll stack(Material material, int amount, Type type, String label) {
        ItemStack item = new ItemStack(material, amount);
        item.editMeta(meta -> meta.displayName(displayName(type, label)));
        return new Roll(type, item, label);
    }

    private static Component displayName(Type type, String label) {
        NamedTextColor color = type == Type.BUILD ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(label, color, TextDecoration.BOLD);
    }

    @FunctionalInterface
    private interface RollFactory {
        Roll create();
    }
}
