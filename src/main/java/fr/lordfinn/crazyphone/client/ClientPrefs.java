package fr.lordfinn.crazyphone.client;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Player-side (client) preferences, persisted to {@code config/crazyphone-client.properties}: a plain
 * properties file rather than a loader config spec, for the same reason ClientConfig is a plain holder
 * (a CLIENT config's load lifecycle is loader-specific and was unreliable here), and so it behaves the
 * same on Fabric, NeoForge and Forge.
 *
 * Robustness: a missing file is created with the defaults and a comment per option; an unreadable,
 * corrupted or out-of-range value falls back to its default or is clamped, never throws; the file is
 * re-read when it changes on disk (checked at most once a second), so the size can be tuned while the
 * game runs. Nothing here is ever sent to the server.
 */
public final class ClientPrefs {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-client-prefs");
    private static final String FILE_NAME = "crazyphone-client.properties";
    /** Files bigger than this are not a hand-edited properties file: ignored, defaults used. */
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final long RECHECK_INTERVAL_MS = 1000;

    public static final String KEY_PHONE_SIZE = "phoneScreenHeightPercent";
    public static final String KEY_EMOJI_PICKER = "emojiPickerEnabled";

    /** 0 = keep the vanilla GUI scale size (the phone as it was before this option existed). */
    public static final int PHONE_SIZE_VANILLA = 0;
    public static final int PHONE_SIZE_MIN = 40;
    public static final int PHONE_SIZE_MAX = 95;
    public static final int PHONE_SIZE_DEFAULT = 70;

    private static int phoneSizePercent = PHONE_SIZE_DEFAULT;
    private static boolean emojiPickerEnabled = true;

    private static boolean loadedOnce;
    private static long lastCheckMillis;
    private static long lastModified = Long.MIN_VALUE;

    private ClientPrefs() {
    }

    /** Target height of the phone as a percentage of the window height, or {@link #PHONE_SIZE_VANILLA}. */
    public static synchronized int phoneSizePercent() {
        refreshIfChanged();
        return phoneSizePercent;
    }

    public static synchronized boolean emojiPickerEnabled() {
        refreshIfChanged();
        return emojiPickerEnabled;
    }

    /** 0 or less means vanilla; anything else is clamped into [MIN, MAX]. */
    public static int clampPhoneSizePercent(int value) {
        if (value <= PHONE_SIZE_VANILLA)
            return PHONE_SIZE_VANILLA;
        return Math.max(PHONE_SIZE_MIN, Math.min(PHONE_SIZE_MAX, value));
    }

    private static Path file() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null)
            return null;
        return mc.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static void refreshIfChanged() {
        long now = System.currentTimeMillis();
        if (loadedOnce && now - lastCheckMillis < RECHECK_INTERVAL_MS)
            return;
        lastCheckMillis = now;
        try {
            Path path = file();
            if (path == null)
                return;
            if (!Files.exists(path)) {
                if (!loadedOnce)
                    writeDefaults(path);
                loadedOnce = true;
                return;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (loadedOnce && modified == lastModified)
                return;
            lastModified = modified;
            loadedOnce = true;
            load(path);
        } catch (IOException | RuntimeException e) {
            loadedOnce = true;
            LOGGER.warn("Could not read {}, using defaults", FILE_NAME, e);
        }
    }

    private static void load(Path path) throws IOException {
        int size = PHONE_SIZE_DEFAULT;
        boolean picker = true;
        if (Files.size(path) <= MAX_FILE_BYTES) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(path);
                 Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            size = parseInt(props.getProperty(KEY_PHONE_SIZE), PHONE_SIZE_DEFAULT);
            picker = parseBoolean(props.getProperty(KEY_EMOJI_PICKER), true);
        } else {
            LOGGER.warn("{} is unexpectedly large, ignoring it and using defaults", FILE_NAME);
        }
        phoneSizePercent = clampPhoneSizePercent(size);
        emojiPickerEnabled = picker;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null)
            return fallback;
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("vanilla"))
            return PHONE_SIZE_VANILLA;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(trimmed);
                if (Double.isFinite(d))
                    return (int) Math.round(Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, d)));
            } catch (NumberFormatException ignored) {
            }
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null)
            return fallback;
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equals("1") || trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("on"))
            return true;
        if (trimmed.equalsIgnoreCase("false") || trimmed.equals("0") || trimmed.equalsIgnoreCase("no") || trimmed.equalsIgnoreCase("off"))
            return false;
        return fallback;
    }

    private static void writeDefaults(Path path) {
        String text = "# CrazyPhone client preferences (only affect this game, never sent to the server).\n"
                + "# No restart needed: changes apply the next time a phone screen opens or the window is resized.\n"
                + "\n"
                + "# Height of the phone on screen, in percent of the window height (" + PHONE_SIZE_MIN + " to " + PHONE_SIZE_MAX + ").\n"
                + "# The phone snaps to whole pixel sizes so it stays crisp, never gets smaller than at your GUI scale\n"
                + "# and never overflows the window. 0 (or \"vanilla\") keeps the size given by the GUI scale alone.\n"
                + KEY_PHONE_SIZE + "=" + PHONE_SIZE_DEFAULT + "\n"
                + "\n"
                + "# Emoji button next to the message field of a conversation.\n"
                + KEY_EMOJI_PICKER + "=true\n";
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            lastModified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not write default {}", FILE_NAME, e);
        }
    }
}
