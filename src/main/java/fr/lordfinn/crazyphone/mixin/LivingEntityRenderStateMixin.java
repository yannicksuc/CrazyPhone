package fr.lordfinn.crazyphone.mixin;

/**
 * Attaches {@link fr.lordfinn.crazyphone.client.ICrazyPhonePresentingState}'s backing field to
 * {@code LivingEntityRenderState} - see that interface's own doc comment for why this needs to live on the
 * per-frame state object rather than a shared static, on >=1.21.10 only (no such state object exists on
 * older versions - PlayerPresentPoseMixin's own <1.21.10 branch reads the live entity directly instead).
 */
//? if >=1.21.10 {
/*import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import fr.lordfinn.crazyphone.client.ICrazyPhonePresentingState;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements ICrazyPhonePresentingState {
    @Unique
    private boolean crazyphone$presenting = false;

    @Override
    public boolean crazyphone$isPresenting() {
        return this.crazyphone$presenting;
    }

    @Override
    public void crazyphone$setPresenting(boolean value) {
        this.crazyphone$presenting = value;
    }
}
*///?} else {
/*// Inert placeholder for <1.21.10 - crazyphone.mixins.json references this class unconditionally across
// every NeoForge node (same reasoning as PlayerPresentPoseMixin's own <1.21.10 placeholder), but there's no
// LivingEntityRenderState to attach anything to on this version at all.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class LivingEntityRenderStateMixin {
}
*///?}
