package fr.lordfinn.crazyphone.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fr.lordfinn.crazyphone.client.BenchRunner;

/**
 * Dev only: a fake mouse position (window pixels) read by the game instead of the real one while
 * BenchRunner.devMouse is set, to look at hover states in a test game without moving the player's mouse (UI test
 * bench, /phoneui hover). Does nothing at all while it is null, which is always the case outside dev tools.
 */
@Mixin(MouseHandler.class)
public abstract class BenchMouseMixin {
    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseX(CallbackInfoReturnable<Double> cir) {
        double[] dev = BenchRunner.devMouse;
        if (dev != null) {
            BenchRunner.devMouseHookRan = true;
            cir.setReturnValue(dev[0]);
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseY(CallbackInfoReturnable<Double> cir) {
        double[] dev = BenchRunner.devMouse;
        if (dev != null)
            cir.setReturnValue(dev[1]);
    }

    // Since 1.21.10 the screens get the mouse through getScaledXPos/getScaledYPos(Window), which read the fields
    // directly (not through xpos()/ypos()). Same conversion as the game's own. Absent before: nothing to hook there.
    // (Only compiled there: the Forge 1.20.1 mixin processor refuses a target it cannot find.)
    //? if >=1.21.10 {
    /*@Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseScaledX(com.mojang.blaze3d.platform.Window window, CallbackInfoReturnable<Double> cir) {
        double[] dev = BenchRunner.devMouse;
        if (dev != null) {
            BenchRunner.devMouseHookRan = true;
            cir.setReturnValue(dev[0] * window.getGuiScaledWidth() / window.getScreenWidth());
        }
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$devMouseScaledY(com.mojang.blaze3d.platform.Window window, CallbackInfoReturnable<Double> cir) {
        double[] dev = BenchRunner.devMouse;
        if (dev != null)
            cir.setReturnValue(dev[1] * window.getGuiScaledHeight() / window.getScreenHeight());
    }
    *///?}
}
