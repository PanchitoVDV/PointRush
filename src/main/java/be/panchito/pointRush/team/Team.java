package be.panchito.pointRush.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single PointRush team.
 * A team has a unique id, a (case-insensitive unique) display name,
 * up to {@link #MAX_MEMBERS} members, a color and a points score.
 */
public final class Team {

    public static final int MAX_MEMBERS = 4;

    private final UUID id;
    private String name;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private NamedTextColor color;
    private long points;

    /**
     * Shared team teleport target set via /team sethome. Stored as raw world-name + coords (not a
     * {@link Location}) so the home survives even when its world isn't loaded yet — e.g. a Multiverse
     * world that loads after the team data, or while an event has the world unloaded. The {@link Location}
     * is resolved lazily in {@link #getHome()} and never dropped from storage just because the world is
     * momentarily unavailable.
     */
    private String homeWorld;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float homeYaw;
    private float homePitch;
    private boolean hasHome;
    private Location homeCache;

    public Team(UUID id, String name, UUID leader, NamedTextColor color) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.color = color;
        this.members.add(leader);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    /**
     * Used by the storage layer to repopulate the team without mutating the
     * leader/order semantics from public callers.
     */
    public void addMemberRaw(UUID uuid) {
        members.add(uuid);
    }

    public boolean addMember(UUID uuid) {
        if (members.size() >= MAX_MEMBERS) {
            return false;
        }
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    public int size() {
        return members.size();
    }

    public NamedTextColor getColor() {
        return color;
    }

    public void setColor(NamedTextColor color) {
        this.color = color;
    }

    public long getPoints() {
        return points;
    }

    public void setPoints(long points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(long amount) {
        this.points = Math.max(0, this.points + amount);
    }

    public void removePoints(long amount) {
        this.points = Math.max(0, this.points - amount);
    }

    /** True als er een home is ingesteld, los van of de doelwereld nu geladen is. */
    public boolean hasHome() {
        return hasHome;
    }

    /**
     * Resolvet de home lui: de wereld kan later geladen zijn (bv. via Multiverse) dan dit team. Geeft
     * {@code null} als er geen home is of de wereld (nog) niet geladen is — de opgeslagen home blijft
     * dan wel bewaard.
     */
    public Location getHome() {
        if (!hasHome || homeWorld == null) {
            return null;
        }
        if (homeCache != null && homeCache.getWorld() != null) {
            return homeCache.clone();
        }
        World world = Bukkit.getWorld(homeWorld);
        if (world == null) {
            return null;
        }
        homeCache = new Location(world, homeX, homeY, homeZ, homeYaw, homePitch);
        return homeCache.clone();
    }

    public void setHome(Location home) {
        if (home == null || home.getWorld() == null) {
            clearHome();
            return;
        }
        this.homeWorld = home.getWorld().getName();
        this.homeX = home.getX();
        this.homeY = home.getY();
        this.homeZ = home.getZ();
        this.homeYaw = home.getYaw();
        this.homePitch = home.getPitch();
        this.hasHome = true;
        this.homeCache = home.clone();
    }

    /** Herstelt de home uit opslag zonder dat de wereld geladen hoeft te zijn. */
    public void setHomeRaw(String world, double x, double y, double z, float yaw, float pitch) {
        if (world == null) {
            clearHome();
            return;
        }
        this.homeWorld = world;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeYaw = yaw;
        this.homePitch = pitch;
        this.hasHome = true;
        this.homeCache = null;
    }

    public void clearHome() {
        this.hasHome = false;
        this.homeWorld = null;
        this.homeCache = null;
    }

    // Raw accessors voor de opslaglaag: persisteer de home ongeacht of de wereld geladen is.
    public String getHomeWorld() {
        return homeWorld;
    }

    public double getHomeX() {
        return homeX;
    }

    public double getHomeY() {
        return homeY;
    }

    public double getHomeZ() {
        return homeZ;
    }

    public float getHomeYaw() {
        return homeYaw;
    }

    public float getHomePitch() {
        return homePitch;
    }
}
