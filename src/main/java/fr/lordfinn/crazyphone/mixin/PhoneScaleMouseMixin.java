package fr.lordfinn.crazyphone.mixin;

import fr.lordfinn.crazyphone.client.gui.PhoneScale;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hands a scaled phone screen (see PhoneScale) its mouse events in its own virtual coordinates: click,
 * release, drag (position and delta), scroll and move. Vanilla computes every screen mouse position as
 * {@code cursor * guiScaledSize / windowSize}; this swaps the GUI-scaled size for the phone's virtual one
 * at exactly those spots, so any screen, widget or EditBox below sees coordinates that match what is drawn,
 * with no per-screen code. A no-op (the original value is returned untouched) whenever the current screen
 * is not a scaled phone screen.
 *
 * Up to 1.21.1 the conversion is inlined in MouseHandler's onPress/onScroll/onMove (or
 * handleAccumulatedMovement); 1.21.10+ funnels it all through the static getScaledXPos/getScaledYPos.
 * 1.21.1 and newer chain with other mods (MixinExtras / a plain return-value inject); Forge 1.20.1 ships no
 * MixinExtras, so it uses a plain redirect there.
 */
@Mixin(MouseHandler.class)
public abstract class PhoneScaleMouseMixin {
    //? if <1.20.5 {
    @org.spongepowered.asm.mixin.injection.Redirect(method = {"onPress", "onScroll", "onMove"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int crazyphone$virtualWidth(com.mojang.blaze3d.platform.Window window) {
        return PhoneScale.virtualGuiScaledSize(window.getGuiScaledWidth());
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = {"onPress", "onScroll", "onMove"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int crazyphone$virtualHeight(com.mojang.blaze3d.platform.Window window) {
        return PhoneScale.virtualGuiScaledSize(window.getGuiScaledHeight());
    }
    //?}

    //? if >=1.20.5 <1.21.10 {
    /*@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = {"onPress", "onScroll", "handleAccumulatedMovement"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int crazyphone$virtualWidth(int guiScaledWidth) {
        return PhoneScale.virtualGuiScaledSize(guiScaledWidth);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = {"onPress", "onScroll", "handleAccumulatedMovement"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int crazyphone$virtualHeight(int guiScaledHeight) {
        return PhoneScale.virtualGuiScaledSize(guiScaledHeight);
    }
    *///?}

    //? if >=1.21.10 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"), cancellable = true)
    private static void crazyphone$virtualX(com.mojang.blaze3d.platform.Window window, double x,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(PhoneScale.toVirtualCoordinate(cir.getReturnValueD()));
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"), cancellable = true)
    private static void crazyphone$virtualY(com.mojang.blaze3d.platform.Window window, double y,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(PhoneScale.toVirtualCoordinate(cir.getReturnValueD()));
    }
    *///?}
}
