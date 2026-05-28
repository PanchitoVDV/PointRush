package be.panchito.pointRush.storage.mongo;

import be.panchito.pointRush.random.EventScheduleState;
import be.panchito.pointRush.random.SpinState;
import be.panchito.pointRush.random.UpcomingEvent;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class MongoScheduledEventRepository {

    private static final String DOC_ID = "current";

    private final MongoCollection<Document> schedule;

    public MongoScheduledEventRepository(MongoClient client, String database, String collectionName) {
        this.schedule = client.getDatabase(database).getCollection(collectionName);
    }

    public EventScheduleState loadOrDefault(List<String> defaultPool) {
        Document doc = schedule.find(Filters.eq("_id", DOC_ID)).first();
        if (doc == null) {
            return new EventScheduleState(defaultPool, null, SpinState.idle());
        }
        List<String> pool = doc.getList("pool", String.class);
        if (pool == null || pool.isEmpty()) {
            pool = new ArrayList<>(defaultPool);
        }
        UpcomingEvent upcoming = parseUpcoming(doc.get("upcoming", Document.class));
        SpinState spin = parseSpin(doc.get("spin", Document.class));
        return new EventScheduleState(pool, upcoming, spin);
    }

    public void save(EventScheduleState state) {
        Document doc = new Document("_id", DOC_ID)
                .append("pool", new ArrayList<>(state.pool()));
        if (state.upcoming() != null) {
            UpcomingEvent u = state.upcoming();
            doc.append("upcoming", new Document()
                    .append("eventId", u.eventId())
                    .append("displayName", u.displayName())
                    .append("scheduledFor", u.scheduledFor().toString())
                    .append("selectedAt", u.selectedAtMillis())
                    .append("status", u.status().name()));
        }
        SpinState spin = state.spin();
        if (spin != null && (spin.active() || !spin.sequence().isEmpty())) {
            doc.append("spin", new Document()
                    .append("active", spin.active())
                    .append("startedAt", spin.startedAtMillis())
                    .append("candidateIds", spin.candidateIds())
                    .append("candidateNames", spin.candidateNames())
                    .append("sequence", spin.sequence())
                    .append("winnerId", spin.winnerId()));
        }
        schedule.replaceOne(Filters.eq("_id", DOC_ID), doc, new ReplaceOptions().upsert(true));
    }

    private static UpcomingEvent parseUpcoming(Document doc) {
        if (doc == null) {
            return null;
        }
        String eventId = doc.getString("eventId");
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        String displayName = doc.getString("displayName");
        String dateStr = doc.getString("scheduledFor");
        LocalDate scheduledFor = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now().plusDays(1);
        long selectedAt = doc.get("selectedAt") instanceof Number n ? n.longValue() : System.currentTimeMillis();
        String statusStr = doc.getString("status");
        UpcomingEvent.UpcomingStatus status = UpcomingEvent.UpcomingStatus.SCHEDULED;
        if (statusStr != null) {
            try {
                status = UpcomingEvent.UpcomingStatus.valueOf(statusStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new UpcomingEvent(
                eventId,
                displayName == null ? eventId : displayName,
                scheduledFor,
                selectedAt,
                status
        );
    }

    private static SpinState parseSpin(Document doc) {
        if (doc == null) {
            return SpinState.idle();
        }
        boolean active = Boolean.TRUE.equals(doc.getBoolean("active"));
        long startedAt = doc.get("startedAt") instanceof Number n ? n.longValue() : 0L;
        List<String> candidateIds = doc.getList("candidateIds", String.class);
        List<String> candidateNames = doc.getList("candidateNames", String.class);
        List<String> sequence = doc.getList("sequence", String.class);
        String winnerId = doc.getString("winnerId");
        if (candidateIds == null) candidateIds = List.of();
        if (candidateNames == null) candidateNames = List.of();
        if (sequence == null) sequence = List.of();
        return new SpinState(active, startedAt, candidateIds, candidateNames, sequence, winnerId);
    }
}
