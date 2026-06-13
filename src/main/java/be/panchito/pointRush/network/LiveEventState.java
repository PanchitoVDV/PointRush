package be.panchito.pointRush.network;

import java.util.Locale;

/**
 * Momentopname van het gedeelde {@code live_event} document in MongoDB — de cross-server
 * coördinatiestaat van het lopende event. Eén event tegelijk (singleton doc).
 *
 * @param eventId     unieke id van deze event-run
 * @param minigame    minigame-id (zoals in {@code MinigameRegistry})
 * @param phase       PENDING (spelers worden overgestuurd) of RUNNING (game draait op de host)
 * @param hostServer  proxy-naam van de server die het event host (de events-server)
 * @param startedAtMs tijdstip waarop het event werd aangevraagd
 */
public record LiveEventState(String eventId, String minigame, Phase phase, String hostServer, long startedAtMs) {

    public enum Phase {
        PENDING,
        RUNNING;

        public static Phase parse(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return Phase.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
