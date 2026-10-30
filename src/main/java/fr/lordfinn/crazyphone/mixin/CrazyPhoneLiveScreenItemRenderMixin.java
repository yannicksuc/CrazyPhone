package fr.lordfinn.crazyphone.mixin;

/**
 * &lt;1.21.10 hook for the live phone screen (client/livescreen): remembers which entity is holding the
 * item being rendered (vanilla's ItemRenderer#renderStatic is the one call that knows it, for first person
 * and third person alike), then draws the screen from inside ItemRenderer#render right before its final
 * popPose, where the pose stack carries exactly the item's display transform in block space, so the screen
 * follows every hand pose, bob, sway and vibration of the phone for free.
 *
 * Every injection is require = 0: if another mod replaces item rendering, the phone simply keeps its
 * static texture instead of failing to load. &gt;=1.21.10 uses LiveScreenItemModel instead and only keeps
 * this inert placeholder (same pattern as CrazyPhonePhotoItemRenderMixin).
 */
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.client.livescreen.LiveScreenRenderer;
import fr.lordfinn.crazyphone.init.ModItems;

@Mixin(ItemRenderer.class)
public abstract class CrazyPhoneLiveScreenItemRenderMixin {
    @Unique
    private LivingEntity crazyphone$liveScreenHolder;

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"), require = 0)
    private void crazyphone$rememberHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                           PoseStack poseStack, MultiBufferSource buffers, Level level, int light, int overlay,
                                           int seed, CallbackInfo ci) {
        crazyphone$liveScreenHolder = entity;
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("RETURN"), require = 0)
    private void crazyphone$forgetHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                         PoseStack poseStack, MultiBufferSource buffers, Level level, int light, int overlay,
                                         int seed, CallbackInfo ci) {
        crazyphone$liveScreenHolder = null;
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"), require = 0)
    private void crazyphone$drawLiveScreen(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                           MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        LivingEntity holder = crazyphone$liveScreenHolder;
        if (holder == null || stack.getItem() != ModItems.CRAZY_PHONE.get())
            return;
        LiveScreenRenderer.renderInBlockSpace(stack, holder, context, poseStack, buffers);
    }
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhoneLiveScreenItemRenderMixin {
}
*///?}
