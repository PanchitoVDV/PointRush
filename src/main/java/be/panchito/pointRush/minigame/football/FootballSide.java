package be.panchito.pointRush.minigame.football;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * De twee voetbal-kanten. Mappen 1-op-1 op de BlockBall {@code Team}-enum-namen
 * ({@code RED} / {@code BLUE}). Alle PointRush-teams worden verdeeld over rood en blauw.
 */
public enum FootballSide {
    RED("Rood", NamedTextColor.RED, "RED"),
    BLUE("Blauw", NamedTextColor.BLUE, "BLUE");

    private final String displayName;
    private final NamedTextColor textColor;
    private final String blockBallTeam;

    FootballSide(String displayName, NamedTextColor textColor, String blockBallTeam) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.blockBallTeam = blockBallTeam;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    /** Naam van de bijbehorende BlockBall {@code Team}-enum-constante. */
    public String getBlockBallTeam() {
        return blockBallTeam;
    }

    public FootballSide opposite() {
        return this == RED ? BLUE : RED;
    }
}
