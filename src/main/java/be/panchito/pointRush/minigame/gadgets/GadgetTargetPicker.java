package be.panchito.pointRush.minigame.gadgets;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class GadgetTargetPicker {

    static final double MAX_RANGE = 14.0;
    static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;

    private GadgetTargetPicker() {
    }

    static Player pickVictim(Player user, Collection<Player> candidates) {
        if (candidates.isEmpty()) return null;
        Location eye = user.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        List<Player> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(user.getLocation())));

        Player bestFacing = null;
        double bestFacingDistSq = Double.MAX_VALUE;
        Player closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Player p : sorted) {
            if (p.getWorld() != user.getWorld()) continue;
            double distSq = p.getLocation().distanceSquared(user.getLocation());
            if (distSq > MAX_RANGE_SQ || distSq < 0.25) continue;

            Vector toMid = p.getLocation().clone().add(0, 1.0, 0).toVector().subtract(eye.toVector());
            double lenSq = toMid.lengthSquared();
            if (lenSq < 1.0e-4) return p;
            Vector toN = toMid.clone().normalize();
            double dot = dir.dot(toN);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = p;
            }
            if (dot > 0.35 && distSq < bestFacingDistSq) {
                bestFacingDistSq = distSq;
                bestFacing = p;
            }
        }
        if (bestFacing != null) return bestFacing;
        if (closest != null && closestDistSq <= 6.0 * 6.0) return closest;
        return null;
    }
}
