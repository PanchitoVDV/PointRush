package be.panchito.pointRush.minigame.treasurehunt;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Locale;

/**
 * Schatkist-tier: bepaalt aantal loot rolls en marker-kleur.
 */
public enum TreasureTier {

    NORMAL("normal", "Normal Crate", 1, Material.CHEST, NamedTextColor.GREEN),
    EPIC("epic", "Epic Crate", 2, Material.ENDER_CHEST, NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("legendary", "Legendary Crate", 3, Material.GOLD_BLOCK, NamedTextColor.GOLD);

    private final String configKey;
    private final String displayName;
    private final int rollCount;
    private final Material markerMaterial;
    private final NamedTextColor color;

    TreasureTier(String configKey, String displayName, int rollCount,
                   Material markerMaterial, NamedTextColor color) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.rollCount = rollCount;
        this.markerMaterial = markerMaterial;
        this.color = color;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRollCount() {
        return rollCount;
    }

    public Material getMarkerMaterial() {
        return markerMaterial;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public static TreasureTier fromConfig(String raw) {
        if (raw == null || raw.isBlank()) return NORMAL;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (TreasureTier tier : values()) {
            if (tier.configKey.equals(key) || tier.name().equalsIgnoreCase(key)) {
                return tier;
            }
        }
        return NORMAL;
    }
}
