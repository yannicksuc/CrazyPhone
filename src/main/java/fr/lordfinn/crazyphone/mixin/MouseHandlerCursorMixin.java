package fr.lordfinn.crazyphone.mixin;

import fr.lordfinn.crazyphone.client.PhoneCursorStabilizer;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
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

        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, pos[0], pos[1]);
        this.xpos = pos[0];
        this.ypos = pos[1];
    }
}
