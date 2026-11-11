package fr.lordfinn.crazyphone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.BitSet;

import fr.lordfinn.crazyphone.Crazyphone;

/**
 * Which characters are emoji pictures: every codepoint the bundled Pixel Twemoji font (the "twemoji:" bitmap
 * providers of minecraft:font/default.json) and the emoji drawer's category icons ("emoji_categories:") declare.
 * That covers the real emoji codepoints (📞, 🔔...) and the private use ones of multi-character emojis.
 *
 * Read once from the loaded resources, kept as a bit set (checked for every glyph drawn, so it must be cheap).
 * Before the resources are loaded nothing counts as an emoji and nothing is cached.
 */
public final class EmojiGlyphs {
    private static final String[] EMOJI_FONT_PREFIXES = {"twemoji:", "emoji_categories:"};
    /** Below this no emoji exists in the font (the first one is the copyright sign, U+00A9). */
    private static final int FIRST_EMOJI = 0xA9;
    private static volatile BitSet emojis;
    private static long lastAttemptMillis;

    private EmojiGlyphs() {
    }

    public static boolean isEmoji(int codepoint) {
        if (codepoint < FIRST_EMOJI)
            return false;
        BitSet set = emojis;
        if (set == null) {
            // Not readable yet (resources still loading): tried again at most once a second, not per glyph.
            long now = System.currentTimeMillis();
            if (now - lastAttemptMillis < 1000)
                return false;
            lastAttemptMillis = now;
            set = load();
            if (set == null)
                return false;
            emojis = set;
        }
        return set.get(codepoint);
    }

    private static BitSet load() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null)
                return null;
            BitSet set = new BitSet();
            for (Resource resource : mc.getResourceManager().getResourceStack(Crazyphone.parseId("minecraft:font/default.json"))) {
                try (Reader reader = resource.openAsReader()) {
                    JsonArray providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
                    if (providers == null)
                        continue;
                    for (JsonElement element : providers) {
                        if (!element.isJsonObject())
                            continue;
                        JsonObject provider = element.getAsJsonObject();
                        if (!provider.has("file") || !isEmojiFont(provider.get("file").getAsString()))
                            continue;
                        JsonArray rows = provider.getAsJsonArray("chars");
                        if (rows == null)
                            continue;
                        for (JsonElement row : rows)
                            row.getAsString().codePoints().filter(cp -> cp >= FIRST_EMOJI).forEach(set::set);
                    }
                } catch (Exception ignored) {
                    // An unreadable font definition: its emojis are simply drawn like plain text.
                }
            }
            return set.isEmpty() ? null : set;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final java.util.Map<Integer, float[]> BOUNDS = new java.util.HashMap<>();
    private static final float[] NO_BOUNDS = new float[0];

    /**
     * The drawn pixels of an emoji inside its font cell, as {x0, y0, x1, y1} (x1 and y1 exclusive) in GUI pixels at
     * the size the font draws it (the flags image is twice as fine as the emoji one, so its pixels count half), read from the
     * font image once per emoji; null for a character that is not an emoji or could not be measured. Used to
     * center icons on what they really show (their transparent margins differ from one emoji to the next).
     */
    public static synchronized float[] bounds(int codepoint) {
        if (!isEmoji(codepoint))
            return null;
        float[] cached = BOUNDS.get(codepoint);
        if (cached == null) {
            cached = measure(codepoint);
            BOUNDS.put(codepoint, cached == null ? NO_BOUNDS : cached);
        }
        return cached == NO_BOUNDS ? null : cached;
    }

    /** Where each emoji sits in the font images: {provider index, row, column, rows, columns}, built once. */
    private static java.util.Map<Integer, int[]> cells;
    private static final java.util.List<String> CELL_FILES = new java.util.ArrayList<>();
    /** Decoded font images, read once each (a drawer page measures dozens of emojis in one frame). */
    private static final java.util.Map<String, java.awt.image.BufferedImage> IMAGES = new java.util.HashMap<>();

    private static float[] measure(int codepoint) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (cells == null)
                cells = indexCells(mc);
            int[] cell = cells.get(codepoint);
            if (cell == null)
                return null;
            java.awt.image.BufferedImage image = image(mc, CELL_FILES.get(cell[0]));
            return image == null ? null : measureCell(image, cell[1], cell[2], cell[3], cell[4], cell[5]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.util.Map<Integer, int[]> indexCells(Minecraft mc) throws Exception {
        java.util.Map<Integer, int[]> index = new java.util.HashMap<>();
        for (Resource resource : mc.getResourceManager().getResourceStack(Crazyphone.parseId("minecraft:font/default.json"))) {
            JsonArray providers;
            try (Reader reader = resource.openAsReader()) {
                providers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("providers");
            }
            if (providers == null)
                continue;
            for (JsonElement element : providers) {
                if (!element.isJsonObject())
                    continue;
                JsonObject provider = element.getAsJsonObject();
                if (!provider.has("file") || !isEmojiFont(provider.get("file").getAsString()) || provider.getAsJsonArray("chars") == null)
                    continue;
                int fileIndex = CELL_FILES.size();
                int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                CELL_FILES.add(provider.get("file").getAsString());
                JsonArray rows = provider.getAsJsonArray("chars");
                for (int row = 0; row < rows.size(); row++) {
                    int[] line = rows.get(row).getAsString().codePoints().toArray();
                    for (int col = 0; col < line.length; col++)
                        if (line[col] >= FIRST_EMOJI)
                            index.putIfAbsent(line[col], new int[]{fileIndex, row, col, rows.size(), line.length, height});
                }
            }
        }
        return index;
    }

    private static java.awt.image.BufferedImage image(Minecraft mc, String file) throws Exception {
        if (IMAGES.containsKey(file))
            return IMAGES.get(file);
        // "twemoji:font/emoji.png" is the texture twemoji:textures/font/emoji.png.
        int colon = file.indexOf(':');
        String id = file.substring(0, colon) + ":textures/" + file.substring(colon + 1);
        java.util.Optional<Resource> texture = mc.getResourceManager().getResource(Crazyphone.parseId(id));
        java.awt.image.BufferedImage image = null;
        if (texture.isPresent()) {
            try (java.io.InputStream in = texture.get().open()) {
                image = javax.imageio.ImageIO.read(in);
            }
        }
        IMAGES.put(file, image);
        return image;
    }

    private static float[] measureCell(java.awt.image.BufferedImage image, int row, int col, int rows, int cols, int height) {
        int cw = image.getWidth() / cols, ch = image.getHeight() / rows;
        int x0 = cw, y0 = ch, x1 = 0, y1 = 0;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                if ((image.getRGB(col * cw + x, row * ch + y) >>> 24) != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
        }
        if (x1 <= x0)
            return null;
        // Image pixels to GUI pixels: the font draws a cell "height" pixels tall.
        float scale = height / (float) ch;
        return new float[]{x0 * scale, y0 * scale, x1 * scale, y1 * scale};
    }

    private static boolean isEmojiFont(String file) {
        for (String prefix : EMOJI_FONT_PREFIXES)
            if (file.startsWith(prefix))
                return true;
        return false;
    }
}
