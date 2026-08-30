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
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.CrazyPhonePresentDebug;
import fr.lordfinn.crazyphone.client.CrazyPhonePresentPose;
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;

// Same architecture as the >=26 branch below (see its own doc comment for the full reasoning) - confirmed
// via decompiled source that renderArmWithItem/renderPlayerArm keep the exact same shape here, just with
// MultiBufferSource where >=26 has SubmitNodeCollector, and the old PlayerRenderer (not yet renamed
// AvatarRenderer) exposing the same renderRightHand/renderLeftHand split, minus the sleeve-visibility/skin-
// texture params >=26 added. The old "cancel then reapply the real camera angle" technique this branch used
// before is what the user's own live testing on 1.21.1-fabric just confirmed broken here too (no bob, arms
// not drawn, up/down inverted) - exactly the >=26 symptom before that same fix, root-caused the same way:
// injecting at renderHandsWithItems' own endBatch fires long after the card's own per-hand render already
// ran, so the two were never guaranteed to share a starting poseStack no matter how the rotation math was
// tuned. Injecting renderArmWithItem's own HEAD instead gives the arm the exact same frame-consistent
// poseStack the card render a few lines later will also use - no camera math needed at all.
@Mixin(ItemInHandRenderer.class)
public abstract class CrazyPhonePresentHandGripMixin {
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void crazyphone$gripPresentedCard(AbstractClientPlayer player, float frameInterp, float xRot,
            InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
            PoseStack poseStack, MultiBufferSource bufferSource, int lightCoords, CallbackInfo ci) {
        if (!CrazyPhonePresentPose.isPresenting(player))
            return;
        boolean isMainHand = hand == InteractionHand.MAIN_HAND;
        HumanoidArm arm = isMainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean isLeftHand = arm == HumanoidArm.LEFT;
        poseStack.pushPose();
        crazyphone$applyGripTransform(poseStack, arm);
        PlayerRenderer playerRenderer = (PlayerRenderer) this.entityRenderDispatcher.<AbstractClientPlayer>getRenderer(player);
        if (!isLeftHand) {
            playerRenderer.renderRightHand(poseStack, bufferSource, lightCoords, player);
        } else {
            playerRenderer.renderLeftHand(poseStack, bufferSource, lightCoords, player);
        }
        poseStack.popPose();

        // Live-reported: the arm (drawn above, from this exact same raw poseStack) bobbed naturally, but the
        // card - drawn separately by render()'s own presenting branch, reached later through vanilla's own
        // per-item dispatch on a DIFFERENT, further-transformed poseStack - didn't, and the two visibly
        // weren't moving together. Rather than keep chasing the item-dispatch poseStack's own bob/camera
        // behavior (already tried and reverted twice above), draw the card here too, directly, on this same
        // raw base the arm just used - guarantees the two can never drift apart again since there's only one
        // transform now, not two independently-computed ones. This also means the card needs no camera math
        // of its own either, for the exact same reason the arm doesn't.
        if (itemStack.getItem() instanceof CrazyPhonePhotoItem) {
            poseStack.pushPose();
            crazyphone$applyCardGripTransform(poseStack, player, isLeftHand);
            fr.lordfinn.crazyphone.utils.PhotoItemData data = fr.lordfinn.crazyphone.utils.PhotoItemData.fromStack(itemStack);
            fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.renderHandFramedCard(data, poseStack,
                    bufferSource, lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        // Always cancel now, not just for non-phone hands: the phone-holding hand's own card is drawn above
        // instead of through vanilla's normal per-item dispatch, so letting that dispatch still run would
        // draw a second, independently-computed (and now provably drift-prone) copy on top of it.
        ci.cancel();
    }

    // Position/rotation values ported verbatim from the >=26 branch's own already-live-tuned constants
    // (same CrazyPhonePresentDebug fields) - the underlying mechanism is now identical on both branches, so
    // there's no reason to expect these to need separate tuning.
    private static void crazyphone$applyGripTransform(PoseStack poseStack, HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        poseStack.translate(side * CrazyPhonePresentDebug.handX, CrazyPhonePresentDebug.handY, CrazyPhonePresentDebug.handZ);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 45.0F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * -135.0F));
    }

    // Shares handX/handY/handZ's own anchor with the arm above (same raw base, same per-hand mirror sign) so
    // the card starts from the exact spot the hand already sits at, then nudges/scales from there via the
    // same CrazyPhonePresentDebug x/y/z/scale fields render()'s own (now-bypassed) presenting branch used -
    // these were tuned against a different, cancelled-then-reapplied frame, so live-retuning via
    // /presentdebug is expected here, just like every other 3D offset in this project.
    private static void crazyphone$applyCardGripTransform(PoseStack poseStack, AbstractClientPlayer player, boolean isLeftHand) {
        float side = isLeftHand ? -1f : 1f;
        poseStack.translate(side * CrazyPhonePresentDebug.handX, CrazyPhonePresentDebug.handY, CrazyPhonePresentDebug.handZ);
        if (CrazyPhonePresentPose.isDualPresenting(player)) {
            // Live-reported (with both hands actually holding a photo at once, not tested one at a time):
            // this frame's own left/right magnitude genuinely isn't symmetric (2.1 left, 1.1 right) - not
            // just a sign-mirror issue, so a single shared dualX mirrored by sign (>=26's own approach,
            // CrazyPhonePresentDebug#dualX) can't represent it. Each hand gets its own independent magnitude
            // instead (dualXLeft/dualXRight), sign still applied by hand so the live-tunable values
            // themselves stay positive/intuitive.
            float dualXMagnitude = isLeftHand ? CrazyPhonePresentDebug.dualXLeft : CrazyPhonePresentDebug.dualXRight;
            poseStack.translate(side * dualXMagnitude, CrazyPhonePresentDebug.dualY, CrazyPhonePresentDebug.z);
            poseStack.scale(CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale);
        } else {
            // Live-reported: x was only correct for the left hand, and mirroring it with the same sign as
            // handX (tried first) came out backwards - opposite hands need opposite-signed x here from what
            // handX itself uses, not the same sign.
            poseStack.translate(-side * CrazyPhonePresentDebug.x, CrazyPhonePresentDebug.y, CrazyPhonePresentDebug.z);
            poseStack.scale(CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale);
        }
        if (CrazyPhonePresentDebug.flipFrontBack)
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
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
