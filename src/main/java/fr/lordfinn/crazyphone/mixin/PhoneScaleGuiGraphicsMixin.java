package fr.lordfinn.crazyphone.mixin;

import fr.lordfinn.crazyphone.client.gui.PhoneScale;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a scaled phone screen renders (see PhoneScale), the graphics it draws with live in the phone's
 * virtual coordinate space:
 * <ul>
 * <li>guiWidth()/guiHeight() report the virtual size, which is what vanilla's tooltip placement (keep the
 * box on screen, flip it left/up near an edge) and wrapping width are computed against;</li>
 * <li>before 1.21.10, scissor rectangles are taken in raw GUI coordinates (the pose is ignored), so they
 * are converted from virtual to GUI coordinates here; 1.21.10+ already transforms them by the pose.</li>
 * </ul>
 * Both only apply to the exact graphics instance a phone screen pushed its zoom on, and only during that
 * screen's render pass; everything else (HUD, other screens, other mods) sees vanilla behavior.
 */
@Mixin(/*$ gui_graphics_type {*/GuiGraphics/*$}*/.class)
public abstract class PhoneScaleGuiGraphicsMixin {
    @Inject(method = "guiWidth", at = @At("HEAD"), cancellable = true)
    private void crazyphone$virtualGuiWidth(CallbackInfoReturnable<Integer> cir) {
        if (PhoneScale.activeFactor(this) > 1.0)
            cir.setReturnValue(PhoneScale.activeVirtualWidth(this, 0));
    }

    @Inject(method = "guiHeight", at = @At("HEAD"), cancellable = true)
    private void crazyphone$virtualGuiHeight(CallbackInfoReturnable<Integer> cir) {
        if (PhoneScale.activeFactor(this) > 1.0)
            cir.setReturnValue(PhoneScale.activeVirtualHeight(this, 0));
    }

    //? if <1.21.10 {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int crazyphone$scissorMinX(int value) {
        return (int) Math.floor(value * PhoneScale.activeFactor(this));
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int crazyphone$scissorMinY(int value) {
        return (int) Math.floor(value * PhoneScale.activeFactor(this));
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int crazyphone$scissorMaxX(int value) {
        return (int) Math.ceil(value * PhoneScale.activeFactor(this));
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int crazyphone$scissorMaxY(int value) {
        return (int) Math.ceil(value * PhoneScale.activeFactor(this));
    }

    //?}

    // Widgets test the mouse against the scissor stack before anything else (containsPointInScissor, from
    // 1.20.5 on): the stack now holds GUI coordinates (converted above), so the virtual mouse point is
    // converted the same way.
    //? if >=1.20.5 <1.21.10 {
    /*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int crazyphone$scissorPointX(int x) {
        return (int) Math.floor(x * PhoneScale.activeFactor(this));
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int crazyphone$scissorPointY(int y) {
        return (int) Math.floor(y * PhoneScale.activeFactor(this));
    }
    *///?}

    // 1.21.10+ stores scissor rectangles already transformed by the pose, but containsPointInScissor (what
    // every widget's hover test checks first) compares them with an untransformed point: inside a zoomed
    // phone, a widget in a scrolled list would never look hovered. Bring the point into the same space.
    //? if >=1.21.10 {
    /*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int crazyphone$scissorPointX(int x) {
        if (PhoneScale.activeFactor(this) <= 1.0)
            return x;
        org.joml.Matrix3x2f pose = ((/^$ gui_graphics_type {^/GuiGraphics/^$}^/) (Object) this).pose();
        return (int) Math.floor(pose.m00() * x + pose.m20());
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int crazyphone$scissorPointY(int y) {
        if (PhoneScale.activeFactor(this) <= 1.0)
            return y;
        org.joml.Matrix3x2f pose = ((/^$ gui_graphics_type {^/GuiGraphics/^$}^/) (Object) this).pose();
        return (int) Math.floor(pose.m11() * y + pose.m21());
    }
    *///?}
}
