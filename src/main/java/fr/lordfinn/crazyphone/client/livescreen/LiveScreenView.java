package fr.lordfinn.crazyphone.client.livescreen;

import fr.lordfinn.crazyphone.utils.PhoneScreenApp;

/**
 * Everything one frame of a held phone's live screen depends on, and nothing else: two views that are
 * equal draw exactly the same pixels, which is what lets {@link LiveScreenMesh} cache the result and share
 * it between every phone currently showing the same thing (all the locked phones in a crowd, for example).
 * Built by {@link LiveScreenViews}, painted by {@link LiveScreenPainter}.
 *
 * Fields that only make sense for the holder's own phone ({@code unread}, {@code caller}) are left at their
 * "unknown" value for anybody else's phone, so other players' private data can't end up here at all.
 */
public record LiveScreenView(Mode mode, PhoneScreenApp app, int clockMinutes, int dayPhase, int unread,
                             int callSeconds, int frame, String caller, int participants) {

    public enum Mode {
        /** No app open, nothing happening: clock, day/night wallpaper, unread badge. */
        LOCK,
        /** Being called, not answered yet. */
        RINGING,
        /** Calling someone, waiting for them to answer. */
        DIALING,
        /** Connected call: timer and participant tiles. */
        IN_CALL,
        /** A phone app is open: its header and a simplified, content-free body. */
        APP,
        /** Capture mode: a viewfinder. */
        CAMERA
    }

    public static final int DAY = 0;
    public static final int TWILIGHT = 1;
    public static final int NIGHT = 2;
}
