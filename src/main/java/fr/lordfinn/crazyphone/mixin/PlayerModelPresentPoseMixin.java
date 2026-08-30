package fr.lordfinn.crazyphone.mixin;

/**
 * >=26: AvatarPresentPoseMixin's original approach - inject right after AvatarRenderer#submit()'s explicit
 * this.model.setupAnim(state) call and mutate the arm bones in place - had no visible effect live: the body
 * model itself is submitted via submitNodeCollector.submitModel(this.model, state, ...) BEFORE that explicit
 * call (confirmed reading the real decompiled submit() body - the explicit setupAnim() right before the
 * layers loop only exists so LAYERS see a freshly-posed model, it's not what actually poses the body mesh
 * for its own deferred draw), so whatever pose the body's own deferred submission captures/recomputes at
 * actual draw time silently overwrites any live mutation applied afterward in submit() itself.
 *
 * Injecting straight into PlayerModel#setupAnim(AvatarRenderState) - the method that actually (re)computes
 * bone pose from state, however many times or whenever it's invoked - sidesteps the whole question of
 * submission timing entirely: every real invocation, deferred or not, lands here and gets the override
 * applied fresh. AvatarPresentPoseMixin's own extractRenderState injection (still needed, unchanged) is what
 * populates the ICrazyPhonePresentingState flag this reads.
 */
//? if neoforge && >=26 {
/*import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhonePresentingState;

@Mixin(PlayerModel.class)
public abstract class PlayerModelPresentPoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void crazyphone$presentPhoto(AvatarRenderState state, CallbackInfo ci) {
        boolean presenting = ((ICrazyPhonePresentingState) state).crazyphone$isPresenting();
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingThisRender = presenting;
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.isDualPresentingThisRender = ((ICrazyPhonePresentingState) state).crazyphone$isDualPresenting();
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingEntityYaw = state.bodyRot;
        if (!presenting)
            return;
        PlayerModel self = (PlayerModel) (Object) this;
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(self, HumanoidArm.RIGHT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(self, HumanoidArm.LEFT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingHeadPitch = self.head.xRot;
    }
}
*///?} else {
// Inert placeholder for every other version - PlayerModel#setupAnim(AvatarRenderState) is a >=26-only
// signature (see this file's own doc comment); older versions are covered by PlayerPresentPoseMixin's own
// <1.21.10 branch instead.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PlayerModelPresentPoseMixin {
}
//?}
