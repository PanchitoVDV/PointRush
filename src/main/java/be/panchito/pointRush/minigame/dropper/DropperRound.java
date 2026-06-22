package be.panchito.pointRush.minigame.dropper;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Eén dropper-ronde: een {@link #getTop() top-spawn} waar spelers vanaf vallen en
 * een {@link #contains(Location) finish-zone} (meestal het waterbad onderaan).
 *
 * <p>Een speler "completet" de ronde door levend in de finish-zone te landen; mist
 * hij het water en valt hij op de grond, dan respawnt hij bovenaan om opnieuw te
 * proberen tot de ronde-timer afloopt.
 */
public final class DropperRound {

    private final Location top;
    private final Location finishMin;
    private final Location finishMax;

    public DropperRound(Location top, Location finishA, Location finishB) {
        this.top = top.clone();
        // Normaliseer de twee hoeken naar een min/max-box zodat contains() simpel blijft.
        World world = finishA.getWorld();
        double minX = Math.min(finishA.getX(), finishB.getX());
        double minY = Math.min(finishA.getY(), finishB.getY());
        double minZ = Math.min(finishA.getZ(), finishB.getZ());
        double maxX = Math.max(finishA.getX(), finishB.getX());
        double maxY = Math.max(finishA.getY(), finishB.getY());
        double maxZ = Math.max(finishA.getZ(), finishB.getZ());
        this.finishMin = new Location(world, minX, minY, minZ);
        this.finishMax = new Location(world, maxX, maxY, maxZ);
    }

    public Location getTop() {
        return top.clone();
    }

    public Location getFinishMin() {
        return finishMin.clone();
    }

    public Location getFinishMax() {
        return finishMax.clone();
    }

    /** True wanneer {@code loc} binnen de finish-box van deze ronde valt. */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || finishMin.getWorld() == null) return false;
        if (loc.getWorld() != finishMin.getWorld()) return false;
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        return bx >= finishMin.getBlockX() && bx <= finishMax.getBlockX()
                && by >= finishMin.getBlockY() && by <= finishMax.getBlockY()
                && bz >= finishMin.getBlockZ() && bz <= finishMax.getBlockZ();
    }
}
