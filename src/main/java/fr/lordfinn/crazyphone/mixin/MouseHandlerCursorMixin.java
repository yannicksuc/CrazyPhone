package fr.lordfinn.crazyphone.mixin;

import fr.lordfinn.crazyphone.client.PhoneCursorStabilizer;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the cursor position PhoneCursorStabilizer recorded, immediately after vanilla centers the
 * cursor as part of a mouse grab/release transition (see MouseHandler#releaseMouse / #grabMouse, both
 * called from Minecraft#setScreen on every container open/close). Injecting at the tail of both methods
 * covers the full close-then-reopen cycle a phone-to-phone menu switch goes through.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerCursorMixin {

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseX(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        double[] dev = PhoneCursorStabilizer.devMouse;
        if (dev != null)
            cir.setReturnValue(dev[0]);
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseY(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        double[] dev = PhoneCursorStabilizer.devMouse;
        if (dev != null)
            cir.setReturnValue(dev[1]);
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void crazyphone$restoreCursorAfterRelease(CallbackInfo ci) {
        restoreIfPending();
    }

    @Inject(method = "grabMouse", at = @At("TAIL"))
    private void crazyphone$restoreCursorAfterGrab(CallbackInfo ci) {
        restoreIfPending();
    }

    private void restoreIfPending() {
        double[] pos = new double[2];
        if (!PhoneCursorStabilizer.consumePendingRestore(pos))
            return;

        //? if <1.21.10 {
        long window = Minecraft.getInstance().getWindow().getWindow();
        //? } else {
        /*long window = Minecraft.getInstance().getWindow().handle();
        *///?}
        // 26.3 swapped GLFW for SDL3: same warp vanilla's own InputConstants#releaseMouse does there.
        //? if >=26.3 {
        /*org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window, (float) pos[0], (float) pos[1]);
        *///? } else {
        GLFW.glfwSetCursorPos(window, pos[0], pos[1]);
        //?}
        this.xpos = pos[0];
        this.ypos = pos[1];
    }
}
