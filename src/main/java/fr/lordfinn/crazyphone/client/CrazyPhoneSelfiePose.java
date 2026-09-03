package fr.lordfinn.crazyphone.client;

/**
 * "Selfie" framing: while {@link CrazyPhoneCaptureMode#isSelfieMode()} is active (F5-cycled in capture mode,
 * see {@link CrazyPhoneCaptureMode#cycleView()}) and the camera is in third-person-front, the framing
 * player's own main-hand arm raises toward the camera the way an actual phone selfie is held - instead of
 * vanilla's normal idle/walk arm animation, which would otherwise leave the phone hanging at the player's
 * side, invisible to their own third-person-front view. Deliberately separate from
 * {@link CrazyPhonePresentPose} - unrelated trigger (capture mode holding the phone itself vs. sneaking with
 * an already-taken photo item) and unrelated pose (one arm raised toward camera vs. both arms held out flat)
 * - the two share only the general "override the arm bone right after vanilla's own setupAnim runs"
 * mechanism, not any code or state.
 *
 * The arm's own live rotation ALSO carries the stick angle - a dedicated per-element custom renderer for
 * "just the stick swings, the phone stays put" was tried and repeatedly got vanilla's own hand-attachment/
 * display-transform math wrong (see git history). The held item's own model already rotates correctly
 * within the hand on its own (it's the same static crazy_phone_with_selfie_stick.json model, unmodified,
 * that vanilla's own baked-model pipeline already positions correctly) - tilting the ARM instead swings the
 * WHOLE phone+stick assembly together as one rigid piece, matching how a real selfie stick is actually
 * angled (from the wrist), and reuses a technique (override the arm bone after setupAnim) already proven
 * correct elsewhere in this file.
 *
 * Visible to OTHER players, not just the framing player's own client: {@link #isSelfieFraming(LivingEntity)}
 * and the stickX/stickY this class's own transforms use are both entity-aware - for the LOCAL player they
 * read {@link CrazyPhoneCaptureMode}/{@link CrazyPhoneSelfieStickPose}'s own live, zero-latency state
 * directly; for any OTHER entity they read whatever {@link CrazyPhoneSelfiePoseNetwork} last decoded off
 * that entity's own held phone stack (written server-side by CrazyPhoneSelfiePoseSyncPacket's handler,
 * propagated to nearby observers by vanilla's own equipment sync - the same "state lives on the stack"
 * pattern CrazyPhoneItemProperties already uses for screen_on/call-state).
 */
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class CrazyPhoneSelfiePose {
    private CrazyPhoneSelfiePose() {
    }

    /** Whether {@code entity} should currently show the selfie arm/head pose - the local player's own
     * client-side capture-mode state for themselves (zero-latency, no network round trip needed for your own
     * rendering), or their held phone stack's own synced data for anyone else. */
    public static boolean isSelfieFraming(LivingEntity entity) {
        if (entity == net.minecraft.client.Minecraft.getInstance().player)
            return CrazyPhoneCaptureMode.isActive() && CrazyPhoneCaptureMode.isSelfieMode();
        return CrazyPhoneSelfiePoseNetwork.isSelfieActive(entity);
    }

    private static float stickXFor(LivingEntity entity) {
        return entity == net.minecraft.client.Minecraft.getInstance().player
                ? CrazyPhoneSelfieStickPose.stickX
                : CrazyPhoneSelfiePoseNetwork.stickX(entity);
    }

    private static float stickYFor(LivingEntity entity) {
        return entity == net.minecraft.client.Minecraft.getInstance().player
                ? CrazyPhoneSelfieStickPose.stickY
                : CrazyPhoneSelfiePoseNetwork.stickY(entity);
    }

    public static void applyArmTransform(HumanoidModel<?> model, LivingEntity entity, HumanoidArm mainArm) {
        ModelPart armPart = mainArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // Y (left/right) sign live-reported as inverted here - the opposite of what the (now-removed) custom
        // stick renderer needed, since ModelPart's own yRot convention differs from that renderer's PoseStack
        // rotation. Not negated.
        armPart.xRot = -(float) Math.toRadians(stickXFor(entity));
        armPart.yRot = (float) Math.toRadians(stickYFor(entity));
        armPart.zRot = 0f;
        //? if <1.21.10 {
        if (model instanceof /*$ player_model_pkg {*/net.minecraft.client.model.PlayerModel/*$}*/<?> playerModel) {
            ModelPart sleevePart = mainArm == HumanoidArm.RIGHT ? playerModel.rightSleeve : playerModel.leftSleeve;
            sleevePart.xRot = armPart.xRot;
            sleevePart.yRot = armPart.yRot;
            sleevePart.zRot = armPart.zRot;
        }
        //?}
    }

    // Matches CrazyPhoneSelfieCameraMixin's own REACH_DISTANCE - both compute the same approximate
    // world-space "phone position" from the player's eye position and body yaw, one to place the LOCAL
    // player's own camera there, this one to make ANY framing entity's head look toward it (their own camera
    // for the local player, or just "toward where their own phone would be" for anyone else being watched).
    private static final double REACH_DISTANCE = 1.3;

    public static void applyHeadTransform(HumanoidModel<?> model, LivingEntity entity, float partialTicks) {
        Vec3 eyePos = entity.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float reachYawDeg = bodyYaw + stickYFor(entity);
        float reachPitchDeg = -stickXFor(entity);
        double yawRad = Math.toRadians(reachYawDeg);
        double pitchRad = Math.toRadians(reachPitchDeg);
        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 cameraPos = eyePos.add(dirX * REACH_DISTANCE, dirY * REACH_DISTANCE, dirZ * REACH_DISTANCE);

        Vec3 lookDelta = cameraPos.subtract(eyePos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));

        model.head.yRot = (float) Math.toRadians(Mth.wrapDegrees(lookYaw - bodyYaw));
        model.head.xRot = (float) Math.toRadians(lookPitch);
    }
}
