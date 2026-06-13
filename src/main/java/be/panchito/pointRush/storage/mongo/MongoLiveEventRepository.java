package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.network.LiveEventState;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

/**
 * Het gedeelde {@code live_event} document: één singleton ({@code _id: "current"}) dat het lopende
 * cross-server event beschrijft. Survival schrijft PENDING, de events-host markeert RUNNING en wist het
 * doc bij afloop. Beide servers lezen het om te weten of er een event loopt.
 */
public final class MongoLiveEventRepository {

    private static final String DOC_ID = "current";

    private final MongoCollection<Document> liveEvents;

    public MongoLiveEventRepository(MongoClient client, String database, String collectionName) {
        this.liveEvents = client.getDatabase(database).getCollection(collectionName);
    }

    /** Huidige live event, of {@code null} als er geen loopt (of het doc onvolledig is). */
    public LiveEventState get() {
        Document doc = liveEvents.find(Filters.eq("_id", DOC_ID)).first();
        if (doc == null) {
            return null;
        }
        String eventId = doc.getString("eventId");
        String minigame = doc.getString("minigame");
        LiveEventState.Phase phase = LiveEventState.Phase.parse(doc.getString("phase"));
        String hostServer = doc.getString("hostServer");
        if (eventId == null || minigame == null || phase == null) {
            return null;
        }
        long startedAt = doc.get("startedAtMs") instanceof Number n ? n.longValue() : 0L;
        return new LiveEventState(eventId, minigame, phase, hostServer, startedAt);
    }

    /** Schrijft (of overschrijft) het event in PENDING: spelers worden naar de host gestuurd. */
    public void setPending(String eventId, String minigame, String hostServer, long startedAtMs) {
        Document doc = new Document("_id", DOC_ID)
                .append("eventId", eventId)
                .append("minigame", minigame)
                .append("phase", LiveEventState.Phase.PENDING.name())
                .append("hostServer", hostServer)
                .append("startedAtMs", startedAtMs);
        liveEvents.replaceOne(Filters.eq("_id", DOC_ID), doc, new ReplaceOptions().upsert(true));
    }

    /** Markeert het event RUNNING (alleen als het nog dezelfde {@code eventId} betreft). */
    public void markRunning(String eventId) {
        liveEvents.updateOne(
                Filters.and(Filters.eq("_id", DOC_ID), Filters.eq("eventId", eventId)),
                Updates.set("phase", LiveEventState.Phase.RUNNING.name()));
    }

    /** Wist het live event (na afloop). */
    public void clear() {
        liveEvents.deleteOne(Filters.eq("_id", DOC_ID));
    }
}
