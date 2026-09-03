package fr.lordfinn.crazyphone.client;

/**
 * Live rotation state for the selfie stick's shaft, driven by raw mouse movement while
 * {@link CrazyPhoneCaptureMode#isSelfieMode()} is active (see {@link fr.lordfinn.crazyphone.mixin.CrazyPhoneSelfieStickMouseMixin}
 * - it feeds the same accumulated mouse delta vanilla's own camera turn already reads, as an
 * additional observer, so normal camera look is completely untouched). Deliberately separate state
 * from {@link CrazyPhoneSelfiePose} (the arm-raise pose) - this only ever affects the shaft's own
 * geometry, drawn by {@link fr.lordfinn.crazyphone.client.render.CrazyPhoneSelfieStickItemRenderer}.
 *
 * First-pass angle convention, not yet live-tuned: stickX/stickY are applied as plain Euler degrees
 * (Y then X) around the shaft's own pivot, starting from its raw (unrotated) Blockbench geometry -
 * expect the default/range to need adjustment once seen in-game.
 */
public final class CrazyPhoneSelfieStickPose {
    private CrazyPhoneSelfieStickPose() {
    }

    // Mutable (not final) - live-tunable via /selfiecamdebug minx/maxx/miny/maxy (see
    // CrazyPhoneSelfieCameraDebugCommand) so the arm's own rotation limits can be tested live alongside the
    // camera's own knobs, without a recompile/relaunch round trip each time.
    public static float minX = -70f;
    public static float maxX = 70f;
    // Was 80 - never updated after minX/maxX got live-tuned down from a wider range, so the UNCLAMPED
    // default ended up outside the now-legal -70..70 range entirely (reset() sets stickX = DEFAULT_X
    // directly, bypassing the clamp addMouseDelta applies) - live-reported as the camera ending up nearly
    // straight overhead and the head unable to visually track it, since 80 degrees is a near-vertical
    // pitch. A moderate value that's actually reachable within the current range.
    private static final float DEFAULT_X = 45f;

    // Y (yaw/left-right) limits - unlike X, stickY previously had no limit at all (just wrapped around a
    // full 360), live-requested alongside the X limits above so the arm can't swing arbitrarily far
    // sideways either. Symmetric around 0 (the default/rest yaw) since there's no equivalent of DEFAULT_X
    // asymmetry on this axis.
    public static float minY = -70f;
    public static float maxY = 70f;

    // Degrees per accumulated-mouse-delta unit - starting value, live-tunable only via how far the mouse
    // actually has to move in-game, not a debug command (the mouse control itself IS the live tuning
    // mechanism here, unlike CrazyPhonePresentDebug's own separate /presentdebug command).
    private static final float SENSITIVITY = 0.15f;

    public static float stickX = DEFAULT_X;
    public static float stickY = 0f;

    public static void addMouseDelta(double dx, double dy) {
        stickY = clamp(stickY + (float) (dx * SENSITIVITY), minY, maxY);
        // Screen-space dy is positive moving DOWN - subtracting keeps "mouse up" raising the stick angle.
        stickX = clamp(stickX - (float) (dy * SENSITIVITY), minX, maxX);
    }

    // Live-requested: entering selfie mode (F5) should start the stick angled roughly where the player was
    // ALREADY looking, not always snap to the same fixed default - a more natural transition. stickY stays
    // 0 (relative to body facing, which the camera math already tracks separately via bodyYaw) - only the
    // pitch (stickX) adapts. -playerXRot: player pitch uses vanilla's own convention (positive = looking
    // down), the same convention the camera math's own lookPitch output uses, so this is a direct mapping,
    // not derived/verified against pitchBendDeg's own small correction - a first pass, live-tune the mapping
    // itself if it doesn't feel right once seen in-game.
    public static void resetToLook(float playerXRot) {
        stickX = clamp(-playerXRot, minX, maxX);
        stickY = 0f;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
