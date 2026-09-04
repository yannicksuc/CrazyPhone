package fr.lordfinn.crazyphone.utils;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/** Single choke point for the 2D GUI matrix-stack calls that changed shape when Mojang split GuiGraphics's
 *  {@code pose()} from a full 3D {@code PoseStack} to a 2D-only {@code Matrix3x2fStack} as of 1.21.10:
 *  {@code pushPose/popPose} were renamed {@code pushMatrix/popMatrix}, and {@code scale/translate} dropped
 *  their third (Z) argument since there's no Z axis left to translate/scale on. Every screen in this mod goes
 *  through this instead of calling {@code guiGraphics.pose()} directly for these four operations, so porting
 *  to a version with yet another rendering-stack shape only means rewriting this one file. */
public final class GuiCompat {
    private GuiCompat() {
    }

    /** Forces GuiGraphics's batched draw calls to actually submit in call order - a translucent fillGradient
     *  background and a textured blit drawn after it go through different {@code RenderType} pipelines
     *  (color vs textured), which don't necessarily flush in the order they were called in otherwise, e.g. a
     *  background ending up drawn over content issued after it instead of under it. 1.21.10 submits each
     *  draw immediately (no batching left to force), so this is a no-op there. */
    public static void flush(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.flush();
        //?}
    }

    public static void pushPose(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.pose().pushPose();
        //? } else {
        /*guiGraphics.pose().pushMatrix();
        *///?}
    }

    public static void popPose(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.pose().popPose();
        //? } else {
        /*guiGraphics.pose().popMatrix();
        *///?}
    }

    public static void translate(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, float x, float y) {
        //? if <1.21.10 {
        guiGraphics.pose().translate(x, y, 0);
        //? } else {
        /*guiGraphics.pose().translate(x, y);
        *///?}
    }

    public static void scale(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, float x, float y) {
        //? if <1.21.10 {
        guiGraphics.pose().scale(x, y, 1.0f);
        //? } else {
        /*guiGraphics.pose().scale(x, y);
        *///?}
    }

    /** Rotates the current pose about the Z axis (screen-plane rotation), in degrees. Pre-1.21.10 goes
     *  through the full 3D {@code PoseStack}'s {@code mulPose}; 1.21.10's 2D-only {@code Matrix3x2fStack}
     *  has a direct {@code rotate(radians)} instead. */
    public static void rotateDegrees(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, float degrees) {
        //? if <1.21.10 {
        guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        //? } else {
        /*guiGraphics.pose().rotate((float) Math.toRadians(degrees));
        *///?}
    }

    /** Single choke point for the {@code GuiGraphics.blit(ResourceLocation, ...)} overload that draws a
     *  texture 1:1 (sampling the whole file, no atlas) at (x,y): pre-1.21.10 that overload took the texture
     *  as its first argument directly, plus a now-gone z-depth {@code blitOffset}; 1.21.10 instead takes a
     *  {@code RenderPipeline} first, drops blitOffset entirely (z-layering is handled by draw order/strata
     *  now, not a manual z coordinate) and always draws opaque/untinted. Pass whatever blitOffset the old
     *  call used (0 for most call sites, some use a nonzero value to win against other overlapping content) -
     *  it's simply dropped on the 1.21.10 branch since there's no equivalent. */
    public static void blit(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, /*$ res_loc {*/ResourceLocation/*$}*/ texture, int x, int y, int blitOffset, int width, int height) {
        //? if <1.21.10 {
        guiGraphics.blit(texture, x, y, blitOffset, 0, 0, width, height, width, height);
        //? } else {
        /*guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, width, height, width, height);
        *///?}
    }

    /** {@link #blit(GuiGraphics, ResourceLocation, int, int, int, int, int)}, alpha-tinted (used for a
     *  disabled/faded icon state). Pre-1.21.10 this is a plain {@code RenderSystem.setShaderColor} bracketing
     *  the same call as above; 1.21.10 has no shader-color state to set, so the alpha is folded into the
     *  packed ARGB color blit's new trailing color argument instead. */
    public static void blit(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, /*$ res_loc {*/ResourceLocation/*$}*/ texture, int x, int y, int blitOffset, int width, int height, float alpha) {
        //? if <1.21.10 {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, alpha);
        guiGraphics.blit(texture, x, y, blitOffset, 0, 0, width, height, width, height);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
        //? } else {
        /*int argb = net.minecraft.util.ARGB.white(alpha);
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, width, height, width, height, argb);
        *///?}
    }

