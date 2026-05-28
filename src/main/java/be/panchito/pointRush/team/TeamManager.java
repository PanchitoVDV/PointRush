package be.panchito.pointRush.team;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for teams, member -> team lookups and pending invites.
 * Pure in-memory; persistence is handled by the storage layer that calls into us.
 */
public final class TeamManager {

    public static final long INVITE_TIMEOUT_MS = 60_000L;

    /** Default team colors players can pick from when creating a team. */
    public static final List<NamedTextColor> AVAILABLE_COLORS = List.of(
            NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.WHITE
    );

    private final Map<UUID, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> teamsByName = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToTeam = new ConcurrentHashMap<>();

    /** invite key = invited player uuid, value = invite info. */
    private final Map<UUID, Invite> pendingInvites = new ConcurrentHashMap<>();

    public Team createTeam(String name, UUID leader, NamedTextColor color) {
        if (getTeamByName(name) != null) {
            throw new IllegalArgumentException("Een team met deze naam bestaat al.");
        }
        if (getTeamOfPlayer(leader) != null) {
            throw new IllegalStateException("Je zit al in een team.");
        }
        Team team = new Team(UUID.randomUUID(), name, leader, color);
        registerTeam(team);
        return team;
    }

    /**
     * Registers an already-constructed team. Used by the storage layer when loading
     * teams from disk.
     */
    public void registerTeam(Team team) {
        teamsById.put(team.getId(), team);
        teamsByName.put(team.getName().toLowerCase(), team.getId());
        for (UUID member : team.getMembers()) {
            playerToTeam.put(member, team.getId());
        }
    }

    public void disbandTeam(Team team) {
        teamsById.remove(team.getId());
        teamsByName.remove(team.getName().toLowerCase());
        for (UUID member : team.getMembers()) {
            playerToTeam.remove(member);
        }
        pendingInvites.values().removeIf(invite -> invite.teamId().equals(team.getId()));
    }

    public boolean addMember(Team team, UUID player) {
        if (team.isFull() || getTeamOfPlayer(player) != null) {
            return false;
        }
        if (team.addMember(player)) {
            playerToTeam.put(player, team.getId());
            pendingInvites.remove(player);
            return true;
        }
        return false;
    }

    public void removeMember(Team team, UUID player) {
        if (team.removeMember(player)) {
            playerToTeam.remove(player);
        }
    }

    public Team getTeam(UUID id) {
        return teamsById.get(id);
    }

    public Team getTeamByName(String name) {
        UUID id = teamsByName.get(name.toLowerCase());
        return id != null ? teamsById.get(id) : null;
    }

    public Team getTeamOfPlayer(UUID player) {
        UUID id = playerToTeam.get(player);
        return id != null ? teamsById.get(id) : null;
    }

    public Collection<Team> getTeams() {
        return Collections.unmodifiableCollection(teamsById.values());
    }

    /** Returns teams sorted by points (highest first). */
    public List<Team> getLeaderboard() {
        List<Team> list = new ArrayList<>(teamsById.values());
        list.sort((a, b) -> Long.compare(b.getPoints(), a.getPoints()));
        return list;
    }

    public void renameTeam(Team team, String newName) {
        if (getTeamByName(newName) != null && !team.getName().equalsIgnoreCase(newName)) {
            throw new IllegalArgumentException("Een team met deze naam bestaat al.");
        }
        teamsByName.remove(team.getName().toLowerCase());
        team.setName(newName);
        teamsByName.put(newName.toLowerCase(), team.getId());
    }

    public void clear() {
        teamsById.clear();
        teamsByName.clear();
        playerToTeam.clear();
        pendingInvites.clear();
    }

    public Map<UUID, Team> getTeamsByIdView() {
        return Collections.unmodifiableMap(new HashMap<>(teamsById));
    }

    public void invite(UUID inviter, UUID target, UUID teamId) {
        pendingInvites.put(target, new Invite(teamId, inviter, System.currentTimeMillis() + INVITE_TIMEOUT_MS));
    }

    public Invite consumeInvite(UUID target) {
        Invite invite = pendingInvites.remove(target);
        if (invite == null || invite.isExpired()) {
            return null;
        }
        return invite;
    }

    public Invite peekInvite(UUID target) {
        Invite invite = pendingInvites.get(target);
        if (invite == null) {
            return null;
        }
        if (invite.isExpired()) {
            pendingInvites.remove(target);
            return null;
        }
        return invite;
    }

    public void cancelInvite(UUID target) {
        pendingInvites.remove(target);
    }

    /**
     * Represents a pending invite for a player to join a specific team.
     */
    public record Invite(UUID teamId, UUID inviter, long expiresAt) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
