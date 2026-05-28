package be.panchito.pointRush.random;

import java.util.List;

/**
 * Live spin-data voor het website-rad (gesynchroniseerd via MongoDB).
 */
public record SpinState(
        boolean active,
        long startedAtMillis,
        List<String> candidateIds,
        List<String> candidateNames,
        List<String> sequence,
        String winnerId
) {
    public static SpinState idle() {
        return new SpinState(false, 0L, List.of(), List.of(), List.of(), null);
    }
}
