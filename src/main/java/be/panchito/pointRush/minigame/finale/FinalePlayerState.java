package be.panchito.pointRush.minigame.finale;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Runtime-state voor één deelnemer in een Finale-event.
 *
 * <p>Bewaart de pre-event snapshot (locatie, gamemode) voor restore bij stop/quit,
 * de toegewezen random spawn, en de live status: of de speler nog leeft, hoeveel
 * kills hij maakte, en zijn uiteindelijke plaatsing. De laatste-aanvaller wordt
 * kort bijgehouden om kills correct toe te kennen.
 */
public final class FinalePlayerState {

    private final UUID uuid;
    private final Location savedLocation;
    private final GameMode savedGameMode;

    /** De random startlocatie die deze speler kreeg toegewezen. */
    private Location spawn;

    private boolean alive = true;
    private int kills = 0;
    /** Uiteindelijke plaatsing (1 = winnaar). 0 zolang in leven. */
    private int placement = 0;
    private long eliminatedAtMs = 0L;

    /** UUID van wie deze speler het laatst raakte (voor kill-credit). */
    private UUID lastDamager;
    private long lastDamagerAtMs = 0L;

    public FinalePlayerState(UUID uuid, Location savedLocation, GameMode savedGameMode) {
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

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getPlacement() {
        return placement;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    public long getEliminatedAtMs() {
        return eliminatedAtMs;
    }

    public void setEliminatedAtMs(long eliminatedAtMs) {
        this.eliminatedAtMs = eliminatedAtMs;
    }

    public UUID getLastDamager() {
        return lastDamager;
    }

    public void recordDamager(UUID damager, long atMs) {
        this.lastDamager = damager;
        this.lastDamagerAtMs = atMs;
    }

    /** True wanneer de laatste rake klap recent genoeg was om als kill te tellen. */
    public boolean hasRecentDamager(long nowMs, long windowMs) {
        return lastDamager != null && (nowMs - lastDamagerAtMs) <= windowMs;
    }
}
