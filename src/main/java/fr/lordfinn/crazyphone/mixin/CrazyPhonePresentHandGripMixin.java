package fr.lordfinn.crazyphone.mixin;

/**
 * While sneak-presenting, draws BOTH hands gripping the presented card, mirrored left/right by
 * CrazyPhonePresentDebug#handX - regardless of what's actually held in each hand. Loader-neutral (same
 * reasoning as CrazyPhonePresentHandGripInvokerMixin - neither loader has a dedicated event for this) but
 * version-gated the same way that class and PlayerPresentPoseMixin are - see either one's own doc comment.
 *
 * Vanilla's own {@code renderArmWithItem} only ever draws a bare-arm mesh ({@code renderPlayerArm}) for an
 * EMPTY hand, and even then only when that empty hand happens to be specifically the MAIN hand (no floating
 * empty off-hand fist normally either) - a hand actually holding an item (the phone, in the common case)
 * gets no arm mesh at all, just the item's own model via renderItem/CrazyPhonePhotoItemRenderer. An earlier
 * version of this mixin tried to patch just that one specific gap (main hand = phone, off hand = empty)
 * conditionally, but still only ever showed one hand live - simpler and more robust to stop trying to figure
 * out which of vanilla's several branches applies and just unconditionally draw both arms ourselves via a
 * manual invoke (CrazyPhonePresentHandGripInvokerMixin's own {@literal @}Invoker onto the otherwise-private
 * renderPlayerArm - split into its own file, see that class's own doc comment for why) right before the
 * frame's buffer flush, positioned with the same "cancel then reapply the real camera angle" grip transform
 * CrazyPhonePhotoItemRenderer's own first-person presenting branch uses. The phone's own item model still
 * renders normally alongside on the holding hand's side - this only adds the arm meshes on top.
 */
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhonePresentDebug;
import fr.lordfinn.crazyphone.client.CrazyPhonePresentPose;

@Mixin(ItemInHandRenderer.class)
public abstract class CrazyPhonePresentHandGripMixin {
    // Fires for every call to renderPlayerArm regardless of who triggers it - vanilla's own natural call
    // (an empty main hand) and this mixin's own manual invokes below both land here identically, so both
    // end up positioned the same way.
    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void crazyphone$gripPresentedCard(PoseStack poseStack, MultiBufferSource bufferSource, int light, float equipProgress, float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        if (!CrazyPhonePresentPose.isPresenting(Minecraft.getInstance().player))
            return;
        crazyphone$applyGripTransform(poseStack, arm);
    }

    // Injected right before MultiBufferSource.BufferSource#endBatch, not at TAIL (after it) - TAIL would add
    // these arms' vertices after the batch has already been flushed, risking them not drawing at all (or
    // drawing on a later, unrelated flush) instead of as part of this same frame's hand render.
    @Inject(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void crazyphone$renderBothGripHands(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (!CrazyPhonePresentPose.isPresenting(player))
            return;
        CrazyPhonePresentHandGripInvokerMixin self = (CrazyPhonePresentHandGripInvokerMixin) this;
        for (HumanoidArm arm : HumanoidArm.values()) {
            poseStack.pushPose();
            self.crazyphone$renderPlayerArm(poseStack, bufferSource, light, 0f, 0f, arm);
            poseStack.popPose();
        }
    }

    // Same "cancel then reapply the real camera angle" technique as CrazyPhonePhotoItemRenderer's own first-
    // person presenting branch (see that method's own doc comment) - duplicated here rather than shared
    // because this runs on a different class entirely with no common call site to factor it into.
    private static void crazyphone$applyGripTransform(PoseStack poseStack, HumanoidArm arm) {
        Vector3f pos = poseStack.last().pose().getTranslation(new Vector3f());
        poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
        poseStack.translate(-pos.x, -pos.y, -pos.z);
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float yaw = camera.getYRot() * CrazyPhonePresentDebug.yawSign + CrazyPhonePresentDebug.yawOffset;
        float pitch = camera.getXRot() * CrazyPhonePresentDebug.pitchSign + CrazyPhonePresentDebug.pitchOffset;
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        poseStack.translate(side * CrazyPhonePresentDebug.handX, CrazyPhonePresentDebug.y + CrazyPhonePresentDebug.handY, 1f / 16f + CrazyPhonePresentDebug.z + CrazyPhonePresentDebug.handZ);
    }
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhonePresentHandGripMixin {
}
*///?}
