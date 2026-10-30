package fr.lordfinn.crazyphone.utils;

import javax.annotation.Nullable;

/**
 * Which phone "app" a player currently has on screen, as far as the live screen on the held phone item is
 * concerned (see client/livescreen). Deliberately coarse: this is the ONLY piece of a player's phone UI that
 * ever leaves their client for other players, so it carries a category, never anything about what is on the
 * screen (no conversation, contact, photo or number). Ids are part of the wire format and of the item data
 * (PhoneLiveScreenData), so never renumber an existing entry: only append.
 */
public enum PhoneScreenApp {
    NONE(0),
    HOME(1),
    CONTACTS(2),
    CONVERSATION(3),
    CAMERA(4),
    PHOTOS(5),
    CALL(6),
    LOCKED(7),
    SETTINGS(8),
    ELECTION(9),
    OTHER(10);

    private static final PhoneScreenApp[] BY_ID;

    static {
        int max = 0;
        for (PhoneScreenApp app : values())
            max = Math.max(max, app.id);
        BY_ID = new PhoneScreenApp[max + 1];
        for (PhoneScreenApp app : values())
            BY_ID[app.id] = app;
    }

    public final int id;

    PhoneScreenApp(int id) {
        this.id = id;
    }

    /** {@code null} for anything outside the known range: callers decide whether that means "ignore the
     * packet" (server) or "fall back to NONE" (item data written by a newer/older version of the mod). */
    public static @Nullable PhoneScreenApp byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    public static PhoneScreenApp byIdOrNone(int id) {
        PhoneScreenApp app = byId(id);
        return app == null ? NONE : app;
    }
}
