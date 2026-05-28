package be.panchito.pointRush.shop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plant hoeveelheid per Nexo-id om een vast credit-bedrag te betalen (bounded DFS).
 */
public final class CoinSpendPlanner {

    private CoinSpendPlanner() {
    }

    /**
     * @param availableCounts huidige totals per nexo id
     * @param creditPerCoin   credits per muntstuk (ontbrekende ids → 0)
     * @param priceCredits    target som
     * @return positieve aantallen per id om af te trekken, of leeg
     */
    public static Optional<Map<String, Integer>> planPayment(
            Map<String, Integer> availableCounts,
            Map<String, Integer> creditPerCoin,
            int priceCredits
    ) {
        if (priceCredits <= 0) {
            return Optional.of(Map.of());
        }
        Map<String, Integer> avail = new HashMap<>();
        for (Map.Entry<String, Integer> e : availableCounts.entrySet()) {
            int n = e.getValue();
            if (n > 0 && creditPerCoin.getOrDefault(e.getKey(), 0) > 0) {
                avail.put(e.getKey(), n);
            }
        }
        List<String> ids = new ArrayList<>(avail.keySet());
        ids.sort(Comparator.comparingInt((String id) -> creditPerCoin.getOrDefault(id, 0)).reversed());
        Map<String, Integer> spend = new LinkedHashMap<>();
        if (!dfs(0, ids, priceCredits, avail, spend, creditPerCoin)) {
            return Optional.empty();
        }
        return Optional.of(Map.copyOf(spend));
    }

    private static boolean dfs(
            int idx,
            List<String> ids,
            int remaining,
            Map<String, Integer> avail,
            Map<String, Integer> spend,
            Map<String, Integer> creditPerCoin
    ) {
        if (remaining == 0) {
            return true;
        }
        if (idx >= ids.size()) {
            return false;
        }
        String id = ids.get(idx);
        int unit = creditPerCoin.getOrDefault(id, 0);
        if (unit <= 0) {
            return dfs(idx + 1, ids, remaining, avail, spend, creditPerCoin);
        }
        int maxTake = Math.min(avail.getOrDefault(id, 0), remaining / unit);
        for (int take = maxTake; take >= 0; take--) {
            int pay = take * unit;
            if (pay > remaining) {
                continue;
            }
            if (take > 0) {
                spend.put(id, take);
                avail.put(id, avail.getOrDefault(id, 0) - take);
            } else {
                spend.remove(id);
            }
            if (dfs(idx + 1, ids, remaining - pay, avail, spend, creditPerCoin)) {
                return true;
            }
            if (take > 0) {
                avail.put(id, avail.getOrDefault(id, 0) + take);
            }
            spend.remove(id);
        }
        return false;
    }
}
