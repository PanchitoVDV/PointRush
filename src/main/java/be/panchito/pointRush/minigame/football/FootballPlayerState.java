package be.panchito.pointRush.minigame.football;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Per-player runtime state voor een lopend Voetbal-event. We bewaren de start-locatie en
 * gamemode zodat we de speler ná afloop met onze eigen teleport terugzetten — BlockBall zelf
 * stuurt spelers naar zijn eigen leave-spawn, dat overschrijven we bewust.
 */
public final class FootballPlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    private FootballSide side;
    private int pointsEarned = 0;

    public FootballPlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
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

    public FootballSide getSide() {
        return side;
    }

    public void setSide(FootballSide side) {
        this.side = side;
    }

    public int getPointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(int amount) {
        this.pointsEarned += amount;
    }
}
