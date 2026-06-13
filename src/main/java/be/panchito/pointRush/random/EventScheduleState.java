package be.panchito.pointRush.random;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Volledige schedule: event-pool, tijdelijk uitgeschakelde events, gepland event en optionele live spin.
 */
public final class EventScheduleState {

    private final List<String> pool;
    /** Events die tijdelijk uit het rad zijn gehaald (blijven persistent tot ze weer worden aangezet). */
    private final Set<String> disabled;
    private UpcomingEvent upcoming;
    private SpinState spin;

    public EventScheduleState(List<String> pool, List<String> disabled,
                              UpcomingEvent upcoming, SpinState spin) {
        this.pool = new ArrayList<>(pool);
        this.disabled = new LinkedHashSet<>(disabled == null ? List.of() : disabled);
        this.upcoming = upcoming;
        this.spin = spin == null ? SpinState.idle() : spin;
    }

    public List<String> pool() {
        return List.copyOf(pool);
    }

    public List<String> disabled() {
        return List.copyOf(disabled);
    }

    public boolean isDisabled(String eventId) {
        return disabled.contains(eventId);
    }

    /** Zet een event uit voor het rad. Returnt false als het al uitstond. */
    public boolean disable(String eventId) {
        return disabled.add(eventId);
    }

    /** Zet een event weer aan voor het rad. Returnt false als het al aanstond. */
    public boolean enable(String eventId) {
        return disabled.remove(eventId);
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
