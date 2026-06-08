package be.panchito.pointRush.minigame.boss;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Per-player runtime state voor Boss Event.
 * Inventories are managed per world by Multiverse-Inventories.
 */
public final class BossEventPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private String arenaId;
    private boolean arenaAlive = true;
    private boolean arenaSurvivor;
    private boolean finalAlive = true;
    private boolean finalSurvivor;
    private int pointsEarned;

    public BossEventPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
        this.uuid = uuid;
        this.savedLocation = savedLocation;
        this.savedGameMode = savedGameMode;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getSavedLocation() {
        return savedLocation;
    }

    public GameMode getSavedGameMode() {
        return savedGameMode;
    }

    public String getArenaId() {
        return arenaId;
    }

    public void setArenaId(String arenaId) {
        this.arenaId = arenaId;
    }

    /** Nog actief in arena-fase (niet uitgeschakeld door dood). */
    public boolean isArenaAlive() {
        return arenaAlive;
    }

    public void setArenaAlive(boolean arenaAlive) {
        this.arenaAlive = arenaAlive;
    }

    /** Overleefde alle 3 arena-rondes — bepaald na ronde 3. */
    public boolean isArenaSurvivor() {
        return arenaSurvivor;
    }

    public void setArenaSurvivor(boolean arenaSurvivor) {
        this.arenaSurvivor = arenaSurvivor;
    }

    /** Nog actief in finaalronde. */
    public boolean isFinalAlive() {
        return finalAlive;
    }

    public void setFinalAlive(boolean finalAlive) {
        this.finalAlive = finalAlive;
    }

    public boolean isFinalSurvivor() {
        return finalSurvivor;
    }

    public void setFinalSurvivor(boolean finalSurvivor) {
        this.finalSurvivor = finalSurvivor;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }

    /** Spectator in huidige fase (arena of finaal). */
    public boolean isSpectatingInPhase(BossEventGame.Phase phase) {
        return switch (phase) {
            case ARENA_ROUND, ARENA_INTERMISSION -> !arenaAlive;
            case FINAL_ROUND, FINAL_COUNTDOWN -> !finalAlive;
            default -> false;
        };
    }
}
