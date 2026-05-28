package be.panchito.pointRush.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Locale;

/**
 * Gedeelde minigame-teksten: Nederlandse staten, countdown-titles en scoreboard-labels.
 */
public final class MinigameText {

    private MinigameText() {
    }

    /** Nederlandse status voor {@code /... info} en admin-UI. */
    public static String stateLabel(Enum<?> state) {
        if (state == null) {
            return "?";
        }
        return switch (state.name()) {
            case "IDLE" -> "inactief";
            case "STARTING" -> "countdown";
            case "RUNNING" -> "bezig";
            case "INTERMISSION" -> "pauze";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    public static Component goTitle() {
        return Component.text(SmallText.of("GO!"), NamedTextColor.GREEN, TextDecoration.BOLD);
    }

    public static String pts(int points) {
        return points + " pts";
    }

    public static String idle() {
        return SmallText.of("wachtend");
    }

    public static String finished() {
        return SmallText.of("gefinisht");
    }

    public static String alive() {
        return SmallText.of("in leven");
    }

    public static String kills(int count) {
        return count + " kills";
    }

    public static String capturing() {
        return SmallText.of("bezetting");
    }

    public static String contested() {
        return SmallText.of("omstreden");
    }
}
