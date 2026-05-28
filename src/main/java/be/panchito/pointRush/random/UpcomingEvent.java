package be.panchito.pointRush.random;

import java.time.LocalDate;

/**
 * Event gekozen via {@code /randomevent} voor een geplande dag (meestal morgen).
 */
public record UpcomingEvent(
        String eventId,
        String displayName,
        LocalDate scheduledFor,
        long selectedAtMillis,
        UpcomingStatus status
) {
    public enum UpcomingStatus {
        SCHEDULED,
        STARTED
    }
}
