package be.panchito.pointRush.team;

import net.kyori.adventure.text.format.NamedTextColor;

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
}
