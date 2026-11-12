package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.ClientConfig;
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
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

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
 *
 * This is the ONLY reader and writer of that file: the held phone's live screen (/phonescreen) toggles
 * keep their values in ClientConfig, but load them through
 * {@link #ensureLoaded()} and save them through {@link #save()}, which rewrites every key (and keeps any
 * key it does not know), so no feature can wipe another one's settings. Those in-game toggles are read
 * from the file once per game start; the phone size and emoji switch are also re-read when the file
 * changes on disk.
 */
public final class ClientPrefs {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-client-prefs");
    private static final String FILE_NAME = "crazyphone-client.properties";
    /** Files bigger than this are not a hand-edited properties file: ignored, defaults used. */
    private static final long MAX_FILE_BYTES = 64 * 1024;
    private static final long RECHECK_INTERVAL_MS = 1000;

    public static final String KEY_PHONE_SIZE = "phoneScreenHeightPercent";
    public static final String KEY_EMOJI_PICKER = "emojiPickerEnabled";
    public static final String KEY_LIVE_SCREEN_OWN = "liveScreenOwnPhone";
    public static final String KEY_LIVE_SCREEN_OTHERS = "liveScreenOtherPhones";
    public static final String KEY_LAUNCH_SPLASH_SHOWN = "launchSplashShown";
    /** Set once the player picks a phone size themselves (the home screen's magnifier, or by hand). */
    public static final String KEY_PHONE_SIZE_CHOSEN = "phoneScreenHeightChosen";
    /** The home screen's magnifier already pulsed once to show it is there. */
    public static final String KEY_SIZE_HINT_SHOWN = "phoneSizeHintShown";

    /** 0 = keep the vanilla GUI scale size (the phone as it was before this option existed). */
    public static final int PHONE_SIZE_VANILLA = 0;
    public static final int PHONE_SIZE_MIN = 40;
    public static final int PHONE_SIZE_MAX = 95;
    /** The vanilla size by default: the bigger phone made the game report a smaller window to every screen,
     * so other mods that draw beside a screen (JEI, REI, EMI...) landed on top of the phone. */
    public static final int PHONE_SIZE_DEFAULT = PHONE_SIZE_VANILLA;
    /** The size the home screen's magnifier switches to, and the old default (files written before
     * 1.8.0-beta.8 hold it without having been chosen: they go back to the vanilla size once). */
    public static final int PHONE_SIZE_BIG = 70;

    private static int phoneSizePercent = PHONE_SIZE_DEFAULT;
    private static boolean emojiPickerEnabled = true;
    /** The "Sandbox creation" splash is shown the first time the phone is opened on this game, then never again. */
    private static boolean launchSplashShown;
    private static boolean phoneSizeChosen;
    private static boolean sizeHintShown;

    private static boolean loadedOnce;
    /** The in-game toggles (ClientConfig) are taken from the file only once: afterwards the game owns them. */
    private static boolean togglesApplied;
    /** Keys found in the file that this version does not know: written back untouched by save(). */
    private static final Map<String, String> unknownKeys = new TreeMap<>();
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

    /** Picks the phone size (vanilla or a percentage), saved right away and kept from then on. */
    public static synchronized void setPhoneSizePercent(int percent) {
        refreshIfChanged();
        phoneSizePercent = clampPhoneSizePercent(percent);
        phoneSizeChosen = true;
        Path path = file();
        if (path != null)
            write(path);
    }

    public static synchronized boolean sizeHintShown() {
        refreshIfChanged();
        return sizeHintShown;
    }

    /** Remembers that the magnifier's one-time pulse was seen, saved right away. */
    public static synchronized void markSizeHintShown() {
        refreshIfChanged();
        if (sizeHintShown)
            return;
        sizeHintShown = true;
        Path path = file();
        if (path != null)
            write(path);
    }

    public static synchronized boolean launchSplashShown() {
        refreshIfChanged();
        return launchSplashShown;
    }

    /** Remembers that the launch splash was shown, saved right away. */
    public static synchronized void markLaunchSplashShown() {
        refreshIfChanged();
        if (launchSplashShown)
            return;
        launchSplashShown = true;
        Path path = file();
        if (path != null)
            write(path);
    }

    /** Reads the file now if it was never read (creating it with defaults if missing). */
    public static synchronized void ensureLoaded() {
        refreshIfChanged();
    }

