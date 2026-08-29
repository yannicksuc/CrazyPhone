package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code ViewportEvent.ComputeFov} hook
 * (Fabric API has no direct equivalent) - divides the live-rendered FOV by capture mode's current zoom
 * factor without ever touching the persisted {@code options.fov()} value itself, avoiding both bugs that
 * approach had (see CrazyPhoneCaptureMode's own doc comment for the full explanation): the 30-110 slider
 * clamp silently capping how far zoom could go, and FOV staying stuck at whatever it was clamped to if the
 * player left mid-shot instead of exiting normally.
 *
 * Targets GameRenderer#getFov directly (confirmed via decompiled source for both Fabric nodes' Minecraft
 * versions - private, returns double, signature (Camera, float, boolean) unchanged between them).
 */
//? if fabric {
/*import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(GameRenderer.class)
public abstract class CrazyPhoneCaptureFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void crazyphone$applyCaptureZoom(Camera camera, float partialTicks, boolean useFov, CallbackInfoReturnable<Double> cir) {
        if (CrazyPhoneCaptureMode.isActive())
            cir.setReturnValue(cir.getReturnValue() / CrazyPhoneCaptureMode.currentZoom());
    }
}
*///?}
