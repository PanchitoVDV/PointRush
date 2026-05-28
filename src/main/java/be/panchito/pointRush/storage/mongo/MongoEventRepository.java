package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.history.EventHistoryEntry;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors finished events to MongoDB for the stats website API.
 */
public final class MongoEventRepository {

    private final MongoCollection<Document> events;

    public MongoEventRepository(MongoClient client, String database, String collectionName) {
        this.events = client.getDatabase(database).getCollection(collectionName);
    }

    public void upsert(EventHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        events.replaceOne(
                Filters.eq("_id", entry.id()),
                toDocument(entry),
                new ReplaceOptions().upsert(true));
    }

    public void syncAll(Iterable<EventHistoryEntry> entries) {
        for (EventHistoryEntry entry : entries) {
            upsert(entry);
        }
    }

    private static Document toDocument(EventHistoryEntry entry) {
        List<Document> placements = new ArrayList<>();
        for (EventHistoryEntry.Placement p : entry.placements()) {
            Document doc = new Document("rank", p.rank())
                    .append("score", p.score());
            if (p.playerId() != null) {
                doc.append("playerId", p.playerId().toString());
            }
            if (p.playerName() != null) {
                doc.append("playerName", p.playerName());
            }
            if (p.teamId() != null) {
                doc.append("teamId", p.teamId().toString());
            }
            if (p.teamName() != null) {
                doc.append("teamName", p.teamName());
            }
            if (p.teamColor() != null) {
                doc.append("teamColor", p.teamColor());
            }
            if (p.detail() != null) {
                doc.append("detail", p.detail());
            }
            placements.add(doc);
        }

        return new Document("_id", entry.id())
                .append("type", entry.eventType())
                .append("started", entry.startedAt())
                .append("ended", entry.endedAt())
                .append("placements", placements);
    }

    /** Reconstructs an entry from Mongo (used by tests / future plugin reads). */
    public static EventHistoryEntry fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        String id = doc.getString("_id");
        String type = doc.getString("type");
        long started = doc.get("started") instanceof Number n ? n.longValue() : 0L;
        long ended = doc.get("ended") instanceof Number n ? n.longValue() : started;

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Document> raw = doc.getList("placements", Document.class);
        if (raw != null) {
            for (Document p : raw) {
                placements.add(parsePlacement(p));
            }
        }
        return new EventHistoryEntry(id, type, started, ended, placements);
    }

    private static EventHistoryEntry.Placement parsePlacement(Document raw) {
        int rank = raw.get("rank") instanceof Number n ? n.intValue() : 0;
        UUID playerId = toUuid(raw.getString("playerId"));
        String playerName = raw.getString("playerName");
        UUID teamId = toUuid(raw.getString("teamId"));
        String teamName = raw.getString("teamName");
        String teamColor = raw.getString("teamColor");
        int score = raw.get("score") instanceof Number n ? n.intValue() : 0;
        String detail = raw.getString("detail");
        return new EventHistoryEntry.Placement(
                rank, playerId, playerName, teamId, teamName, teamColor, score, detail);
    }

    private static UUID toUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