    /**
     * Writes every option to the file: the phone size and emoji switch as currently loaded, the in-game
     * toggles from ClientConfig, and any unknown key found in the file. Never throws.
     */
    public static synchronized void save() {
        refreshIfChanged();
        Path path = file();
        if (path == null)
            return;
        write(path);
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
                    write(path);
                loadedOnce = true;
                togglesApplied = true;
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
            togglesApplied = true;
            LOGGER.warn("Could not read {}, using defaults", FILE_NAME, e);
        }
    }

    private static void load(Path path) throws IOException {
        int size = PHONE_SIZE_DEFAULT;
        boolean picker = true;
        boolean splashShown = false;
        boolean sizeChosen = false;
        boolean hintShown = false;
        if (Files.size(path) <= MAX_FILE_BYTES) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(path);
                 Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            size = parseInt(props.getProperty(KEY_PHONE_SIZE), PHONE_SIZE_DEFAULT);
            picker = parseBoolean(props.getProperty(KEY_EMOJI_PICKER), true);
            splashShown = parseBoolean(props.getProperty(KEY_LAUNCH_SPLASH_SHOWN), false);
            sizeChosen = parseBoolean(props.getProperty(KEY_PHONE_SIZE_CHOSEN), false);
            hintShown = parseBoolean(props.getProperty(KEY_SIZE_HINT_SHOWN), false);
            // Written by an older version with its default size, never picked by the player: back to the new default.
            if (!sizeChosen && size == PHONE_SIZE_BIG)
                size = PHONE_SIZE_DEFAULT;
            if (!togglesApplied) {
                ClientConfig.liveScreenOwnPhone = parseBoolean(props.getProperty(KEY_LIVE_SCREEN_OWN), true);
                ClientConfig.liveScreenOtherPhones = parseBoolean(props.getProperty(KEY_LIVE_SCREEN_OTHERS), true);
            }
            unknownKeys.clear();
            for (String key : props.stringPropertyNames()) {
                if (!key.equals(KEY_PHONE_SIZE) && !key.equals(KEY_EMOJI_PICKER) && !key.equals(KEY_LIVE_SCREEN_OWN)
                        && !key.equals(KEY_LIVE_SCREEN_OTHERS) && !key.equals(KEY_LAUNCH_SPLASH_SHOWN) && !key.equals(KEY_PHONE_SIZE_CHOSEN) && !key.equals(KEY_SIZE_HINT_SHOWN))
                    unknownKeys.put(key, props.getProperty(key));
            }
        } else {
            LOGGER.warn("{} is unexpectedly large, ignoring it and using defaults", FILE_NAME);
        }
        togglesApplied = true;
        phoneSizePercent = clampPhoneSizePercent(size);
        emojiPickerEnabled = picker;
        launchSplashShown = splashShown;
        phoneSizeChosen = sizeChosen;
        sizeHintShown = hintShown;
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

    private static void write(Path path) {
        String size = phoneSizePercent == PHONE_SIZE_VANILLA ? "0" : Integer.toString(phoneSizePercent);
        StringBuilder text = new StringBuilder()
                .append("# CrazyPhone client preferences (only affect this game, never sent to the server).\n")
                .append("# The phone size and the emoji button apply without a restart (next phone screen or window resize).\n")
                .append("# The other options are changed in game and read from here when the game starts.\n")
                .append("\n")
                .append("# Height of the phone on screen, in percent of the window height (").append(PHONE_SIZE_MIN).append(" to ").append(PHONE_SIZE_MAX).append(").\n")
                .append("# The phone snaps to whole pixel sizes so it stays crisp, never gets smaller than at your GUI scale\n")
                .append("# and never overflows the window. 0 (or \"vanilla\") keeps the size given by the GUI scale alone.\n")
                .append("# Bigger than the GUI scale, other mods that draw beside the phone's screens (JEI, REI, EMI...) may\n")
                .append("# overlap it. The magnifier on the phone's home screen switches between 0 and 70.\n")
                .append(KEY_PHONE_SIZE).append('=').append(size).append("\n")
                .append(KEY_PHONE_SIZE_CHOSEN).append('=').append(phoneSizeChosen).append("\n")
                .append(KEY_SIZE_HINT_SHOWN).append('=').append(sizeHintShown).append("\n")
                .append("\n")
                .append("# Emoji button next to the message field of a conversation.\n")
                .append(KEY_EMOJI_PICKER).append('=').append(emojiPickerEnabled).append("\n")
                .append("\n")
                .append("# Live screen on the phone in your own hand, and on phones held by other players (/phonescreen).\n")
                .append(KEY_LIVE_SCREEN_OWN).append('=').append(ClientConfig.liveScreenOwnPhone).append("\n")
                .append(KEY_LIVE_SCREEN_OTHERS).append('=').append(ClientConfig.liveScreenOtherPhones).append("\n")
                .append("\n")
                .append("# The \"Sandbox creation\" splash was already shown on the first phone launch (false shows it again).\n")
                .append(KEY_LAUNCH_SPLASH_SHOWN).append('=').append(launchSplashShown).append("\n");
        if (!unknownKeys.isEmpty()) {
            Properties extra = new Properties();
            extra.putAll(unknownKeys);
            java.io.StringWriter out = new java.io.StringWriter();
            try {
                extra.store(out, null);
            } catch (IOException ignored) {
            }
            text.append("\n# Kept as found (not used by this version).\n");
            for (String line : out.toString().split("\\R")) {
                if (!line.startsWith("#") && !line.isEmpty())
                    text.append(line).append("\n");
            }
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, text.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            lastModified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not write {}", FILE_NAME, e);
        }
    }
}
