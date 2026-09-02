package fr.lordfinn.crazyphone.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts :shortcode: text (and classic ASCII emoticons like :) or <3) typed into a message into the real
 * emoji character(s) they name, right before the message is sent - client-side only, since the conversion
 * only needs to happen once, at composition time, not on every subsequent read of an already-sent message.
 * Backed by Pixel Twemoji 9x's own shortcode index (crazyphone_data/emoji_shortcodes.json, bundled
 * unmodified - see THIRD-PARTY-LICENSES.md), the same bundled resource behind that pack's own font
 * provider - its keys are already colon-delimited (":thinking:") and its values already use the pack's own
 * private-use remapping for multi-codepoint sequences, so no further translation is needed here beyond a
 * straight lookup. The pack ships no ASCII-emoticon table of its own (that's a different, standalone mod's
 * feature - "Symbol Chat", referenced only as an external data source in the pack's own kaomoji tab config),
 * so EMOTICON_SHORTCODES below is hand-picked, each entry verified to exist in the bundled index.
 */
public final class EmojiShortcodes {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-emoji");
    private static final String RESOURCE_PATH = "crazyphone_data/emoji_shortcodes.json";
    // Matches the exact shape every key in the index uses - a colon, one or more name characters, a colon -
    // deliberately conservative (no spaces) so it can't accidentally swallow two unrelated colon-delimited
    // pieces of text spanning a shortcode-shaped gap between them.
    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":[a-zA-Z0-9_+-]+:");

    // Classic ASCII emoticons, mapped to a shortcode name already present in the bundled index (resolved
    // through that same map, not a second image/character source) - same convention every other chat app
    // converting ":)" -> a smiley uses. LinkedHashMap so EMOTICON_PATTERN below can be built with longer/
    // more specific emoticons first (";-)" before ";)", "xD" before a bare "D") in case two ever shared a
    // starting position - Java's regex alternation takes the first alternative that matches at a position,
    // not the longest, so insertion order here is load-bearing, not cosmetic.
    private static final Map<String, String> EMOTICON_SHORTCODES = new LinkedHashMap<>();
    static {
        EMOTICON_SHORTCODES.put(":-)", ":slight_smile:");
        EMOTICON_SHORTCODES.put(":)", ":slight_smile:");
        EMOTICON_SHORTCODES.put(":-(", ":frowning_face:");
        EMOTICON_SHORTCODES.put(":(", ":frowning_face:");
        EMOTICON_SHORTCODES.put(":-D", ":smiley:");
        EMOTICON_SHORTCODES.put(":D", ":smiley:");
        EMOTICON_SHORTCODES.put("xD", ":laughing:");
        EMOTICON_SHORTCODES.put("XD", ":laughing:");
        EMOTICON_SHORTCODES.put(":-P", ":stuck_out_tongue:");
        EMOTICON_SHORTCODES.put(":-p", ":stuck_out_tongue:");
        EMOTICON_SHORTCODES.put(":P", ":stuck_out_tongue:");
        EMOTICON_SHORTCODES.put(":p", ":stuck_out_tongue:");
        EMOTICON_SHORTCODES.put(";-)", ":wink:");
        EMOTICON_SHORTCODES.put(";)", ":wink:");
        EMOTICON_SHORTCODES.put(";-P", ":stuck_out_tongue_winking_eye:");
        EMOTICON_SHORTCODES.put(";P", ":stuck_out_tongue_winking_eye:");
        EMOTICON_SHORTCODES.put(":3", ":smiley_cat:");
        EMOTICON_SHORTCODES.put("<3", ":heart:");
        EMOTICON_SHORTCODES.put("</3", ":broken_heart:");
        EMOTICON_SHORTCODES.put(":'(", ":cry:");
        EMOTICON_SHORTCODES.put(":'D", ":joy:");
        EMOTICON_SHORTCODES.put(":-O", ":open_mouth:");
        EMOTICON_SHORTCODES.put(":-o", ":open_mouth:");
        EMOTICON_SHORTCODES.put(":O", ":open_mouth:");
        EMOTICON_SHORTCODES.put(":o", ":open_mouth:");
        EMOTICON_SHORTCODES.put(":-|", ":neutral_face:");
        EMOTICON_SHORTCODES.put(":|", ":neutral_face:");
        EMOTICON_SHORTCODES.put(">:(", ":angry:");
        EMOTICON_SHORTCODES.put(">:O", ":rage:");
        EMOTICON_SHORTCODES.put(">:o", ":rage:");
        EMOTICON_SHORTCODES.put("8-)", ":sunglasses:");
        EMOTICON_SHORTCODES.put("8)", ":sunglasses:");
        EMOTICON_SHORTCODES.put("B)", ":sunglasses:");
        EMOTICON_SHORTCODES.put(":-*", ":kissing_heart:");
        EMOTICON_SHORTCODES.put(":*", ":kissing_heart:");
        EMOTICON_SHORTCODES.put("^_^", ":blush:");
        EMOTICON_SHORTCODES.put("^^", ":blush:");
        EMOTICON_SHORTCODES.put("T_T", ":sob:");
        EMOTICON_SHORTCODES.put("T.T", ":sob:");
        EMOTICON_SHORTCODES.put("-_-", ":expressionless:");
        EMOTICON_SHORTCODES.put("o/", ":wave:");
        EMOTICON_SHORTCODES.put("\\o", ":wave:");
    }
    private static final Pattern EMOTICON_PATTERN = Pattern.compile(
            EMOTICON_SHORTCODES.keySet().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(Pattern::quote)
                    .reduce((a, b) -> a + "|" + b)
                    .orElse("(?!)"));

    // Canonical shortcode key (already verified present in the bundled index) -> the lang key suffix for its
    // translatable alias. The bundled index itself is English-only third-party data (no lang variants
    // shipped for it, and translating its ~5900 entries isn't realistic) - this is CrazyPhone's own small,
    // curated layer on top, going through the mod's usual en_us.json/fr_fr.json the same way every other
    // player-facing string does, so a French player can type ":reflexion:" and get the same result as an
    // English player typing ":thinking:" - both remain valid, this only ever ADDS alternate spellings.
    private static final Map<String, String> TRANSLATABLE_ALIASES = new LinkedHashMap<>();
    static {
        TRANSLATABLE_ALIASES.put(":thinking:", "thinking");
        TRANSLATABLE_ALIASES.put(":heart:", "heart");
        TRANSLATABLE_ALIASES.put(":fire:", "fire");
        TRANSLATABLE_ALIASES.put(":laughing:", "laughing");
        TRANSLATABLE_ALIASES.put(":joy:", "joy");
        TRANSLATABLE_ALIASES.put(":sob:", "sob");
        TRANSLATABLE_ALIASES.put(":angry:", "angry");
        TRANSLATABLE_ALIASES.put(":heart_eyes:", "heart_eyes");
        TRANSLATABLE_ALIASES.put(":sunglasses:", "sunglasses");
        TRANSLATABLE_ALIASES.put(":ok:", "ok");
        TRANSLATABLE_ALIASES.put(":thumbsup:", "thumbsup");
        TRANSLATABLE_ALIASES.put(":thumbsdown:", "thumbsdown");
        TRANSLATABLE_ALIASES.put(":tada:", "tada");
        TRANSLATABLE_ALIASES.put(":cake:", "cake");
        TRANSLATABLE_ALIASES.put(":star:", "star");
        TRANSLATABLE_ALIASES.put(":sunny:", "sunny");
        TRANSLATABLE_ALIASES.put(":cloud_rain:", "cloud_rain");
        TRANSLATABLE_ALIASES.put(":coffee:", "coffee");
        TRANSLATABLE_ALIASES.put(":pizza:", "pizza");
        TRANSLATABLE_ALIASES.put(":beer:", "beer");
        TRANSLATABLE_ALIASES.put(":musical_note:", "musical_note");
        TRANSLATABLE_ALIASES.put(":video_game:", "video_game");
        TRANSLATABLE_ALIASES.put(":zzz:", "zzz");
        TRANSLATABLE_ALIASES.put(":nauseated_face:", "nauseated_face");
        TRANSLATABLE_ALIASES.put(":skull:", "skull");
        TRANSLATABLE_ALIASES.put(":ghost:", "ghost");
        TRANSLATABLE_ALIASES.put(":alien:", "alien");
        TRANSLATABLE_ALIASES.put(":robot:", "robot");
        TRANSLATABLE_ALIASES.put(":poop:", "poop");
        TRANSLATABLE_ALIASES.put(":clap:", "clap");
        TRANSLATABLE_ALIASES.put(":pray:", "pray");
        TRANSLATABLE_ALIASES.put(":muscle:", "muscle");
        TRANSLATABLE_ALIASES.put(":eyes:", "eyes");
        TRANSLATABLE_ALIASES.put(":brain:", "brain");
        TRANSLATABLE_ALIASES.put(":heavy_check_mark:", "heavy_check_mark");
        TRANSLATABLE_ALIASES.put(":x:", "x");
        TRANSLATABLE_ALIASES.put(":warning:", "warning");
        TRANSLATABLE_ALIASES.put(":gift:", "gift");
    }

    private static volatile Map<String, String> shortcodes;

    private EmojiShortcodes() {
    }

    /** Replaces every recognized :shortcode: and ASCII emoticon in the text with its emoji - a shortcode-
     * or emoticon-shaped span with no match (including one just not in the index) passes through unchanged,
     * so a literal ":" used normally in a message (a URL, a timestamp) is never touched. */
    public static String replace(String text) {
        if (text == null || text.isEmpty())
            return text;
        Map<String, String> map = shortcodes();
        if (map.isEmpty())
            return text;
        text = applyPattern(text, SHORTCODE_PATTERN, map::get);
        text = applyPattern(text, EMOTICON_PATTERN, match -> map.get(EMOTICON_SHORTCODES.get(match)));
        return text;
    }

    /** Live-typing hook: called right after the player types a space, with everything typed before that
     * space. Converts only the single word/token immediately preceding the cursor (the one just finished by
     * that space) rather than rescanning the whole message, so a shortcode typed earlier and already left
     * alone (because it wasn't recognized, or the player kept typing past it without a space) is never
     * retroactively touched. Returns null when that last token isn't a recognized shortcode or emoticon -
     * callers should leave the field untouched in that case, not clear/reset anything. */
    public static String tryConvertLastToken(String textBeforeSpace) {
        if (textBeforeSpace == null || textBeforeSpace.isEmpty())
            return null;
        int tokenStart = textBeforeSpace.lastIndexOf(' ') + 1;
        String token = textBeforeSpace.substring(tokenStart);
        if (token.isEmpty())
            return null;
        String converted = replace(token);
        if (converted.equals(token))
            return null;
        return textBeforeSpace.substring(0, tokenStart) + converted;
    }

    private static String applyPattern(String text, Pattern pattern, java.util.function.Function<String, String> resolver) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = null;
        int last = 0;
        while (matcher.find()) {
            String replacement = resolver.apply(matcher.group());
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
            int localizedAdded = addTranslatableAliases(map);
            LOGGER.info("Loaded {} emoji shortcodes ({} localized aliases for the current language)", map.size(), localizedAdded);
            return map;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load emoji shortcode index - :shortcode: typing will be unavailable", e);
            return Map.of();
        }
    }

    // Adds ":<localized word>:" -> the same emoji the canonical (English) shortcode already resolves to, for
    // every entry in TRANSLATABLE_ALIASES, using en_us.json/fr_fr.json's own emoji.crazyphone.alias.* keys
    // for the current game language. Snapshotted once, at first use (same as the rest of this cache) - a
    // language switch mid-session won't retroactively update an already-loaded map, matching this project's
    // existing lazy-load pattern rather than adding a live-reload hook for a cosmetic typing convenience.
    private static int addTranslatableAliases(Map<String, String> map) {
        int added = 0;
        for (Map.Entry<String, String> entry : TRANSLATABLE_ALIASES.entrySet()) {
            String canonicalKey = entry.getKey();
            String emoji = map.get(canonicalKey);
            if (emoji == null)
                continue;
            String langKey = "emoji.crazyphone.alias." + entry.getValue();
            String localizedWord;
            try {
                localizedWord = net.minecraft.client.resources.language.I18n.get(langKey);
            } catch (RuntimeException e) {
                continue;
            }
            // I18n.get(...) returns the key itself when no translation is found - guards against
            // accidentally registering a literal ":emoji.crazyphone.alias.thinking:" as a real shortcode.
            if (localizedWord == null || localizedWord.isBlank() || localizedWord.equals(langKey))
                continue;
            map.put(":" + localizedWord + ":", emoji);
            added++;
        }
        return added;
    }
}
