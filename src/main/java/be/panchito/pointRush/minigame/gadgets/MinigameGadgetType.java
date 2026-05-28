package be.panchito.pointRush.minigame.gadgets;

import org.bukkit.Material;

/**
 * Mario Kart-achtige items tijdens PointRush-events (CoreSMP).
 */
public enum MinigameGadgetType {

    TWIST_ROD(Material.BLAZE_ROD, false, 3,
            "Warrelstok",
            "Iemand voor je laat tollen - nog 3 keer."),

    INK_BLOB(Material.INK_SAC, false, 1,
            "Inktfles",
            "Zorgt voor 7 seconden blindheid."),

    GOO_BALL(Material.SLIME_BALL, false, 1,
            "Plakbal",
            "Maakt iemand zwaar traag."),

    SPARK_ROD(Material.COPPER_INGOT, false, 1,
            "Donderstaaf",
            "Zwakte en bliksem - geen echte schade."),

    /** Alleen parkour / TNT Run: eigen speed-boost. */
    TURBO_FUNGUS(Material.RED_MUSHROOM, true, 1,
            "Snel-paddo",
            "Jouw eigen Speed II, 5 seconden."),

    /** Alleen TNT Tag: duizeligheid bij slachtoffer. */
    CHAOS_FRUIT(Material.POPPED_CHORUS_FRUIT, false, 1,
            "Duizelbes",
            "Laat iemand tollen en misselijk worden.");

    private final Material material;
    private final boolean selfOnly;
    private final int defaultAmount;
    private final String displayName;
    private final String loreLine;

    MinigameGadgetType(Material material, boolean selfOnly, int defaultAmount,
                       String displayName, String loreLine) {
        this.material = material;
        this.selfOnly = selfOnly;
        this.defaultAmount = defaultAmount;
        this.displayName = displayName;
        this.loreLine = loreLine;
    }

    public Material getMaterial() {
        return material;
    }

    public boolean isSelfOnly() {
        return selfOnly;
    }

    public int getDefaultAmount() {
        return defaultAmount;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLoreLine() {
        return loreLine;
    }

    public boolean allowedIn(MinigameGadgetMode mode) {
        if (mode == MinigameGadgetMode.KOTH) {
            return true;
        }
        return switch (this) {
            case TURBO_FUNGUS -> mode != MinigameGadgetMode.TNT_TAG;
            case CHAOS_FRUIT -> mode == MinigameGadgetMode.TNT_TAG;
            default -> true;
        };
    }
}
