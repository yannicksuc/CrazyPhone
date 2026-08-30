package fr.lordfinn.crazyphone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.MultiBufferSource;
import /*$ render_type_import {*/net.minecraft.client.renderer.RenderType/*$}*/;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.utils.PhotoItemData;
import fr.lordfinn.crazyphone.utils.PhotoResolution;
import fr.lordfinn.crazyphone.client.CrazyPhonePresentPose;
import fr.lordfinn.crazyphone.client.CrazyPhonePresentDebug;

/**
 * Draws a flat "photo" facing the viewer, in the same local unit-cube space vanilla's own generated 2D item
 * models occupy. Loader-neutral: NeoForge's {@code IClientItemExtensions.getCustomRenderer()} and Fabric's
 * {@code BuiltinItemRendererRegistry} both call straight into this one method, so there is exactly one place
 * that ever emits these vertices. The item's own model ({@code builtin/entity}, no {@code display} block)
 * gets none of vanilla's usual per-context transforms applied for it automatically (that's what a real
 * {@code minecraft:item/generated} parent's own display block would normally provide) - every
 * context-specific size/position/crop/tilt decision below is this class's own responsibility.
 *
 * Draws the real per-instance captured photo (via FabricPictureCache, keyed by this stack's own photoId) once
 * it has loaded, falling back to a fixed placeholder texture until then (or if the stack somehow carries no
 * photo pointer at all). Not-in-hand contexts (GUI/inventory, ground, item frames) render as a small
 * Polaroid-style card: a 16x16 off-white frame with the (cover-cropped, THUMBNAIL-resolution) photo inset
 * 1px in on the front only, a plain frame back (never a mirrored photo), and a 1px physical edge thickness
 * around the card, all in the same frame texture. Held-in-hand contexts (first or third person) skip the
 * frame entirely and show the FULL-resolution image at its real, uncropped aspect ratio instead - a photo
 * being looked at should look like an actual photo, not a bordered thumbnail.
 */
public final class CrazyPhonePhotoItemRenderer {
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ PLACEHOLDER_TEXTURE = Crazyphone.parseId("crazyphone:textures/item/crazy_phone_photo_placeholder.png");
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ FRAME_TEXTURE = Crazyphone.parseId("crazyphone:textures/item/crazy_phone_photo_frame.png");

    // Not-in-hand ("framed card") layout - a 16x16 frame with the photo inset 1px on every side (14x14).
    private static final float FRAME_HALF = 0.5f;
    private static final float PHOTO_INSET_HALF = FRAME_HALF * 14f / 16f;
    // Half the card's 1-pixel physical thickness - front face at +CARD_THICKNESS_HALF, back at -.
    private static final float CARD_THICKNESS_HALF = (1f / 16f) / 2f;
    // Keeps the inset photo layer from z-fighting against the frame's own front face, both of which would
    // otherwise sit at the exact same CARD_THICKNESS_HALF depth. Kept tiny on purpose: any bigger and the
    // photo's edge visibly parallax-shifts away from the frame's inner border at a raking view angle,
    // opening a gap that shows whatever is behind the card (sky, etc.) through it.
    private static final float PHOTO_Z_EPSILON = 0.0004f;
    // Extends the photo quad slightly past the frame's own inner border on every side, so its edge tucks
    // under the frame's inner lip instead of butting exactly against it - closes the same raking-angle gap
    // PHOTO_Z_EPSILON alone can't fully eliminate, since the photo is the closer (frontmost) layer here and
    // safely covers the sliver of border it overlaps into.
    private static final float PHOTO_EDGE_OVERLAP = 0.004f;
    // Ground: matches vanilla's own minecraft:item/generated "ground" display scale (0.5) - without it the
    // card renders at its full GUI-card size sitting on the ground, twice as big as every other item.
    private static final float GROUND_SCALE = 0.5f;
    // Item frame: exact inverse of ItemFrameRenderer's own poseStack.scale(0.5, 0.5, 0.5) (confirmed via
    // decompiled source) applied right before it calls into FIXED rendering - cancels that shrink so the
    // card fills the frame's full block face.
    private static final float FIXED_SCALE = 2f;
    // Squashes the card's own physical depth (frame edge + photo/back layering) much thinner than its
    // face - a uniform 2x scale doubled that depth right along with the face, which read as the card
    // standing noticeably off the wall from a raking side angle instead of sitting close and flat the way a
    // vanilla map does in a frame.
    private static final float FIXED_DEPTH_SCALE = 1.1f;
    // Pulls the card back toward the frame's own backing block, closer to how flush a vanilla map sits -
    // scaling the card's own depth thinner (above) didn't touch its actual distance from the wall, only how
    // thick it reads once there. Needs a live-tested value like every other 3D offset in this file.
    private static final float FIXED_Z_PULL = 0.056f;

    // Held-in-hand layout.
    // Half-extent (in item-space units, same unit cube a generated 2D item occupies) of the LONGER side when
    // held in hand - under half of the framed card's own 0.5 half-extent (so under half its 1x1 full size).
    private static final float HAND_HALF_SIZE = 0.28f;
    // Raises the card's center above the unit cube's own vertical middle so it doesn't read as "planted in
    // the middle of the hand" the way a dead-center card does - first and third person need different
    // amounts since the hand/arm bone each is anchored to sits at a different height in each view.
    private static final float FIRST_PERSON_Y_LIFT = 0.20f;
    private static final float THIRD_PERSON_Y_LIFT = 0.16f;
    // First-person only: nudges the card toward the camera and slightly right of dead-center, on top of the
    // z-fight NORMAL_OFFSET below - mirrored by hand side (a left-handed hold shifts left instead).
    private static final float FIRST_PERSON_Z_FORWARD = 0.06f;
    private static final float FIRST_PERSON_X_OFFSET = 0.08f;
    // First-person presenting's own position/size/rotation constants now live in CrazyPhonePresentDebug
    // instead of here - see that class's own doc comment.
    // Nudges the card away from the arm along its own normal (the axis perpendicular to its flat face) by
    // one texture pixel (Minecraft's usual 16-units-per-block convention) - without this, the card sits
    // exactly coplanar with the arm/hand mesh underneath it, which z-fights and flickers against the bottom
    // of the hand.
    private static final float NORMAL_OFFSET = 1f / 16f;
    // Small supplementary tilt for first-person hands only, mirrored by hand side, so the card isn't
    // perfectly flat/frontal the way every other first-person-held 2D item isn't either (their own
    // minecraft:item/generated parent model bakes in a similar [0,-90,25]-style tilt that our custom
    // builtin/entity model has no equivalent of).
    private static final float FIRST_PERSON_YAW = 17f;
    private static final float FIRST_PERSON_ROLL = 8f;
    // Third-person "presenting" (see CrazyPhonePresentPose): the arm pose there is a fixed rotation (xRot
    // -90 around the shoulder, no yaw), swinging the whole forearm from hanging down to pointing straight
    // forward - and since the card is a rigid child of the hand bone, that swing carries its own local frame
    // with it too. Two guesses at a fixed compensating angle (+90, then -90 around the same axis) both still
    // came out lying flat, meaning whatever the actual composed rotation reaching this point is, it isn't
    // the simple single-axis swing it looked like on paper. Rather than guess a third angle, render() below
    // extracts the ACTUAL accumulated rotation from the poseStack and cancels it exactly (quaternion
    // conjugate), then re-applies only the body's own yaw (the same formula LivingEntityRenderer itself uses
    // for it) - landing back on "faces the same way the body currently does" by construction, which is also
    // "parallel to the body" the way the user asked for.
    private static final float PRESENT_SCALE = 2.4f;
    // Negative on purpose: the hand attachment point itself (before this offset) sits near shoulder/head
    // height even with the arm pointing straight forward, and the 2.4x scale then expands the card outward
    // from that same anchor - pushing the top of the card well above the head unless pulled back down
    // toward where the hands actually are. Needs a live-tested value (this project's own established
    // pattern for every 3D hand-offset constant here).
    private static final float PRESENT_Y_LIFT = -0.4f;
    private static final float PRESENT_Z_FORWARD = -0.1f;
    // Pulls the card from its own hand's natural (off-center) position toward the body's own midline, so it
    // reads as one card held between both hands rather than sitting anchored on whichever arm actually holds
    // it - confirmed live at PRESENT_CENTER_X=0 that with no correction the card sits centered on the
    // holding hand itself (visibly off-center toward whichever side that hand is on), not the body. 0.3
    // roughly matches vanilla's own arm shoulder-pivot X offset (5 of 16 units, mirrored per arm), which is
    // exactly the gap this needs to close.
    private static final float PRESENT_CENTER_X = 0.4f;
    // Same physical border width as the framed card's own 1-pixel-of-16 border, applied here as a nine-slice
    // around the (variable, uncropped-aspect) hand photo instead of a fixed 14x14 square - the border stays
    // a constant physical width regardless of the photo's own size/aspect, exactly like a real Polaroid's
    // margin never stretches just because the photo inside it is a different shape.
    private static final float HAND_FRAME_BORDER = 1f / 32f;
    private static final float FRAME_UV_BORDER = 1f / 16f;
    // Own thickness for the hand card specifically - matches HAND_FRAME_BORDER instead of reusing the
    // not-in-hand card's own CARD_THICKNESS_HALF, which read as too thick once the border itself was halved.
    private static final float HAND_CARD_THICKNESS_HALF = HAND_FRAME_BORDER / 2f;

