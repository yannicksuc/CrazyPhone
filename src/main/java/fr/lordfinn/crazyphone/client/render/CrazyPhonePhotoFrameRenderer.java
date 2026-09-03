package fr.lordfinn.crazyphone.client.render;

/**
 * Draws a {@link CrazyPhonePhotoFrameEntity}. Two looks, chosen per-photo by whether its texture has
 * meaningful transparency (see {@link FabricPictureCache.CachedTexture#hasTransparency()}) - applies the
 * same way regardless of which face the frame is attached to (floor/ceiling/wall alike - "images on sides
 * still have no profondeur" - live request, an earlier version only boxed floor/ceiling placements):
 * - Opaque photo: the photo itself covers the ENTIRE outward face edge to edge (no
 *   separate top/background quad at all - a straight-down view sees only the photo, "je ne veux pas de
 *   bordure visible depuis le dessus" - live request), aspect-fit within the resize slot so it's never
 *   stretched or cropped (see {@link #fitRotated}). The HITBOX, unlike this render size, is always the FULL
 *   slot regardless of any letterboxing - see
 *   {@link CrazyPhonePhotoFrameEntity#computeBoundingBox()}'s own comment for why (matches vanilla
 *   paintings, whose hitbox is always their whole canvas). Brown only shows on the four SIDE walls (see
 *   {@link #drawBoxSides}), DEPTH tall
 *   ({@link CrazyPhonePhotoFrameEntity#DEPTH}) and sized to the photo's own footprint plus a 1-pixel margin -
 *   this is the actual depth-wise border on the north/south/east/west sides the live request asked for,
 *   replacing an earlier flat 2D border AND an earlier version's coplanar top background quad that z-fought
 *   with the photo.
 * - A photo with transparency, on any face: a plain thin single-sided card, no border/background at all -
 *   it floats a small gap off the face ({@link #TRANSPARENT_FLOAT_GAP}) rather than sitting in the box, so
 *   its see-through parts don't reveal the box's inside.
 *
 * Rotation ({@link CrazyPhonePhotoFrameEntity#rotation()}) is a pure visual spin of the photo's pixel
 * content within its existing slot rectangle - NOT a swap of which world axis width/height bind to (that
 * would cascade into the bounding box, the resize grid GUI, and the save format for comparatively little
 * benefit over what "add a rotate button" actually asked for). See {@link #uvForCorner} for how a 90°/270°
 * rotation both re-maps which texture edge lands on which screen edge AND swaps the effective aspect ratio
 * used to fit the image into its slot, so a rotated portrait photo still fits without stretching.
 *
 * Hand-rolled VertexConsumer quads, same low-level technique CrazyPhonePhotoItemRenderer already uses for
 * its own Polaroid-card frame (see that class's own doc comment) - no baked model, this entity has no
 * block/item model of its own at all.
 *
 * >=26 branch is unproven against a real decompile the way every other >=26 rendering piece in this codebase
 * was (see PORTING-26x.md's own established practice) - this project has never had a custom entity before
 * (confirmed via a full-repo search before writing this), so there was no existing >=26 entity-render
 * precedent to copy. Written against the same EntityRenderState-based extractRenderState()/submit() split
 * LivingEntityRenderer subclasses already use elsewhere in this codebase (vanilla's own RenderState rework
 * applies to the whole EntityRenderer hierarchy, not just living entities) - expect this to need real
 * compile-error-driven correction, same as every other >=26 gap this project has closed so far.
 */
//? if <26 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import /*$ render_type_import {*/net.minecraft.client.renderer.RenderType/*$}*/;
import net.minecraft.core.Direction;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

public class CrazyPhonePhotoFrameRenderer extends EntityRenderer<CrazyPhonePhotoFrameEntity> {
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ GROUND_BACKING_TEXTURE =
            Crazyphone.parseId("crazyphone:textures/entity/photo_frame_ground_backing.png");
    private static final float DEPTH = (float) CrazyPhonePhotoFrameEntity.DEPTH;
    // The side walls sit FLUSH against the photo's own edge, zero gap - an earlier version added a small
    // margin here, which (since the top face is the photo alone, no background quad under it anymore) left
    // an empty 1-pixel ring between the photo's edge and the walls where the block underneath showed through
    // ("il ne faut pas laisser un vide de 1 pixel sur la face du dessus" - live request, reported with a
    // screenshot showing exactly that gap).
    private static final float BORDER_MARGIN = 0f;
    // "reculer l'image de 0.75 pixel pour que ça flotte à .25 pixel au dessus de la face visée" - a
    // transparent photo skips the box entirely and just floats this far off the face instead (out of the
    // full 1-pixel/DEPTH budget, only a quarter pixel of it is used as a gap).
    private static final float TRANSPARENT_FLOAT_GAP = DEPTH * 0.25f;

