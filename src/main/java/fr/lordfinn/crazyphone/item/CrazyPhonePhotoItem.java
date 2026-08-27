package fr.lordfinn.crazyphone.item;

//? if neoforge && <1.21.10 {
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
//?}

import net.minecraft.world.InteractionHand;
//? if <1.21.10 {
import net.minecraft.world.InteractionResultHolder;
//?} else {
/*import net.minecraft.world.InteractionResult;
*///?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * A captured photo, held/given/dropped like any other item. Unlike a map's "flat icon in a slot, only ever
 * rendered live in-hand" split, a photo needs its own image visible everywhere an item can appear (slot,
 * hotbar, hand, ground, item frame) - so this is a genuine custom-rendered item (the shield/banner shape),
 * not a data-component-driven texture swap the way CrazyPhoneItemProperties handles the phone's own states.
 * Actual per-instance image drawing lives in {@link fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer},
 * shared by both loaders - this class only wires that renderer in.
 */
public class CrazyPhonePhotoItem extends Item {
    // Right-click opens the full-size viewer, reading the pointer this specific item instance carries
    // (see PhotoItemData) instead of a message's - same screen a chat-bubble click opens
    // (MessageWidget#onImageClick). This class is common-loaded on BOTH sides (registered from
    // CrazyphoneFabric#onInitialize / ModItems' DeferredRegister), so it must never reference
    // net.minecraft.client.* types directly anywhere in ITS OWN bytecode - both Fabric's dedicated-server
    // classloader AND NeoForge's own dist transformer reject that outright once the class is actually
    // loaded (confirmed the hard way twice: once for use() directly opening a Screen, once for a
    // client-setup callback living on this same class - see CrazyPhonePhotoItemClientBinding, which now
    // owns that wiring instead, since IT is never instantiated from common code the way this Item is).
    // clientViewerOpener is set once from each loader's own client-only entrypoint - a class the dedicated
    // server never loads at all - so the actual Minecraft/Screen reference only ever lives in that lambda's
    // own compiled bytecode, never in this class's.
    public static Consumer<UUID> clientViewerOpener = null;

    public CrazyPhonePhotoItem(Properties properties) {
        super(properties);
    }

    //? if <1.21.10 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        openViewerOnClient(world, stack);
        return InteractionResultHolder.success(stack);
    }
    //?} else {
    /*@Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        openViewerOnClient(world, stack);
        return InteractionResult.SUCCESS;
    }
    *///?}

    private void openViewerOnClient(Level world, ItemStack stack) {
        if (world.isClientSide() && clientViewerOpener != null) {
            PhotoItemData data = PhotoItemData.fromStack(stack);
            if (data != null)
                clientViewerOpener.accept(data.photoId());
        }
    }

    //? if fabric && >=1.20.5 {
    /*// Fabric equivalent of the NeoForge renderer branch below - one BuiltinItemRendererRegistry.register
    // call from CrazyphoneFabricClient#onInitializeClient instead of an overridden initializeClient() method.
    public static void registerFabricRenderer() {
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE_PHOTO.get(),
                (stack, displayContext, poseStack, buffer, light, overlay) ->
                        fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.render(stack, poseStack, buffer, light, overlay));
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
