package be.panchito.pointRush.minigame.treasurehunt;

import org.bukkit.Location;

/**
 * Geconfigureerde schatlocatie op de survival map.
 */
public final class TreasureLocation {

    private final String id;
    private final Location location;
    private final TreasureTier tier;
    private String hint;

    public TreasureLocation(String id, Location location, TreasureTier tier, String hint) {
        this.id = id;
        this.location = location;
        this.tier = tier;
        this.hint = hint != null ? hint : "Zoek de schat";
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public TreasureTier getTier() {
        return tier;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint != null && !hint.isBlank() ? hint : "Zoek de schat";
    }

    public String formatCoords() {
        if (location.getWorld() == null) return "? ? ?";
        return String.format("%s · X %d Y %d Z %d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
