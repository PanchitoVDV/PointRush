package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.team.Team;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bson.Document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores teams (names, leaders, colors, points and member UUID lists) in MongoDB.
 */
public final class MongoTeamRepository {

    private final MongoCollection<Document> teams;

    public MongoTeamRepository(MongoClient client, String database, String collectionName) {
        this.teams = client.getDatabase(database).getCollection(collectionName);
    }

    public List<Team> loadAll(Logger log) {
        List<Team> out = new ArrayList<>();
        for (Document doc : teams.find()) {
            try {
                Team team = fromDocument(doc);
                if (team != null) {
                    out.add(team);
                }
            } catch (Exception ex) {
                log.log(Level.WARNING, "Kon team-document niet laden: " + doc.get("_id"), ex);
            }
        }
        return out;
    }

    /**
     * Upserts every live team and deletes Mongo rows that no longer exist in-memory.
     */
    public void syncAll(Iterable<Team> liveTeams) {
        Set<String> wanted = new HashSet<>();
        for (Team team : liveTeams) {
            wanted.add(team.getId().toString());
            teams.replaceOne(
                    Filters.eq("_id", team.getId().toString()),
                    toDocument(team),
                    new ReplaceOptions().upsert(true));
        }
        if (wanted.isEmpty()) {
            teams.deleteMany(new Document());
            return;
        }
        teams.deleteMany(Filters.nin("_id", wanted));
    }

    private static Document toDocument(Team team) {
        List<String> memberStrings = team.getMembers().stream().map(UUID::toString).toList();
        return new Document("_id", team.getId().toString())
                .append("name", team.getName())
                .append("leader", team.getLeader().toString())
                .append("color", team.getColor().toString())
                .append("points", team.getPoints())
                .append("members", memberStrings);
    }

    private static Team fromDocument(Document doc) {
        Object rawId = doc.get("_id");
        if (rawId == null) {
            return null;
        }
        UUID id = UUID.fromString(rawId.toString());
        String name = doc.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        String leaderStr = doc.getString("leader");
        if (leaderStr == null) {
            return null;
        }
        UUID leader = UUID.fromString(leaderStr);
        String colorName = doc.getString("color");
        if (colorName == null) {
            colorName = "WHITE";
        }
        NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase());
        if (color == null) {
            color = NamedTextColor.WHITE;
        }
        long points = doc.get("points") instanceof Number n ? n.longValue() : doc.getLong("points");

        Team team = new Team(id, name, leader, color);
        team.setPoints(points);

        @SuppressWarnings("unchecked")
        List<String> memberStrings = doc.getList("members", String.class);
        if (memberStrings != null) {
            for (String memberId : memberStrings) {
                try {
                    team.addMemberRaw(UUID.fromString(memberId));
                } catch (IllegalArgumentException ignored) {
                    // skip bad UUID entry
                }
            }
        }
        return team;
    }
}
