package be.panchito.pointRush.live;

import java.util.UUID;

public record LiveStreamEntry(
        UUID playerId,
        String playerName,
        LivePlatform platform,
        String url,
        long startedAt
) {
}
