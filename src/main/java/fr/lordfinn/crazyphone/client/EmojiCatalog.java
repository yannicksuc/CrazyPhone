package fr.lordfinn.crazyphone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the conversation emoji drawer offers: categories (Unicode's own grouping and order, see
 * crazyphone_data/emoji_picker.json and scripts/gen_emoji_picker.py) and the player's recently used emojis
 * (persisted client side in {@code <game dir>/crazyphone/emoji_recents.txt}).
 *
 * Only emojis the bundled Pixel Twemoji font really has are ever offered: every entry must resolve through
 * the shortcode index (EmojiShortcodes) to a single codepoint that one of the loaded "twemoji:" font
 * providers declares. Anything else (unknown shortcode, glyph missing from the font, a resource pack that
 * replaced the font) is silently skipped, never shown as a blank or "missing glyph" box.
 *
 * Failure handling: a missing or broken picker file gives an empty catalog (the drawer then just shows
 * nothing to pick, typing still works); a missing, corrupted or oversized recents file gives an empty
 * recents row; writing recents can fail silently. Nothing here ever throws to the caller.
 */
public final class EmojiCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-emoji");
    private static final String PICKER_RESOURCE = "crazyphone_data/emoji_picker.json";
    private static final String TWEMOJI_FONT_NAMESPACE = "twemoji:";
    private static final Pattern SHORTCODE = Pattern.compile(":[a-z0-9_+-]{1,64}:");
    /** Two full rows of the drawer grid. */
    public static final int MAX_RECENTS = 18;
    /** A recents file larger than this was not written by us: ignored. */
    private static final long MAX_RECENTS_FILE_BYTES = 8 * 1024;

    public record Emoji(String shortcode, String glyph) {
    }

    public record Category(String id, Emoji icon, List<Emoji> emojis) {
    }

    private static volatile List<Category> categories;
    private static List<Emoji> recents;
    private static Map<String, Emoji> byShortcode = Map.of();

    private EmojiCatalog() {
    }

    /** All categories in display order (never null, possibly empty). */
    public static List<Category> categories() {
        List<Category> loaded = categories;
        if (loaded == null) {
            synchronized (EmojiCatalog.class) {
                loaded = categories;
                if (loaded == null)
                    categories = loaded = load();
            }
        }
        return loaded;
    }

    /** Most recent first (never null, possibly empty). */
    public static synchronized List<Emoji> recents() {
        if (recents == null)
            recents = loadRecents();
        return Collections.unmodifiableList(recents);
    }

    /** Moves the emoji to the front of the recents row and saves it. */
    public static synchronized void recordUse(Emoji emoji) {
        if (emoji == null)
            return;
        if (recents == null)
            recents = loadRecents();
        recents.removeIf(e -> e.shortcode().equals(emoji.shortcode()) || e.glyph().equals(emoji.glyph()));
        recents.add(0, emoji);
        while (recents.size() > MAX_RECENTS)
            recents.remove(recents.size() - 1);
        saveRecents(new ArrayList<>(recents));
    }

    private static List<Category> load() {
        Set<Integer> fontGlyphs = twemojiFontCodepoints();
        List<Category> result = new ArrayList<>();
        Map<String, Emoji> index = new LinkedHashMap<>();
        try (InputStream in = EmojiCatalog.class.getClassLoader().getResourceAsStream(PICKER_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("Emoji picker data not found at {} - the emoji drawer will be empty", PICKER_RESOURCE);
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray cats = root.getAsJsonArray("categories");
            int skipped = 0;
            for (JsonElement catElement : cats) {
                if (!catElement.isJsonObject())
                    continue;
                JsonObject cat = catElement.getAsJsonObject();
                String id = cat.has("id") ? cat.get("id").getAsString() : null;
                if (id == null || id.isEmpty())
                    continue;
                List<Emoji> emojis = new ArrayList<>();
                Set<String> seenGlyphs = new HashSet<>();
                JsonArray list = cat.getAsJsonArray("emojis");
                if (list != null) {
                    for (JsonElement e : list) {
                        Emoji emoji = resolve(e.isJsonPrimitive() ? e.getAsString() : null, fontGlyphs);
                        if (emoji == null || !seenGlyphs.add(emoji.glyph())) {
                            skipped++;
                            continue;
                        }
                        emojis.add(emoji);
                        index.putIfAbsent(emoji.shortcode(), emoji);
                    }
                }
                if (emojis.isEmpty())
                    continue;
                Emoji icon = resolve(cat.has("icon") ? cat.get("icon").getAsString() : null, fontGlyphs);
                result.add(new Category(id, icon != null ? icon : emojis.get(0), List.copyOf(emojis)));
            }
            LOGGER.info("Emoji drawer: {} emojis in {} categories ({} skipped, not in the bundled font)",
                    index.size(), result.size(), skipped);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to load emoji picker data - the emoji drawer will be empty", e);
            return List.of();
        }
        synchronized (EmojiCatalog.class) {
            byShortcode = index;
        }
        return List.copyOf(result);
    }

    /** Shortcode -> emoji, only if the glyph is a single codepoint the twemoji font declares. */
    private static Emoji resolve(String shortcode, Set<Integer> fontGlyphs) {
        if (shortcode == null || !SHORTCODE.matcher(shortcode).matches())
            return null;
        String glyph = EmojiShortcodes.glyphFor(shortcode);
        if (glyph == null || glyph.isEmpty() || glyph.codePointCount(0, glyph.length()) != 1)
            return null;
        // An empty set means the font definitions could not be read at all: trust the index, which is
        // checked against the bundled font when the picker data is generated.
        if (!fontGlyphs.isEmpty() && !fontGlyphs.contains(glyph.codePointAt(0)))
            return null;
        return new Emoji(shortcode, glyph);
    }

    /** Every codepoint declared by a "twemoji:" bitmap provider of the loaded default font. */
    private static Set<Integer> twemojiFontCodepoints() {
        Set<Integer> codepoints = new HashSet<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null)
                return codepoints;
            for (Resource resource : mc.getResourceManager().getResourceStack(Crazyphone.parseId("minecraft:font/default.json"))) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonArray providers = root.getAsJsonArray("providers");
                    if (providers == null)
                        continue;
                    for (JsonElement p : providers) {
                        if (!p.isJsonObject())
                            continue;
                        JsonObject provider = p.getAsJsonObject();
                        if (!provider.has("file") || !provider.get("file").getAsString().startsWith(TWEMOJI_FONT_NAMESPACE))
                            continue;
                        JsonArray rows = provider.getAsJsonArray("chars");
                        if (rows == null)
                            continue;
                        for (JsonElement row : rows)
                            row.getAsString().codePoints().filter(cp -> cp != 0).forEach(codepoints::add);
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.debug("Skipping an unreadable font definition", e);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.debug("Could not read the font definitions, trusting the shortcode index", e);
        }
        return codepoints;
    }

    private static Path recentsFile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null)
            return null;
        return mc.gameDirectory.toPath().resolve("crazyphone").resolve("emoji_recents.txt");
    }

    private static List<Emoji> loadRecents() {
        List<Emoji> loaded = new ArrayList<>();
        try {
            categories();
            Path file = recentsFile();
            if (file == null || !Files.isRegularFile(file) || Files.size(file) > MAX_RECENTS_FILE_BYTES)
                return loaded;
            Map<String, Emoji> index;
            synchronized (EmojiCatalog.class) {
                index = byShortcode;
            }
            Set<String> seen = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String code = line.trim();
                Emoji emoji = index.get(code);
                if (emoji != null && seen.add(emoji.glyph()))
                    loaded.add(emoji);
                if (loaded.size() >= MAX_RECENTS)
                    break;
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Ignoring unreadable emoji recents file", e);
            loaded.clear();
        }
        return loaded;
    }

    private static void saveRecents(List<Emoji> snapshot) {
        try {
            Path file = recentsFile();
            if (file == null)
                return;
            Files.createDirectories(file.getParent());
            StringBuilder text = new StringBuilder();
            for (Emoji e : snapshot)
                text.append(e.shortcode()).append('\n');
            Path tmp = file.resolveSibling("emoji_recents.txt.tmp");
            Files.writeString(tmp, text.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not save emoji recents", e);
        }
    }
}