    /** Single choke point for a hand-rolled textured quad draw (used by every screen that draws a camera-mod
     *  photo proportionally scaled/cropped into an arbitrary box - see CrazyPhoneMayorCandidateScreenScreen,
     *  CrazyPhonePicturesScreenScreen and MessageWidget's own drawImage/drawCroppedImage methods) - {@code
     *  (x0,y0)-(x1,y1)} is the on-screen quad in the CURRENT pose (already translated to the caller's origin),
     *  {@code (u0,v0)-(u1,v1)} is the sampled region in NORMALIZED 0..1 texture-fraction coordinates. Pre-
     *  1.21.10 this pushes the texture through the fixed-function shader and uploads a raw BufferBuilder quad
     *  (immediate-mode rendering, matching whichever <1.21.10 vertex API sub-version is active); 1.21.10 has
     *  no immediate-mode draw path left at all - GuiGraphics's own {@code blit(ResourceLocation, x0,y0,x1,y1,
     *  u0,v0,u1,v1)} overload (corners + normalized UV, always opaque/untinted) replaces the whole thing. */
    public static void drawTexturedQuad(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, /*$ res_loc {*/ResourceLocation/*$}*/ texture, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        //? if <1.21.10 {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        org.joml.Matrix4f matrix = guiGraphics.pose().last().pose();
        //? if >=1.20.5 {
        /*com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance()
                .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, y0, 0).setUv(u0, v0);
        buffer.addVertex(matrix, x0, y1, 0).setUv(u0, v1);
        buffer.addVertex(matrix, x1, y1, 0).setUv(u1, v1);
        buffer.addVertex(matrix, x1, y0, 0).setUv(u1, v0);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow());
        *///? } else {
        com.mojang.blaze3d.vertex.BufferBuilder buffer = com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
        buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, x0, y0, 0).uv(u0, v0).endVertex();
        buffer.vertex(matrix, x0, y1, 0).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x1, y1, 0).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x1, y0, 0).uv(u1, v0).endVertex();
        com.mojang.blaze3d.vertex.Tesselator.getInstance().end();
        //?}
        //? } else {
        /*// GuiGraphicsExtractor#blit(Identifier, x0, y0, x1, y1, u0, u1, v0, v1) takes the full U range
        // before the full V range, not interleaved (u0, v0, u1, v1) like the pre-1.21.10 vertex convention
        // this method's other branch follows - confirmed against the real 26.1.2.100 decompiled source,
        // where this overload forwards straight into innerBlit(..., u0, u1, v0, v1, ...) in that exact order.
        guiGraphics.blit(texture, Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), u0, u1, v0, v1);
        *///?}
    }

    /** Single choke point for {@code InventoryScreen.renderEntityInInventory}'s signature change: pre-
     *  1.21.10 it took a single screen-space anchor point (x,y) that the model's local origin maps to,
     *  plus a scale factor; 1.21.10 instead takes an (x0,y0)-(x1,y1) destination rectangle (the model is
     *  rendered to an offscreen texture sized to that rect and centered within it, then blitted back at
     *  the same rect) - there's no direct equivalent of an arbitrary anchor point anymore. To preserve the
     *  old anchor semantics, the rectangle is built centered on (x,y) with a generous margin (so the model
     *  isn't clipped inside its own offscreen texture before any outer scissor - e.g. CallBustPreview's own
     *  enableScissor - gets a chance to crop the final result down to the intended area, exactly like it did
     *  pre-1.21.10). */
    public static void renderEntityInInventory(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int scale, Vector3f translation, Quaternionf rotation, @Nullable Quaternionf cameraOrientation, LivingEntity entity) {
        //? if <1.21.10 {
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(guiGraphics, x, y, scale, translation, rotation, cameraOrientation, entity);
        //?}
        //? if >=1.21.10 <26 {
        /*int half = Math.max(scale * 3, 1);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(guiGraphics, x - half, y - half, x + half, y + half, scale, translation, rotation, cameraOrientation, entity);
        *///?}
        //? if >=26 {
        /*// 26.x replaced this exact overload with an angle-driven one (renderEntityInInventoryFollowsAngle,
        // taking a coarse xAngle/yAngle pair that INTERNALLY overrides the render state's own bodyRot/yRot/
        // xRot fields) - that would silently discard the caller's own already-computed
        // yBodyRot/setYRot/setXRot/yHeadRot values (see CallBustPreview#render, the only caller), which is
        // exactly the fine-grained control this method exists to preserve. Its own underlying primitive,
        // GuiGraphicsExtractor#entity(EntityRenderState, scale, translation, rotation, overrideCameraAngle,
        // x0,y0,x1,y1), is still a real quaternion-based call and still public - this replicates the small
        // render-state-extraction helper InventoryScreen keeps private, then calls that primitive directly,
        // preserving this method's original signature/semantics exactly (confirmed against the real
        // decompiled 26.1.2 InventoryScreen source for both the wrapper and the extraction helper's body).
        int half = Math.max(scale * 3, 1);
        net.minecraft.client.renderer.entity.EntityRenderDispatcher dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        net.minecraft.client.renderer.entity.state.EntityRenderState renderState = renderer.createRenderState(entity, 1.0f);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        guiGraphics.entity(renderState, scale, translation, rotation, cameraOrientation, x - half, y - half, x + half, y + half);
        *///?}
    }
}
