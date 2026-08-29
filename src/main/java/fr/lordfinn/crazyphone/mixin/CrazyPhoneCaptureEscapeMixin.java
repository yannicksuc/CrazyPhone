package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code ScreenEvent.Opening} hook
 * (Fabric API has no direct equivalent). Escape while framing a shot has no Screen to fall back on
 * (mc.setScreen(null) in enter()), so vanilla opens the pause menu the same way it would on any other
 * in-game Escape press - every screen open funnels through Minecraft#setScreen, so cancelling it here when
 * the incoming screen is specifically PauseScreen and capture mode is active redirects to a normal exit()
 * (mouse-grab restored, previous screen if any reopened) instead of ever actually showing the pause menu.
 */
//? if fabric {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(Minecraft.class)
public abstract class CrazyPhoneCaptureEscapeMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void crazyphone$interceptPauseWhileCapturing(Screen screen, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive() && screen instanceof PauseScreen) {
            ci.cancel();
            CrazyPhoneCaptureMode.exit();
        }
    }
}
*///?}
