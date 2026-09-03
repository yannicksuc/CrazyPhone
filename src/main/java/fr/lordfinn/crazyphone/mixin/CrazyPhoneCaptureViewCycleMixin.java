package fr.lordfinn.crazyphone.mixin;

/**
 * While {@link fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode#isActive()}, F5 cycles through capture
 * mode's own 4 view states (see {@code CrazyPhoneCaptureMode#viewState}'s own doc comment) instead of
 * vanilla's normal 3-state first/third-back/third-front cycle.
 *
 * Not implemented via cancelling the raw key event - {@code InputEvent.Key} (NeoForge's own per-keystroke
 * event) is documented as NOT cancellable (confirmed via the real 1.21.1 decompiled source). Instead, this
 * injects at the HEAD of {@code Minecraft#handleKeybinds()} and drains the toggle-perspective keybind's own
 * click queue itself first: {@code KeyMapping#consumeClick()} returns true (and decrements its own internal
 * counter) once per queued press, so consuming every pending click here means vanilla's OWN
 * {@code while (this.options.keyTogglePerspective.consumeClick())} loop a few lines later in that same
 * method finds nothing left to consume and never runs its body at all - no cancellation needed, just winning
 * the race by running first.
 *
 * Targets a plain vanilla method with no known version-specific signature changes across this project's
 * targets - left ungated, matching this codebase's own precedent for similarly stable mixins (e.g.
 * MouseHandlerCursorMixin).
 */
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

@Mixin(Minecraft.class)
public abstract class CrazyPhoneCaptureViewCycleMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void crazyphone$cycleCaptureView(CallbackInfo ci) {
        if (!CrazyPhoneCaptureMode.isActive())
            return;
        Minecraft mc = (Minecraft) (Object) this;
        while (mc.options.keyTogglePerspective.consumeClick())
            CrazyPhoneCaptureMode.cycleView();
    }
}
