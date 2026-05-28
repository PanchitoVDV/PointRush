package be.panchito.pointRush.shop;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Credits per Nexo-id (Yaml {@code shop.coin-credits}). */
public final class CoinCreditRegistry {

    private final Map<String, Integer> credits;

    private CoinCreditRegistry(Map<String, Integer> credits) {
        this.credits = Collections.unmodifiableMap(credits);
    }

    public static CoinCreditRegistry load(UnifiedSettings unified) {
        Map<String, Integer> map = new LinkedHashMap<>(defaults());
        ConfigurationSection sec = unified.yaml().getConfigurationSection("shop.coin-credits");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int v = Math.max(1, sec.getInt(key, 1));
                map.put(key, v);
            }
        }
        return new CoinCreditRegistry(map);
    }

    public Set<String> allIds() {
        return credits.keySet();
    }

    public int creditsPerCoin(String nexoId) {
        return credits.getOrDefault(nexoId, 0);
    }

    private static Map<String, Integer> defaults() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("coin_golden_cross_anim", 8);
        m.put("coin_golden_diamond_anim", 38);
        m.put("coin_golden_emerald_anim", 30);
        m.put("coin_golden_heart_anim", 18);
        m.put("coin_golden_hole_anim", 14);
        m.put("coin_golden_long_anim", 11);
        m.put("coin_golden_person_anim", 15);
        m.put("coin_golden_pet_anim", 16);
        m.put("coin_golden_pickaxe_anim", 22);
        m.put("coin_golden_rectangle_anim", 10);
        m.put("coin_golden_redstone_anim", 26);
        m.put("coin_golden_skull_anim", 34);
        return m;
    }
}
