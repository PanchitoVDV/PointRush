package be.panchito.pointRush.storage.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-speler Nexo coins + shop-laadjes ({@code shop_charges}).
 * Document: {@code {_id: uuid-string, coins: {...}, shop_charges: {...}}}.
 */
public final class MongoPlayerCoinRepository {

    private final MongoCollection<Document> profiles;

    public MongoPlayerCoinRepository(MongoClient client, String database, String collectionName) {
        this.profiles = client.getDatabase(database).getCollection(collectionName);
    }

    /**
     * Verhoogt de teller atomisch (upsert). Mongo maakt automatisch nested {@code coins} aan via dot-notatie.
     */
    public void incrementCollected(UUID player, String nexoItemId, int delta) {
        if (delta == 0) {
            return;
        }
        profiles.updateOne(
                Filters.eq("_id", player.toString()),
                Updates.inc("coins." + nexoItemId, delta),
                new UpdateOptions().upsert(true));
    }

    /** Som van alle ingestelde coin-types (inclusief 0 voor types die nog niet gescoord zijn). */
    public Map<String, Integer> getTotals(UUID player, Iterable<String> knownIds) {
        Document doc = profiles.find(Filters.eq("_id", player.toString())).first();
        @SuppressWarnings("unchecked")
        Map<String, Integer> extracted = extractCoins(doc);

        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : knownIds) {
            out.put(id, extracted.getOrDefault(id, 0));
        }
        for (Map.Entry<String, Integer> e : extracted.entrySet()) {
            out.putIfAbsent(e.getKey(), e.getValue());
        }
        return out;
    }

    public Map<String, Integer> getShopCharges(UUID player) {
        Document doc = profiles.find(Filters.eq("_id", player.toString())).first();
        return extractShopCharges(doc);
    }

    /**
     * Trekt munten af en verhoogt één shop-laadtelling atomisch (alleen als er genoeg munten zijn).
     */
    public boolean tryPurchaseShopCharge(UUID player, Map<String, Integer> coinSpendPositiveCounts, String perkKey) {
        if (coinSpendPositiveCounts.isEmpty()) {
            UpdateResult r = profiles.updateOne(
                    Filters.eq("_id", player.toString()),
                    Updates.inc("shop_charges." + perkKey, 1),
                    new UpdateOptions().upsert(true));
            return r.getModifiedCount() > 0 || r.getUpsertedId() != null;
        }
        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("_id", player.toString()));
        for (Map.Entry<String, Integer> e : coinSpendPositiveCounts.entrySet()) {
            int need = e.getValue();
            if (need <= 0) {
                continue;
            }
            conditions.add(Filters.gte("coins." + e.getKey(), need));
        }
        List<Bson> incs = new ArrayList<>();
        for (Map.Entry<String, Integer> e : coinSpendPositiveCounts.entrySet()) {
            int need = e.getValue();
            if (need > 0) {
                incs.add(Updates.inc("coins." + e.getKey(), -need));
            }
        }
        incs.add(Updates.inc("shop_charges." + perkKey, 1));
        UpdateResult r = profiles.updateOne(Filters.and(conditions), Updates.combine(incs));
        return r.getModifiedCount() == 1;
    }

    /** Één gebruikte boost aan het begin van een minigame; alleen als voorraad &gt; 0. */
    public boolean tryConsumeShopCharge(UUID player, String perkKey) {
        UpdateResult r = profiles.updateOne(
                Filters.and(
                        Filters.eq("_id", player.toString()),
                        Filters.gt("shop_charges." + perkKey, 0)),
                Updates.inc("shop_charges." + perkKey, -1));
        return r.getModifiedCount() == 1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> extractCoins(Document doc) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (doc == null) {
            return out;
        }

        Object coinsObj = doc.get("coins");
        if (!(coinsObj instanceof Document coins)) {
            return out;
        }
        for (String key : coins.keySet()) {
            Object raw = coins.get(key);
            int v = raw instanceof Number n ? n.intValue() : 0;
            out.put(key, v);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> extractShopCharges(Document doc) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (doc == null) {
            return out;
        }
        Object raw = doc.get("shop_charges");
        if (!(raw instanceof Document sub)) {
            return out;
        }
        for (String key : sub.keySet()) {
            Object v = sub.get(key);
            int n = v instanceof Number num ? num.intValue() : 0;
            if (n > 0) {
                out.put(key, n);
            }
        }
        return out;
    }
}
