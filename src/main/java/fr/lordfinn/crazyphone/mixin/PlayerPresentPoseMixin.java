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
 * Targets LivingEntityRenderer#render directly (a real, normally-importable class - not PlayerRenderer
 * itself, string-targeted or otherwise) since it's the one place a HumanoidModel-driven entity's setupAnim
 * call and its bone rotations are both reachable in the same method, for ANY humanoid entity (guarded down
 * to just the local/remote player via an isPresenting check inside). <1.21.10 only: NeoForge 1.21.10's
 * rendering rework changed LivingEntityRenderer to a 3-type-parameter, RenderState-based signature entirely
 * (confirmed via javap - same broader rework CrazyPhonePhotoItemRenderer's own 1.21.10 TODOs already track),
 * so the >=1.21.10 branch below is a deliberately inert placeholder mixin (targets Minecraft.class, which is
 * never itself modified) rather than attempting that rework blind.
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
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

// Inert placeholder for >=1.21.10 - see this file's own doc comment. crazyphone.mixins.json references this
// class unconditionally across every NeoForge node, so it has to exist (and be a syntactically valid mixin)
// everywhere even where there's nothing to actually do yet.
@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class PlayerPresentPoseMixin {
}
*///?}
