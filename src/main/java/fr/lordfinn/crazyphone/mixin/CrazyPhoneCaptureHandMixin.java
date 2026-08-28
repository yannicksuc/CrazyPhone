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
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(ItemInHandRenderer.class)
public abstract class CrazyPhoneCaptureHandMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void crazyphone$hideHandsWhileCapturing(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive())
            ci.cancel();
    }
}
*///?}
