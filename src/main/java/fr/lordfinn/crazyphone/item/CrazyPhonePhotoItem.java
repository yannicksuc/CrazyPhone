package fr.lordfinn.crazyphone.item;

//? if neoforge && <1.21.10 {
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
//?}

import net.minecraft.world.item.Item;

/**
 * A captured photo, held/given/dropped like any other item. Unlike a map's "flat icon in a slot, only ever
 * rendered live in-hand" split, a photo needs its own image visible everywhere an item can appear (slot,
 * hotbar, hand, ground, item frame) - so this is a genuine custom-rendered item (the shield/banner shape),
 * not a data-component-driven texture swap the way CrazyPhoneItemProperties handles the phone's own states.
 * Actual per-instance image drawing lives in {@link fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer},
 * shared by both loaders - this class only wires that renderer in.
 */
public class CrazyPhonePhotoItem extends Item {
    public CrazyPhonePhotoItem(Properties properties) {
        super(properties);
    }

    //? if fabric && >=1.20.5 {
    /*// Fabric equivalent of the NeoForge branch below - one BuiltinItemRendererRegistry.register call from
    // CrazyphoneFabricClient#onInitializeClient instead of an overridden initializeClient() method.
    public static void registerFabricRenderer() {
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE_PHOTO.get(),
                (stack, displayContext, poseStack, buffer, light, overlay) ->
                        fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.render(stack, poseStack, buffer, light, overlay));
    }

    // Right-click opens the same full-size viewer a chat-bubble click does (see MessageWidget#onImageClick),
    // reading the pointer this specific item instance carries instead of a message's. This class is loaded
    // on BOTH sides (registered from the common CrazyphoneFabric#onInitialize), so it must never reference
    // net.minecraft.client.* types directly - Fabric's dedicated-server classloader rejects that outright,
    // unlike NeoForge's own initializeClient() hook below (which NeoForge's dist-cleaner specially strips).
    // clientViewerOpener is set once from CrazyphoneFabricClient - a class the server never loads at all -
    // so the actual Minecraft/Screen reference only ever lives in that lambda's own compiled bytecode.
    public static java.util.function.Consumer<java.util.UUID> clientViewerOpener = null;

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(
            net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (world.isClientSide() && clientViewerOpener != null) {
            fr.lordfinn.crazyphone.utils.PhotoItemData data = fr.lordfinn.crazyphone.utils.PhotoItemData.fromStack(stack);
            if (data != null)
                clientViewerOpener.accept(data.photoId());
        }
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }
    *///?}
    //? if neoforge && <1.21.10 {
    /*// TODO: 1.21.10 removed BlockEntityWithoutLevelRenderer from net.minecraft.client.renderer entirely
    // (item custom-rendering was reworked into a data-driven "special model" system) - no NeoForge-side
    // photo rendering on that version yet, tracked as a backport follow-up once the rest of this pipeline
    // is proven on 1.21.1/Fabric first (see the implementation plan's rollout order).
    private static net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

    @Override
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    renderer = new net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                        @Override
                        public void renderByItem(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext displayContext,
                                                  com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer,
                                                  int packedLight, int packedOverlay) {
                            fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.render(stack, poseStack, buffer, packedLight, packedOverlay);
                        }
                    };
                }
                return renderer;
            }
        });
    }
    *///?}
}
