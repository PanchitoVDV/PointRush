package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verdeelt spelers over rood/blauw. Alle leden van hetzelfde PointRush-team
 * krijgen gegarandeerd dezelfde kant; spelers zonder team tellen als solo-bucket.
 */
public final class CtfSideAssigner {

    private CtfSideAssigner() {
    }

    /**
     * @return per-speler toewijzing rood/blauw
     */
    public static Map<UUID, CtfSide> assignSides(TeamManager teamManager, Collection<Player> eligible) {
        Map<UUID, Set<UUID>> teamBuckets = new HashMap<>();
        for (Player p : eligible) {
            Team t = teamManager.getTeamOfPlayer(p.getUniqueId());
            UUID bucket = t != null ? t.getId() : p.getUniqueId();
            teamBuckets.computeIfAbsent(bucket, k -> new HashSet<>()).add(p.getUniqueId());
        }

        List<Map.Entry<UUID, Set<UUID>>> sorted = new ArrayList<>(teamBuckets.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        Map<UUID, CtfSide> sideByBucket = new HashMap<>();
        Map<CtfSide, Integer> sideCounts = new EnumMap<>(CtfSide.class);
        sideCounts.put(CtfSide.RED, 0);
        sideCounts.put(CtfSide.BLUE, 0);

        for (Map.Entry<UUID, Set<UUID>> entry : sorted) {
            CtfSide assign = sideCounts.get(CtfSide.RED) <= sideCounts.get(CtfSide.BLUE)
                    ? CtfSide.RED : CtfSide.BLUE;
            sideByBucket.put(entry.getKey(), assign);
            sideCounts.merge(assign, entry.getValue().size(), Integer::sum);
        }

        Map<UUID, CtfSide> result = new HashMap<>();
        for (Player p : eligible) {
            Team t = teamManager.getTeamOfPlayer(p.getUniqueId());
            UUID bucket = t != null ? t.getId() : p.getUniqueId();
            CtfSide side = sideByBucket.get(bucket);
            if (side == null) {
                side = CtfSide.RED;
            }
            result.put(p.getUniqueId(), side);
        }
        return result;
    }
}
