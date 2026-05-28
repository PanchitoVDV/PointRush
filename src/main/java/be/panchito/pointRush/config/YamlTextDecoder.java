package be.panchito.pointRush.config;

import java.nio.charset.StandardCharsets;

/**
 * Detects UTF-16 YAML saves (Windows Notepad "Unicode", corrupted UTF-8 with NUL padding)
 * and returns UTF-8-safe text for {@link org.bukkit.configuration.file.YamlConfiguration}.
 */
final class YamlTextDecoder {

    record Result(String text, boolean needsRewriteAsUtf8) {
    }

    private YamlTextDecoder() {
    }

    static Result decode(byte[] raw) {
        if (raw.length == 0) {
            return new Result("", false);
        }
        byte[] buf = raw;
        // Strip UTF-8 BOM
        if (buf.length >= 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
            byte[] stripped = new byte[buf.length - 3];
            System.arraycopy(buf, 3, stripped, 0, stripped.length);
            buf = stripped;
        }
        // UTF-16 BE BOM
        if (buf.length >= 2 && (buf[0] & 0xFF) == 0xFE && (buf[1] & 0xFF) == 0xFF) {
            String s = new String(buf, 2, buf.length - 2, StandardCharsets.UTF_16BE);
            return new Result(s, true);
        }
        // UTF-16 LE BOM
        if (buf.length >= 2 && (buf[0] & 0xFF) == 0xFF && (buf[1] & 0xFF) == 0xFE) {
            String s = new String(buf, 2, buf.length - 2, StandardCharsets.UTF_16LE);
            return new Result(s, true);
        }
        // UTF-16 LE without BOM (e.g. "# comment" stored as wide chars)
        if (looksLikeUtf16LeAscii(buf)) {
            return new Result(new String(buf, StandardCharsets.UTF_16LE), true);
        }
        String asUtf8 = new String(buf, StandardCharsets.UTF_8);
        if (asUtf8.indexOf('\u0000') >= 0) {
            return new Result(new String(buf, StandardCharsets.UTF_16LE), true);
        }
        return new Result(asUtf8, false);
    }

    /**
     * Typical UTF-16LE ASCII text has NUL at odd indices for the first bytes (# ! space newline letters).
     */
    private static boolean looksLikeUtf16LeAscii(byte[] raw) {
        if (raw.length < 10) {
            return false;
        }
        int oddNuls = 0;
        int evenNuls = 0;
        int lim = Math.min(raw.length, 64);
        for (int i = 0; i < lim; i++) {
            if (raw[i] == 0) {
                if ((i & 1) == 1) {
                    oddNuls++;
                } else {
                    evenNuls++;
                }
            }
        }
        return oddNuls >= 4 && oddNuls >= evenNuls * 2;
    }
}
