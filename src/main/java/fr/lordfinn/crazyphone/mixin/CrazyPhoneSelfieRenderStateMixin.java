package fr.lordfinn.crazyphone.mixin;

/**
 * Attaches {@link fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState}'s backing field to
 * {@code LivingEntityRenderState} - see that interface's own doc comment for why this needs to live on the
 * per-frame state object rather than a shared static, on >=1.21.10 only (no such state object exists on
 * older versions - CrazyPhoneSelfieArmPoseMixin's own <1.21.10 branch reads the live entity directly
 * instead). A separate mixin from {@link LivingEntityRenderStateMixin} targeting the same class - Mixin
 * happily applies multiple independent mixins to one target, each contributing its own @Unique field, no
 * conflict - kept apart because selfie framing and presenting are unrelated features (see
 * ICrazyPhoneSelfieState's own doc comment).
 */
//? if >=1.21.10 {
/*import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState;

@Mixin(LivingEntityRenderState.class)
public abstract class CrazyPhoneSelfieRenderStateMixin implements ICrazyPhoneSelfieState {
    @Unique
    private boolean crazyphone$selfie = false;

    @Override
    public boolean crazyphone$isSelfie() {
        return this.crazyphone$selfie;
    }

    @Override
    public void crazyphone$setSelfie(boolean value) {
        this.crazyphone$selfie = value;
    }
}
*///?} else {
// Inert placeholder for <1.21.10 - crazyphone.mixins.json references this class unconditionally across
// every NeoForge node (same reasoning as LivingEntityRenderStateMixin's own placeholder), but there's no
// LivingEntityRenderState to attach anything to on this version at all.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhoneSelfieRenderStateMixin {
}
//?}
