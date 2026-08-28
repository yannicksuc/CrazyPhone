package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's {@code RenderGuiEvent.Post} hook
 * (Fabric API has no direct equivalent) - draws the capture reticle/zoom readout on top of the rest of the
 * HUD by injecting at the TAIL of Gui#render, after the layered HUD system (hotbar included) has already
 * drawn. Gui#render's own signature changed from {@code (GuiGraphics, float)} to
 * {@code (GuiGraphics, DeltaTracker)} at the 1.20.5 boundary (confirmed via decompiled source for both
 * Fabric nodes' Minecraft versions, same as CrazyPhonePresentGuiMixin), hence the nested version gate.
 */
//? if fabric {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(Gui.class)
public abstract class CrazyPhoneCaptureGuiMixin {
    //? if <1.20.5 {
    /^@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"))
    private void crazyphone$drawCaptureOverlay(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        CrazyPhoneCaptureMode.drawOverlay(guiGraphics);
    }
    ^///?} else {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void crazyphone$drawCaptureOverlay(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        CrazyPhoneCaptureMode.drawOverlay(guiGraphics);
    }
    //?}
}
*///?}
