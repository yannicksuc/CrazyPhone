package fr.lordfinn.crazyphone.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose;

/**
 * Feeds the same raw accumulated mouse delta vanilla's own {@code MouseHandler#turnPlayer} reads for
 * camera look into {@link CrazyPhoneSelfieStickPose} as a side channel, while
 * {@link CrazyPhoneCaptureMode#isSelfieMode()} is active - read at the HEAD of
 * {@code handleAccumulatedMovement()}, before vanilla's own end-of-method reset zeroes both fields out,
 * and never cancelled - normal camera look stays completely untouched, exactly as requested ("camera
 * shouldn't move [because of this] for now").
 */
@Mixin(MouseHandler.class)
public abstract class CrazyPhoneSelfieStickMouseMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void crazyphone$captureSelfieStickDelta(CallbackInfo ci) {
        if (CrazyPhoneCaptureMode.isSelfieMode())
            CrazyPhoneSelfieStickPose.addMouseDelta(this.accumulatedDX, this.accumulatedDY);
    }
}
