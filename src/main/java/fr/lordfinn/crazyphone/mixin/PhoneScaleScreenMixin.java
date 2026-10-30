package fr.lordfinn.crazyphone.mixin;

import fr.lordfinn.crazyphone.client.gui.PhoneScale;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a scaled phone screen (see PhoneScale) bigger: one pose push around the screen's top-level,
 * final render entry point, the one place every version funnels a screen's whole frame through
 * (background, the most-derived render override, widget tooltips and deferred elements), so no
 * individual screen needs to know about the zoom. The mouse coordinates that entry point hands down are
 * replaced by virtual ones recomputed from the raw cursor, so hover states match what is drawn.
 *
 * Entry point per version: renderWithTooltip (up to 1.21.1), renderWithTooltipAndSubtitles (1.21.10),
 * extractRenderStateWithTooltipAndSubtitles (26.x). Does nothing at all for non-phone screens or at a
 * zoom of 1. The pop is tied to the exact graphics instance that was pushed (PhoneScale#endRender).
 */
@Mixin(Screen.class)
public abstract class PhoneScaleScreenMixin {
    @Inject(method = /*$ screen_render_entry {*/"renderWithTooltip"/*$}*/, at = @At("HEAD"))
    private void crazyphone$beginPhoneScale(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PhoneScale.beginRender((Screen) (Object) this, guiGraphics);
    }

    @ModifyVariable(method = /*$ screen_render_entry {*/"renderWithTooltip"/*$}*/, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int crazyphone$virtualMouseX(int mouseX) {
        return PhoneScale.virtualMouse(true, mouseX, PhoneScale.factorOf((Screen) (Object) this));
    }

    @ModifyVariable(method = /*$ screen_render_entry {*/"renderWithTooltip"/*$}*/, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int crazyphone$virtualMouseY(int mouseY) {
        return PhoneScale.virtualMouse(false, mouseY, PhoneScale.factorOf((Screen) (Object) this));
    }

    @Inject(method = /*$ screen_render_entry {*/"renderWithTooltip"/*$}*/, at = @At("RETURN"))
    private void crazyphone$endPhoneScale(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        PhoneScale.endRender(guiGraphics);
    }
}
