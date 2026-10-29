package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code RenderHandEvent#setCanceled}
 * (Fabric API has no direct equivalent) - hides the held phone while framing a shot, the same way vanilla's
 * own F1 hides the HUD but never touches this first-person world-space render pass on its own (options.hideGui,
 * toggled by CrazyPhoneCaptureMode#enter itself, only covers the 2D HUD - hotbar, crosshair, etc).
 */
//? if fabric {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

// 26.3 replaced ItemInHandRenderer with FirstPersonHandsAndItemsRenderer (render-state based, the player and
// light arguments became render states): the >=26 handler below takes no target arguments at all, so the same
// injection fits 26.1, 26.2 and 26.3 and only the target class differs.
@Mixin(/^$ first_person_hands_renderer {^/net.minecraft.client.renderer.ItemInHandRenderer/^$}^/.class)
public abstract class CrazyPhoneCaptureHandMixin {
    // renderHandsWithItems' own buffer parameter type is MultiBufferSource.BufferSource on <26, replaced by
    // SubmitNodeCollector on >=26 - same rename CrazyPhonePresentHandGripMixin's own >=26 branch already
    // needed (see its own doc comment for the fuller explanation), confirmed here too via the real runtime
    // mixin-apply error ("Invalid descriptor... Expected ...SubmitNodeCollector... but found
    // ...MultiBufferSource$BufferSource...").
    // 26.2 renamed renderHandsWithItems to submitHandsWithItems (same descriptor): both names are listed so the
    // one injection resolves on 26.1 and 26.2+.
    //? if >=26 {
    /^@Inject(method = {"renderHandsWithItems", "submitHandsWithItems"}, at = @At("HEAD"), cancellable = true)
    private void crazyphone$hideHandsWhileCapturing(CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive())
            ci.cancel();
    }
    ^///? } else {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void crazyphone$hideHandsWhileCapturing(float partialTicks, PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive())
            ci.cancel();
    }
    //?}
}
*///?}
