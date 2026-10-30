package fr.lordfinn.crazyphone.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.video.CallVideoClient;
import fr.lordfinn.crazyphone.client.video.CallVideoFrontCamera;

/**
 * The two hooks of call camera video in the frame (see CallVideoClient):
 * <ul>
 * <li>The head of renderItemInHand: the one moment of a frame where the main render target holds the world and
 * nothing else. The level is drawn; the first-person hand, the HUD and every screen are not yet. GameRenderer draws
 * the hand right after the level on every version this mod supports, so this is what makes the back camera film the
 * world only, never the phone screen, the chat, the hotbar or any other private interface. During the offscreen
 * selfie pass the hand is skipped entirely (the selfie camera sees the player's whole body instead).</li>
 * <li>The head of renderLevel (below 26.x): the start of the frame's world pass, where the selfie camera's own
 * offscreen pass runs, when due, by calling renderLevel once more with the same arguments.</li>
 * </ul>
 * Both are optional injections: if another mod reshapes these methods, camera video just never starts (the call
 * keeps the 3D previews) instead of the game failing to start.
 */
@Mixin(GameRenderer.class)
public abstract class CallVideoCaptureMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$captureCallVideo(CallbackInfo ci) {
        if (CallVideoFrontCamera.isRendering()) {
            ci.cancel();
            return;
        }
        CallVideoClient.onLevelRendered();
    }

    //? if <1.21 {
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void crazyphone$selfieCameraPass(float partialTicks, long finishTimeNano, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
        if (!CallVideoFrontCamera.isRendering())
            CallVideoClient.beforeLevelRender(() -> ((GameRenderer) (Object) this).renderLevel(partialTicks, finishTimeNano, new com.mojang.blaze3d.vertex.PoseStack()));
    }
    //?}
    //? if >=1.21 <26 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void crazyphone$selfieCameraPass(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!CallVideoFrontCamera.isRendering())
            CallVideoClient.beforeLevelRender(() -> ((GameRenderer) (Object) this).renderLevel(deltaTracker));
    }
    *///?}
}
