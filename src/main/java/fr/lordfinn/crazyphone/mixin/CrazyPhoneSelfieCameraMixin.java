package fr.lordfinn.crazyphone.mixin;

/**
 * Makes the third-person-front camera a genuine "child of the arm" while framing a selfie, instead of
 * {@link fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode}'s own earlier ComputeCameraAngles-offset
 * attempt (live-reported as "desynced" - that approach only nudged vanilla's own generic third-person
 * yaw/pitch by a scaled amount, with no real geometric relationship to how the arm/stick actually swings).
 * Computes an approximate world-space "phone position" from the player's own eye position and body yaw,
 * combined with the exact same {@link fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose} values
 * driving the arm bone itself (see CrazyPhoneSelfiePose#applyArmTransform) - so the camera and the visible
 * arm swing through the same motion, driven by the same numbers.
 *
 * A single straight line from the shoulder, not a true two-segment model of the real arm-then-stick
 * geometry (there's a fixed bend at the hand from the item's own static display transform) - an attempt to
 * model that bend analytically made things worse, not better, and was reverted. Instead, the residual error
 * is absorbed by {@link fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug#pitchBendDeg}, a flat,
 * live-tuned correction confirmed to resolve it in practice.
 *
 * Reproduces the SHAPE of the rotation chain the visible arm/stick renders through - vanilla's own body-yaw
 * application (LivingEntityRenderer uses Axis.YP.rotationDegrees(180 - bodyYaw) for the whole player MODEL;
 * this uses just -bodyYaw, no baked-in 180, since this vector's own local "forward" is a free choice unlike
 * vanilla's own model geometry) composed with ModelPart#translateAndRotate's own rotationZYX(zRot, yRot,
 * xRot) for the arm bone itself (CrazyPhoneSelfiePose#applyArmTransform sets zRot=0, yRot=stickY,
 * xRot=-stickX - mirrored here, confirmed against live feedback), using Mojang's own rotation primitives
 * instead of a hand-derived spherical yaw/pitch formula.
 *
 * Four independent version/loader blocks below, not nested if/else - a nested loader split inside an
 * already-version-wrapped block broke stonecutter's own comment escaping (tried once, reverted) - matching
 * this codebase's own established convention of fully separate blocks per version+loader combo instead
 * (e.g. CrazyPhoneItemProperties' own SelfieMode/SelfieModeSelf records). Camera#setup(BlockGetter, Entity,
 * boolean, boolean, float) was restructured into update(DeltaTracker) calling a now-private
 * alignWithEntity(float) internally on >=1.21.10 (confirmed against the real decompiled 26.1.2 source);
 * setPosition/setRotation(3 args) keep their exact old signatures on both. setRotation's own arity differs
 * by LOADER (3-arg is a NeoForge-only patch onto vanilla Camera - Fabric only has the vanilla 2-arg
 * overload), independently of this version split.
 */
//? if neoforge && <1.21.10 {
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose;

