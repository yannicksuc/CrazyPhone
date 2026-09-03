package fr.lordfinn.crazyphone.mixin;

/**
 * >=26: {@link CrazyPhoneSelfieArmPoseMixin}'s own >=26 approach - inject right after
 * LivingEntityRenderer#submit()'s explicit this.model.setupAnim(state) call and mutate the arm/head bones in
 * place - has no visible effect live (live-reported: "the arm still doesn't move", head not turning either):
 * the body model itself is submitted via submitNodeCollector.submitModel(this.model, state, ...) BEFORE that
 * explicit call (confirmed reading the real decompiled submit() body - the explicit setupAnim() right before
 * the layers loop only exists so LAYERS see a freshly-posed model, it's not what actually poses the body
 * mesh for its own deferred draw), so whatever pose the body's own deferred submission captures/recomputes
 * at actual draw time silently overwrites any live mutation applied afterward in submit() itself. Exactly
 * the same root cause {@link PlayerModelPresentPoseMixin}'s own doc comment already documents and fixed for
 * the unrelated presenting feature - mirrored here.
 *
 * Injecting straight into PlayerModel#setupAnim(AvatarRenderState) - the method that actually (re)computes
 * bone pose from state, however many times or whenever it's invoked - sidesteps the whole question of
 * submission timing entirely: every real invocation, deferred or not, lands here and gets the override
 * applied fresh. CrazyPhoneSelfieAvatarExtractMixin's own extractRenderState injection (still needed,
 * unchanged) is what populates the ICrazyPhoneSelfieState flag this reads.
 *
 * Was neoforge-only at first (matching PlayerModelPresentPoseMixin's own >=26 branch, which stayed
 * neoforge-only since presenting never needed Fabric parity) - live-reported on 26.1-fabric once the camera
 * itself started working there ("camera moving, arm not moving/not following") that selfie DOES need this on
 * Fabric too, so widened to both loaders.
 *
 * state.partialTick itself does NOT port, though - confirmed via javap against the real NeoForge-patched
 * jar (versions/26.1/build/moddev/artifacts/minecraft-patched-*.jar): NeoForge's own patch adds
 * {@code public float partialTick} directly onto vanilla EntityRenderState (also re-parents it onto NeoForge's
 * own BaseRenderState) - plain Fabric's EntityRenderState has no such field at all, hence "cannot find symbol"
 * there specifically (not the same cause as CrazyPhoneSelfieArmPoseMixin's own generic-erasure compile
 * failure on its shared >=26 branch - a genuinely different, field-doesn't-exist-on-Fabric problem). Reading
 * partial tick from Minecraft's own DeltaTracker instead sidesteps this - loader-neutral, vanilla API.
 */
//? if >=26 {
/*import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;

@Mixin(PlayerModel.class)
public abstract class PlayerModelSelfiePoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void crazyphone$applySelfiePose(AvatarRenderState state, CallbackInfo ci) {
        if (!((ICrazyPhoneSelfieState) state).crazyphone$isSelfie())
            return;
        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null)
            return;
        PlayerModel self = (PlayerModel) (Object) this;
        // Local-player-only for now - see CrazyPhoneSelfieArmPoseMixin's own >=26 branch doc comment for why
        // (submit() itself never had the live entity either; this injection point doesn't gain it back).
        float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CrazyPhoneSelfiePose.applyArmTransform(self, localPlayer, localPlayer.getMainArm());
        CrazyPhoneSelfiePose.applyHeadTransform(self, localPlayer, partialTick);
    }
}
*///?} else {
// Inert placeholder for every other version - PlayerModel#setupAnim(AvatarRenderState) is a >=26-only
// signature (see this file's own doc comment); older versions are covered by CrazyPhoneSelfieArmPoseMixin's
// own <1.21.10 branch instead.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PlayerModelSelfiePoseMixin {
}
//?}
