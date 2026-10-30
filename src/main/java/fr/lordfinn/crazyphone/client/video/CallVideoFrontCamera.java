package fr.lordfinn.crazyphone.client.video;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug;

/**
 * The selfie ("front") camera of a video call: one extra view of the world from the phone held at arm's length
 * looking back at the player (the very spot the photo mode's selfie camera uses, see CrazyPhoneSelfieCameraMixin),
 * drawn only while it is needed and only at the capped frame rate.
 *
 * How: at the very start of a frame's world pass, one extra GameRenderer#renderLevel call is made with the camera
 * moved to the selfie spot and set up as the third-person front view (CallVideoFrontCameraMixin, so every renderer
 * draws the player's own body, phone held out on the selfie stick like in the photo mode), and the first-person
 * hand skipped (CallVideoCaptureMixin). It draws into the main render target, which is read back right away
 * through the same asynchronous path as the back camera; then the frame's real world pass clears it and draws the
 * player's own view as usual, so nothing of it is ever shown on screen. (Drawing into a separate small target was
 * tried first: on 1.21.1 entities then never reached it, so the player was missing from their own selfie.)
 *
 * Not offered (the back camera keeps working) when it cannot work right: with a shader pack (Iris / Oculus render
 * through their own pipeline), with Fabulous graphics (its post chain keeps its own state between passes), on the
 * 26.x renderer (an extract/render split this cannot redo for a second view), or after any failure in this session.
 */
public final class CallVideoFrontCamera {
    /** The selfie spot is a selfie stick's length away: a closer crop of the middle keeps the player's head and
     * shoulders filling the small picture instead of a tiny figure in a field. */
    private static final float SELFIE_ZOOM = 1.8f;

    private static boolean rendering;
    private static boolean failed;

    private CallVideoFrontCamera() {
    }

    /** True while the offscreen selfie view is being drawn (the capture hook must ignore that pass). */
    public static boolean isRendering() {
        return rendering;
    }

    public static boolean isAvailable() {
        //? if <26 {
        return !failed && !Minecraft.useShaderTransparency() && !shaderPackInUse();
        //?} else {
        /*return false;
        *///?}
    }

    /** Iris (Fabric) and Oculus (Forge) share this public API. Looked up by name: neither is a dependency. */
    private static boolean shaderPackInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** The selfie camera spot for the local player: same formula, same live-tuned constants and same stick angles
     * (the last ones used, 45 degrees up by default) as the photo mode's CrazyPhoneSelfieCameraMixin, so the camera
     * sits exactly where the arm others see holds the phone. */
    public record Pose(Vec3 position, float yaw, float pitch) {
    }

    public static Pose selfiePose(LocalPlayer player, float partialTicks) {
        Vec3 eyePos = player.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        double bodyYawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        float sideSign = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 shoulderPos = eyePos.add(rightX * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign,
                -CrazyPhoneSelfieCameraDebug.shoulderDrop, rightZ * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign);
        float pitchDeg = -fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose.stickX - CrazyPhoneSelfieCameraDebug.pitchBendDeg;
        Quaternionf rotation = com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw)
                .mul(new Quaternionf().rotationZYX(0f, (float) Math.toRadians(-fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose.stickY), (float) Math.toRadians(pitchDeg)));
        Vector3f dir = rotation.transform(new Vector3f(0f, 0f, 1f));
        double reach = CrazyPhoneSelfieCameraDebug.reachDistance;
        Vec3 phonePos = shoulderPos.add(dir.x() * reach, dir.y() * reach, dir.z() * reach);
        Vec3 cameraPos = phonePos.add(eyePos.subtract(phonePos).normalize().scale(CrazyPhoneSelfieCameraDebug.cameraPullback));
        Vec3 lookDelta = eyePos.subtract(cameraPos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));
        return new Pose(cameraPos, lookYaw, lookPitch);
    }

    //? if <26 {
    /** Draws the selfie view with {@code renderLevel} (one more call of the frame's own GameRenderer#renderLevel
     * with the very same arguments) and starts reading it back. Called right before the frame's real world pass,
     * never inside it. Returns false if nothing was started. */
    public static boolean renderAndCapture(Minecraft mc, int side, Runnable renderLevel, CallVideoCapture.Sink sink) {
        if (!isAvailable() || rendering || CallVideoCapture.isBusy() || mc.player == null || mc.level == null)
            return false;
        com.mojang.blaze3d.pipeline.RenderTarget main = mc./*$ mc_main_render_target {*/getMainRenderTarget/*$}*/();
        net.minecraft.client.CameraType previousView = mc.options.getCameraType();
        try {
            rendering = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            try {
                renderLevel.run();
            } finally {
                mc.options.setCameraType(previousView);
                rendering = false;
            }
            return CallVideoCapture.capture(main, side, SELFIE_ZOOM, sink);
        } catch (Throwable t) {
            failed = true;
            rendering = false;
            mc.options.setCameraType(previousView);
            org.slf4j.LoggerFactory.getLogger("crazyphone").error("Call video selfie camera failed, only the back camera stays available", t);
            return false;
        }
    }

    /** Nothing to free any more (kept so callers do not care how the selfie view is drawn). */
    public static void release() {
    }
    //?} else {
    /*public static boolean renderAndCapture(Minecraft mc, int side, Runnable renderLevel, CallVideoCapture.Sink sink) {
        return false;
    }

    public static void release() {
    }
    *///?}
}
