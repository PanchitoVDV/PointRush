package be.panchito.pointRush.util;

/**
 * Converts normal latin text to small-caps unicode characters (ѕᴍᴀʟʟ ᴛᴇxᴛ).
 * Used everywhere we want the PointRush "stylized" font in chat messages.
 */
public final class SmallText {

    private SmallText() {
    }

    private static final char[] SMALL_CAPS = {
            'ᴀ', 'ʙ', 'ᴄ', 'ᴅ', 'ᴇ', 'ꜰ', 'ɢ', 'ʜ', 'ɪ', 'ᴊ',
            'ᴋ', 'ʟ', 'ᴍ', 'ɴ', 'ᴏ', 'ᴘ', 'ǫ', 'ʀ', 'ѕ', 'ᴛ',
            'ᴜ', 'ᴠ', 'ᴡ', 'x', 'ʏ', 'ᴢ'
    };

    /**
     * Converts every latin letter in the input to its small-caps unicode equivalent.
     * Numbers, symbols and non-latin characters are kept as-is.
     */
    public static String of(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'a' && c <= 'z') {
                out.append(SMALL_CAPS[c - 'a']);
            } else if (c >= 'A' && c <= 'Z') {
                out.append(SMALL_CAPS[c - 'A']);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
