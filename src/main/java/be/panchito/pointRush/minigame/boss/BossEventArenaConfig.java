package be.panchito.pointRush.minigame.boss;

import org.bukkit.Location;

import java.util.Arrays;
import java.util.Objects;

/**
 * Statische arena-configuratie (spawn + 3 MythicMobs per ronde).
 */
public final class BossEventArenaConfig {

    private final String id;
    private final Location playerSpawn;
    private final Location bossSpawn;
    private final String[] roundBosses;

    public BossEventArenaConfig(String id, Location playerSpawn, Location bossSpawn, String[] roundBosses) {
        this.id = Objects.requireNonNull(id, "id");
        this.playerSpawn = playerSpawn;
        this.bossSpawn = bossSpawn;
        this.roundBosses = roundBosses != null ? roundBosses.clone() : new String[3];
    }

    public String getId() {
        return id;
    }

    public Location getPlayerSpawn() {
        return playerSpawn != null ? playerSpawn.clone() : null;
    }

    public Location getBossSpawn() {
        return bossSpawn != null ? bossSpawn.clone() : null;
    }

    public String getRoundBoss(int round) {
        if (round < 1 || round > roundBosses.length) {
            return null;
        }
        String boss = roundBosses[round - 1];
        return boss != null && !boss.isBlank() ? boss : null;
    }

    public void setRoundBoss(int round, String mobId) {
        if (round < 1 || round > roundBosses.length) {
            return;
        }
        roundBosses[round - 1] = mobId;
    }

    public String[] getRoundBosses() {
        return roundBosses.clone();
    }

    public boolean isRoundReady(int round) {
        return getRoundBoss(round) != null && playerSpawn != null && bossSpawn != null;
    }

    public boolean isReady() {
        return isReadyFor(roundBosses.length);
    }

    /** Klaar voor {@code rounds} arena-rondes (spawns gezet + boss per ronde gevuld). */
    public boolean isReadyFor(int rounds) {
        if (playerSpawn == null || bossSpawn == null) {
            return false;
        }
        int needed = Math.max(1, Math.min(roundBosses.length, rounds));
        for (int round = 1; round <= needed; round++) {
            if (getRoundBoss(round) == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BossEventArenaConfig that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "BossEventArenaConfig{id='" + id + "', bosses=" + Arrays.toString(roundBosses) + "}";
    }
}
