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
    // Overall left/right shift on top of the left/right-hand centering compensation (see its own comment
    // where it's applied) - live-reported as "both hands consistent now, but not centered" once that
    // compensation made both hands match each other, meaning there's a further shared offset still needing
    // this shift to land on true center.
    public static float x = 0.5f;
    public static float y = -0.800f;
    public static float z = -1.500f;
    public static float scale = 6.000f;
    // Multiplies camera.getYRot()/getXRot() before reapplying them as the card's own yaw/pitch - flip
    // between 1 and -1 to test which direction is correct without editing code.
    public static float yawSign = 1f;
    public static float pitchSign = 1f;
    // Flat degree offsets added on top of the camera's own (signed) yaw/pitch, for fine adjustment once the
    // sign itself is right. Neutral (0) by default now that CrazyPhonePresentHandGripMixin's own camera
    // formula is verified exact against Camera.java's own rotation math - these are live-tunable nudges on
    // top of that, not the source of correctness themselves.
    public static float yawOffset = 0f;
    public static float pitchOffset = 0f;
    // Whether the extra 180-degree turn (meant to show the card's front outward instead of its back) is
    // applied at all - toggle off to check whether that flip is itself the thing hiding the card.
    public static boolean flipFrontBack = true;

    // CrazyPhonePresentHandGripMixin's own ABSOLUTE position for both hands' bare arms, mirrored left/right
    // by handX so each hand converges toward its own edge of the card. Fully independent of y/z above (used
    // to be added on top of them plus a hardcoded anchor - live-reported as confusing to tune, since moving
    // y/z to adjust the card also dragged the arms along) - these three now only ever move the arms.
    public static float handX = 0.500f;
    public static float handY = -1.000f;
    public static float handZ = -1.000f;

    // Separate set for CrazyPhonePresentPose#isDualPresenting (a photo in EACH hand at once) - the single-
    // photo x/y/scale above converge both hands on one shared centered card, which would put two overlapping
    // photos on top of each other. dualX is mirrored (right hand = +dualX, left hand = -dualX) so each photo
    // splits out to sit under its own hand instead. First-person values only for now, live-tuned; third-
    // person needs its own pass (the existing PRESENT_CENTER_X/Y_LIFT/SCALE constants only ever handled the
    // single-shared-card case, same problem the single-photo x/y/scale above had before this split existed).
    public static float dualX = 2.200f;
    public static float dualY = -0.400f;
    public static float dualScale = 5.000f;
    // Live-reported: dualX's mirror isn't quite symmetric between hands (same underlying asymmetry the
    // single-photo case needed its own extra x correction for, on top of the invert*0.56 hand compensation) -
    // this is added to the LEFT hand's X only, on top of everything else, to close that remaining gap.
    public static float dualLeftExtra = 1.000f;

    // Third-person counterpart to dualX/dualY/dualScale above - same problem (PRESENT_CENTER_X only ever
    // converges both arms toward one shared card, needs to instead sit one photo under each arm when both
    // hands hold one) but a completely separate code path (CrazyPhonePhotoItemRenderer's third-person
    // presenting branch, driven by the arm-bone pose rather than the first-person hand-render pipeline), so
    // it needs its own live-tuned values rather than reusing the first-person ones. Starting from
    // PRESENT_CENTER_X/Y_LIFT/SCALE's own values as a seed (not yet live-tuned - untested, since verifying
    // this needs an outside/F5 view of yourself rather than the direct first-person view).
    public static float dualThirdX = 0.500f;
    public static float dualThirdY = -0.400f;
    public static float dualThirdScale = 2.400f;

    // Temporary: when true, the third-person presenting branch renders 10 small colored candidate cards
    // fanned out side by side instead of the one real card - each tries a different rotation formula, so a
    // live tester can report back which COLOR stays locked to the arms while turning the camera, instead of
    // one guess-compile-relaunch cycle per formula. See CrazyPhonePhotoItemRenderer#renderPresentingCandidates
    // for the color/formula legend. Winning formula found (pink, index 7 - no cancel at all on 1.20.4) and
    // applied directly in the real branch - left false, off, unless another round of this is needed.
    public static boolean presentCandidateFan = false;

    public static String describe() {
        return String.format(java.util.Locale.ROOT,
                "x=%.3f y=%.3f z=%.3f scale=%.3f yawSign=%.0f pitchSign=%.0f yawOffset=%.1f pitchOffset=%.1f flip=%s handX=%.3f handY=%.3f handZ=%.3f dualX=%.3f dualY=%.3f dualScale=%.3f dualLeftExtra=%.3f dualThirdX=%.3f dualThirdY=%.3f dualThirdScale=%.3f",
                x, y, z, scale, yawSign, pitchSign, yawOffset, pitchOffset, flipFrontBack, handX, handY, handZ, dualX, dualY, dualScale, dualLeftExtra, dualThirdX, dualThirdY, dualThirdScale);
    }
}
