package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code InputEvent.MouseScrollingEvent}
 * hook (Fabric API has no direct equivalent) - redirects mouse scroll to zoom while capture mode is active
 * and cancels vanilla's own handling (hotbar slot switching) entirely, injecting at HEAD with a cancellable
 * callback exactly like NeoForge's own Pre-style input event does.
 *
 * Targets MouseHandler#onScroll directly (private, void, signature (long, double, double) - GLFW's raw
 * window handle plus x/y scroll deltas - unchanged across both Fabric nodes' Minecraft versions).
 */
//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(MouseHandler.class)
public abstract class CrazyPhoneCaptureScrollMixin {
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void crazyphone$captureScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive()) {
            CrazyPhoneCaptureMode.adjustZoom(yOffset);
            ci.cancel();
        }
    }
}
*///?}