    private CrazyPhonePhotoItemRenderer() {
    }

    // Temporary diagnostic - confirms whether render() is even reached at all on 1.20.4 (vs. never being
    // called in the first place, which would point at custom-renderer registration instead of anything
    // inside this method) and, if reached, whether PhotoItemData actually resolves. Throttled per
    // displayContext so every hand/gui/ground/frame case gets its own log line without spamming.
    private static final java.util.Set<ItemDisplayContext> crazyphone$loggedContexts = new java.util.HashSet<>();

    public static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (crazyphone$loggedContexts.add(displayContext)) {
            fr.lordfinn.crazyphone.utils.PhotoItemData loggedData = fr.lordfinn.crazyphone.utils.PhotoItemData.fromStack(stack);
            org.slf4j.LoggerFactory.getLogger("crazyphone-capture-debug").info(
                    "CrazyPhonePhotoItemRenderer.render() reached: displayContext={} photoData={}",
                    displayContext, loggedData == null ? "null" : loggedData.photoId());
        }
        boolean isLeftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean isHand = isLeftHand || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        boolean isFirstPersonHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        if (isHand) {
            if (isFirstPersonHand) {
                boolean presentingFirstPerson = CrazyPhonePresentPose.isPresenting(net.minecraft.client.Minecraft.getInstance().player);
                if (presentingFirstPerson) {
                    // Replaces the old flat GUI overlay (a standalone 2D draw with no world lighting at all,
                    // which read as visibly wrong next to the properly-lit 3D card everywhere else) - reuses
                    // this exact same 3D geometry/lighting path instead, just repositioned/enlarged.
                    //
                    // A full rotation+position reset (tried first) was meant to only cancel vanilla's own
                    // per-frame "hand sway" (ItemInHandRenderer#renderHandsWithItems's own
                    // Axis.XP/YP.rotationDegrees((viewRot - bobRot) * 0.1F), applied before this method is
                    // ever reached) and the per-hand mirror offset underneath it - but the resulting card
                    // stayed fixed facing one world direction while only its position tracked the player,
                    // and was enormous. Both point to the same conclusion: the camera's own view rotation is
                    // ALSO baked into this poseStack chain (not applied separately, as vanilla's decompiled
                    // source alone suggested), so cancelling all inherited rotation removed that too,
                    // landing at world-space identity instead of camera-relative identity - explains the
                    // world-locked facing directly, and very likely the scale too (a fixed local offset from
                    // a "reset origin" that isn't actually near the camera can put the card at a wildly
                    // different apparent distance than intended).
                    //
                    // Rather than keep guessing what's left in that chain, this reapplies the game's own
                    // ACTUAL current camera yaw/pitch directly after the reset - authoritative data instead
                    // of an inferred cancellation - the same "cancel then reapply a known-good real angle"
                    // pattern already proven for the third-person presenting branch below (reapplying
                    // presentEntityYaw there for the same reason).
                    //
                    // Every number from here down reads from CrazyPhonePresentDebug instead of a compile-time
                    // constant - live-tunable via the /presentdebug client command (Fabric-only for now) so
                    // this can be dialed in directly from chat instead of another guess-compile-relaunch
                    // round trip. See that class's own doc comment for the full field list.
                    org.joml.Vector3f handPos = poseStack.last().pose().getTranslation(new org.joml.Vector3f());
                    poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
                    poseStack.translate(-handPos.x, -handPos.y, -handPos.z);
                    // Live-confirmed (via CrazyPhonePresentHandGripMixin's own identical grip transform):
                    // dropping this cancel to keep renderHandsWithItems' own small natural view/walk bob
                    // (tried first) broke camera-tracking outright - more than just that bob was baked into
                    // the incoming rotation, so "build on top of whatever's there" isn't a safe way to get
                    // it back. Keeping the reliable cancel-to-identity baseline and instead recomputing that
                    // one specific small "hand sway" formula ourselves (Axis.XP/YP.rotationDegrees((viewRot -
                    // bob) * 0.1F)) gets the same subtle motion without that risk. Gated >=1.20.5 (same as
                    // the reapply below) because a fixed 1.0F partial tick here (tried first, on the arm grip
                    // mixin) reproduced a visible stutter instead of interpolating smoothly like the rest of
                    // the frame, and the fix (the game's own actual interpolated partial tick,
                    // DeltaTracker#getGameTimeDeltaPartialTick(false)) doesn't exist before 1.20.5.
                    // 1.20.4-only: live testing showed this card (and CrazyPhonePresentHandGripMixin's own
                    // grip, same root cause) was only correctly oriented while facing due north - exactly
                    // the case where the reapply below computes to zero extra rotation. The cancel step
                    // above already lands camera-relative on this version, unlike >=1.20.5 where cancel
                    // alone was proven (through this same kind of live testing) to land at world-space
                    // identity, which is what made reapplying the real camera angle necessary there in the
                    // first place - reapplying it again here on a version where cancel is already
                    // camera-relative double-counts the rotation, so it's skipped below <1.20.5.
                    //? if >=1.20.5 {
                    /*net.minecraft.client.player.LocalPlayer bobPlayer = net.minecraft.client.Minecraft.getInstance().player;
                    if (bobPlayer != null) {
                        float bobPartialTick = net.minecraft.client.Minecraft.getInstance()./^$ mc_delta_tracker {^/getDeltaTracker/^$}^/().getGameTimeDeltaPartialTick(false);
                        poseStack.mulPose(Axis.XP.rotationDegrees((bobPlayer.getViewXRot(bobPartialTick) - bobPlayer.xBob) * 0.1F));
                        poseStack.mulPose(Axis.YP.rotationDegrees((bobPlayer.getViewYRot(bobPartialTick) - bobPlayer.yBob) * 0.1F));
                    }
                    net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer./^$ gr_main_camera {^/getMainCamera/^$}^/();
                    float debugYaw = camera./^$ cam_yaw {^/getYRot/^$}^/() * CrazyPhonePresentDebug.yawSign + CrazyPhonePresentDebug.yawOffset;
                    float debugPitch = camera./^$ cam_pitch {^/getXRot/^$}^/() * CrazyPhonePresentDebug.pitchSign + CrazyPhonePresentDebug.pitchOffset;
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - debugYaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(debugPitch));
                    *///?}
                    // Live-reported (>=26 branch first, same root cause here): the card sat visibly further
                    // left when the phone was in the LEFT hand than the right - vanilla's own
                    // ItemInHandRenderer#applyItemArmTransform applies translate(invert*0.56, ...) on the
                    // real poseStack for any non-special held item, before this method is ever reached -
                    // invert is +1 right hand / -1 left hand, a genuine hand-dependent constant. Cancelling
                    // it here (opposite sign) keeps the card centered regardless of which hand holds it.
                    // Two photos at once (one per hand, see CrazyPhonePresentPose#isDualPresenting) need to
                    // split apart under each hand instead of both converging here - dualX/dualY/dualScale are
                    // a separate, live-tuned set just for that case (dualX mirrored outward per hand, not
                    // just the single-photo case's small hand-centering compensation).
                    if (CrazyPhonePresentPose.isDualPresenting(net.minecraft.client.Minecraft.getInstance().player)) {
                        poseStack.translate((isLeftHand ? -CrazyPhonePresentDebug.dualX : CrazyPhonePresentDebug.dualX) + (isLeftHand ? 0.56f : -0.56f)
                                        + (isLeftHand ? CrazyPhonePresentDebug.dualLeftExtra : 0f),
                                CrazyPhonePresentDebug.dualY, NORMAL_OFFSET + CrazyPhonePresentDebug.z);
                        poseStack.scale(CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale);
                    } else {
                        poseStack.translate(CrazyPhonePresentDebug.x + (isLeftHand ? 0.56f : -0.56f), CrazyPhonePresentDebug.y, NORMAL_OFFSET + CrazyPhonePresentDebug.z);
                        poseStack.scale(CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale);
                    }
                    // Presenting means showing the photo's front to whoever's in front of you, so from your
                    // own first-person view you'd only ever see its blank back (same reasoning as the old GUI
                    // overlay's own back-face choice) - one flip swaps which face is outward.
                    if (CrazyPhonePresentDebug.flipFrontBack)
                        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                } else {
                    poseStack.translate(isLeftHand ? -FIRST_PERSON_X_OFFSET : FIRST_PERSON_X_OFFSET, FIRST_PERSON_Y_LIFT, NORMAL_OFFSET + FIRST_PERSON_Z_FORWARD);
                    poseStack.mulPose(Axis.YP.rotationDegrees(isLeftHand ? FIRST_PERSON_YAW : -FIRST_PERSON_YAW));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(isLeftHand ? FIRST_PERSON_ROLL : -FIRST_PERSON_ROLL));
                }
            } else {
                boolean presenting = CrazyPhonePresentPose.presentingThisRender;
                float presentEntityYaw = CrazyPhonePresentPose.presentingEntityYaw;
                float presentHeadPitch = CrazyPhonePresentPose.presentingHeadPitch;
                if (presenting && CrazyPhonePresentDebug.presentCandidateFan) {
                    renderPresentingCandidates(poseStack, bufferSource, packedLight, packedOverlay, isLeftHand, presentEntityYaw, presentHeadPitch);
                    poseStack.popPose();
                    return;
                } else if (presenting) {
                    // Winning formula, confirmed live via a 10-color candidate fan test (see
                    // renderPresentingCandidates, kept as dead code behind
                    // CrazyPhonePresentDebug#presentCandidateFan for any future round of this): on 1.20.4 the
                    // arm bone's own baked-in rotation is ALREADY correct at this point in the poseStack
                    // chain - no cancellation needed at all. Every "cancel it, then reapply something"
                    // attempt tried here (full reset to camera-relative identity, then reapplying the
                    // entity's own body yaw/head pitch) instead left the card tracking the live camera while
                    // turning the mouse alone with the body still, confirmed live - meaning the cancel step
                    // itself was introducing the camera dependency, not removing it.
                    // >=1.20.5 still needs the old cancel+reapply approach (untouched here, not re-tested
                    // this round - that version's own poseStack chain lands somewhere different by this
                    // point, per the first-person presenting branch's own doc comment on the same split).
                    //? if >=1.20.5 {
                    /*poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(presentHeadPitch)));
                    *///?}
                    // Two photos at once need one under each arm instead of both converging toward the body's
                    // own center - see CrazyPhonePresentDebug#dualThirdX's own doc comment. Reads the bridge
                    // flag (this renderer has no entity reference of its own), not a direct player check.
                    if (CrazyPhonePresentPose.isDualPresentingThisRender) {
                        float dualCenterX = isLeftHand ? -CrazyPhonePresentDebug.dualThirdX : CrazyPhonePresentDebug.dualThirdX;
                        poseStack.translate(dualCenterX, CrazyPhonePresentDebug.dualThirdY, NORMAL_OFFSET + PRESENT_Z_FORWARD);
                        poseStack.scale(CrazyPhonePresentDebug.dualThirdScale, CrazyPhonePresentDebug.dualThirdScale, CrazyPhonePresentDebug.dualThirdScale);
                    } else {
                        float centerX = isLeftHand ? PRESENT_CENTER_X : -PRESENT_CENTER_X;
                        poseStack.translate(centerX, PRESENT_Y_LIFT, NORMAL_OFFSET + PRESENT_Z_FORWARD);
                        poseStack.scale(PRESENT_SCALE, PRESENT_SCALE, PRESENT_SCALE);
                    }
                    // The card's own front (+Z) ended up facing the presenter instead of whoever's in front
                    // of them - vanilla's own body-facing formula above doesn't guarantee alignment with
                    // this card's own authored front-facing convention, only with vanilla's own body mesh.
                    // One more half turn swaps which face ends up outward. Applied last (innermost, closest
                    // to the drawn geometry) specifically so it doesn't change what the translate/scale
                    // above are themselves expressed in - those were tuned in the frame from before this
                    // flip, and inserting it earlier would silently invert their meaning.
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                } else {
                    poseStack.translate(0, THIRD_PERSON_Y_LIFT, NORMAL_OFFSET);
                }
            }
        } else if (displayContext == ItemDisplayContext.GROUND) {
            poseStack.scale(GROUND_SCALE, GROUND_SCALE, GROUND_SCALE);
        } else if (displayContext == ItemDisplayContext.FIXED) {
            // Item frames: confirmed via ItemFrameRenderer's own decompiled source that it applies its own
            // poseStack.scale(0.5, 0.5, 0.5) right before calling into FIXED rendering - undoing that with a
            // 2x scale here makes the already block-sized (FRAME_HALF=0.5 spans a full 1x1) card actually
            // fill the frame's block face edge-to-edge, like a painting, instead of sitting at half that
            // size. The extra 180 flips which face ends up outward, matching the presenting card's own
            // front/back fix - FIXED doesn't align with this card's own authored front-facing convention any
            // more reliably than the arm bone chain did.
            // Pulls the card back toward the block, in plain (pre-scale) units so the constant's own value
            // stays easy to reason about instead of being multiplied by FIXED_SCALE.
            poseStack.translate(0, 0, FIXED_Z_PULL);
            poseStack.scale(FIXED_SCALE, FIXED_SCALE, FIXED_SCALE * FIXED_DEPTH_SCALE);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        }

        PhotoItemData data = PhotoItemData.fromStack(stack);
        if (isHand) {
            renderHandFramedCard(data, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            renderFramedCard(data, poseStack, bufferSource, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    // Debug only (see CrazyPhonePresentDebug#presentCandidateFan): draws 10 small flat colored rectangles
    // side by side, each testing a different rotation formula for the third-person presenting card, so a
    // live tester can report back which COLOR stays locked to the arms while turning the camera instead of
    // one guess-compile-relaunch cycle per formula. Legend (color -> formula), all starting from the same
    // "cancel the arm bone's baked-in rotation" step unless noted:
    //   0 RED     - no reapply at all (cancel-only baseline - expected to face the camera/billboard)
    //   1 ORANGE  - reapply yaw only: YP(180 - entityYaw)
    //   2 YELLOW  - reapply yaw + pitch (the combo just tried live): YP(180-entityYaw), XP(-pitch)
    //   3 GREEN   - reapply yaw only, opposite convention: YP(entityYaw)
    //   4 CYAN    - reapply yaw only, negated: YP(-entityYaw)
    //   5 BLUE    - yaw + pitch with pitch sign flipped: YP(180-entityYaw), XP(+pitch)
    //   6 PURPLE  - pitch only, no yaw: XP(-pitch)
    //   7 MAGENTA - no cancel at all (raw arm-bone transform, untouched)
    //   8 WHITE   - cancel + reapply the LIVE CAMERA's own yaw/pitch (same technique the first-person branch
    //               above uses), instead of the entity's own body/head model values
    //   9 GRAY    - yaw + pitch, order swapped: XP(-pitch) applied BEFORE YP(180-entityYaw)
    private static void renderPresentingCandidates(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                                     boolean isLeftHand, float presentEntityYaw, float presentHeadPitch) {
        int[] colors = {
                0xDC3232, 0xE68C28, 0xE6DC28, 0x3CC83C, 0x32C8C8,
                0x3C5AE6, 0x963CDC, 0xE63CB4, 0xF0F0F0, 0x5A5A5A,
        };
        float spacing = 0.55f;
        float fanScale = PRESENT_SCALE / 3.2f;
        for (int i = 0; i < 10; i++) {
            poseStack.pushPose();
            // Spreads the 10 candidates apart BEFORE any candidate-specific rotation, in the arm bone's own
            // (shared, un-rotated) local frame, so the separation is consistent across every candidate
            // regardless of what that candidate's own formula does afterward - each candidate's rotation is
            // then applied around its own already-offset position instead of around a shared center.
            poseStack.translate((i - 4.5f) * spacing, 0, 0);
            boolean cancel = i != 7;
            if (cancel)
                poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
            switch (i) {
                case 1 -> poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                case 2 -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(presentHeadPitch)));
                }
                case 3 -> poseStack.mulPose(Axis.YP.rotationDegrees(presentEntityYaw));
                case 4 -> poseStack.mulPose(Axis.YP.rotationDegrees(-presentEntityYaw));
                case 5 -> {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(presentHeadPitch)));
                }
                case 6 -> poseStack.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(presentHeadPitch)));
                case 8 -> {
                    net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer./*$ gr_main_camera {*/getMainCamera/*$}*/();
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - camera./*$ cam_yaw {*/getYRot/*$}*/()));
                    poseStack.mulPose(Axis.XP.rotationDegrees(camera./*$ cam_pitch {*/getXRot/*$}*/()));
                }
                case 9 -> {
                    poseStack.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(presentHeadPitch)));
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                }
                default -> {
                }
            }
            poseStack.translate(0, PRESENT_Y_LIFT, NORMAL_OFFSET + PRESENT_Z_FORWARD);
            poseStack.scale(fanScale, fanScale, fanScale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            drawColorTintedCard(poseStack, bufferSource, packedLight, packedOverlay, colors[i]);
            poseStack.popPose();
        }
    }

    // Plain colored rectangle (both faces, tinted, no photo) - reuses FRAME_TEXTURE (near-white) as the
    // sampled texture purely so the existing vertex-color-tinting path works, not for its actual pixels.
    private static void drawColorTintedCard(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, int rgb) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = bufferSource.getBuffer(/*$ render_types {*/RenderType/*$}*/.entityCutout(FRAME_TEXTURE));
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float h = FRAME_HALF;
        tintedQuad(buffer, pose, light, overlay, r, g, b,
                -h, h, 0, 0, 0,
                -h, -h, 0, 0, 1,
                h, -h, 0, 1, 1,
                h, h, 0, 1, 0,
                0, 0, 1);
        tintedQuad(buffer, pose, light, overlay, r, g, b,
                h, h, 0, 0, 0,
                h, -h, 0, 0, 1,
                -h, -h, 0, 1, 1,
                -h, h, 0, 1, 0,
                0, 0, -1);
    }

    private static void tintedQuad(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay, int r, int g, int b,
                                    float x0, float y0, float z0, float u0, float v0,
                                    float x1, float y1, float z1, float u1, float v1,
                                    float x2, float y2, float z2, float u2, float v2,
                                    float x3, float y3, float z3, float u3, float v3,
                                    float nx, float ny, float nz) {
        tintedVertex(buffer, pose, x0, y0, z0, u0, v0, light, overlay, nx, ny, nz, r, g, b);
        tintedVertex(buffer, pose, x1, y1, z1, u1, v1, light, overlay, nx, ny, nz, r, g, b);
        tintedVertex(buffer, pose, x2, y2, z2, u2, v2, light, overlay, nx, ny, nz, r, g, b);
        tintedVertex(buffer, pose, x3, y3, z3, u3, v3, light, overlay, nx, ny, nz, r, g, b);
    }

    private static void tintedVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                                      float u, float v, int light, int overlay, float nx, float ny, float nz,
                                      int r, int g, int b) {
        //? if <1.20.5 {
        buffer.vertex(pose.pose(), x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        //? } else {
        /*buffer.addVertex(pose, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        *///?}
    }

    // Same Polaroid-style frame as renderFramedCard (front/back/1px edge, off-white frame texture), but
    // nine-sliced around the real (uncropped) photo aspect ratio instead of a fixed 14x14 square opening -
    // the four 1x1 corner tiles and four edge strips of the frame texture stay a constant physical width,
    // only the edge strips' long axis stretches to fit whatever size the photo itself came out to.
    private static void renderHandFramedCard(PhotoItemData data, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        /*$ res_loc {*/ResourceLocation/*$}*/ photoTexture = PLACEHOLDER_TEXTURE;
        float iw = 0.5f, ih = 0.5f;
        if (data != null) {
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(data.photoId(), PhotoResolution.FULL);
            if (texture != null) {
                photoTexture = texture.location();
                // Width is always the fixed dimension (matches HAND_HALF_SIZE exactly, in both hand-held
                // contexts - first and third person share this same method) and height is always the one
                // that adapts to the photo's own aspect ratio - a tall photo used to get its HEIGHT pinned
                // instead (the longer-side branch this replaced), which made two photos of the same width
                // but different aspect ratios visually inconsistent widths in hand.
                float srcWidth = texture.width(), srcHeight = texture.height();
                iw = HAND_HALF_SIZE;
                ih = HAND_HALF_SIZE * srcHeight / srcWidth;
            }
        }

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer frameBuffer = bufferSource.getBuffer(/*$ render_types {*/RenderType/*$}*/.entityCutout(FRAME_TEXTURE));
        float t = HAND_CARD_THICKNESS_HALF;
        float b = HAND_FRAME_BORDER;
        float uB = FRAME_UV_BORDER;
        float ow = iw + b, oh = ih + b;

        // Frame front, nine-sliced: 4 fixed-size corners + 4 edges stretched only along their long axis: the
        // hollow center (where the photo quad goes) is left unfilled.
        slice(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, oh, ih, t, 0, uB, 0, uB); // top-left
        slice(frameBuffer, pose, packedLight, packedOverlay, -iw, iw, oh, ih, t, uB, 1 - uB, 0, uB); // top
        slice(frameBuffer, pose, packedLight, packedOverlay, iw, ow, oh, ih, t, 1 - uB, 1, 0, uB); // top-right
        slice(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, ih, -ih, t, 0, uB, uB, 1 - uB); // left
        slice(frameBuffer, pose, packedLight, packedOverlay, iw, ow, ih, -ih, t, 1 - uB, 1, uB, 1 - uB); // right
        slice(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, -ih, -oh, t, 0, uB, 1 - uB, 1); // bottom-left
        slice(frameBuffer, pose, packedLight, packedOverlay, -iw, iw, -ih, -oh, t, uB, 1 - uB, 1 - uB, 1); // bottom
        slice(frameBuffer, pose, packedLight, packedOverlay, iw, ow, -ih, -oh, t, 1 - uB, 1, 1 - uB, 1); // bottom-right

        // Frame back, nine-sliced the same as the front - a single stretched quad here would distort the
        // border's own width unevenly on a rectangular (non-square) hand photo, exactly the artifact a
        // nine-slice exists to avoid.
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, oh, ih, -t, 0, uB, 0, uB); // top-left
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -iw, iw, oh, ih, -t, uB, 1 - uB, 0, uB); // top
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, iw, ow, oh, ih, -t, 1 - uB, 1, 0, uB); // top-right
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, ih, -ih, -t, 0, uB, uB, 1 - uB); // left
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, iw, ow, ih, -ih, -t, 1 - uB, 1, uB, 1 - uB); // right
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -ow, -iw, -ih, -oh, -t, 0, uB, 1 - uB, 1); // bottom-left
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -iw, iw, -ih, -oh, -t, uB, 1 - uB, 1 - uB, 1); // bottom
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, iw, ow, -ih, -oh, -t, 1 - uB, 1, 1 - uB, 1); // bottom-right
        // The back's own hollow center (where the front's photo shows through the opening) needs a plain
        // frame-colored fill too, unlike the front where the photo quad covers it - otherwise the inner
        // rectangle is just an open hole showing whatever is behind the whole card.
        sliceBack(frameBuffer, pose, packedLight, packedOverlay, -iw, iw, ih, -ih, -t, uB, 1 - uB, uB, 1 - uB); // center
        // Frame edge - see renderFramedCard's own doc comment on why this is double-sided.
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -ow, oh, t, ow, oh, t, ow, oh, -t, -ow, oh, -t, 0, 1, 0); // top
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -ow, -oh, -t, ow, -oh, -t, ow, -oh, t, -ow, -oh, t, 0, -1, 0); // bottom
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -ow, oh, -t, -ow, oh, t, -ow, -oh, t, -ow, -oh, -t, -1, 0, 0); // left
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, ow, oh, t, ow, oh, -t, ow, -oh, -t, ow, -oh, t, 1, 0, 0); // right

        // Photo, front face only, full (uncropped) 0..1 UV - pushed slightly ahead of the frame's own front
        // face (and its edges overlapped slightly past iw/ih) to avoid z-fighting AND a raking-angle gap
        // against the frame's inner border (see PHOTO_Z_EPSILON/PHOTO_EDGE_OVERLAP's own doc comments).
        VertexConsumer photoBuffer = bufferSource.getBuffer(/*$ render_types {*/RenderType/*$}*/.entityCutout(photoTexture));
        float pz = t + PHOTO_Z_EPSILON;
        float po = PHOTO_EDGE_OVERLAP;
        quad(photoBuffer, pose, packedLight, packedOverlay,
                -iw - po, ih + po, pz, 0, 0,
                -iw - po, -ih - po, pz, 0, 1,
                iw + po, -ih - po, pz, 1, 1,
                iw + po, ih + po, pz, 1, 0,
                0, 0, 1);
    }

    // Polaroid-style card: a 16x16 off-white frame (front, back, and a 1px physical edge) with the
    // cover-cropped thumbnail inset 1px on the front only.
    private static void renderFramedCard(PhotoItemData data, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer frameBuffer = bufferSource.getBuffer(/*$ render_types {*/RenderType/*$}*/.entityCutout(FRAME_TEXTURE));
        float t = CARD_THICKNESS_HALF;
        float h = FRAME_HALF;

        // Frame front/back.
        quad(frameBuffer, pose, packedLight, packedOverlay,
                -h, h, t, 0, 0,
                -h, -h, t, 0, 1,
                h, -h, t, 1, 1,
                h, h, t, 1, 0,
                0, 0, 1);
        quad(frameBuffer, pose, packedLight, packedOverlay,
                h, h, -t, 0, 0,
                h, -h, -t, 0, 1,
                -h, -h, -t, 1, 1,
                -h, h, -t, 1, 0,
                0, 0, -1);
        // Frame edge - a thin strip on each of the 4 sides, connecting the front face's border to the back
        // face's. Emitted with both windings (see doubleSidedQuad) since getting the "correct" single
        // winding right for 4 different face orientations by hand isn't worth the risk of an invisible edge.
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, h, t, h, h, t, h, h, -t, -h, h, -t, 0, 1, 0); // top
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, -h, -t, h, -h, -t, h, -h, t, -h, -h, t, 0, -1, 0); // bottom
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, h, -t, -h, h, t, -h, -h, t, -h, -h, -t, -1, 0, 0); // left
        doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, h, h, t, h, h, -t, h, -h, -t, h, -h, t, 1, 0, 0); // right

        // Inset photo, front face only - pushed slightly ahead of the frame's own front face to avoid
        // z-fighting with it.
        /*$ res_loc {*/ResourceLocation/*$}*/ photoTexture = PLACEHOLDER_TEXTURE;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (data != null) {
            PhotoResolution resolution = fr.lordfinn.crazyphone.ClientConfig.itemPreviewPixelated ? PhotoResolution.THUMBNAIL : PhotoResolution.FULL;
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(data.photoId(), resolution);
            if (texture != null) {
                photoTexture = texture.location();
                float srcWidth = texture.width(), srcHeight = texture.height();
                if (srcWidth > srcHeight) {
                    float uSpan = srcHeight / srcWidth;
                    u0 = (1f - uSpan) / 2f;
                    u1 = u0 + uSpan;
                } else if (srcHeight > srcWidth) {
                    float vSpan = srcWidth / srcHeight;
                    v0 = (1f - vSpan) / 2f;
                    v1 = v0 + vSpan;
                }
            }
        }
        VertexConsumer photoBuffer = bufferSource.getBuffer(/*$ render_types {*/RenderType/*$}*/.entityCutout(photoTexture));
        float p = PHOTO_INSET_HALF + PHOTO_EDGE_OVERLAP;
        float pz = t + PHOTO_Z_EPSILON;
        quad(photoBuffer, pose, packedLight, packedOverlay,
                -p, p, pz, u0, v0,
                -p, -p, pz, u0, v1,
                p, -p, pz, u1, v1,
                p, p, pz, u1, v0,
                0, 0, 1);
    }

    // One nine-slice tile: a front-facing (normal +Z) rectangle from (xLeft,yTop) to (xRight,yBottom),
    // sampling the given UV sub-rectangle of the frame texture.
    private static void slice(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay,
                               float xLeft, float xRight, float yTop, float yBottom, float z,
                               float uLeft, float uRight, float vTop, float vBottom) {
        quad(buffer, pose, light, overlay,
                xLeft, yTop, z, uLeft, vTop,
                xLeft, yBottom, z, uLeft, vBottom,
                xRight, yBottom, z, uRight, vBottom,
                xRight, yTop, z, uRight, vTop,
                0, 0, 1);
    }

    // Same tile as slice(), facing -Z instead of +Z (the card's back) - same corner rectangle and UV
    // sub-rectangle, wound in reverse (xRight/xLeft swapped) to face outward from the back instead.
    private static void sliceBack(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay,
                                   float xLeft, float xRight, float yTop, float yBottom, float z,
                                   float uLeft, float uRight, float vTop, float vBottom) {
        quad(buffer, pose, light, overlay,
                xRight, yTop, z, uRight, vTop,
                xRight, yBottom, z, uRight, vBottom,
                xLeft, yBottom, z, uLeft, vBottom,
                xLeft, yTop, z, uLeft, vTop,
                0, 0, -1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay,
                              float x0, float y0, float z0, float u0, float v0,
                              float x1, float y1, float z1, float u1, float v1,
                              float x2, float y2, float z2, float u2, float v2,
                              float x3, float y3, float z3, float u3, float v3,
                              float nx, float ny, float nz) {
        vertex(buffer, pose, x0, y0, z0, u0, v0, light, overlay, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, u1, v1, light, overlay, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, u2, v2, light, overlay, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, u3, v3, light, overlay, nx, ny, nz);
    }

    // A quad sampling a single UV point (fine for the near-solid-color frame texture) emitted in both vertex
    // orders, so it renders regardless of which winding direction the current RenderType treats as front.
    private static void doubleSidedQuad(VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float x3, float y3, float z3,
                                         float nx, float ny, float nz) {
        quad(buffer, pose, light, overlay,
                x0, y0, z0, 0.5f, 0.5f,
                x1, y1, z1, 0.5f, 0.5f,
                x2, y2, z2, 0.5f, 0.5f,
                x3, y3, z3, 0.5f, 0.5f,
                nx, ny, nz);
        quad(buffer, pose, light, overlay,
                x3, y3, z3, 0.5f, 0.5f,
                x2, y2, z2, 0.5f, 0.5f,
                x1, y1, z1, 0.5f, 0.5f,
                x0, y0, z0, 0.5f, 0.5f,
                -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                                float u, float v, int light, int overlay, float nx, float ny, float nz) {
        //? if <1.20.5 {
        buffer.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        //? } else {
        /*buffer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        *///?}
    }

    // 26.x / 1.21.10+ item-rendering model (NeoForge only for now - see PORTING-26x.md for the Fabric
    // side, which needs its own separate investigation): vanilla removed BlockEntityWithoutLevelRenderer
    // entirely, replacing custom item rendering with a data-driven ItemModel + SpecialModelRenderer pair.
    // Scoped to >=26 only (not the wider >=1.21.10 range where this problem actually starts) - 1.21.10
    // itself has a meaningfully different ItemModel.Unbaked/SpecialModelRenderer API shape (bake()'s
    // parameter list, getExtents()'s callback type - confirmed by attempting to compile this exact code
    // against it) that wasn't worth reconciling in the same pass; 1.21.10 still has no working custom
    // photo rendering at all on NeoForge, unchanged from before this work - a real follow-up, not
    // forgotten, just deliberately out of scope here.
    // Vanilla's own ready-made SpecialModelWrapper ItemModel (JSON "type": "minecraft:special") was
    // checked and ruled out - its update() only pulls a STATIC per-displayContext transform from a base
    // model's own vanilla display JSON, with no hook for the runtime logic below (live camera-relative
    // rotation cancellation while presenting, /presentdebug live-tunable values) - hence a fully custom
    // ItemModel here, registered via RegisterItemModelsEvent (Crazyphone.java), referenced from
    // crazy_phone_photo.json's "type" field.
    //
    // NOT live-tested yet - no way to launch a graphical client from this session. The transform math
    // below is a direct, unchanged transcription of render()'s own logic above (just building a fresh
    // PoseStack instead of operating on one handed in by the old BlockEntityWithoutLevelRenderer
    // callback), so the geometry itself should be correct if this is reached at all - but the
    // registration wiring (does the model actually get picked up by the item, does update() really run
    // fresh every frame for a held item the way the old render() was called) is unverified. Live-test
    // before considering this done.
    //
    // The presenting-fan debug feature (renderPresentingCandidates above) is deliberately NOT ported
    // here - dead debug-only code (CrazyPhonePresentDebug#presentCandidateFan defaults false, already
    // resolved via live testing pre-1.21.10) not worth the extra complexity of multi-layer submission
    // for a feature nobody enables; falls back to the normal single-card presenting path if somehow left
    // on.
    //? if neoforge && >=26 {
    /*private record DrawArgument(fr.lordfinn.crazyphone.utils.PhotoItemData data, boolean isHand) {
    }

    public static final class ModelImpl implements net.minecraft.client.renderer.item.ItemModel {
        static final ModelImpl INSTANCE = new ModelImpl();
        private static final SpecialRendererImpl SPECIAL_RENDERER = new SpecialRendererImpl();

        @Override
        public void update(net.minecraft.client.renderer.item.ItemStackRenderState output, ItemStack item,
                            net.minecraft.client.renderer.item.ItemModelResolver resolver, ItemDisplayContext displayContext,
                            @javax.annotation.Nullable net.minecraft.client.multiplayer.ClientLevel level,
                            @javax.annotation.Nullable net.minecraft.world.entity.ItemOwner owner, int seed) {
            output.appendModelIdentityElement(this);
            boolean isLeftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            boolean isHand = isLeftHand || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            boolean isFirstPersonHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5, 0.5, 0.5);
            if (isHand) {
                if (isFirstPersonHand) {
                    boolean presentingFirstPerson = CrazyPhonePresentPose.isPresenting(net.minecraft.client.Minecraft.getInstance().player);
                    if (presentingFirstPerson) {
                        // No camera-yaw/pitch reapplication here (unlike the <26 branch above, which needs
                        // it) - confirmed live via a 10-candidate colored fan test that >=26's FIRST_PERSON_
                        // HAND base frame is ALREADY camera-aligned on its own (candidate 0, "no extra
                        // rotation at all", is the one that stayed locked to the camera while turning the
                        // mouse - every dynamic yaw/pitch reapplication candidate instead fought against an
                        // already-correct base, same conclusion the third-person fan reached for ITS own
                        // context). Only resetting to origin remains necessary, same as the fan does.
                        org.joml.Vector3f handPos = poseStack.last().pose().getTranslation(new org.joml.Vector3f());
                        poseStack.translate(-handPos.x, -handPos.y, -handPos.z);
                        // Live-reported: the card sat visibly further left when the phone was in the LEFT
                        // hand than when it was in the right - vanilla's own ItemInHandRenderer#
                        // applyItemArmTransform (called on the REAL poseStack before this renderer ever runs,
                        // for any non-special held item) applies translate(invert*0.56, ...) where invert is
                        // +1 for the right hand / -1 for the left - a genuine, hand-dependent, camera-
                        // independent constant baked into the frame this local transform ultimately composes
                        // with. Cancelling it here (the exact opposite sign) keeps the card centered
                        // regardless of which hand is actually holding the phone.
                        // Two photos at once (one per hand) need to split apart under each hand instead of
                        // both converging on this same centered spot, which would stack them on top of each
                        // other - dualX/dualY/dualScale are a separate, live-tuned set just for that case,
                        // with dualX mirrored (right hand out to the right, left hand out to the left) rather
                        // than the single-photo case's small hand-centering compensation only.
                        if (CrazyPhonePresentPose.isDualPresenting(net.minecraft.client.Minecraft.getInstance().player)) {
                            poseStack.translate((isLeftHand ? -CrazyPhonePresentDebug.dualX : CrazyPhonePresentDebug.dualX) + (isLeftHand ? 0.56f : -0.56f)
                                            + (isLeftHand ? CrazyPhonePresentDebug.dualLeftExtra : 0f),
                                    CrazyPhonePresentDebug.dualY, NORMAL_OFFSET + CrazyPhonePresentDebug.z);
                            poseStack.scale(CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale, CrazyPhonePresentDebug.dualScale);
                        } else {
                            poseStack.translate(CrazyPhonePresentDebug.x + (isLeftHand ? 0.56f : -0.56f), CrazyPhonePresentDebug.y, NORMAL_OFFSET + CrazyPhonePresentDebug.z);
                            poseStack.scale(CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale);
                        }
                        if (CrazyPhonePresentDebug.flipFrontBack)
                            poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                    } else {
                        poseStack.translate(isLeftHand ? -FIRST_PERSON_X_OFFSET : FIRST_PERSON_X_OFFSET, FIRST_PERSON_Y_LIFT, NORMAL_OFFSET + FIRST_PERSON_Z_FORWARD);
                        poseStack.mulPose(Axis.YP.rotationDegrees(isLeftHand ? FIRST_PERSON_YAW : -FIRST_PERSON_YAW));
                        poseStack.mulPose(Axis.ZP.rotationDegrees(isLeftHand ? FIRST_PERSON_ROLL : -FIRST_PERSON_ROLL));
                    }
                } else {
                    boolean presenting = CrazyPhonePresentPose.presentingThisRender;
                    if (presenting) {
                        // No cancel/reapply here either (same lesson as the first-person branch above) -
                        // confirmed live via a 10-candidate colored fan that >=26's THIRD_PERSON_HAND base
                        // frame is ALREADY correctly aligned on its own; every yaw/pitch reapplication
                        // candidate fought against it instead (reported live as "upside down, doesn't
                        // follow the camera at all").
                        // Two photos at once need one under each arm instead of both converging toward the
                        // body's own center (see CrazyPhonePresentDebug#dualThirdX's own doc comment) -
                        // requested but not yet live-tuned, unlike the confirmed-working first-person values.
                        // Reads the bridge flag, not Minecraft.getInstance().player directly - this branch
                        // renders whichever entity is actually being watched (third person), which in F5 is
                        // yourself but could be any other presenting player, never necessarily the viewer.
                        if (CrazyPhonePresentPose.isDualPresentingThisRender) {
                            float dualCenterX = isLeftHand ? -CrazyPhonePresentDebug.dualThirdX : CrazyPhonePresentDebug.dualThirdX;
                            poseStack.translate(dualCenterX, CrazyPhonePresentDebug.dualThirdY, NORMAL_OFFSET + PRESENT_Z_FORWARD);
                            poseStack.scale(CrazyPhonePresentDebug.dualThirdScale, CrazyPhonePresentDebug.dualThirdScale, CrazyPhonePresentDebug.dualThirdScale);
                        } else {
                            float centerX = isLeftHand ? PRESENT_CENTER_X : -PRESENT_CENTER_X;
                            poseStack.translate(centerX, PRESENT_Y_LIFT, NORMAL_OFFSET + PRESENT_Z_FORWARD);
                            poseStack.scale(PRESENT_SCALE, PRESENT_SCALE, PRESENT_SCALE);
                        }
                        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                    } else {
                        poseStack.translate(0, THIRD_PERSON_Y_LIFT, NORMAL_OFFSET);
                    }
                }
            } else if (displayContext == ItemDisplayContext.GROUND) {
                poseStack.scale(GROUND_SCALE, GROUND_SCALE, GROUND_SCALE);
            } else if (displayContext == ItemDisplayContext.FIXED) {
                poseStack.translate(0, 0, FIXED_Z_PULL);
                poseStack.scale(FIXED_SCALE, FIXED_SCALE, FIXED_SCALE * FIXED_DEPTH_SCALE);
                poseStack.mulPose(Axis.YP.rotationDegrees(180f));
            }

            fr.lordfinn.crazyphone.utils.PhotoItemData data = fr.lordfinn.crazyphone.utils.PhotoItemData.fromStack(item);
            net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState layer = output.newLayer();
            layer.setLocalTransform(poseStack.last().pose());
            // Extents matter beyond shadow/culling: ItemEntityRenderer computes how far to lift a dropped item
            // off the ground from getModelBoundingBox().minY, itself built by aggregating every layer's own
            // reported extents (see ItemStackRenderState#visitExtents/getModelBoundingBox in the real
            // decompiled source) - a layer that never calls setExtents() defaults to NO_EXTENTS, so the whole
            // bounding box collapses to a zero-sized point at the origin, and the automatic ground-lift ends
            // up as a token 0.0625 instead of anything matching this card's real size. Confirmed live: the
            // dropped item was visibly sinking about half its own height into the ground. These points are in
            // LOCAL (pre-transform) space - the same space setLocalTransform's own matrix operates in - so
            // they get the correct per-context scale (GROUND_SCALE, PRESENT_SCALE, etc.) applied automatically
            // by ItemStackRenderState#visitExtents before the final AABB is built, no per-context math needed
            // here. Roughly matches each style's own local half-size (isHand ~= HAND_HALF_SIZE, everything
            // else ~= FRAME_HALF) - doesn't need to be exact, just no longer degenerate.
            float extentHalf = isHand ? HAND_HALF_SIZE : FRAME_HALF;
            layer.setExtents(() -> new org.joml.Vector3fc[] {
                    new org.joml.Vector3f(-extentHalf, -extentHalf, -extentHalf),
                    new org.joml.Vector3f(extentHalf, extentHalf, extentHalf)
            });
            layer.setupSpecialModel(SPECIAL_RENDERER, new DrawArgument(data, isHand));
        }

        public static final class Unbaked implements net.minecraft.client.renderer.item.ItemModel.Unbaked {
            public static final com.mojang.serialization.MapCodec<Unbaked> MAP_CODEC = com.mojang.serialization.MapCodec.unit(new Unbaked());

            @Override
            public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
            }

            @Override
            public net.minecraft.client.renderer.item.ItemModel bake(net.minecraft.client.renderer.item.ItemModel.BakingContext context, org.joml.Matrix4fc transformation) {
                return INSTANCE;
            }

            @Override
            public com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.item.ItemModel.Unbaked> type() {
                return MAP_CODEC;
            }
        }
    }

    private static final class SpecialRendererImpl implements net.minecraft.client.renderer.special.SpecialModelRenderer<DrawArgument> {
        @Override
        public void submit(DrawArgument argument, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
                            int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
            if (argument == null)
                return;
            if (argument.isHand())
                renderHandFramedCardNew(argument.data(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
            else
                renderFramedCardNew(argument.data(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
        }

        @Override
        public void getExtents(java.util.function.Consumer<org.joml.Vector3fc> output) {
            // Generous bound covering both the small framed card and the enlarged presenting card - only
            // used for shadow/culling extent hints, doesn't need to be tight.
            float h = FRAME_HALF + PRESENT_SCALE;
            output.accept(new org.joml.Vector3f(-h, -h, -h));
            output.accept(new org.joml.Vector3f(h, h, h));
        }

        @Override
        public @javax.annotation.Nullable DrawArgument extractArgument(ItemStack stack) {
            return null; // Unused - ModelImpl#update() sets the argument directly via setupSpecialModel.
        }

        public static final class Unbaked implements net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked<DrawArgument> {
            public static final com.mojang.serialization.MapCodec<Unbaked> MAP_CODEC = com.mojang.serialization.MapCodec.unit(new Unbaked());
            static final SpecialRendererImpl INSTANCE = new SpecialRendererImpl();

            @Override
            public @javax.annotation.Nullable net.minecraft.client.renderer.special.SpecialModelRenderer<DrawArgument> bake(net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext context) {
                return INSTANCE;
            }

            @Override
            public com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked<DrawArgument>> type() {
                return MAP_CODEC;
            }
        }
    }

    // Adapted from renderFramedCard/renderHandFramedCard above: identical geometry/UV math, just wrapped
    // in submitCustomGeometry(...) per texture instead of pulling a VertexConsumer directly from a
    // MultiBufferSource - see this block's own doc comment above for why >=1.21.10 needs this split.
    private static void renderFramedCardNew(fr.lordfinn.crazyphone.utils.PhotoItemData data, PoseStack poseStack,
                                             net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight, int packedOverlay) {
        float t = CARD_THICKNESS_HALF;
        float h = FRAME_HALF;

        collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/.entityCutout(FRAME_TEXTURE), (pose, frameBuffer) -> {
            quad(frameBuffer, pose, packedLight, packedOverlay,
                    -h, h, t, 0, 0,
                    -h, -h, t, 0, 1,
                    h, -h, t, 1, 1,
                    h, h, t, 1, 0,
                    0, 0, 1);
            quad(frameBuffer, pose, packedLight, packedOverlay,
                    h, h, -t, 0, 0,
                    h, -h, -t, 0, 1,
                    -h, -h, -t, 1, 1,
                    -h, h, -t, 1, 0,
                    0, 0, -1);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, h, t, h, h, t, h, h, -t, -h, h, -t, 0, 1, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, -h, -t, h, -h, -t, h, -h, t, -h, -h, t, 0, -1, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -h, h, -t, -h, h, t, -h, -h, t, -h, -h, -t, -1, 0, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, h, h, t, h, h, -t, h, -h, -t, h, -h, t, 1, 0, 0);
        });

        net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ photoTexture = PLACEHOLDER_TEXTURE;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (data != null) {
            PhotoResolution resolution = fr.lordfinn.crazyphone.ClientConfig.itemPreviewPixelated ? PhotoResolution.THUMBNAIL : PhotoResolution.FULL;
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(data.photoId(), resolution);
            if (texture != null) {
                photoTexture = texture.location();
                float srcWidth = texture.width(), srcHeight = texture.height();
                if (srcWidth > srcHeight) {
                    float uSpan = srcHeight / srcWidth;
                    u0 = (1f - uSpan) / 2f;
                    u1 = u0 + uSpan;
                } else if (srcHeight > srcWidth) {
                    float vSpan = srcWidth / srcHeight;
                    v0 = (1f - vSpan) / 2f;
                    v1 = v0 + vSpan;
                }
            }
        }
        float p = PHOTO_INSET_HALF + PHOTO_EDGE_OVERLAP;
        float pz = t + PHOTO_Z_EPSILON;
        net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ finalPhotoTexture = photoTexture;
        float fu0 = u0, fv0 = v0, fu1 = u1, fv1 = v1;
        collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/.entityCutout(finalPhotoTexture), (pose, photoBuffer) ->
                quad(photoBuffer, pose, packedLight, packedOverlay,
                        -p, p, pz, fu0, fv0,
                        -p, -p, pz, fu0, fv1,
                        p, -p, pz, fu1, fv1,
                        p, p, pz, fu1, fv0,
                        0, 0, 1));
    }

    private static void renderHandFramedCardNew(fr.lordfinn.crazyphone.utils.PhotoItemData data, PoseStack poseStack,
                                                 net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLight, int packedOverlay) {
        net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ photoTexture = PLACEHOLDER_TEXTURE;
        float iw = 0.5f, ih = 0.5f;
        if (data != null) {
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(data.photoId(), PhotoResolution.FULL);
            if (texture != null) {
                photoTexture = texture.location();
                float srcWidth = texture.width(), srcHeight = texture.height();
                iw = HAND_HALF_SIZE;
                ih = HAND_HALF_SIZE * srcHeight / srcWidth;
            }
        }

        float t = HAND_CARD_THICKNESS_HALF;
        float b = HAND_FRAME_BORDER;
        float uB = FRAME_UV_BORDER;
        float ow = iw + b, oh = ih + b;
        float fiw = iw, fih = ih, fow = ow, foh = oh;

        collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/.entityCutout(FRAME_TEXTURE), (pose, frameBuffer) -> {
            slice(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, foh, fih, t, 0, uB, 0, uB);
            slice(frameBuffer, pose, packedLight, packedOverlay, -fiw, fiw, foh, fih, t, uB, 1 - uB, 0, uB);
            slice(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, foh, fih, t, 1 - uB, 1, 0, uB);
            slice(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, fih, -fih, t, 0, uB, uB, 1 - uB);
            slice(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, fih, -fih, t, 1 - uB, 1, uB, 1 - uB);
            slice(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, -fih, -foh, t, 0, uB, 1 - uB, 1);
            slice(frameBuffer, pose, packedLight, packedOverlay, -fiw, fiw, -fih, -foh, t, uB, 1 - uB, 1 - uB, 1);
            slice(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, -fih, -foh, t, 1 - uB, 1, 1 - uB, 1);

            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, foh, fih, -t, 0, uB, 0, uB);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fiw, fiw, foh, fih, -t, uB, 1 - uB, 0, uB);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, foh, fih, -t, 1 - uB, 1, 0, uB);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, fih, -fih, -t, 0, uB, uB, 1 - uB);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, fih, -fih, -t, 1 - uB, 1, uB, 1 - uB);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fow, -fiw, -fih, -foh, -t, 0, uB, 1 - uB, 1);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fiw, fiw, -fih, -foh, -t, uB, 1 - uB, 1 - uB, 1);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, fiw, fow, -fih, -foh, -t, 1 - uB, 1, 1 - uB, 1);
            sliceBack(frameBuffer, pose, packedLight, packedOverlay, -fiw, fiw, fih, -fih, -t, uB, 1 - uB, uB, 1 - uB);

            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -fow, foh, t, fow, foh, t, fow, foh, -t, -fow, foh, -t, 0, 1, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -fow, -foh, -t, fow, -foh, -t, fow, -foh, t, -fow, -foh, t, 0, -1, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, -fow, foh, -t, -fow, foh, t, -fow, -foh, t, -fow, -foh, -t, -1, 0, 0);
            doubleSidedQuad(frameBuffer, pose, packedLight, packedOverlay, fow, foh, t, fow, foh, -t, fow, -foh, -t, fow, -foh, t, 1, 0, 0);
        });

        float pz = t + PHOTO_Z_EPSILON;
        float po = PHOTO_EDGE_OVERLAP;
        net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ finalPhotoTexture = photoTexture;
        collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/.entityCutout(finalPhotoTexture), (pose, photoBuffer) ->
                quad(photoBuffer, pose, packedLight, packedOverlay,
                        -fiw - po, fih + po, pz, 0, 0,
                        -fiw - po, -fih - po, pz, 0, 1,
                        fiw + po, -fih - po, pz, 1, 1,
                        fiw + po, fih + po, pz, 1, 0,
                        0, 0, 1));
    }

    *///?}
}
