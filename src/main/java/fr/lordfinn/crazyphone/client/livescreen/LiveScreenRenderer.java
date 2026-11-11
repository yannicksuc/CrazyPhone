package fr.lordfinn.crazyphone.client.livescreen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if <1.21.10 {
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
//?}
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Crazyphone;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Draws a {@link LiveScreenMesh} as full-bright colored quads right on top of the screen face of the phone
 * item model (crazy_phone.json: the "south" face, texture pixels 10..17 x 9..20 of crazyphone.png).
 *
 * Two entry points, one per item rendering generation:
 * <ul>
 *     <li>&lt;1.21.10: {@link #renderInBlockSpace}, called by CrazyPhoneLiveScreenItemRenderMixin from inside
 *     vanilla's ItemRenderer#render, where the pose already carries the item's display transform;</li>
 *     <li>&gt;=1.21.10: {@link LiveScreenItemModel} adds one extra special-model layer, positioned with
 *     {@link #layerTransform} (the same display transform, computed here).</li>
 * </ul>
 * Any exception is caught, logged once, and after a few failures the feature turns itself off for the
 * session: a cosmetic overlay must never break item rendering.
 */
public final class LiveScreenRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ WHITE = Crazyphone.parseId("crazyphone:textures/item/live_screen.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int MAX_FAILURES = 3;

    // Screen rectangle in block units (model pixels / 16) on the model's south face: x 6..10, the face's
    // v 4..11.5 maps onto y 8..0, and the screen spans texture rows 9..20 (v 4.5..10.5).
    private static final float X0 = 6f / 16f;
    private static final float X1 = 10f / 16f;
    private static final float Y_TOP = (8f - 0.5f * 16f / 15f) / 16f;
    private static final float Y_BOTTOM = (8f - 6.5f * 16f / 15f) / 16f;
    private static final float Z = 8.5f / 16f + 0.0015f;

    private static int failures = 0;

    private LiveScreenRenderer() {
    }

    static void resetFailures() {
        failures = 0;
    }

    /** The mesh to draw on this phone right now, or null to keep the plain static texture. */
    public static @Nullable LiveScreenMesh prepare(ItemStack stack, @Nullable LivingEntity holder, ItemDisplayContext context) {
        if (failures >= MAX_FAILURES)
            return null;
        try {
            LiveScreenView view = LiveScreenViews.resolve(stack, holder, context);
            return view == null ? null : LiveScreenMesh.of(view);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    /**
     * Whether the live screen is drawn over this phone right now. The plain texture under it is then the dark
     * "off" one (CrazyPhoneItemProperties): the game smooths textures seen from afar (mipmaps), which blended the
     * bezel row above the screen with the bright cyan or blue top row of the "on" or "in call" texture and showed
     * a light blue line between the bezel and the drawn screen; with the dark one it reads as the bezel line of
     * a phone that is off. Never for a phone drawn as an icon (hotbar, inventory).
     */
    public static boolean coversScreen(ItemStack stack, @Nullable LivingEntity holder) {
        if (holder == null || failures >= MAX_FAILURES || fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose.guiItemDepth > 0)
            return false;
        try {
            return LiveScreenViews.resolve(stack, holder, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static void fail(Throwable t) {
        failures++;
        if (failures == 1 || failures == MAX_FAILURES)
            LOGGER.error("CrazyPhone: live phone screen failed ({}/{}), {}", failures, MAX_FAILURES,
                    failures >= MAX_FAILURES ? "turned off until the next world join" : "skipping this frame", t);
    }

    //? if <1.21.10 {
    /** &lt;1.21.10: the pose is in the item model's own block space (0..1), display transform applied. */
    public static void renderInBlockSpace(ItemStack stack, @Nullable LivingEntity holder, ItemDisplayContext context,
                                          PoseStack poseStack, MultiBufferSource buffers) {
        LiveScreenMesh mesh = prepare(stack, holder, context);
        if (mesh == null)
            return;
        try {
            // No-cull cutout with full-bright light: entityTranslucentEmissive drew nothing here (checked live).
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
            emit(buffer, poseStack.last(), mesh);
        } catch (Throwable t) {
            fail(t);
        }
    }
    //?}

    static void emit(VertexConsumer buffer, PoseStack.Pose pose, LiveScreenMesh mesh) {
        float sx = (X1 - X0) / LiveScreenPainter.WIDTH;
        float sy = (Y_TOP - Y_BOTTOM) / LiveScreenPainter.HEIGHT;
        for (int i = 0; i < mesh.count; i++) {
            float x0 = X0 + mesh.rects[i * 4] * sx;
            float y0 = Y_TOP - mesh.rects[i * 4 + 1] * sy;
            float x1 = X0 + mesh.rects[i * 4 + 2] * sx;
            float y1 = Y_TOP - mesh.rects[i * 4 + 3] * sy;
            int c = mesh.colors[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            // Counter-clockwise seen from +Z (the side the screen faces).
            vertex(buffer, pose, x0, y1, r, g, b, 0f, 1f);
            vertex(buffer, pose, x1, y1, r, g, b, 1f, 1f);
            vertex(buffer, pose, x1, y0, r, g, b, 1f, 0f);
            vertex(buffer, pose, x0, y0, r, g, b, 0f, 0f);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, int r, int g, int b, float u, float v) {
        //? if <1.20.5 {
        buffer.vertex(pose.pose(), x, y, Z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(pose.normal(), 0f, 0f, 1f)
                .endVertex();
        //?} else {
        /*buffer.addVertex(pose, x, y, Z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0f, 0f, 1f);
        *///?}
    }

    // The phone's display transforms, copied from models/item/crazy_phone.json (identical in every phone
    // model variant). Only needed on >=1.21.10, where our extra layer can't inherit the base layer's own.
    private static final float[] THIRD_PERSON = {31f, -19f, 0f, 0f, 5.75f, 4.5f, 0.9f};
    private static final float[] FIRST_PERSON = {-17f, -41f, -10f, 2f, 7.5f, -0.75f, 1.1f};

    /**
     * The local transform for an extra item layer on >=1.21.10, mapping the model's block space (0..1) onto
     * exactly where the base phone model is drawn. Such a layer (with no item transform of its own) is drawn
     * with translate(-0.5) * local, while the base model is drawn with display * translate(-0.5), so
     * local = translate(0.5) * display * translate(-0.5), where display mirrors vanilla's ItemTransform#apply
     * (translation x and rotation y/z negated for the left hand).
     */
    public static Matrix4f layerTransform(ItemDisplayContext context) {
        boolean firstPerson = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        float[] t = firstPerson ? FIRST_PERSON : THIRD_PERSON;
        float deg = (float) (Math.PI / 180.0);
        float rx = t[0], ry = left ? -t[1] : t[1], rz = left ? -t[2] : t[2];
        Matrix4f m = new Matrix4f().translation(0.5f, 0.5f, 0.5f);
        m.translate((left ? -t[3] : t[3]) / 16f, t[4] / 16f, t[5] / 16f);
        m.rotate(new Quaternionf().rotationXYZ(rx * deg, ry * deg, rz * deg));
        m.scale(t[6]);
        m.translate(-0.5f, -0.5f, -0.5f);
        return m;
    }
}
