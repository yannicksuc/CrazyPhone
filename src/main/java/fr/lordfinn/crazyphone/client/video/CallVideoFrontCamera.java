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
 * The selfie ("front") camera of a video call: a second, small, offscreen view of the world from the phone held at
 * arm's length looking back at the player (the very spot the photo mode's selfie camera uses, see
 * CrazyPhoneSelfieCameraMixin), drawn only while it is needed and only at the capped frame rate, before the frame's
 * real world pass starts (so the real pass then sets everything back up for the player's own view).
 *
 * How: for the duration of one extra GameRenderer#renderLevel call, the main render target is swapped for a small
 * offscreen one, the camera is moved to the selfie spot (CallVideoFrontCameraMixin) and detached so the player's
 * own body is drawn, and the first-person hand is skipped (CallVideoCaptureMixin). The picture is then read back
 * through the same asynchronous path as the back camera.
 *
 * Not offered (the back camera keeps working) when it cannot work right: with a shader pack (Iris / Oculus render
 * through their own targets), with Fabulous graphics (its post chain is bound to the real main target), on the 26.x
 * renderer (an extract/render split this cannot redo for a second view), or after any failure in this session.
 */
public final class CallVideoFrontCamera {
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
    private static com.mojang.blaze3d.pipeline.RenderTarget target;

    /** Draws the selfie view into a small offscreen target with {@code renderLevel} (one more call of the frame's own
     * GameRenderer#renderLevel with the very same arguments), then starts reading it back. Called right before the
     * frame's real world pass, never inside it. Returns false if nothing was started. */
    public static boolean renderAndCapture(Minecraft mc, int side, Runnable renderLevel, CallVideoCapture.Sink sink) {
        if (!isAvailable() || rendering || CallVideoCapture.isBusy() || mc.player == null || mc.level == null)
            return false;
        com.mojang.blaze3d.pipeline.RenderTarget main = mc./*$ mc_main_render_target {*/getMainRenderTarget/*$}*/();
        // Same aspect ratio as the window (the projection uses the window's), short side 3x the frame side;
        // the capture then cuts the center square, like the back camera.
        int h = side * 3;
        int w = Math.max(h, Math.round(h * (float) main.width / Math.max(1, main.height)));
        try {
            ensureTarget(w, h);
            rendering = true;
            ((fr.lordfinn.crazyphone.mixin.CallVideoMainTargetAccessor) mc).crazyphone$setMainRenderTarget(target);
            //? if <1.21.10 {
            target.bindWrite(true);
            //?}
            try {
                renderLevel.run();
            } finally {
                ((fr.lordfinn.crazyphone.mixin.CallVideoMainTargetAccessor) mc).crazyphone$setMainRenderTarget(main);
                rendering = false;
            }
            boolean started = CallVideoCapture.capture(target, side, sink);
            //? if <1.21.10 {
            main.bindWrite(true);
            //?}
            return started;
        } catch (Throwable t) {
            failed = true;
            rendering = false;
            org.slf4j.LoggerFactory.getLogger("crazyphone").error("Call video selfie camera failed, only the back camera stays available", t);
            try {
                ((fr.lordfinn.crazyphone.mixin.CallVideoMainTargetAccessor) mc).crazyphone$setMainRenderTarget(main);
                //? if <1.21.10 {
                main.bindWrite(true);
                //?}
            } catch (Throwable ignored) {
            }
            release();
            return false;
        }
    }

    private static void ensureTarget(int w, int h) {
        if (target != null && target.width == w && target.height == h)
            return;
        release();
        //? if <1.21.10 {
        target = new com.mojang.blaze3d.pipeline.TextureTarget(w, h, true, Minecraft.ON_OSX);
        //?} else {
        /*target = new com.mojang.blaze3d.pipeline.TextureTarget("CrazyPhone selfie camera", w, h, true);
        *///?}
    }

    /** Frees the offscreen target (camera switched back, stopped, left the call). Render thread only. */
    public static void release() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
    //?} else {
    /*public static boolean renderAndCapture(Minecraft mc, int side, Runnable renderLevel, CallVideoCapture.Sink sink) {
        return false;
    }

    public static void release() {
    }
    *///?}
}
