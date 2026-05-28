package be.panchito.pointRush.minigame.ctf;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * De twee Capture the Flag-kanten. Alle PointRush-teams worden gesplitst over rood en blauw.
 */
public enum CtfSide {
    RED("Rood", NamedTextColor.RED, Color.fromRGB(180, 20, 20)),
    BLUE("Blauw", NamedTextColor.BLUE, Color.fromRGB(30, 60, 200));

    private final String displayName;
    private final NamedTextColor textColor;
    private final Color leatherColor;

    CtfSide(String displayName, NamedTextColor textColor, Color leatherColor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.leatherColor = leatherColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public Color getLeatherColor() {
        return leatherColor;
    }

    public CtfSide opposite() {
        return this == RED ? BLUE : RED;
    }

    public ItemStack leatherPiece(Material piece) {
        ItemStack stack = new ItemStack(piece);
        stack.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(leatherColor));
        return stack;
    }
}
