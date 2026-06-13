package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.team.Team;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
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
     * Upserts metadata (name, leader, color, members, home) for every team <em>without</em> touching
     * {@code points}. Safe to run from multiple servers against one database: it never deletes rows it
     * doesn't know about and never clobbers a points value written atomically elsewhere.
     */
    public void upsertAllMetadata(Iterable<Team> liveTeams) {
        for (Team team : liveTeams) {
            upsertMetadata(team);
        }
    }

    /**
     * Upserts a single team's metadata. The {@code points} field is only set on insert
     * ({@code $setOnInsert}); on an existing document it is left untouched so concurrent atomic
     * point writes from another server are never overwritten.
     */
    public void upsertMetadata(Team team) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set("name", team.getName()));
        updates.add(Updates.set("leader", team.getLeader().toString()));
        updates.add(Updates.set("color", team.getColor().toString()));
        updates.add(Updates.set("members", team.getMembers().stream().map(UUID::toString).toList()));
        Location home = team.getHome();
        if (home != null && home.getWorld() != null) {
            updates.add(Updates.set("home", homeDocument(home)));
        } else {
            updates.add(Updates.unset("home"));
        }
        updates.add(Updates.setOnInsert("points", team.getPoints()));
        teams.updateOne(Filters.eq("_id", team.getId().toString()),
                Updates.combine(updates),
                new UpdateOptions().upsert(true));
    }

    /**
     * Atomically adds {@code delta} to a team's points ({@code $inc}). Commutative, so simultaneous
     * awards from multiple servers can never lose an update. Self-heals if the document does not exist
     * yet by writing the full team (with its already-updated in-memory points).
     */
    public void incrementPoints(Team team, long delta) {
        long matched = teams.updateOne(Filters.eq("_id", team.getId().toString()),
                Updates.inc("points", delta)).getMatchedCount();
        if (matched == 0) {
            teams.replaceOne(Filters.eq("_id", team.getId().toString()),
                    toDocument(team), new ReplaceOptions().upsert(true));
        }
    }

    /**
     * Authoritatively sets a team's points ({@code $set}). Used for admin set/reset/remove — rare and
     * intentionally last-writer-wins, unlike the commutative {@link #incrementPoints} hot path.
     */
    public void setPoints(Team team, long value) {
        long matched = teams.updateOne(Filters.eq("_id", team.getId().toString()),
                Updates.set("points", value)).getMatchedCount();
        if (matched == 0) {
            teams.replaceOne(Filters.eq("_id", team.getId().toString()),
                    toDocument(team), new ReplaceOptions().upsert(true));
        }
    }

    /** Deletes a single team. Called explicitly on disband — routine saves never delete. */
    public void deleteTeam(UUID id) {
        teams.deleteOne(Filters.eq("_id", id.toString()));
    }

    /** Deletes every team. Returns the number removed. */
    public long deleteAll() {
        return teams.deleteMany(new Document()).getDeletedCount();
    }

    private static Document toDocument(Team team) {
        List<String> memberStrings = team.getMembers().stream().map(UUID::toString).toList();
        Document doc = new Document("_id", team.getId().toString())
                .append("name", team.getName())
                .append("leader", team.getLeader().toString())
                .append("color", team.getColor().toString())
                .append("points", team.getPoints())
                .append("members", memberStrings);
        Location home = team.getHome();
        if (home != null && home.getWorld() != null) {
            doc.append("home", homeDocument(home));
        }
        return doc;
    }

    private static Document homeDocument(Location home) {
        return new Document("world", home.getWorld().getName())
                .append("x", home.getX())
                .append("y", home.getY())
                .append("z", home.getZ())
                .append("yaw", (double) home.getYaw())
                .append("pitch", (double) home.getPitch());
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
        long points = doc.get("points") instanceof Number n ? n.longValue() : 0L;

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

        if (doc.get("home") instanceof Document home) {
            String worldName = home.getString("world");
            World world = worldName != null ? Bukkit.getWorld(worldName) : null;
            if (world != null) {
                team.setHome(new Location(
                        world,
                        asDouble(home.get("x")),
                        asDouble(home.get("y")),
                        asDouble(home.get("z")),
                        (float) asDouble(home.get("yaw")),
                        (float) asDouble(home.get("pitch"))));
            }
        }
        return team;
    }

    private static double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }
}
