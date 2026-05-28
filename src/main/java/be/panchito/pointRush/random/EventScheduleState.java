package be.panchito.pointRush.random;

import java.util.ArrayList;
import java.util.List;

/**
 * Volledige schedule: event-pool, gepland event en optionele live spin.
 */
public final class EventScheduleState {

    private final List<String> pool;
    private UpcomingEvent upcoming;
    private SpinState spin;

    public EventScheduleState(List<String> pool, UpcomingEvent upcoming, SpinState spin) {
        this.pool = new ArrayList<>(pool);
        this.upcoming = upcoming;
        this.spin = spin == null ? SpinState.idle() : spin;
    }

    public List<String> pool() {
        return List.copyOf(pool);
    }

    public UpcomingEvent upcoming() {
        return upcoming;
    }

    public void setUpcoming(UpcomingEvent upcoming) {
        this.upcoming = upcoming;
    }

    public SpinState spin() {
        return spin;
    }

    public void setSpin(SpinState spin) {
        this.spin = spin == null ? SpinState.idle() : spin;
    }

    public boolean removeFromPool(String eventId) {
        return pool.remove(eventId);
    }

    public void resetPool(List<String> allIds) {
        pool.clear();
        pool.addAll(allIds);
    }
}
