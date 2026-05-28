package be.panchito.pointRush.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Central place for prefixes, colors and reusable message components.
 * Every user-facing string is funneled through {@link SmallText} so the
 * whole plugin shares the same ѕᴍᴀʟʟ ᴄᴀᴘѕ look.
 */
public final class Messages {

    private Messages() {
    }

    public static final Component PREFIX = Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(SmallText.of("PointRush"), NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .build();

    public static Component info(String text) {
        return PREFIX.append(Component.text(SmallText.of(text), NamedTextColor.GRAY));
    }

    public static Component success(String text) {
        return PREFIX.append(Component.text(SmallText.of(text), NamedTextColor.GREEN));
    }

    public static Component error(String text) {
        return PREFIX.append(Component.text(SmallText.of(text), NamedTextColor.RED));
    }

    public static Component warn(String text) {
        return PREFIX.append(Component.text(SmallText.of(text), NamedTextColor.YELLOW));
    }

    public static Component accent(String text) {
        return Component.text(SmallText.of(text), NamedTextColor.GOLD);
    }

    public static Component plain(String text, NamedTextColor color) {
        return Component.text(SmallText.of(text), color);
    }
}
