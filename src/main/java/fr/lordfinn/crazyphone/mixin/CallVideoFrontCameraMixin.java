package fr.lordfinn.crazyphone.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
//? if <26 {
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

import fr.lordfinn.crazyphone.client.video.CallVideoFrontCamera;

/**
 * During the call video selfie pass only (CallVideoFrontCamera#isRendering), moves the camera to the selfie spot
 * and detaches it, so the world is drawn from the phone held out in front and the player's own body is in the
 * picture. Every other frame is left exactly as it was. Below 26.x only (see CallVideoFrontCamera).
 */
@Mixin(Camera.class)
public abstract class CallVideoFrontCameraMixin {
    //? if <26 {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void crazyphone$callVideoSelfieCamera(BlockGetter level, Entity entity, boolean detachedArg, boolean mirrored, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!CallVideoFrontCamera.isRendering() || mc.player == null || entity != mc.player)
            return;
        CallVideoFrontCamera.Pose pose = CallVideoFrontCamera.selfiePose(mc.player, partialTicks);
        this.detached = true;
        this.setRotation(pose.yaw(), pose.pitch());
        this.setPosition(pose.position());
    }
    //?}
}
