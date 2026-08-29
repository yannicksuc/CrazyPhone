package fr.lordfinn.crazyphone.mixin;

/**
 * NeoForge-only, direct workaround for a real platform gap found on the 1.20.4 NeoForge SDK specifically:
 * CrazyPhonePhotoItem#initializeClient (the standard IClientItemExtensions.getCustomRenderer() hookup, see
 * that class's own doc comment) is a valid, correctly-signature-matched @Override - confirmed via javap
 * against the actual patched Item class in that exact artifact - but is simply never invoked by the
 * platform for this item there (confirmed live: neither it nor getCustomRenderer() ever logged a single
 * line, and CrazyPhonePhotoItemRenderer#render() itself was never reached either, in every display context
 * - hand, GUI, ground, item frame). The exact same code (this is loader-neutral, version-shared with
 * 1.21.1) works correctly on 1.21.1's own NeoForge SDK, so this is a version-specific platform quirk, not a
 * bug in the registration code itself - extensive jar archaeology (javap + grep across the neoforge and
 * fancymodloader 2.0.17 jars) didn't turn up the actual internal caller to fix at the source.
 *
 * Bypasses IClientItemExtensions entirely for this one item: intercepts vanilla's own ItemRenderer#render
 * (the method that would normally decide "custom BEWLR vs normal baked-model quads" per item), for this
 * specific item, and calls straight into the same shared CrazyPhonePhotoItemRenderer#render every other
 * context already uses (Fabric's BuiltinItemRendererRegistry, 1.21.1's own working initializeClient) - then
 * cancels so vanilla's own normal-item path never runs on top.
 *
 * Injects right after the isCustomRenderer() check specifically, NOT at HEAD (tried first) - javap -c on
 * the real patched ItemRenderer confirmed render() runs two things before that check that our own code
 * never replicated: NeoForge's own ClientHooks.handleCameraTransforms(...) (hand/camera positioning setup)
 * and a poseStack.translate(-0.5,-0.5,-0.5) origin-recentering shift the rest of this file's own transforms
 * (starting with its own +0.5,+0.5,+0.5 in render()'s very first line) are authored to build on top of - a
 * HEAD injection skipped both, which is what actually caused every single display context except the one
 * branch that resets the poseStack to a known identity itself (sneak-presenting first-person) to come out
 * visibly offset/oversized, confirmed live. Injecting after the check instead means every one of those
 * transforms runs against the exact same baseline they'd get on every other version/loader.
 */
//? if neoforge && <1.20.5 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.init.ModItems;

@Mixin(ItemRenderer.class)
public abstract class CrazyPhonePhotoItemRenderMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;isCustomRenderer()Z", shift = At.Shift.AFTER), cancellable = true)
    private void crazyphone$renderPhotoDirectly(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
                                                 PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                                 BakedModel model, CallbackInfo ci) {
        if (stack.getItem() == ModItems.CRAZY_PHONE_PHOTO.get()) {
            fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.render(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
            // Balances render()'s own pushPose() near its very top (before this injection point, so this
            // callback never sees it) - cancelling here skips vanilla's matching popPose() too, which would
            // otherwise leak one extra level onto the stack every single time this item is drawn.
            poseStack.popPose();
            ci.cancel();
        }
    }
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhonePhotoItemRenderMixin {
}
*///?}