    public CrazyPhonePhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ getTextureLocation(CrazyPhonePhotoFrameEntity entity) {
        return GROUND_BACKING_TEXTURE;
    }

    @Override
    public void render(CrazyPhonePhotoFrameEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        applyFaceTransform(entity, poseStack);

        float w = entity.widthBlocks();
        float h = entity.heightBlocks();
        int rotation = entity.rotation();
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(entity.photoId(), PhotoResolution.FULL);
        float drawW = w, drawH = h;
        if (texture != null) {
            float[] fit = fitRotated(w, h, texture.width(), texture.height(), rotation);
            drawW = fit[0];
            drawH = fit[1];
        }

        boolean transparent = texture != null && texture.hasTransparency();
        // The depth-wise box (side walls) now applies to every opaque placement, not just floor/ceiling -
        // "images on sides still have no profondeur" (live request, reported for wall-mounted frames).
        boolean boxed = !transparent;
        Direction face = entity.attachFace();
        float depthSign = outwardDepthSign(face);
        boolean mirrored = isMirroredFace(face);

        if (boxed) {
            drawBoxSides(poseStack, buffer, packedLight, drawW, drawH, depthSign, mirrored);
            drawBacking(poseStack, buffer, packedLight, drawW, drawH, mirrored);
        }
        if (texture != null) {
            float z = depthSign * (boxed ? DEPTH : (transparent ? TRANSPARENT_FLOAT_GAP : DEPTH));
            drawImageQuad(poseStack, buffer, packedLight, texture.location(), drawW, drawH, z, rotation, mirrored);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // Rotates/translates from the entity's own tracked position (the attach block's CENTER, world-space -
    // see CrazyPhonePhotoFrameEntity's own tryPlace, which sets it there) to the actual face plane, facing
    // outward along that face's normal. Direction has no built-in "orient a flat quad's front face this
    // way" helper (its own toYRot()/toXRot() are for entity look-direction, not this) - explicit per-face
    // cases instead, same shape HangingEntity's own vanilla renderer uses internally. After this, local
    // z=0 is exactly the block face and local +z points outward (away from the block) - both drawImageQuad
    // and the box helpers below build everything in that local space.
    private void applyFaceTransform(CrazyPhonePhotoFrameEntity entity, PoseStack poseStack) {
        Direction face = entity.attachFace();
        double faceOffset = entity.computeFaceOffset(entity.level());
        switch (face) {
            case DOWN -> {
                poseStack.translate(0, faceOffset - 0.5, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
            }
            case UP -> {
                poseStack.translate(0, faceOffset - 0.5, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case NORTH -> poseStack.translate(0, 0, faceOffset - 0.5);
            case SOUTH -> {
                poseStack.translate(0, 0, faceOffset - 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            case WEST -> {
                poseStack.translate(faceOffset - 0.5, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case EAST -> {
                poseStack.translate(faceOffset - 0.5, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
        }
    }

    // NORTH and SOUTH apply no rotation (NORTH) or a 180-about-Y rotation (SOUTH) in applyFaceTransform,
    // neither of which flips local Z the way UP/DOWN/WEST/EAST's 90-degree rotations do - the net effect is
    // that local +Z ends up pointing INTO the block for these two faces instead of outward, so a photo
    // rendered at the usual +DEPTH local z landed coplanar with (or behind) the block's own face instead of
    // a pixel in front of it ("the image is on the same z-index as the block... not one pixel in front like
    // the other faces" - live request, confirmed live: a wall photo rendering visibly recessed into the
    // block). Flipping the sign of every z used for these two faces corrects it without touching
    // applyFaceTransform's own rotation (a real axis flip there isn't expressible as a proper rotation
    // without also mirroring X or Y, which would flip the image itself).
    //
    // That alone moves the geometry to the right POSITION but not the right ORIENTATION: translating a flat
    // quad's vertices to a different z, unchanged order, does not change which way its face/normal points -
    // RenderType.entityCutout backface-culls, so the quad still shows its BACK (culled from the real outward
    // viewing angle) at the new, correctly-offset position ("all drawed face have the wrong normal...
    // visible on the wrong side" - live request, confirmed live: the photo only showed as a sliver, edge-on,
    // never face-on). isMirroredFace()/REVERSE_WINDING below is the actual orientation fix - every quad
    // builder takes it and swaps its two middle vertices when true, reversing winding without changing shape
    // or position.
    private static float outwardDepthSign(Direction face) {
        return isMirroredFace(face) ? -1f : 1f;
    }

    private static boolean isMirroredFace(Direction face) {
        return face == Direction.NORTH || face == Direction.SOUTH;
    }

    // Fits an image (pixelW x pixelH) into a w x h slot without cropping, accounting for a 90/270 rotation
    // swapping the image's own effective aspect ratio (a rotated portrait photo behaves like a landscape one
    // for fitting purposes) - same "min of the two scale factors" idea as
    // CrazyPhonePhotoViewerScreen#drawFitted, just rotation-aware. Returns {drawW, drawH}.
    private static float[] fitRotated(float w, float h, int pixelW, int pixelH, int rotation) {
        boolean swapped = (rotation & 1) != 0;
        float aspect = (swapped ? pixelH : pixelW) / (float) (swapped ? pixelW : pixelH);
        if (w / aspect <= h)
            return new float[]{w, w / aspect};
        return new float[]{h * aspect, h};
    }

    // The four side walls of the border box, DEPTH tall, running from the block face (z=0) out to the
    // photo's own resting surface (z=DEPTH) - this is the ONLY brown geometry drawn for a boxed frame; there
    // is deliberately no separate top/background quad anymore (an earlier version drew one directly under
    // the photo quad, both coplanar at z=DEPTH, which z-fought with the photo - "attention... les deux
    // texture clash" - live request) - looking straight down at the frame you see only the photo, edge to
    // edge; the brown only shows from an angle, on these 4 side walls. Sized to the photo's own actual
    // footprint plus a 1-pixel margin ("1 pixel margin on the front face" - live request) so the walls read
    // as a thin lip around the photo rather than starting exactly at its edge.
    private void drawBoxSides(PoseStack poseStack, MultiBufferSource buffer, int light, float w, float h, float depthSign, boolean mirrored) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(GROUND_BACKING_TEXTURE));
        var pose = poseStack.last();
        float x0 = -w / 2f - BORDER_MARGIN, x1 = w / 2f + BORDER_MARGIN;
        float y0 = -h / 2f - BORDER_MARGIN, y1 = h / 2f + BORDER_MARGIN;
        sideQuad(consumer, pose, x0, y0, x1, y0, depthSign, mirrored, light); // "south" edge of the box footprint
        sideQuad(consumer, pose, x1, y0, x1, y1, depthSign, mirrored, light); // "east"
        sideQuad(consumer, pose, x1, y1, x0, y1, depthSign, mirrored, light); // "north"
        sideQuad(consumer, pose, x0, y1, x0, y0, depthSign, mirrored, light); // "west"
    }

    private void sideQuad(VertexConsumer consumer, PoseStack.Pose pose, float xa, float ya, float xb, float yb, float depthSign, boolean mirrored, int light) {
        float outZ = depthSign * DEPTH;
        if (!mirrored) {
            vertex(consumer, pose, xa, ya, outZ, 0f, 0f, light);
            vertex(consumer, pose, xa, ya, 0f, 0f, 1f, light);
            vertex(consumer, pose, xb, yb, 0f, 1f, 1f, light);
            vertex(consumer, pose, xb, yb, outZ, 1f, 0f, light);
        } else {
            vertex(consumer, pose, xa, ya, outZ, 0f, 0f, light);
            vertex(consumer, pose, xb, yb, outZ, 1f, 0f, light);
            vertex(consumer, pose, xb, yb, 0f, 1f, 1f, light);
            vertex(consumer, pose, xa, ya, 0f, 0f, 1f, light);
        }
    }

    // ONE face only, at the outward z - drawing a mirrored second copy at the block-flush side ("front face,
    // then back face") made the photo visibly render twice, close enough in depth to look like a smeared
    // double exposure ("tu dessine deux fois l'image" - live request). The block-flush side gets its own
    // plain brown quad instead (drawBacking) when this is a boxed placement, matching a real photo's
    // cardboard backing rather than a mirrored copy of the picture itself. `mirrored` swaps the winding
    // order (see outwardDepthSign's own comment for why moving z alone isn't enough on north/south).
    private void drawImageQuad(PoseStack poseStack, MultiBufferSource buffer, int light, /*$ res_loc {*/ResourceLocation/*$}*/ texture, float w, float h, float z, int rotation, boolean mirrored) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(texture));
        var pose = poseStack.last();
        float x0 = -w / 2f, x1 = w / 2f, y0 = -h / 2f, y1 = h / 2f;
        float[] uv0 = uvForCorner(0, rotation), uv1 = uvForCorner(1, rotation), uv2 = uvForCorner(2, rotation), uv3 = uvForCorner(3, rotation);
        if (!mirrored) {
            vertex(consumer, pose, x0, y1, z, uv0[0], uv0[1], light);
            vertex(consumer, pose, x0, y0, z, uv1[0], uv1[1], light);
            vertex(consumer, pose, x1, y0, z, uv2[0], uv2[1], light);
            vertex(consumer, pose, x1, y1, z, uv3[0], uv3[1], light);
        } else {
            vertex(consumer, pose, x0, y1, z, uv0[0], uv0[1], light);
            vertex(consumer, pose, x1, y1, z, uv3[0], uv3[1], light);
            vertex(consumer, pose, x1, y0, z, uv2[0], uv2[1], light);
            vertex(consumer, pose, x0, y0, z, uv1[0], uv1[1], light);
        }
    }

    // The plain brown "backing" at the block-flush side (z=0) of a boxed frame - see drawImageQuad's own
    // comment for why this replaced a mirrored second copy of the photo there.
    private void drawBacking(PoseStack poseStack, MultiBufferSource buffer, int light, float w, float h, boolean mirrored) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(GROUND_BACKING_TEXTURE));
        var pose = poseStack.last();
        float x0 = -w / 2f, x1 = w / 2f, y0 = -h / 2f, y1 = h / 2f;
        if (!mirrored) {
            vertex(consumer, pose, x0, y1, 0f, 0f, 0f, light);
            vertex(consumer, pose, x0, y0, 0f, 0f, 1f, light);
            vertex(consumer, pose, x1, y0, 0f, 1f, 1f, light);
            vertex(consumer, pose, x1, y1, 0f, 1f, 0f, light);
        } else {
            vertex(consumer, pose, x0, y1, 0f, 0f, 0f, light);
            vertex(consumer, pose, x1, y1, 0f, 1f, 0f, light);
            vertex(consumer, pose, x1, y0, 0f, 1f, 1f, light);
            vertex(consumer, pose, x0, y0, 0f, 0f, 1f, light);
        }
    }

    // Corner order: 0=top-left, 1=bottom-left, 2=bottom-right, 3=top-right (screen-space, front face).
    // Cycling which texture corner (same order) lands on which screen corner is what actually rotates the
    // pixel content by rotation*90° clockwise - the aspect swap in fitRotated() above is what keeps the
    // overall rectangle the right shape for that rotated content to not look stretched.
    private static float[] uvForCorner(int corner, int rotation) {
        float[][] base = {{0f, 0f}, {0f, 1f}, {1f, 1f}, {1f, 0f}};
        return base[Math.floorMod(corner - rotation, 4)];
    }

    private void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
    }
}
//?}
//? if >=26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import /^$ render_type_import {^/net.minecraft.client.renderer.RenderType/^$}^/;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

public class CrazyPhonePhotoFrameRenderer extends EntityRenderer<CrazyPhonePhotoFrameEntity, CrazyPhonePhotoFrameRenderer.State> {
    private static final Identifier GROUND_BACKING_TEXTURE =
            Crazyphone.parseId("crazyphone:textures/entity/photo_frame_ground_backing.png");
    private static final float DEPTH = (float) CrazyPhonePhotoFrameEntity.DEPTH;
    // Flush against the photo's own edge, zero gap - see the <26 branch's own comment on this same constant.
    private static final float BORDER_MARGIN = 0f;
    private static final float TRANSPARENT_FLOAT_GAP = DEPTH * 0.25f;

    public CrazyPhonePhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static class State extends EntityRenderState {
        public Direction face = Direction.NORTH;
        public double faceOffset;
        public float width = 1f, height = 1f;
        public int rotation;
        public java.util.UUID photoId;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CrazyPhonePhotoFrameEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.face = entity.attachFace();
        state.faceOffset = entity.computeFaceOffset(entity.level());
        state.width = entity.widthBlocks();
        state.height = entity.heightBlocks();
        state.rotation = entity.rotation();
        state.photoId = entity.photoId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        poseStack.pushPose();
        applyFaceTransform(state, poseStack);

        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(state.photoId, PhotoResolution.FULL);
        float drawW = state.width, drawH = state.height;
        if (texture != null) {
            float[] fit = fitRotated(state.width, state.height, texture.width(), texture.height(), state.rotation);
            drawW = fit[0];
            drawH = fit[1];
        }

        boolean transparent = texture != null && texture.hasTransparency();
        // The depth-wise box (side walls) now applies to every opaque placement, not just floor/ceiling -
        // "images on sides still have no profondeur" (live request, reported for wall-mounted frames).
        boolean boxed = !transparent;
        float depthSign = outwardDepthSign(state.face);
        boolean mirrored = isMirroredFace(state.face);

        if (boxed) {
            float finalW = drawW, finalH = drawH;
            collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/RenderType/^$}^/.entityCutout(GROUND_BACKING_TEXTURE),
                    (pose, consumer) -> drawBoxSides(consumer, pose, finalW, finalH, depthSign, mirrored, state.lightCoords));
            collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/RenderType/^$}^/.entityCutout(GROUND_BACKING_TEXTURE),
                    (pose, consumer) -> drawBacking(consumer, pose, finalW, finalH, mirrored, state.lightCoords));
        }
        if (texture != null) {
            float z = depthSign * (boxed ? DEPTH : (transparent ? TRANSPARENT_FLOAT_GAP : DEPTH));
            float finalDrawW = drawW, finalDrawH = drawH;
            int rotation = state.rotation;
            collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/RenderType/^$}^/.entityCutout(texture.location()),
                    (pose, consumer) -> drawImageQuad(consumer, pose, finalDrawW, finalDrawH, z, rotation, mirrored, state.lightCoords));
        }

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private void applyFaceTransform(State state, PoseStack poseStack) {
        Direction face = state.face;
        double faceOffset = state.faceOffset;
        switch (face) {
            case DOWN -> {
                poseStack.translate(0, faceOffset - 0.5, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
            }
            case UP -> {
                poseStack.translate(0, faceOffset - 0.5, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case NORTH -> poseStack.translate(0, 0, faceOffset - 0.5);
            case SOUTH -> {
                poseStack.translate(0, 0, faceOffset - 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            case WEST -> {
                poseStack.translate(faceOffset - 0.5, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case EAST -> {
                poseStack.translate(faceOffset - 0.5, 0, 0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
        }
    }

    // NORTH and SOUTH apply no rotation (NORTH) or a 180-about-Y rotation (SOUTH) in applyFaceTransform,
    // neither of which flips local Z the way UP/DOWN/WEST/EAST's 90-degree rotations do - see the <26
    // branch's own comment on this same method for the full explanation, including why moving z alone isn't
    // enough (isMirroredFace/the `mirrored` param below is the actual winding-order fix).
    private static float outwardDepthSign(Direction face) {
        return isMirroredFace(face) ? -1f : 1f;
    }

    private static boolean isMirroredFace(Direction face) {
        return face == Direction.NORTH || face == Direction.SOUTH;
    }

    private static float[] fitRotated(float w, float h, int pixelW, int pixelH, int rotation) {
        boolean swapped = (rotation & 1) != 0;
        float aspect = (swapped ? pixelH : pixelW) / (float) (swapped ? pixelW : pixelH);
        if (w / aspect <= h)
            return new float[]{w, w / aspect};
        return new float[]{h * aspect, h};
    }

    private static void drawBoxSides(VertexConsumer consumer, PoseStack.Pose pose, float w, float h, float depthSign, boolean mirrored, int light) {
        float x0 = -w / 2f - BORDER_MARGIN, x1 = w / 2f + BORDER_MARGIN;
        float y0 = -h / 2f - BORDER_MARGIN, y1 = h / 2f + BORDER_MARGIN;
        sideQuad(consumer, pose, x0, y0, x1, y0, depthSign, mirrored, light);
        sideQuad(consumer, pose, x1, y0, x1, y1, depthSign, mirrored, light);
        sideQuad(consumer, pose, x1, y1, x0, y1, depthSign, mirrored, light);
        sideQuad(consumer, pose, x0, y1, x0, y0, depthSign, mirrored, light);
    }

    private static void sideQuad(VertexConsumer consumer, PoseStack.Pose pose, float xa, float ya, float xb, float yb, float depthSign, boolean mirrored, int light) {
        float outZ = depthSign * DEPTH;
        if (!mirrored) {
            vertex(consumer, pose, xa, ya, outZ, 0f, 0f, light);
            vertex(consumer, pose, xa, ya, 0f, 0f, 1f, light);
            vertex(consumer, pose, xb, yb, 0f, 1f, 1f, light);
            vertex(consumer, pose, xb, yb, outZ, 1f, 0f, light);
        } else {
            vertex(consumer, pose, xa, ya, outZ, 0f, 0f, light);
            vertex(consumer, pose, xb, yb, outZ, 1f, 0f, light);
            vertex(consumer, pose, xb, yb, 0f, 1f, 1f, light);
            vertex(consumer, pose, xa, ya, 0f, 0f, 1f, light);
        }
    }

    // ONE face only, at the outward z - see the <26 branch's own comment on this same method for why a
    // mirrored second copy at z=0 was removed. `mirrored` swaps the winding order.
    private static void drawImageQuad(VertexConsumer consumer, PoseStack.Pose pose, float w, float h, float z, int rotation, boolean mirrored, int light) {
        float x0 = -w / 2f, x1 = w / 2f, y0 = -h / 2f, y1 = h / 2f;
        float[] uv0 = uvForCorner(0, rotation), uv1 = uvForCorner(1, rotation), uv2 = uvForCorner(2, rotation), uv3 = uvForCorner(3, rotation);
        if (!mirrored) {
            vertex(consumer, pose, x0, y1, z, uv0[0], uv0[1], light);
            vertex(consumer, pose, x0, y0, z, uv1[0], uv1[1], light);
            vertex(consumer, pose, x1, y0, z, uv2[0], uv2[1], light);
            vertex(consumer, pose, x1, y1, z, uv3[0], uv3[1], light);
        } else {
            vertex(consumer, pose, x0, y1, z, uv0[0], uv0[1], light);
            vertex(consumer, pose, x1, y1, z, uv3[0], uv3[1], light);
            vertex(consumer, pose, x1, y0, z, uv2[0], uv2[1], light);
            vertex(consumer, pose, x0, y0, z, uv1[0], uv1[1], light);
        }
    }

    // The plain brown "backing" at the block-flush side (z=0) of a boxed frame - see drawImageQuad's own
    // comment.
    private static void drawBacking(VertexConsumer consumer, PoseStack.Pose pose, float w, float h, boolean mirrored, int light) {
        float x0 = -w / 2f, x1 = w / 2f, y0 = -h / 2f, y1 = h / 2f;
        if (!mirrored) {
            vertex(consumer, pose, x0, y1, 0f, 0f, 0f, light);
            vertex(consumer, pose, x0, y0, 0f, 0f, 1f, light);
            vertex(consumer, pose, x1, y0, 0f, 1f, 1f, light);
            vertex(consumer, pose, x1, y1, 0f, 1f, 0f, light);
        } else {
            vertex(consumer, pose, x0, y1, 0f, 0f, 0f, light);
            vertex(consumer, pose, x1, y1, 0f, 1f, 0f, light);
            vertex(consumer, pose, x1, y0, 0f, 1f, 1f, light);
            vertex(consumer, pose, x0, y0, 0f, 0f, 1f, light);
        }
    }

    private static float[] uvForCorner(int corner, int rotation) {
        float[][] base = {{0f, 0f}, {0f, 1f}, {1f, 1f}, {1f, 0f}};
        return base[Math.floorMod(corner - rotation, 4)];
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 0f, 1f);
    }
}
*///?}
