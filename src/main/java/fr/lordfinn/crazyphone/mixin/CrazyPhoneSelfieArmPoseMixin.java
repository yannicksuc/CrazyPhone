package fr.lordfinn.crazyphone.mixin;

/**
 * Overrides an entity's own main-hand arm rotation directly, right after vanilla's own {@code setupAnim} has
 * already computed its normal pose for the frame, while
 * {@link fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose#isSelfieFraming(net.minecraft.world.entity.LivingEntity)}
 * holds for THIS specific entity - true for the local player mid-capture-mode, or for any OTHER entity whose
 * held phone reports selfie framing over the network (see that class's own doc comment). Structurally
 * mirrors {@link PlayerPresentPoseMixin} (same three version-shaped branches, same "override the ModelPart
 * rotation after setupAnim" technique - see that file's own doc comment for the full reasoning on each
 * branch boundary), but deliberately its own separate mixin/target pair rather than added onto that one -
 * selfie framing and presenting are unrelated features.
 */
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;

@Mixin(LivingEntityRenderer.class)
public abstract class CrazyPhoneSelfieArmPoseMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER),
            require = 0)
    private void crazyphone$applySelfiePose(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!CrazyPhoneSelfiePose.isSelfieFraming(entity))
            return;
        if (!(this.model instanceof HumanoidModel<?> humanoidModel))
            return;
        CrazyPhoneSelfiePose.applyArmTransform(humanoidModel, entity, entity.getMainArm());
        CrazyPhoneSelfiePose.applyHeadTransform(humanoidModel, entity, partialTicks);
    }
}
//?}
// 1.21.10-only inert placeholder - same reasoning as PlayerPresentPoseMixin's own placeholder for this
// version (a third, unreconciled RenderState API shape, not worth chasing in this pass).
//? if >=1.21.10 <26 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhoneSelfieArmPoseMixin {
}
*///?}
//? if >=26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhoneSelfieState;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;

// >=26: same extractRenderState/submit split PlayerPresentPoseMixin's own >=26 branch already documents in
// full - "is this the local player mid-selfie" gets computed once in extractRenderState (has the live
// entity; here that's CrazyPhoneSelfieAvatarExtractMixin, not this class - see that mixin's own doc comment
// for why AvatarRenderer's own override means this class's own extractRenderState injection would silently
// never fire for a real player) and carried across to submit() via CrazyPhoneSelfieRenderStateMixin's own
// bridge field, since submit() only ever sees the per-frame RenderState, never the entity itself.
@Mixin(LivingEntityRenderer.class)
public abstract class CrazyPhoneSelfieArmPoseMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    protected M model;

    // setupAnim's INVOKE owner is EntityModel, not Model - confirmed via javap -c against the real
    // compiled LivingEntityRenderer.class ("invokevirtual ... Method net/minecraft/client/model/
    // EntityModel.setupAnim:(Ljava/lang/Object;)V"): javac emits invokevirtual against the receiver's
    // static/erased type at the call site (M extends EntityModel<? super S>, here), not the class that
    // actually declares the method (Model<S>) - same shape PlayerPresentPoseMixin's own >=26 branch
    // already documents and relies on. (First checked this against the decompiled Java source alone,
    // which only shows declaring classes, not INVOKE owners, and got it backwards - reverted.)
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER),
            require = 0)
    private void crazyphone$applySelfiePose(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        if (!((ICrazyPhoneSelfieState) state).crazyphone$isSelfie())
            return;
        if (!(this.model instanceof HumanoidModel<?> humanoidModel))
            return;
        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null)
            return;
        // Local-player-only for now (matching AvatarExtractMixin's own local-only isSelfie computation
        // below) - submit() only ever sees the per-frame RenderState, never the entity itself, so full
        // cross-player support here would need stickX/stickY/mainArm bridged as their own RenderState
        // fields the way isSelfie already is, not yet done for this version (see this project's own
        // established "primary target first, port later" pattern - the <1.21.10 branch above is fully
        // cross-player-aware and is the actively live-tested target).
        // Redundant with PlayerModelSelfiePoseMixin (the actual fix, NeoForge-only for now - see that
        // mixin's own doc comment for why THIS injection point alone has no visible effect on >=26: the body
        // mesh's own deferred draw re-invokes setupAnim at actual render time, discarding whatever gets
        // mutated here afterward) - kept anyway, matching PlayerPresentPoseMixin's own >=26 branch precedent
        // for the equivalent presenting feature. applyHeadTransform deliberately NOT called here (unlike
        // that mixin) - state.partialTick doesn't compile on this branch's own Fabric target (a real
        // loader-specific field-access difference, confirmed live on 26.1-fabric), and since this whole call
        // site is already known dead weight, chasing that difference isn't worth it.
        CrazyPhoneSelfiePose.applyArmTransform(humanoidModel, localPlayer, localPlayer.getMainArm());
    }
}
*///?}
