package be.panchito.pointRush.history;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a single finished event.
 *
 * <p>Stored on disk by {@link EventHistoryManager} and rendered by the
 * {@code /pointrush} command. Each entry has a stable id (so chat clicks can
 * reference it) and a list of player placements with the points awarded.
 */
public record EventHistoryEntry(
        String id,
        String eventType,
        long startedAt,
        long endedAt,
        List<Placement> placements
) {

    public EventHistoryEntry {
        placements = placements == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(placements));
    }

    /**
     * A single ranked player inside an event.
     *
     * <p>{@code score} is event-specific (e.g. placement points for parkour,
     * total points earned for tnttag) and is displayed as "+N pts" in the UI.
     * {@code detail} is an optional secondary line (e.g. "ronde 5" for tnttag).
     */
    public record Placement(
            int rank,
            UUID playerId,
            String playerName,
            UUID teamId,
            String teamName,
            String teamColor,
            int score,
            String detail
    ) {
    }
}
