package fr.lordfinn.crazyphone.network;

//? if neoforge {
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.UUID;

/**
 * Client -> server: "Add to My Photos" from the photo viewer, only ever offered when the viewer was opened
 * from a conversation (a chat bubble) - links this photoId into the requester's own gallery list without
 * creating a physical item, unlike CrazyPhoneGivePhotoItemPacket's "Save to Inventory". Same authorization
 * as that packet (owner or live group member of the photo's stored conversationId).
 *
 * PhotoSavedData#linkPhotoToOwner is already idempotent (a no-op if the requester's own list already has
 * this id), so this button is shown unconditionally rather than needing a separate "is this already in my
 * photos" round trip first - clicking it when the photo is already there simply does nothing extra.
 */
public record CrazyPhoneAddPhotoToMyPhotosPacket(UUID photoId) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneAddPhotoToMyPhotosPacket> TYPE = new Type<>(
            Crazyphone.resource("add_photo_to_my_photos")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneAddPhotoToMyPhotosPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneAddPhotoToMyPhotosPacket message) -> buffer.writeUUID(message.photoId),
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneAddPhotoToMyPhotosPacket(buffer.readUUID())
            );

    @Override
    public Type<CrazyPhoneAddPhotoToMyPhotosPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("add_photo_to_my_photos");

    public CrazyPhoneAddPhotoToMyPhotosPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(photoId);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

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

        PhotoSavedData.get(world).linkPhotoToOwner(requesterNumber, photoId);
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneAddPhotoToMyPhotosPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.photoId);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneAddPhotoToMyPhotosPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.photoId);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneAddPhotoToMyPhotosPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.photoId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneAddPhotoToMyPhotosPacket::handleDataFabric);
    }
    *///?}

    //? if neoforge {
    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneAddPhotoToMyPhotosPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneAddPhotoToMyPhotosPacket::new, CrazyPhoneAddPhotoToMyPhotosPacket::handleData);
            //?}
        }
    }
    //?}
}
