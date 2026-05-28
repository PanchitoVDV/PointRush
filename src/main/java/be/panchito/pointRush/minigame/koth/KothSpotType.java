package be.panchito.pointRush.minigame.koth;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetItems;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Soort loot op een KOTH power-spot (1x pickup).
 */
public enum KothSpotType {

    RANDOM("random", Material.NETHER_STAR, "Willekeurige Rush-truc"),
    TROLL("troll", Material.POPPED_CHORUS_FRUIT, "Troll-truc"),
    WIND("wind", Material.WIND_CHARGE, "Wind charges"),
    KNOCK("knock", Material.STICK, "Mega knock stick"),
    TWIST_ROD("twist", Material.BLAZE_ROD, "Warrelstok"),
    INK_BLOB("ink", Material.INK_SAC, "Inktfles"),
    GOO_BALL("goo", Material.SLIME_BALL, "Plakbal"),
    SPARK_ROD("spark", Material.COPPER_INGOT, "Donderstaaf"),
    CHAOS_FRUIT("chaos", Material.POPPED_CHORUS_FRUIT, "Duizelbes");

    private static final List<MinigameGadgetType> TROLL_POOL = List.of(
            MinigameGadgetType.TWIST_ROD,
            MinigameGadgetType.INK_BLOB,
            MinigameGadgetType.GOO_BALL,
            MinigameGadgetType.SPARK_ROD,
            MinigameGadgetType.CHAOS_FRUIT
    );

    private static final List<MinigameGadgetType> RANDOM_POOL = List.of(
            MinigameGadgetType.TWIST_ROD,
            MinigameGadgetType.INK_BLOB,
            MinigameGadgetType.GOO_BALL,
            MinigameGadgetType.SPARK_ROD,
            MinigameGadgetType.CHAOS_FRUIT,
            MinigameGadgetType.TURBO_FUNGUS
    );

    private final String configKey;
    private final Material markerMaterial;
    private final String label;

    KothSpotType(String configKey, Material markerMaterial, String label) {
        this.configKey = configKey;
        this.markerMaterial = markerMaterial;
        this.label = label;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Material getMarkerMaterial() {
        return markerMaterial;
    }

    public String getLabel() {
        return label;
    }

    public static KothSpotType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return RANDOM;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (KothSpotType type : values()) {
            if (type.configKey.equals(key) || type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RANDOM;
        }
    }

    public ItemStack createItem(PointRush plugin) {
        return switch (this) {
            case RANDOM -> MinigameGadgetItems.createStack(plugin, pick(RANDOM_POOL));
            case TROLL -> MinigameGadgetItems.createStack(plugin, pick(TROLL_POOL));
            case WIND -> {
                ItemStack stack = new ItemStack(Material.WIND_CHARGE, 3);
                stack.editMeta(meta -> meta.displayName(Component.text("Wind Burst", NamedTextColor.AQUA, TextDecoration.BOLD)));
                yield stack;
            }
            case KNOCK -> {
                ItemStack stick = new ItemStack(Material.STICK);
                stick.editMeta(meta -> {
                    meta.displayName(Component.text("Mega Knock", NamedTextColor.RED, TextDecoration.BOLD));
                    meta.addEnchant(Enchantment.KNOCKBACK, 4, true);
                    meta.setUnbreakable(true);
                });
                yield stick;
            }
            case TWIST_ROD -> MinigameGadgetItems.createStack(plugin, MinigameGadgetType.TWIST_ROD);
            case INK_BLOB -> MinigameGadgetItems.createStack(plugin, MinigameGadgetType.INK_BLOB);
            case GOO_BALL -> MinigameGadgetItems.createStack(plugin, MinigameGadgetType.GOO_BALL);
            case SPARK_ROD -> MinigameGadgetItems.createStack(plugin, MinigameGadgetType.SPARK_ROD);
            case CHAOS_FRUIT -> MinigameGadgetItems.createStack(plugin, MinigameGadgetType.CHAOS_FRUIT);
        };
    }

    private static MinigameGadgetType pick(List<MinigameGadgetType> pool) {
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
