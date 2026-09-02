package fr.lordfinn.crazyphone.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts :shortcode: text typed into a message into the real emoji character(s) it names, right before
 * the message is sent - client-side only, since the conversion only needs to happen once, at composition
 * time, not on every subsequent read of an already-sent message. Backed by Pixel Twemoji 9x's own shortcode
 * index (crazyphone_data/emoji_shortcodes.json, bundled unmodified - see THIRD-PARTY-LICENSES.md), the same
 * bundled resource behind that pack's own font provider - its keys are already colon-delimited (":thinking:")
 * and its values already use the pack's own private-use remapping for multi-codepoint sequences, so no
 * further translation is needed here beyond a straight lookup.
 */
public final class EmojiShortcodes {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-emoji");
    private static final String RESOURCE_PATH = "crazyphone_data/emoji_shortcodes.json";
    // Matches the exact shape every key in the index uses - a colon, one or more name characters, a colon -
    // deliberately conservative (no spaces) so it can't accidentally swallow two unrelated colon-delimited
    // pieces of text spanning a shortcode-shaped gap between them.
    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":[a-zA-Z0-9_+-]+:");

    private static volatile Map<String, String> shortcodes;

    private EmojiShortcodes() {
    }

    /** Replaces every recognized :shortcode: in the text with its emoji - text with no match for a given
     * shortcode-shaped span (including one that just isn't in the index) passes through unchanged, so a
     * literal ":" used normally in a message (a URL, an emoticon, timestamps) is never touched. */
    public static String replace(String text) {
        if (text == null || text.indexOf(':') < 0)
            return text;
        Map<String, String> map = shortcodes();
        if (map.isEmpty())
            return text;
        Matcher matcher = SHORTCODE_PATTERN.matcher(text);
        StringBuilder result = null;
        int last = 0;
        while (matcher.find()) {
            String replacement = map.get(matcher.group());
            if (replacement == null)
                continue;
            if (result == null)
                result = new StringBuilder(text.length());
            result.append(text, last, matcher.start()).append(replacement);
            last = matcher.end();
        }
        if (result == null)
            return text;
        return result.append(text, last, text.length()).toString();
    }

    private static Map<String, String> shortcodes() {
        Map<String, String> loaded = shortcodes;
        if (loaded == null) {
            synchronized (EmojiShortcodes.class) {
                loaded = shortcodes;
                if (loaded == null) {
                    shortcodes = loaded = load();
                }
            }
        }
        return loaded;
    }

    private static Map<String, String> load() {
        try (InputStream in = EmojiShortcodes.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Emoji shortcode index not found on classpath at {} - :shortcode: typing will be unavailable", RESOURCE_PATH);
                return Map.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> map = new ConcurrentHashMap<>(root.size());
            for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet())
                map.put(entry.getKey(), entry.getValue().getAsString());
            LOGGER.info("Loaded {} emoji shortcodes", map.size());
            return map;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load emoji shortcode index - :shortcode: typing will be unavailable", e);
            return Map.of();
        }
    }
}
