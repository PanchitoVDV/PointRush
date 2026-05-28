package be.panchito.pointRush.live;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates and normalises stream URLs per platform. */
public final class LiveStreamUrls {

    private static final Pattern TWITCH_CHANNEL = Pattern.compile(
            "(?:https?://)?(?:www\\.)?twitch\\.tv/([a-zA-Z0-9_]{3,25})(?:/.*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_WATCH = Pattern.compile(
            "(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{6,})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_SHORT = Pattern.compile(
            "(?:https?://)?youtu\\.be/([a-zA-Z0-9_-]{6,})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_LIVE = Pattern.compile(
            "(?:https?://)?(?:www\\.)?youtube\\.com/live/([a-zA-Z0-9_-]{6,})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIKTOK = Pattern.compile(
            "(?:https?://)?(?:www\\.)?tiktok\\.com/@([a-zA-Z0-9._]{2,})/live(?:/.*)?",
            Pattern.CASE_INSENSITIVE);

    private LiveStreamUrls() {
    }

    public static Optional<String> normalize(LivePlatform platform, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        String trimmed = rawUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getHost() == null) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        return switch (platform) {
            case TWITCH -> normalizeTwitch(trimmed);
            case YOUTUBE -> normalizeYouTube(trimmed);
            case TIKTOK -> normalizeTikTok(trimmed);
        };
    }

    private static Optional<String> normalizeTwitch(String url) {
        Matcher m = TWITCH_CHANNEL.matcher(url);
        if (!m.matches()) {
            return Optional.empty();
        }
        String channel = m.group(1).toLowerCase(Locale.ROOT);
        return Optional.of("https://www.twitch.tv/" + channel);
    }

    private static Optional<String> normalizeYouTube(String url) {
        Matcher live = YOUTUBE_LIVE.matcher(url);
        if (live.matches()) {
            return Optional.of("https://www.youtube.com/live/" + live.group(1));
        }
        Matcher watch = YOUTUBE_WATCH.matcher(url);
        if (watch.matches()) {
            return Optional.of("https://www.youtube.com/watch?v=" + watch.group(1));
        }
        Matcher shortUrl = YOUTUBE_SHORT.matcher(url);
        if (shortUrl.matches()) {
            return Optional.of("https://www.youtube.com/watch?v=" + shortUrl.group(1));
        }
        if (url.toLowerCase(Locale.ROOT).contains("youtube.com/")
                || url.toLowerCase(Locale.ROOT).contains("youtu.be/")) {
            return Optional.of(url.split("\\?")[0].split("#")[0]);
        }
        return Optional.empty();
    }

    private static Optional<String> normalizeTikTok(String url) {
        Matcher m = TIKTOK.matcher(url);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of("https://www.tiktok.com/@" + m.group(1) + "/live");
    }
}
