package be.panchito.pointRush.minigame.koth;

import org.bukkit.Location;

/**
 * Een pickup-spot voor eenmalige Wipeout-trucs tijdens KOTH.
 */
public final class KothPowerSpot {

    private final String id;
    private final Location location;
    private final KothSpotType type;

    public KothPowerSpot(String id, Location location, KothSpotType type) {
        this.id = id;
        this.location = location;
        this.location.setYaw(0f);
        this.location.setPitch(0f);
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public KothSpotType getType() {
        return type;
    }
}
