package fr.lordfinn.crazyphone.mixin;

/**
 * Overrides the player model's arm rotations directly, right after vanilla's own {@code setupAnim} has
 * already computed its normal pose for the frame, when CrazyPhonePresentPose#isPresenting holds - see that
 * class's own doc comment for the trigger condition and the actual rotation math
 * (CrazyPhonePresentPose#applyArmTransform).
 *
 * Deliberately overrides the bone rotations directly here instead of going through a real
 * {@code HumanoidModel.ArmPose} enum constant (NeoForge's IExtensibleEnum + EnumProxy mechanism for adding
 * one) - that mechanism needs a matching entry in a bundled enumextensions.json plus JSON-encodable
 * constructor arguments, which doesn't accommodate a lambda-typed IArmPoseTransformer argument cleanly and
 * isn't precisely documented for this exact case. Setting rightArm/leftArm's rotation straight after
 * setupAnim runs achieves the identical visual result without any of that machinery.
 *
 * Targets LivingEntityRenderer directly (a real, normally-importable class - not PlayerRenderer itself,
 * string-targeted or otherwise) since it's the one place a HumanoidModel-driven entity's setupAnim call and
 * its bone rotations are both reachable, for ANY humanoid entity (guarded down to just the local/remote
 * player via an isPresenting check inside). Three separate branches, not one <1.21.10/else split: <1.21.10
 * targets the old 6-arg render(LivingEntity, float, float, PoseStack, MultiBufferSource, int) method
 * directly; >=26 targets the reworked extractRenderState/submit(RenderState, ...) split instead (see that
 * branch's own doc comment); 1.21.10 itself sits between the two with yet another API shape (confirmed by
 * literally trying to compile the >=26 branch against it) and stays an inert placeholder, same as before -
 * not worth reconciling a third shape in the same pass.
 */
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerPresentPoseMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER),
            require = 0)
    private void crazyphone$presentPhoto(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        boolean presenting = entity instanceof Player player && fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.isPresenting(player);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingThisRender = presenting;
        // NOT this method's own "entityYaw" parameter - that one is barely used inside render() itself (it's
        // only forwarded to super.render() for name-tag/shadow purposes further down) and tracks closer to
        // the player's full look yaw, which is why using it made the card visibly follow head movement
        // instead of staying locked to the body. The rotation vanilla's own setupRotations() actually applies
        // to the model (Axis.YP.rotationDegrees(180 - bodyYaw)) is this locally-recomputed, smoothed body yaw
        // instead - reproducing that exact formula here so the cancel-and-reapply in
        // CrazyPhonePhotoItemRenderer lands on the same value the engine itself used.
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingEntityYaw = bodyYaw;
        if (!presenting)
            return;
        if (!(this.model instanceof HumanoidModel<?> humanoidModel))
            return;
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(humanoidModel, HumanoidArm.RIGHT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(humanoidModel, HumanoidArm.LEFT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingHeadPitch = humanoidModel.head.xRot;
    }
}
//?}
// 1.21.10-only inert placeholder: that version already has the RenderState-based rendering rework (same
// broader change CrazyPhonePhotoItemRenderer's own TODOs track), but its exact class/package shape (e.g.
// CameraRenderState's package) differs from 26.x's - confirmed by literally trying to compile the >=26
// branch below against it and reading the resulting error, not guessed. Not worth reconciling a THIRD API
// shape in the same pass; 1.21.10 keeps zero presenting support, unchanged from before this work.
//? if >=1.21.10 <26 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PlayerPresentPoseMixin {
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.ICrazyPhonePresentingState;

// >=26: LivingEntityRenderer's own render pipeline split into extractRenderState(entity, state, ...) (has
// the live entity, runs earlier) and submit(state, poseStack, ...) (the actual draw call - only ever sees
// the per-frame RenderState, model.setupAnim(state) included, never the entity itself). "Is this player
// presenting" has to be computed in extractRenderState and carried across to submit() somehow -
// LivingEntityRenderStateMixin attaches exactly that bridge onto the state object itself (see its own doc
// comment for why a shared static isn't safe to reuse here the way <1.21.10 got away with). Both target
// method descriptors and the EntityModel.setupAnim(Object) INVOKE owner/descriptor below are confirmed
// against the real 26.1.2.100 compiled class (javap -c on LivingEntityRenderer.class) rather than guessed -
// Model<S> declares setupAnim with an unbounded S, so it erases to setupAnim(Object), and the actual
// INVOKEVIRTUAL in submit()'s bytecode references EntityModel (M's own erased bound at the call site), not
// Model where the method is actually implemented - the same "erase to the static call-site type, not the
// declaring class" shape the old <1.21.10 target already relied on.
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerPresentPoseMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Shadow
    protected M model;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void crazyphone$extractPresenting(T entity, S state, float partialTicks, CallbackInfo ci) {
        boolean presenting = entity instanceof Player player && fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.isPresenting(player);
        ((ICrazyPhonePresentingState) state).crazyphone$setPresenting(presenting);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER),
            require = 0)
    private void crazyphone$presentPhoto(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, CallbackInfo ci) {
        boolean presenting = ((ICrazyPhonePresentingState) state).crazyphone$isPresenting();
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingThisRender = presenting;
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingEntityYaw = state.bodyRot;
        if (!presenting)
            return;
        if (!(this.model instanceof HumanoidModel<?> humanoidModel))
            return;
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(humanoidModel, HumanoidArm.RIGHT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.applyArmTransform(humanoidModel, HumanoidArm.LEFT);
        fr.lordfinn.crazyphone.client.CrazyPhonePresentPose.presentingHeadPitch = humanoidModel.head.xRot;
    }
}
*///?}
