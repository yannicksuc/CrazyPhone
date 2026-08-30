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
    //
    // 1.20.4-only: live testing showed the grip was only correctly oriented while facing due north (yaw
    // 180, pitch ~0) - exactly the one case where 180f-yaw/pitch below happen to compute to zero extra
    // rotation. That means the CANCEL step alone (before any reapply) already lands camera-relative on
    // this version, unlike >=1.20.5 where cancel-alone was proven (through the same kind of live testing)
    // to land at world-space identity, which is what made reapplying the real camera angle necessary there
    // in the first place. Reapplying it AGAIN here on a version where cancel is already camera-relative
    // double-counts the rotation - skip the reapply entirely below <1.20.5.
    //
    // No cancel-to-identity here anymore either way: this injection was never cancellable on this branch
    // (vanilla's own renderPlayerArm body always ran afterward, composing our small offset with its own
    // full chain - which is exactly why this branch never needed >=26's own distance anchor, vanilla's
    // uncancelled body already provides it). Resetting rotation to identity first was ALSO quietly
    // discarding renderHandsWithItems' own small natural view/walk bob every time, on every version this
    // branch covers - live-reported (>=26 first, but the same reset exists here) as the card visibly having
    // a subtle "follows the camera a beat late" motion the arms lacked entirely. Building straight on top of
    // whatever's already there (identity + that small bob) restores it without otherwise changing behavior,
    // since it's a tiny rotation and vanilla's own subsequent chain doesn't care what rotation preceded it.
    private static void crazyphone$applyGripTransform(PoseStack poseStack, HumanoidArm arm) {
        Vector3f pos = poseStack.last().pose().getTranslation(new Vector3f());
        poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
        poseStack.translate(-pos.x, -pos.y, -pos.z);
        // Live-confirmed: dropping this cancel to keep the natural bob (tried first, see git history) broke
        // camera-tracking outright - more than just the small bob was baked into that incoming rotation, so
        // "build on top of whatever's there" isn't safe. Keeping the reliable cancel-to-identity baseline
        // and instead recomputing renderHandsWithItems' own specific small "hand sway" formula ourselves
        // (Axis.XP/YP.rotationDegrees((viewRot - bob) * 0.1F)) gets the same subtle motion without that risk.
        // Gated >=1.20.5 (not attempted below that) because a fixed 1.0F partial tick here (tried first, on
        // >=26) reproduced a visible stutter - snapping between whole-tick values instead of interpolating
        // smoothly like the rest of the frame - and the fix (the game's own actual interpolated partial
        // tick, DeltaTracker#getGameTimeDeltaPartialTick(false)) doesn't exist before 1.20.5.
        //? if >=1.20.5 {
        /*LocalPlayer bobPlayer = Minecraft.getInstance().player;
        if (bobPlayer != null) {
            float partialTick = Minecraft.getInstance()./^$ mc_delta_tracker {^/getTimer/^$}^/().getGameTimeDeltaPartialTick(false);
            poseStack.mulPose(Axis.XP.rotationDegrees((bobPlayer.getViewXRot(partialTick) - bobPlayer.xBob) * 0.1F));
            poseStack.mulPose(Axis.YP.rotationDegrees((bobPlayer.getViewYRot(partialTick) - bobPlayer.yBob) * 0.1F));
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float yaw = camera.getYRot() * CrazyPhonePresentDebug.yawSign + CrazyPhonePresentDebug.yawOffset;
        float pitch = camera.getXRot() * CrazyPhonePresentDebug.pitchSign + CrazyPhonePresentDebug.pitchOffset;
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        *///?}
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        poseStack.translate(side * CrazyPhonePresentDebug.handX, CrazyPhonePresentDebug.y + CrazyPhonePresentDebug.handY, 1f / 16f + CrazyPhonePresentDebug.z + CrazyPhonePresentDebug.handZ);
    }
}
//?}
// 1.21.10-only inert placeholder: yet another incompatible API shape there (same finding as
// PlayerPresentPoseMixin/CrazyPhonePresentHandGripInvokerMixin), not worth reconciling a third shape.
//? if >=1.21.10 <26 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhonePresentHandGripMixin {
}
*///?}
//? if >=26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhonePresentDebug;
import fr.lordfinn.crazyphone.client.CrazyPhonePresentPose;

// >=26: renderPlayerArm/renderHandsWithItems keep the exact same shape as <1.21.10, just with
// MultiBufferSource swapped for SubmitNodeCollector (confirmed against the real decompiled source) - the
// renderHandsWithItems tail still literally calls this.minecraft.renderBuffers().bufferSource().endBatch()
// (an unrelated, still-MultiBufferSource-based flush for particles/features), so that INVOKE target is
// unchanged from the <1.21.10 branch below.
//
// UNLIKE CrazyPhonePhotoItemRenderer's own first/third-person presenting branches (where >=26's poseStack
// turned out to already be camera-aligned, no reapply needed), this mixin's poseStack comes from
// ItemInHandRenderer's own long-lived transform chain, not a fresh standalone one - a genuinely different
// code path, and it apparently still carries the same pre-existing camera-relative composition >=1.20.5
// always needed the reapply for. Confirmed live: removing the reapply reproduced 1.20.4's own old symptom
// verbatim ("only correctly oriented facing due north, ~yaw 180/pitch 0") - the cancel-only result the
// <1.20.5 comment below describes. Restored the same cancel + reapply camera formula the <1.20.5/>=1.20.5
// split already uses.
@Mixin(ItemInHandRenderer.class)
public abstract class CrazyPhonePresentHandGripMixin {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private Minecraft minecraft;

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher;

