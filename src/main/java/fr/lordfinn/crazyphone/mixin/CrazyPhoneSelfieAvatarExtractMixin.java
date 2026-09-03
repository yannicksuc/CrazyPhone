package fr.lordfinn.crazyphone.mixin;

/**
 * >=26 companion to {@link CrazyPhoneSelfieArmPoseMixin}, mirroring exactly why
 * {@link AvatarPresentPoseMixin} exists as its own file alongside {@link PlayerPresentPoseMixin}: the real
 * player renderer on >=26 is {@code AvatarRenderer}, which OVERRIDES {@code extractRenderState} with its own
 * {@code AvatarRenderState}-typed signature - Java virtual dispatch means an injection into the BASE
 * {@code LivingEntityRenderer#extractRenderState} never actually fires for a real player, so this duplicates
 * just that one injection against {@code AvatarRenderer}'s own override instead. Sets
 * {@link fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState}, which
 * {@link CrazyPhoneSelfieArmPoseMixin}'s own >=26 {@code submit} injection then reads - AvatarRenderState
 * transitively extends LivingEntityRenderState, so CrazyPhoneSelfieRenderStateMixin's field already applies
 * with no extra mixin needed for the state side itself.
 *
 * Was neoforge-only at first, copied from AvatarPresentPoseMixin's own >=26 gate without checking whether
 * that scoping actually applied here too - it doesn't: AvatarPresentPoseMixin stayed neoforge-only because
 * presenting never got a Fabric port, but selfie DID (see PlayerModelSelfiePoseMixin's own doc comment).
 * Without this mixin running on Fabric, ICrazyPhoneSelfieState#crazyphone$isSelfie() is never set true there,
 * so PlayerModelSelfiePoseMixin's own isSelfie check always failed silently (require=0 on that Inject masks
 * it) - live-reported as "camera moves now, arm/head still don't" once the camera itself got fixed. Widened
 * to both loaders.
 */
//? if >=26 {
/*import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;

@Mixin(AvatarRenderer.class)
public abstract class CrazyPhoneSelfieAvatarExtractMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void crazyphone$extractSelfie(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        boolean selfie = entity == Minecraft.getInstance().player && CrazyPhoneSelfiePose.isSelfieFraming(entity);
        ((ICrazyPhoneSelfieState) state).crazyphone$setSelfie(selfie);
    }
}
*///?} else {
// Inert placeholder for every other version - AvatarRenderer/Avatar are >=26-only names (see this file's
// own doc comment); older versions are entirely covered by CrazyPhoneSelfieArmPoseMixin's own <1.21.10
// branch, which reads the live entity directly and needs no extraction step at all.
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhoneSelfieAvatarExtractMixin {
}
//?}