@Mixin(Camera.class)
public abstract class CrazyPhoneSelfieCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    // All tunable constants now live in CrazyPhoneSelfieCameraDebug, editable live via the /selfiecamdebug
    // client command instead of a recompile/relaunch round trip each time - see that class's own doc
    // comment. REACH_DISTANCE ("reach"): how far out from the shoulder the phone sits. CAMERA_PULLBACK
    // ("pullback"): pulls the camera back from the phone position toward the player, so the lens isn't
    // planted exactly inside the phone mesh - small, and NOT the lever for overall distance (that's reach).
    // LATERAL_OFFSET ("lateral"): sideways shift toward the main-hand side. SHOULDER_DROP ("shoulderdrop"):
    // vertical drop of the shoulder anchor below eye height. PITCH_BEND_DEG ("pitchbend"): flat correction
    // for the fixed A-B-to-B-C bend this single-segment formula otherwise ignores (see this class's own doc
    // comment history) - not derived from the item model's own numbers, live-tuned directly.

    @Inject(method = "setup", at = @At("TAIL"))
    private void crazyphone$attachCameraToArm(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || entity != player || !mirrored || !CrazyPhoneSelfiePose.isSelfieFraming(player))
            return;

        Vec3 eyePos = player.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);

        double bodyYawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        float sideSign = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 shoulderPos = eyePos.add(rightX * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign,
                -CrazyPhoneSelfieCameraDebug.shoulderDrop, rightZ * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign);

        float pitchDeg = -CrazyPhoneSelfieStickPose.stickX - CrazyPhoneSelfieCameraDebug.pitchBendDeg;
        Quaternionf rotation = Axis.YP.rotationDegrees(-bodyYaw)
                .mul(new Quaternionf().rotationZYX(0f,
                        (float) Math.toRadians(-CrazyPhoneSelfieStickPose.stickY),
                        (float) Math.toRadians(pitchDeg)));
        Vector3f dir = rotation.transform(new Vector3f(0f, 0f, 1f));

        Vec3 phonePos = shoulderPos.add(dir.x() * CrazyPhoneSelfieCameraDebug.reachDistance,
                dir.y() * CrazyPhoneSelfieCameraDebug.reachDistance, dir.z() * CrazyPhoneSelfieCameraDebug.reachDistance);
        Vec3 cameraPos = phonePos.add(eyePos.subtract(phonePos).normalize().scale(CrazyPhoneSelfieCameraDebug.cameraPullback));

        Vec3 lookDelta = eyePos.subtract(cameraPos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));

        this.setPosition(cameraPos);
        this.setRotation(lookYaw, lookPitch, 0f);
    }
}
//?}
//? if fabric && <1.21.10 {
/*import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose;

@Mixin(Camera.class)
public abstract class CrazyPhoneSelfieCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void crazyphone$attachCameraToArm(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || entity != player || !mirrored || !CrazyPhoneSelfiePose.isSelfieFraming(player))
            return;

        Vec3 eyePos = player.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);

        double bodyYawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        float sideSign = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 shoulderPos = eyePos.add(rightX * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign,
                -CrazyPhoneSelfieCameraDebug.shoulderDrop, rightZ * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign);

        float pitchDeg = -CrazyPhoneSelfieStickPose.stickX - CrazyPhoneSelfieCameraDebug.pitchBendDeg;
        Quaternionf rotation = Axis.YP.rotationDegrees(-bodyYaw)
                .mul(new Quaternionf().rotationZYX(0f,
                        (float) Math.toRadians(-CrazyPhoneSelfieStickPose.stickY),
                        (float) Math.toRadians(pitchDeg)));
        Vector3f dir = rotation.transform(new Vector3f(0f, 0f, 1f));

        Vec3 phonePos = shoulderPos.add(dir.x() * CrazyPhoneSelfieCameraDebug.reachDistance,
                dir.y() * CrazyPhoneSelfieCameraDebug.reachDistance, dir.z() * CrazyPhoneSelfieCameraDebug.reachDistance);
        Vec3 cameraPos = phonePos.add(eyePos.subtract(phonePos).normalize().scale(CrazyPhoneSelfieCameraDebug.cameraPullback));

        Vec3 lookDelta = eyePos.subtract(cameraPos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));

        this.setPosition(cameraPos);
        this.setRotation(lookYaw, lookPitch);
    }
}
*///?}
//? if neoforge && >=1.21.10 {
/*import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose;

@Mixin(Camera.class)
public abstract class CrazyPhoneSelfieCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Shadow
    public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Inject(method = "update", at = @At("TAIL"))
    private void crazyphone$attachCameraToArm(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean mirrored = mc.options.getCameraType().isMirrored();
        if (player == null || !mirrored || !CrazyPhoneSelfiePose.isSelfieFraming(player))
            return;

        float partialTicks = this.getCameraEntityPartialTicks(deltaTracker);
        Vec3 eyePos = player.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);

        double bodyYawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        float sideSign = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 shoulderPos = eyePos.add(rightX * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign,
                -CrazyPhoneSelfieCameraDebug.shoulderDrop, rightZ * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign);

        float pitchDeg = -CrazyPhoneSelfieStickPose.stickX - CrazyPhoneSelfieCameraDebug.pitchBendDeg;
        Quaternionf rotation = Axis.YP.rotationDegrees(-bodyYaw)
                .mul(new Quaternionf().rotationZYX(0f,
                        (float) Math.toRadians(-CrazyPhoneSelfieStickPose.stickY),
                        (float) Math.toRadians(pitchDeg)));
        Vector3f dir = rotation.transform(new Vector3f(0f, 0f, 1f));

        Vec3 phonePos = shoulderPos.add(dir.x() * CrazyPhoneSelfieCameraDebug.reachDistance,
                dir.y() * CrazyPhoneSelfieCameraDebug.reachDistance, dir.z() * CrazyPhoneSelfieCameraDebug.reachDistance);
        Vec3 cameraPos = phonePos.add(eyePos.subtract(phonePos).normalize().scale(CrazyPhoneSelfieCameraDebug.cameraPullback));

        Vec3 lookDelta = eyePos.subtract(cameraPos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));

        this.setPosition(cameraPos);
        this.setRotation(lookYaw, lookPitch, 0f);
    }
}
*///?}
//? if fabric && >=1.21.10 {
/*import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieCameraDebug;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose;

@Mixin(Camera.class)
public abstract class CrazyPhoneSelfieCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Inject(method = "update", at = @At("TAIL"))
    private void crazyphone$attachCameraToArm(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean mirrored = mc.options.getCameraType().isMirrored();
        boolean framing = player != null && CrazyPhoneSelfiePose.isSelfieFraming(player);
        if (player == null || !mirrored || !framing)
            return;

        float partialTicks = this.getCameraEntityPartialTicks(deltaTracker);
        Vec3 eyePos = player.getEyePosition(partialTicks);
        float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);

        double bodyYawRad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(bodyYawRad);
        double rightZ = -Math.sin(bodyYawRad);
        float sideSign = player.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 shoulderPos = eyePos.add(rightX * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign,
                -CrazyPhoneSelfieCameraDebug.shoulderDrop, rightZ * CrazyPhoneSelfieCameraDebug.lateralOffset * sideSign);

        float pitchDeg = -CrazyPhoneSelfieStickPose.stickX - CrazyPhoneSelfieCameraDebug.pitchBendDeg;
        Quaternionf rotation = Axis.YP.rotationDegrees(-bodyYaw)
                .mul(new Quaternionf().rotationZYX(0f,
                        (float) Math.toRadians(-CrazyPhoneSelfieStickPose.stickY),
                        (float) Math.toRadians(pitchDeg)));
        Vector3f dir = rotation.transform(new Vector3f(0f, 0f, 1f));

        Vec3 phonePos = shoulderPos.add(dir.x() * CrazyPhoneSelfieCameraDebug.reachDistance,
                dir.y() * CrazyPhoneSelfieCameraDebug.reachDistance, dir.z() * CrazyPhoneSelfieCameraDebug.reachDistance);
        Vec3 cameraPos = phonePos.add(eyePos.subtract(phonePos).normalize().scale(CrazyPhoneSelfieCameraDebug.cameraPullback));

        Vec3 lookDelta = eyePos.subtract(cameraPos);
        double horizontalDist = Math.sqrt(lookDelta.x * lookDelta.x + lookDelta.z * lookDelta.z);
        float lookYaw = (float) Math.toDegrees(Math.atan2(-lookDelta.x, lookDelta.z));
        float lookPitch = (float) -Math.toDegrees(Math.atan2(lookDelta.y, horizontalDist));

        this.setPosition(cameraPos);
        this.setRotation(lookYaw, lookPitch);
    }
}
*///?}
