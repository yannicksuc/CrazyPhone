package fr.lordfinn.crazyphone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Crazyphone;

/**
 * Draws a flat, single-quad "photo" facing the viewer, in the same local unit-cube space vanilla's own
 * generated 2D item models occupy - so a Photo item sits at the same visual scale as any other item in a
 * slot/hotbar/hand/on the ground, without needing per-context scale tweaks. Loader-neutral: NeoForge's
 * {@code IClientItemExtensions.getCustomRenderer()} and Fabric's {@code BuiltinItemRendererRegistry} both
 * call straight into this one method, so there is exactly one place that ever emits these vertices.
 *
 * Currently draws a fixed placeholder texture - wiring this up to the real per-instance captured photo
 * (via PictureTextureCache, once that exists) is a follow-up step, not yet done.
 */
public final class CrazyPhonePhotoItemRenderer {
    private static final ResourceLocation PLACEHOLDER_TEXTURE = Crazyphone.parseId("crazyphone:textures/item/crazy_phone_photo_placeholder.png");

    private CrazyPhonePhotoItemRenderer() {
    }

    public static void render(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        // Centered on the unit cube's middle, flat on the Z=0.5 plane - a thin card facing +Z/-Z.
        poseStack.translate(0.5, 0.5, 0.5);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutout(PLACEHOLDER_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float half = 0.5f;

        // Front face (+Z), visible from the default item-display camera angle.
        quad(buffer, pose, packedLight, packedOverlay,
                -half, half, 0, 0, 0,
                -half, -half, 0, 0, 1,
                half, -half, 0, 1, 1,
                half, half, 0, 1, 0,
                0, 0, 1);
        // Back face (-Z), same image mirrored, so the card isn't invisible from behind.
        quad(buffer, pose, packedLight, packedOverlay,
                half, half, 0, 0, 0,
                half, -half, 0, 0, 1,
                -half, -half, 0, 1, 1,
                -half, half, 0, 1, 0,
                0, 0, -1);

        poseStack.popPose();
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
