package be.panchito.pointRush.minigame.boss;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state per actieve arena tijdens een Boss Event.
 */
public final class BossEventArenaRun {

    private final BossEventArenaConfig config;
    private final Set<UUID> players = new HashSet<>();
    private UUID activeBossId;
    private boolean roundComplete;

    public BossEventArenaRun(BossEventArenaConfig config) {
        this.config = config;
    }

    public BossEventArenaConfig getConfig() {
        return config;
    }

    public String getId() {
        return config.getId();
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public void addPlayer(UUID playerId) {
        players.add(playerId);
    }

    public UUID getActiveBossId() {
        return activeBossId;
    }

    public void setActiveBossId(UUID activeBossId) {
        this.activeBossId = activeBossId;
    }

    public boolean isRoundComplete() {
        return roundComplete;
    }

    public void setRoundComplete(boolean roundComplete) {
        this.roundComplete = roundComplete;
    }

    public void resetRoundState() {
        activeBossId = null;
        roundComplete = false;
    }

    public int countArenaAlive(BossEventGame game) {
        int alive = 0;
        for (UUID id : players) {
            BossEventPlayerState ps = game.getPlayerState(id);
            if (ps != null && ps.isArenaAlive()) {
                alive++;
            }
        }
        return alive;
    }
}
