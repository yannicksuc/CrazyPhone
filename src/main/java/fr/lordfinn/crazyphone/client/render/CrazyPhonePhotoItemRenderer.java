package fr.lordfinn.crazyphone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation PLACEHOLDER_TEXTURE = Crazyphone.parseId("crazyphone:textures/item/crazy_phone_photo_placeholder.png");
    private static final ResourceLocation FRAME_TEXTURE = Crazyphone.parseId("crazyphone:textures/item/crazy_phone_photo_frame.png");

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

    public static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
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
                    net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
                    float debugYaw = camera.getYRot() * CrazyPhonePresentDebug.yawSign + CrazyPhonePresentDebug.yawOffset;
                    float debugPitch = camera.getXRot() * CrazyPhonePresentDebug.pitchSign + CrazyPhonePresentDebug.pitchOffset;
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - debugYaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(debugPitch));
                    poseStack.translate(0, CrazyPhonePresentDebug.y, NORMAL_OFFSET + CrazyPhonePresentDebug.z);
                    poseStack.scale(CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale, CrazyPhonePresentDebug.scale);
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
                if (presenting) {
                    // Cancel whatever rotation the arm bone (and everything above it) baked into the
                    // poseStack by this point, exactly, instead of guessing a fixed compensating angle - see
                    // PRESENT_SCALE's own doc comment for why. getNormalizedRotation/conjugate extracts and
                    // inverts the ACTUAL current rotation regardless of what it is; re-applying the same
                    // "180 - entityYaw" yaw LivingEntityRenderer itself uses restores exactly the body-facing
                    // orientation, no more and no less - the card ends up in the same reference frame the
                    // normal (non-presenting) one-handed carry below already renders correctly in, just
                    // bigger, centered, and always facing forward instead of resting at hip height.
                    poseStack.mulPose(poseStack.last().pose().getNormalizedRotation(new Quaternionf()).conjugate());
                    poseStack.mulPose(Axis.YP.rotationDegrees(180f - presentEntityYaw));
                    // Tilts the reference frame itself to match how much the arms are currently pitched up/
                    // down (head.xRot alone - see applyArmTransform's own doc comment for why that's exactly
                    // the arm's own "extra" delta beyond its fixed baseline), BEFORE the position offset
                    // below - so that offset (and the card sitting at the end of it) tilts along as one rigid
                    // piece with the hand, the same way a card actually resting on an upturned/downturned
                    // palm would. Applying this after the translate instead (tried first) rotated the card
                    // around its own center rather than the hand's own pivot, which read as an odd swinging/
                    // lagging disconnect from the arms instead of a clean tilt.
                    poseStack.mulPose(Axis.XP.rotationDegrees(-(float) Math.toDegrees(presentHeadPitch)));
                    float centerX = isLeftHand ? PRESENT_CENTER_X : -PRESENT_CENTER_X;
                    poseStack.translate(centerX, PRESENT_Y_LIFT, NORMAL_OFFSET + PRESENT_Z_FORWARD);
                    poseStack.scale(PRESENT_SCALE, PRESENT_SCALE, PRESENT_SCALE);
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

    // Same Polaroid-style frame as renderFramedCard (front/back/1px edge, off-white frame texture), but
    // nine-sliced around the real (uncropped) photo aspect ratio instead of a fixed 14x14 square opening -
    // the four 1x1 corner tiles and four edge strips of the frame texture stay a constant physical width,
    // only the edge strips' long axis stretches to fit whatever size the photo itself came out to.
    private static void renderHandFramedCard(PhotoItemData data, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResourceLocation photoTexture = PLACEHOLDER_TEXTURE;
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
        VertexConsumer frameBuffer = bufferSource.getBuffer(RenderType.entityCutout(FRAME_TEXTURE));
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
        VertexConsumer photoBuffer = bufferSource.getBuffer(RenderType.entityCutout(photoTexture));
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
        VertexConsumer frameBuffer = bufferSource.getBuffer(RenderType.entityCutout(FRAME_TEXTURE));
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
        ResourceLocation photoTexture = PLACEHOLDER_TEXTURE;
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
        VertexConsumer photoBuffer = bufferSource.getBuffer(RenderType.entityCutout(photoTexture));
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
}
