package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code ViewportEvent.ComputeFov} hook
 * (Fabric API has no direct equivalent) - divides the live-rendered FOV by capture mode's current zoom
 * factor without ever touching the persisted {@code options.fov()} value itself, avoiding both bugs that
 * approach had (see CrazyPhoneCaptureMode's own doc comment for the full explanation): the 30-110 slider
 * clamp silently capping how far zoom could go, and FOV staying stuck at whatever it was clamped to if the
 * player left mid-shot instead of exiting normally.
 *
 * Targets GameRenderer#getFov on <26 (private, returns double, signature (Camera, float, boolean),
 * confirmed via decompiled source unchanged across both older Fabric nodes' Minecraft versions). >=26 moved
 * FOV computation entirely off GameRenderer and onto Camera itself (confirmed via decompiled source - the
 * whole "render state extraction" rework: Camera#calculateFov(float) now computes and caches this.fov as
 * part of Camera#setup, consumed later by GameRenderer as a plain field read, not recalculated there at
 * all) - same zoom-divide logic, just retargeted to that method instead, with its own simpler
 * (float partialTicks) -> float signature.
 */
//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

//? if >=26 {
/^@Mixin(net.minecraft.client.Camera.class)
public abstract class CrazyPhoneCaptureFovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void crazyphone$applyCaptureZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CrazyPhoneCaptureMode.isActive())
            cir.setReturnValue(cir.getReturnValue() / CrazyPhoneCaptureMode.currentZoom());
    }
}
^///? } else {
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

@Mixin(GameRenderer.class)
public abstract class CrazyPhoneCaptureFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void crazyphone$applyCaptureZoom(Camera camera, float partialTicks, boolean useFov, CallbackInfoReturnable<Double> cir) {
        if (CrazyPhoneCaptureMode.isActive())
            cir.setReturnValue(cir.getReturnValue() / CrazyPhoneCaptureMode.currentZoom());
    }
}
//?}
*///?}
