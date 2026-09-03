package fr.lordfinn.crazyphone.client;

/**
 * Live-tunable knobs for {@link fr.lordfinn.crazyphone.mixin.CrazyPhoneSelfieCameraMixin}, edited via the
 * {@code /selfiecamdebug} client command (see {@link CrazyPhoneSelfieCameraDebugCommand}) instead of a
 * recompile/relaunch round trip each time - mirrors the existing {@code /presentdebug} pattern
 * (CrazyPhonePresentDebug/CrazyPhonePresentDebugCommand) used for the same purpose elsewhere in this
 * codebase. Defaults match this feature's own last live-confirmed values.
 */
public final class CrazyPhoneSelfieCameraDebug {
    private CrazyPhoneSelfieCameraDebug() {
    }

    public static float reachDistance = 3.0f;
    public static float cameraPullback = 0.0f;
    public static float lateralOffset = 0.37f;
    public static float shoulderDrop = 0.3f;
    public static float pitchBendDeg = 4.0f;

    public static String describe() {
        return "reach=" + reachDistance + " pullback=" + cameraPullback + " lateral=" + lateralOffset
                + " shoulderDrop=" + shoulderDrop + " pitchBend=" + pitchBendDeg
                + " minX=" + CrazyPhoneSelfieStickPose.minX + " maxX=" + CrazyPhoneSelfieStickPose.maxX
                + " minY=" + CrazyPhoneSelfieStickPose.minY + " maxY=" + CrazyPhoneSelfieStickPose.maxY;
    }
}
