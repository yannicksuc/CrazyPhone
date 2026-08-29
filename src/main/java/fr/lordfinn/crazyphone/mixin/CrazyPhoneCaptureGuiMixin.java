package fr.lordfinn.crazyphone.mixin;

/**
 * Fabric-only equivalent of the NeoForge-only CrazyPhoneCaptureMode's RenderGuiEvent.Post hook (Fabric API
 * has no direct equivalent). NeoForge-only was tried first here too (a single loader-neutral mixin
 * targeting plain Gui#render on both loaders), but NeoForge's own Minecraft instance actually runs
 * ExtendedGui - a subclass that fully overrides render() with its own per-overlay dispatch - so a Mixin
 * injected into the PARENT Gui#render silently never fires there at all (virtual dispatch always resolves
 * to the override); NeoForge's own event system (RenderGuiEvent.Post, see CrazyPhoneCaptureMode) is the
 * correct hook on that loader instead. Fabric has no such subclass, so targeting Gui#render directly here
 * is correct and necessary.
 *
 * Cancels the ENTIRE vanilla HUD render (hotbar, crosshair, chat, health/food/xp bars, everything) and
 * draws just this mod's own capture reticle/zoom readout in its place - simpler than replicating NeoForge's
 * own per-overlay suppression list on a loader that has no equivalent per-element event to hook, and the
 * end result (only this mod's own overlay visible while framing a shot) is the same either way.
 *
 * Gui#render's own signature changed from {@code (GuiGraphics, float)} to
 * {@code (GuiGraphics, DeltaTracker)} at the 1.20.5 boundary (confirmed via decompiled source for both
 * Fabric nodes' Minecraft versions), hence the nested version gate. 26.x renamed the method itself too,
 * matching the whole GuiGraphics->GuiGraphicsExtractor rework: Gui#render(GuiGraphics, DeltaTracker) became
 * Gui#extractRenderState(GuiGraphicsExtractor, DeltaTracker) - confirmed against the real decompiled 26.1.2
 * Gui.java. The @Inject "method" string is a raw JVM method descriptor, not a Java signature, so both the
 * method name AND the type's binary name (GuiGraphicsExtractor, same package) need updating together.
 */
//? if fabric {
/*import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui./^$ gui_graphics_type {^/GuiGraphics/^$}^/;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(Gui.class)
public abstract class CrazyPhoneCaptureGuiMixin {
    //? if <1.20.5 {
    /^@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void crazyphone$replaceHudWithCaptureOverlay(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive()) {
            CrazyPhoneCaptureMode.drawOverlay(guiGraphics);
            ci.cancel();
        }
    }
    ^///?}
    //? if >=1.20.5 <26 {
    /^@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void crazyphone$replaceHudWithCaptureOverlay(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive()) {
            CrazyPhoneCaptureMode.drawOverlay(guiGraphics);
            ci.cancel();
        }
    }
    ^///?}
    //? if >=26 {
    /^@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void crazyphone$replaceHudWithCaptureOverlay(GuiGraphicsExtractor guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isActive()) {
            CrazyPhoneCaptureMode.drawOverlay(guiGraphics);
            ci.cancel();
        }
    }
    ^///?}
}
*///?}
