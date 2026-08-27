package fr.lordfinn.crazyphone.network;

/**
 * Client -> server: "Save to Inventory" from the photo viewer - gives the player a physical Photo item
 * pointing at this photoId. Fabric-only for now, same reason as the rest of this pipeline.
 */
//? if fabric && >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.util.UUID;

public record CrazyPhoneGivePhotoItemPacket(UUID photoId) implements CustomPacketPayload {

    public static final Type<CrazyPhoneGivePhotoItemPacket> TYPE = new Type<>(
            Crazyphone.resource("give_photo_item")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneGivePhotoItemPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneGivePhotoItemPacket message) -> buffer.writeUUID(message.photoId),
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneGivePhotoItemPacket(buffer.readUUID())
            );

    @Override
    public Type<CrazyPhoneGivePhotoItemPacket> type() {
        return TYPE;
    }

    private static void handle(ServerPlayer player, UUID photoId) {
        Level world = player.level();
        PhotoSavedData.PhotoEntry entry = PhotoSavedData.get(world).getPhoto(photoId);
        if (entry == null)
            return;

        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        boolean authorized = !requesterNumber.isEmpty() && (requesterNumber.equals(entry.owner())
                || CrazyPhoneHelper.getGroupMembers(world, entry.conversationId()).contains(requesterNumber));
        if (!authorized)
            return;

        ItemStack stack = new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get());
        new PhotoItemData(photoId, entry.owner(), entry.createdMinutes()).writeTo(stack);
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    public static void handleDataFabric(CrazyPhoneGivePhotoItemPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.photoId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneGivePhotoItemPacket::handleDataFabric);
    }
}
*///?}
