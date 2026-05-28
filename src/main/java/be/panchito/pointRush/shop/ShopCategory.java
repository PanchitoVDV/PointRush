package be.panchito.pointRush.shop;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/** Minigame-hub in de PointRush shop-GUI. */
public enum ShopCategory {
    PARKOUR("Parkour", Material.LIME_CONCRETE, NamedTextColor.GREEN),
    TNT_TAG("TNT Tag", Material.TNT, NamedTextColor.RED),
    TNT_RUN("TNT Run", Material.SANDSTONE, NamedTextColor.GOLD),
    RACE("Race", Material.GOLD_BLOCK, NamedTextColor.YELLOW);

    private final String title;
    private final Material icon;
    private final TextColor accent;

    ShopCategory(String title, Material icon, TextColor accent) {
        this.title = title;
        this.icon = icon;
        this.accent = accent;
    }

    public String title() {
        return title;
    }

    public Material icon() {
        return icon;
    }

    public TextColor accent() {
        return accent;
    }
}
