package be.panchito.pointRush.shop;

import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CoinShopService {

    private final MongoPlayerCoinRepository coins;
    private final CoinCreditRegistry creditRegistry;
    private final List<String> trackedCoinIds;

    public CoinShopService(MongoPlayerCoinRepository coins, CoinCreditRegistry creditRegistry, Iterable<String> poolCoinIds) {
        this.coins = coins;
        this.creditRegistry = creditRegistry;
        Set<String> merged = new LinkedHashSet<>();
        for (String id : creditRegistry.allIds()) {
            merged.add(id);
        }
        for (String id : poolCoinIds) {
            merged.add(id);
        }
        this.trackedCoinIds = List.copyOf(merged);
    }

    public CoinCreditRegistry credits() {
        return creditRegistry;
    }

    public int balanceCredits(UUID playerId) {
        if (coins == null) {
            return 0;
        }
        Map<String, Integer> totals = coins.getTotals(playerId, trackedCoinIds);
        int sum = 0;
        for (Map.Entry<String, Integer> e : totals.entrySet()) {
            int w = creditRegistry.creditsPerCoin(e.getKey());
            if (w > 0) {
                sum += e.getValue() * w;
            }
        }
        return sum;
    }

    public Map<String, Integer> snapshotTotals(UUID playerId) {
        if (coins == null) {
            return Map.of();
        }
        return coins.getTotals(playerId, trackedCoinIds);
    }

    /**
     * @return foutmelding of {@code null} bij succes
     */
    public String tryPurchase(Player buyer, ShopOfferView offer) {
        MongoPlayerCoinRepository repo = coins;
        if (repo == null) {
            return "Opslag niet beschikbaar.";
        }
        UUID id = buyer.getUniqueId();
        Map<String, Integer> totals = new LinkedHashMap<>(repo.getTotals(id, trackedCoinIds));
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String coinId : trackedCoinIds) {
            int w = creditRegistry.creditsPerCoin(coinId);
            if (w > 0) {
                weights.put(coinId, w);
            }
        }
        Optional<Map<String, Integer>> plan = CoinSpendPlanner.planPayment(totals, weights, offer.priceCredits());
        if (plan.isEmpty()) {
            return "Je hebt niet genoeg Rush-tegoed (nu nodig: " + offer.priceCredits() + ").";
        }
        boolean ok = repo.tryPurchaseShopCharge(id, plan.get(), offer.perkId());
        if (!ok) {
            return "Aankoop mislukt (concurrentie of ontbrekende munten). Probeer opnieuw.";
        }
        return null;
    }

    public ShopOfferView findOffer(String perkId) {
        for (ShopOfferView o : ShopCatalog.allOffers()) {
            if (o.perkId().equals(perkId)) {
                return o;
            }
        }
        return null;
    }

    public List<String> trackedIdsForTests() {
        return new ArrayList<>(trackedCoinIds);
    }
}
