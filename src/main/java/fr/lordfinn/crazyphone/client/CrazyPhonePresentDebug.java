package fr.lordfinn.crazyphone.client;

/**
 * Live-tunable knobs for the first-person presenting card's transform (CrazyPhonePhotoItemRenderer's
 * presenting branch reads these instead of compile-time constants) - lets values be adjusted from chat
 * (see the /presentdebug client command, Fabric-only for now) without a recompile/relaunch cycle, since
 * getting 3D camera-relative positioning right by guessing constants and rebuilding each time has been the
 * single slowest part of this whole feature. Covers everything that plausibly affects where/how the card
 * ends up: position/size, the sign and any extra offset on each of the two camera-rotation axes being
 * reapplied, and whether the front/back flip is even applied at all.
 */
public final class CrazyPhonePresentDebug {
    private CrazyPhonePresentDebug() {
    }

    // Defaults below are the values live-tuned and confirmed working via /presentdebug (recovered from the
    // client log after a session ended before they could be reported directly - see that command's own
    // chat-feedback lines, which double as a recovery trail for exactly this situation).
    public static float y = -0.800f;
    public static float z = -1.500f;
    public static float scale = 4.000f;
    // Multiplies camera.getYRot()/getXRot() before reapplying them as the card's own yaw/pitch - flip
    // between 1 and -1 to test which direction is correct without editing code.
    public static float yawSign = 1f;
    public static float pitchSign = -1f;
    // Flat degree offsets added on top of the camera's own (signed) yaw/pitch, for fine adjustment once the
    // sign itself is right.
    public static float yawOffset = 0f;
    public static float pitchOffset = -5.0f;
    // Whether the extra 180-degree turn (meant to show the card's front outward instead of its back) is
    // applied at all - toggle off to check whether that flip is itself the thing hiding the card.
    public static boolean flipFrontBack = true;

    // CrazyPhonePresentHandGripMixin's own offsets for both hands' bare arms, mirrored left/right by handX
    // so each hand converges toward its own edge of the card. handY/handZ are added on top of the card's
    // own y/z above.
    public static float handX = 0f;
    public static float handY = 0.900f;
    public static float handZ = 1.200f;

    // Temporary: when true, the third-person presenting branch renders 10 small colored candidate cards
    // fanned out side by side instead of the one real card - each tries a different rotation formula, so a
    // live tester can report back which COLOR stays locked to the arms while turning the camera, instead of
    // one guess-compile-relaunch cycle per formula. See CrazyPhonePhotoItemRenderer#renderPresentingCandidates
    // for the color/formula legend. Winning formula found (pink, index 7 - no cancel at all on 1.20.4) and
    // applied directly in the real branch - left false, off, unless another round of this is needed.
    public static boolean presentCandidateFan = false;

    public static String describe() {
        return String.format(java.util.Locale.ROOT,
                "y=%.3f z=%.3f scale=%.3f yawSign=%.0f pitchSign=%.0f yawOffset=%.1f pitchOffset=%.1f flip=%s handX=%.3f handY=%.3f handZ=%.3f",
                y, z, scale, yawSign, pitchSign, yawOffset, pitchOffset, flipFrontBack, handX, handY, handZ);
    }
}
