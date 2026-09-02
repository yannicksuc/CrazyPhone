package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code InputEvent.MouseButton.Pre} hook
 * (Fabric API has no direct equivalent) - left click exits capture mode, right click takes the shot, both
 * cancelling vanilla's own click handling entirely (attack/block-break/item-use) by injecting at HEAD with a
 * cancellable callback, exactly like NeoForge's own Pre-style input event does.
 *
 * Targets MouseHandler#onPress directly (private, void, signature (long, int, int, int) - GLFW's raw window
 * handle, button, action, mods) on <26 - confirmed via decompiled source that 26.x consolidated
 * onPress/onRelease into a single onButton(long, MouseButtonInfo, int action) instead, bundling the old
 * button+mods params into one record (MouseButtonInfo#button()/#modifiers()) with action as its own
 * separately-annotated int parameter - same underlying GLFW values, just regrouped.
 */
//? if fabric {
/*import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(MouseHandler.class)
public abstract class CrazyPhoneCapturePressMixin {
    //? if >=26 {
    /^@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void crazyphone$capturePress(long window, net.minecraft.client.input.MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (!CrazyPhoneCaptureMode.isActive() || action != GLFW.GLFW_PRESS)
            return;
        ci.cancel();
        int button = buttonInfo.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
            CrazyPhoneCaptureMode.exit();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            CrazyPhoneCaptureMode.triggerCapture();
    }
    ^///? } else {
    @Inject(method = "onPress(JIII)V", at = @At("HEAD"), cancellable = true)
    private void crazyphone$capturePress(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!CrazyPhoneCaptureMode.isActive() || action != GLFW.GLFW_PRESS)
            return;
        ci.cancel();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
            CrazyPhoneCaptureMode.exit();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            CrazyPhoneCaptureMode.triggerCapture();
    }
    //?}
}
*///?}
