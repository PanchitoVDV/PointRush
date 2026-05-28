package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.live.LivePlatform;
import be.panchito.pointRush.live.LiveStreamEntry;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MongoLiveStreamRepository {

    private final MongoCollection<Document> streams;

    public MongoLiveStreamRepository(MongoClient client, String database, String collectionName) {
        this.streams = client.getDatabase(database).getCollection(collectionName);
    }

    public void upsert(LiveStreamEntry entry) {
        Document doc = new Document("_id", entry.playerId().toString())
                .append("playerName", entry.playerName())
                .append("platform", entry.platform().id())
                .append("url", entry.url())
                .append("startedAt", entry.startedAt())
                .append("active", true);
        streams.replaceOne(
                Filters.eq("_id", entry.playerId().toString()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public void remove(UUID playerId) {
        if (playerId == null) {
            return;
        }
        streams.deleteOne(Filters.eq("_id", playerId.toString()));
    }

    public List<LiveStreamEntry> findAllActive() {
        List<LiveStreamEntry> out = new ArrayList<>();
        for (Document doc : streams.find(Filters.eq("active", true)).sort(Sorts.descending("startedAt"))) {
            LiveStreamEntry entry = fromDocument(doc);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    private static LiveStreamEntry fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(doc.getString("_id"));
            String name = doc.getString("playerName");
            LivePlatform platform = LivePlatform.parse(doc.getString("platform")).orElse(null);
            String url = doc.getString("url");
            long started = doc.get("startedAt") instanceof Number n ? n.longValue() : System.currentTimeMillis();
            if (platform == null || url == null || url.isBlank()) {
                return null;
            }
            return new LiveStreamEntry(id, name == null ? "?" : name, platform, url, started);
        } catch (Exception ex) {
            return null;
        }
    }
}
