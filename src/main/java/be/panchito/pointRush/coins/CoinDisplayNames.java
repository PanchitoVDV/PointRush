package be.panchito.pointRush.coins;

import java.util.Locale;
import java.util.Map;

/**
 * Nederlandse namen voor Rush-munt skins (zelfde items, andere labels in de UI).
 */
public final class CoinDisplayNames {

    private static final Map<String, String> BY_ID = Map.ofEntries(
            Map.entry("coin_golden_cross_anim", "Gouden kruis"),
            Map.entry("coin_golden_diamond_anim", "Gouden diamant"),
            Map.entry("coin_golden_emerald_anim", "Gouden smaragd"),
            Map.entry("coin_golden_heart_anim", "Gouden hart"),
            Map.entry("coin_golden_hole_anim", "Gouden ring"),
            Map.entry("coin_golden_long_anim", "Gouden staaf"),
            Map.entry("coin_golden_person_anim", "Gouden poppetje"),
            Map.entry("coin_golden_pet_anim", "Gouden pootafdruk"),
            Map.entry("coin_golden_pickaxe_anim", "Gouden houweel"),
            Map.entry("coin_golden_rectangle_anim", "Gouden plaat"),
            Map.entry("coin_golden_redstone_anim", "Gouden vonk"),
            Map.entry("coin_golden_skull_anim", "Gouden schedel")
    );

    private CoinDisplayNames() {
    }

    /** Vriendelijke naam voor UI en chat; valt terug op een opgeschoonde variant van het id. */
    public static String friendlyName(String nexoItemId) {
        if (nexoItemId == null || nexoItemId.isEmpty()) {
            return "?";
        }
        String mapped = BY_ID.get(nexoItemId);
        if (mapped != null) {
            return mapped;
        }
        return humanizeFallback(nexoItemId);
    }

    private static String humanizeFallback(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        s = s.replace("coin_golden_", "").replace("coin_", "");
        s = s.replace("_anim", "").replace('_', ' ').trim();
        if (s.isEmpty()) {
            return id;
        }
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }
}
