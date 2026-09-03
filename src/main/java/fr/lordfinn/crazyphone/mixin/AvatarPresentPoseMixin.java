package fr.lordfinn.crazyphone.mixin;

/**
 * >=26 companion to {@link PlayerPresentPoseMixin}: the actual player renderer on >=26 is
 * {@code net.minecraft.client.renderer.entity.player.AvatarRenderer} (the old {@code PlayerRenderer} was
 * renamed/replaced as part of the same rename that turned {@code Player} into a narrower concept alongside
 * the new {@code Avatar}), and it OVERRIDES {@code extractRenderState} with its own
 * {@code AvatarRenderState}-typed signature instead of just inheriting {@code LivingEntityRenderer}'s -
 * confirmed via javap on the real 26.1.2.100 compiled class. PlayerPresentPoseMixin's own injection into the
 * BASE LivingEntityRenderer method never actually fires for a real player as a result - Java virtual
 * dispatch always calls the most-derived override - so this mixin duplicates just that ONE injection against
 * AvatarRenderer's own overridden extractRenderState instead. Harmless to keep PlayerPresentPoseMixin's own
 * >=26 injection alongside this (it just stays permanently unfired for players).
 *
 * Only extractRenderState lives here now - the arm-pose application itself moved to
 * PlayerModelPresentPoseMixin (targeting PlayerModel#setupAnim directly) after this mixin's original
 * approach (injecting into AvatarRenderer#submit() right after its own explicit setupAnim() call) turned out
 * to have no visible effect live - see that file's own doc comment for why.
 *
 * AvatarRenderState transitively extends LivingEntityRenderState (via HumanoidRenderState,
 * ArmedEntityRenderState - confirmed by reading each class's own declaration in the decompiled source), so
 * LivingEntityRenderStateMixin's ICrazyPhonePresentingState field already applies to it with no extra mixin
 * needed for the state side.
 *
 * Was neoforge-only at first - copied from an assumption that presenting never needed Fabric parity,
 * without checking whether THIS specific injection point actually depended on that. It does: without it
 * running on Fabric, isPresenting/isDualPresenting never get computed for a real player there at all (the
 * base LivingEntityRenderer#extractRenderState injection in PlayerPresentPoseMixin never fires for a real
 * player either, same virtual-dispatch reason this file's own doc comment already explains), so
 * presentingThisRender stays permanently false and CrazyPhonePhotoItemRenderer's third-person presenting
 * branch never activates - live-reported on 26.1-fabric as "third-person sneak-presenting doesn't work
 * anymore" (it never actually worked there; this is the same AvatarRenderer-override gap
 * CrazyPhoneSelfieAvatarExtractMixin's own doc comment already found and fixed for selfie mode). Widened to
 * both loaders.
 */
//? if >=26 {
/*import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhonePresentingState;

@Mixin(AvatarRenderer.class)
public abstract class AvatarPresentPoseMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void crazyphone$extractPresenting(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        boolean presenting = entity instanceof Player player && fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.isPresenting(player);
        boolean dualPresenting = entity instanceof Player player2 && fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.isDualPresenting(player2);
        ((ICrazyPhonePresentingState) state).crazyphone$setPresenting(presenting);
        ((ICrazyPhonePresentingState) state).crazyphone$setDualPresenting(dualPresenting);
    }
}
*///?} else {
// Inert placeholder for every other version - AvatarRenderer/Avatar are >=26-only names (see this file's
// own doc comment); older versions are entirely covered by PlayerPresentPoseMixin's own <1.21.10 branch.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class AvatarPresentPoseMixin {
}
//?}
