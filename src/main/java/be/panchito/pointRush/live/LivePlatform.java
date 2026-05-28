package be.panchito.pointRush.live;

import java.util.Locale;
import java.util.Optional;

public enum LivePlatform {
    TWITCH("twitch"),
    YOUTUBE("youtube"),
    TIKTOK("tiktok");

    private final String id;

    LivePlatform(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<LivePlatform> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String norm = raw.toLowerCase(Locale.ROOT);
        for (LivePlatform p : values()) {
            if (p.id.equals(norm)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}
