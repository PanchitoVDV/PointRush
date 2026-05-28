package be.panchito.pointRush.team;

import java.util.List;

/**
 * Lightweight snapshot cache for sorted teams so frequent readers (zoals de lobby
 * scoreboard) geen nieuwe sort hoeven te bouwen bij elke tick.
 *
 * <p>Wordt automatisch ongeldig gemaakt wanneer {@link be.panchito.pointRush.storage.DataManager}
 * een persist-run plant na een mutatie.
 */
public final class LeaderboardCache {

    private volatile List<Team> snapshot;

    public List<Team> leaderboard(TeamManager teamManager, boolean cacheEnabled) {
        if (!cacheEnabled) {
            return teamManager.getLeaderboard();
        }
        List<Team> snap = snapshot;
        if (snap == null) {
            snap = teamManager.getLeaderboard();
            snapshot = snap;
        }
        return snap;
    }

    public void invalidate() {
        snapshot = null;
    }
}
