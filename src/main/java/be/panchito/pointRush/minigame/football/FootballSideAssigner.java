package be.panchito.pointRush.minigame.football;

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
 * Verdeelt spelers over rood/blauw. Alle leden van hetzelfde PointRush-team komen
 * gegarandeerd aan dezelfde kant; spelers zonder team tellen als solo-bucket. Identiek
 * principe als {@code CtfSideAssigner} zodat teamwerk binnen één voetbalteam blijft.
 */
public final class FootballSideAssigner {

    private FootballSideAssigner() {
    }

    public static Map<UUID, FootballSide> assignSides(TeamManager teamManager, Collection<Player> eligible) {
        Map<UUID, Set<UUID>> teamBuckets = new HashMap<>();
        for (Player p : eligible) {
            Team t = teamManager.getTeamOfPlayer(p.getUniqueId());
            UUID bucket = t != null ? t.getId() : p.getUniqueId();
            teamBuckets.computeIfAbsent(bucket, k -> new HashSet<>()).add(p.getUniqueId());
        }

        List<Map.Entry<UUID, Set<UUID>>> sorted = new ArrayList<>(teamBuckets.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        Map<UUID, FootballSide> sideByBucket = new HashMap<>();
        Map<FootballSide, Integer> sideCounts = new EnumMap<>(FootballSide.class);
        sideCounts.put(FootballSide.RED, 0);
        sideCounts.put(FootballSide.BLUE, 0);

        for (Map.Entry<UUID, Set<UUID>> entry : sorted) {
            FootballSide assign = sideCounts.get(FootballSide.RED) <= sideCounts.get(FootballSide.BLUE)
                    ? FootballSide.RED : FootballSide.BLUE;
            sideByBucket.put(entry.getKey(), assign);
            sideCounts.merge(assign, entry.getValue().size(), Integer::sum);
        }

        Map<UUID, FootballSide> result = new HashMap<>();
        for (Player p : eligible) {
            Team t = teamManager.getTeamOfPlayer(p.getUniqueId());
            UUID bucket = t != null ? t.getId() : p.getUniqueId();
            FootballSide side = sideByBucket.get(bucket);
            result.put(p.getUniqueId(), side != null ? side : FootballSide.RED);
        }
        return result;
    }
}
