package fr.lordfinn.crazyphone.client.render;

/**
 * Draws a {@link CrazyPhonePhotoFrameEntity} - a flat, aspect-fit (letterboxed, never cropped) image quad
 * sitting flush against whichever face it's attached to, plus (floor/ceiling placements only, per the live
 * feature request) a brown border+backing behind it, matching the "rug-like" ground-placed look requested -
 * wall-mounted frames stay borderless. Hand-rolled VertexConsumer quads, same low-level technique
 * CrazyPhonePhotoItemRenderer already uses for its own Polaroid-card frame (see that class's own doc
 * comment) - no baked model, this entity has no block/item model of its own at all.
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
    // How far the brown ground backing extends past the photo's own edge on every side, in blocks -
    // "un fond marron" per the live request, sized to read as a rug/mat under the photo rather than a
    // tight picture-frame border (that treatment is wall-only, per the same request, and this class never
    // draws it for wall placements at all - see render()'s own isFloorOrCeiling() branch).
    private static final float GROUND_BACKING_MARGIN = 1f / 16f;

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
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(entity.photoId(), PhotoResolution.FULL);
        float drawW = w, drawH = h;
        if (texture != null) {
            float aspect = texture.width() / (float) texture.height();
            // Fit within w x h without cropping - same "min of the two scale factors" idea as
            // CrazyPhonePhotoViewerScreen#drawFitted, just against a block-sized box instead of a GUI one.
            if (w / aspect <= h) {
                drawW = w;
                drawH = w / aspect;
            } else {
                drawH = h;
                drawW = h * aspect;
            }
        }

        if (entity.isFloorOrCeiling())
            drawGroundBacking(poseStack, buffer, packedLight, w, h);

        if (texture != null)
            drawQuad(poseStack, buffer, packedLight, texture.location(), drawW, drawH);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // Rotates/translates from the entity's own tracked position (the attach block's CENTER, world-space -
    // see CrazyPhonePhotoFrameEntity's own tryPlace, which sets it there) to the actual face plane, facing
    // outward along that face's normal. Direction has no built-in "orient a flat quad's front face this
    // way" helper (its own toYRot()/toXRot() are for entity look-direction, not this) - explicit per-face
    // cases instead, same shape HangingEntity's own vanilla renderer uses internally.
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

    private void drawQuad(PoseStack poseStack, MultiBufferSource buffer, int light, /*$ res_loc {*/ResourceLocation/*$}*/ texture, float w, float h) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(texture));
        var pose = poseStack.last();
        float x0 = -w / 2f, x1 = w / 2f, y0 = -h / 2f, y1 = h / 2f;
        quad(consumer, pose, x0, y1, x1, y1, x1, y0, x0, y0, 0f, 0f, 1f, 1f, DEPTH, light);
    }

    private void drawGroundBacking(PoseStack poseStack, MultiBufferSource buffer, int light, float w, float h) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(GROUND_BACKING_TEXTURE));
        var pose = poseStack.last();
        float x0 = -w / 2f - GROUND_BACKING_MARGIN, x1 = w / 2f + GROUND_BACKING_MARGIN;
        float y0 = -h / 2f - GROUND_BACKING_MARGIN, y1 = h / 2f + GROUND_BACKING_MARGIN;
        quad(consumer, pose, x0, y1, x1, y1, x1, y0, x0, y0, 0f, 0f, 1f, 1f, DEPTH * 0.5f, light);
    }

    // One double-sided flat quad, both faces at +/-z (a thin "card" rather than a true 1-pixel-thick box -
    // cheap enough for a decorative entity, and matches how CrazyPhonePhotoItemRenderer's own hand-rolled
    // quads work for the exact same reason).
    private void quad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1,
                       float x2, float y2, float x3, float y3, float u0, float v0, float u1, float v1, float z, int light) {
        vertex(consumer, pose, x0, y0, z, u0, v0, light);
        vertex(consumer, pose, x3, y3, z, u0, v1, light);
        vertex(consumer, pose, x2, y2, z, u1, v1, light);
        vertex(consumer, pose, x1, y1, z, u1, v0, light);
        vertex(consumer, pose, x1, y1, -z, u1, v0, light);
        vertex(consumer, pose, x2, y2, -z, u1, v1, light);
        vertex(consumer, pose, x3, y3, -z, u0, v1, light);
        vertex(consumer, pose, x0, y0, -z, u0, v0, light);
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
    private static final float GROUND_BACKING_MARGIN = 1f / 16f;

    public CrazyPhonePhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static class State extends EntityRenderState {
        public Direction face = Direction.NORTH;
        public double faceOffset;
        public float width = 1f, height = 1f;
        public boolean floorOrCeiling;
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
        state.floorOrCeiling = entity.isFloorOrCeiling();
        state.photoId = entity.photoId();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        poseStack.pushPose();
        applyFaceTransform(state, poseStack);

        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(state.photoId, PhotoResolution.FULL);
        float drawW = state.width, drawH = state.height;
        if (texture != null) {
            float aspect = texture.width() / (float) texture.height();
            if (state.width / aspect <= state.height) {
                drawW = state.width;
                drawH = state.width / aspect;
            } else {
                drawH = state.height;
                drawW = state.height * aspect;
            }
        }

        if (state.floorOrCeiling)
            collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/RenderType/^$}^/.entityCutout(GROUND_BACKING_TEXTURE),
                    (pose, consumer) -> quad(consumer, pose, state.width, state.height, GROUND_BACKING_MARGIN, DEPTH * 0.5f, state.lightCoords));
        if (texture != null) {
            float finalDrawW = drawW, finalDrawH = drawH;
            collector.submitCustomGeometry(poseStack, /^$ render_type_import {^/RenderType/^$}^/.entityCutout(texture.location()),
                    (pose, consumer) -> quad(consumer, pose, finalDrawW, finalDrawH, 0f, DEPTH, state.lightCoords));
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

    private void quad(VertexConsumer consumer, PoseStack.Pose pose, float w, float h, float margin, float z, int light) {
        float x0 = -w / 2f - margin, x1 = w / 2f + margin, y0 = -h / 2f - margin, y1 = h / 2f + margin;
        vertex(consumer, pose, x0, y1, z, 0f, 0f, light);
        vertex(consumer, pose, x0, y0, z, 0f, 1f, light);
        vertex(consumer, pose, x1, y0, z, 1f, 1f, light);
        vertex(consumer, pose, x1, y1, z, 1f, 0f, light);
        vertex(consumer, pose, x1, y1, -z, 1f, 0f, light);
        vertex(consumer, pose, x1, y0, -z, 1f, 1f, light);
        vertex(consumer, pose, x0, y0, -z, 0f, 1f, light);
        vertex(consumer, pose, x0, y1, -z, 0f, 0f, light);
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
*///?}
