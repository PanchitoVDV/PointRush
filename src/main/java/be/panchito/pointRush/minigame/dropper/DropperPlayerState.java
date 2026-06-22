package be.panchito.pointRush.minigame.dropper;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Runtime-state voor één deelnemer in een Dropper-event.
 *
 * <p>Bewaart de pre-event snapshot (locatie, gamemode) voor restore bij stop/quit
 * en de live status: of de speler nog in het toernooi zit ({@link #isAlive()}),
 * of hij de huidige ronde al voltooide, hoeveel rondes hij haalde en zijn
 * uiteindelijke plaatsing. Inventories worden per wereld door Multiverse-Inventories
 * beheerd.
 */
public final class DropperPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private boolean alive = true;
    /** True zodra de speler de huidige ronde voltooid heeft (gereset bij elke nieuwe ronde). */
    private boolean completedCurrentRound = false;
    /** Aantal rondes dat deze speler volledig haalde. */
    private int roundsCompleted = 0;
    /** Wall-clock ms toen de speler werd uitgeschakeld (0 = nog in leven). */
    private long eliminatedAtMs = 0L;
    /** Uiteindelijke plaatsing (1 = winnaar). 0 zolang in leven. */
    private int placement = 0;

    public DropperPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
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

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public boolean hasCompletedCurrentRound() {
        return completedCurrentRound;
    }

    public void setCompletedCurrentRound(boolean completedCurrentRound) {
        this.completedCurrentRound = completedCurrentRound;
    }

    public int getRoundsCompleted() {
        return roundsCompleted;
    }

    public void incrementRoundsCompleted() {
        this.roundsCompleted++;
    }

    public long getEliminatedAtMs() {
        return eliminatedAtMs;
    }

    public void setEliminatedAtMs(long eliminatedAtMs) {
        this.eliminatedAtMs = eliminatedAtMs;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }
}