    // User's own insight, and a better one than anything tried before it: the card (rendered via vanilla's
    // own normal per-hand item path, later in this SAME method) and our bare-arm mesh only ever drift apart
    // because they were being computed at two different POINTS IN TIME within the frame - the card inline,
    // right here, and the arm deferred all the way to renderHandsWithItems' own endBatch, well after BOTH
    // hands' own per-hand processing had already run. Any residual difference accumulated in between (off-
    // hand processing, etc.) meant the two were never guaranteed to start from an identical poseStack, no
    // matter how good the transform math itself was - that's what independently produced both bugs (broken
    // wide-angle tracking with a full cancel, and card/arm drift without one): neither was actually a math
    // problem, it was a timing one. Injecting HEAD of renderArmWithItem instead - fired once per hand, by
    // vanilla itself, immediately before it renders that hand's own item - means our transform starts from
    // the EXACT poseStack state vanilla's own subsequent card render will also use, not a snapshot from
    // later in the frame.
    //
    // Cancellable, and cancels whenever THIS hand isn't actually holding the phone: the class's own original
    // design ("draws BOTH hands gripping the presented card ... regardless of what's actually held in each
    // hand") means the off hand should always show the shared grip pose too, never whatever it's actually
    // holding - live-reported as visibly broken (a leftover sword/tool/empty-hand render fighting our own
    // grip arm for the same space) when that hand wasn't cancelled. Only the hand ACTUALLY holding the phone
    // skips the cancel, so vanilla's own subsequent body still renders the card there, completely normally.
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$gripPresentedCard(net.minecraft.client.player.AbstractClientPlayer player, float frameInterp, float xRot,
            net.minecraft.world.InteractionHand hand, float attack, net.minecraft.world.item.ItemStack itemStack, float inverseArmHeight,
            PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        if (!CrazyPhonePresentPose.isPresenting(player))
            return;
        boolean isMainHand = hand == net.minecraft.world.InteractionHand.MAIN_HAND;
        HumanoidArm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        poseStack.pushPose();
        crazyphone$applyGripTransform(poseStack, arm);
        net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> avatarRenderer =
                this.entityRenderDispatcher.getPlayerRenderer(player);
        net.minecraft.resources.Identifier skinTexture = player.getSkin().body().texturePath();
        if (arm != HumanoidArm.LEFT) {
            avatarRenderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skinTexture,
                    player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE), player);
        } else {
            avatarRenderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skinTexture,
                    player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE), player);
        }
        poseStack.popPose();
        if (!(itemStack.getItem() instanceof fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem)) {
            ci.cancel();
        }
    }

    private static void crazyphone$applyGripTransform(PoseStack poseStack, HumanoidArm arm) {
        // Still no cancel here, and now for a cleaner reason than "it happened to look right": this method
        // is reached from crazyphone$gripPresentedCard, injected at renderArmWithItem's own HEAD - the exact
        // same poseStack state vanilla's own subsequent per-hand item render (the card) will use a few lines
        // later in the SAME call, including whatever natural bob is already baked in. Building on top of it
        // as-is, rather than cancelling and reconstructing an approximation of it, is what guarantees the arm
        // and the card can never drift apart - there's nothing left to keep in sync since they now share one
        // frame-consistent starting point instead of two independently-computed ones.
        // Live-confirmed: position (this method's translate below) was already right before today's
        // rotation experiments; only the camera-reapply rotation was wrong, in a way no sign/order fix
        // could patch (a mathematically-verified reapply - matching Camera.java's own rotationYXZ(180-yRot,
        // -xRot, 0) formula almost exactly - still reproduced the same "arms up/mirrored/too fast" result).
        // That means this space, like CrazyPhonePhotoItemRenderer's own first-person presenting branch
        // (whose winning candidate was also "no rotation at all"), is already camera-facing by construction
        // once cancelled to identity - vanilla's own renderPlayerArm confirms this too, it needs zero camera
        // math of its own for its default hold to track the view. Dropping the reapply entirely.
        // handX/handY/handZ used to be a delta added on top of the card's own y/z fields (plus a hardcoded
        // 0.4/-0.75/-0.9 anchor) - live-reported as "not very useful" for tuning, since nudging y/z to fix
        // the card also dragged the arms along with it, and vice versa. Now fully independent: these three
        // fields are the arm's own absolute position, entirely unrelated to the card's y/z. Defaults folded
        // in below so the visual result is unchanged from just before this split (0.4, -1.25, -1.2 - the
        // exact sum of the old anchor + y/z + the old delta values, so nothing jumps on this recompile).
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        poseStack.translate(side * CrazyPhonePresentDebug.handX, CrazyPhonePresentDebug.handY, CrazyPhonePresentDebug.handZ);
        // Live screenshot showed the fists pointing straight up ("hands up" pose) instead of forward - the
        // raw hand mesh isn't pre-oriented for holding anything, it needs real orientation work the same way
        // vanilla's own renderPlayerArm applies to it. Appending vanilla's ROTATION-only steps here (skipping
        // its own interleaved translates, which would fight the position we just set above) is safe: in a
        // matrix chain T(a)*R1*T(b)*R2, the combined rotation component is always just R1*R2 regardless of
        // any translate sandwiched between them - only the translation component picks up a contribution
        // from the sandwiched translate, which we're deliberately not replicating. So this reproduces
        // vanilla's exact net orientation while leaving the origin (our tuned position above) untouched.
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 45.0F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * -135.0F));
    }
}
*///?}
