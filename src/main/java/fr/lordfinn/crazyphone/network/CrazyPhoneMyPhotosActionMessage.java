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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Client -> server: acts on the photos selected in the "My Photos" grid (see
 * {@link fr.lordfinn.crazyphone.client.gui.CrazyPhoneMyPhotosScreenScreen}) - Delete, Take into inventory, or
 * Send to a conversation, mirroring this mod's own pre-Camera-mod-removal picture grid's three actions.
 */
public record CrazyPhoneMyPhotosActionMessage(Action action, List<UUID> photoIds, String conversationId) implements CustomPacketPayload {

    public enum Action {DELETE, TAKE, SEND}

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneMyPhotosActionMessage> TYPE = new Type<>(
            Crazyphone.resource("my_photos_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneMyPhotosActionMessage> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneMyPhotosActionMessage message) -> {
                        buffer.writeVarInt(message.action.ordinal());
                        buffer.writeVarInt(message.photoIds.size());
                        for (UUID id : message.photoIds)
                            buffer.writeUUID(id);
                        buffer.writeUtf(message.conversationId);
                    },
                    (RegistryFriendlyByteBuf buffer) -> {
                        Action action = Action.values()[buffer.readVarInt()];
                        int count = buffer.readVarInt();
                        List<UUID> ids = new ArrayList<>(count);
                        for (int i = 0; i < count; i++)
                            ids.add(buffer.readUUID());
                        String conversationId = buffer.readUtf();
                        return new CrazyPhoneMyPhotosActionMessage(action, ids, conversationId);
                    }
            );

    @Override
    public Type<CrazyPhoneMyPhotosActionMessage> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("my_photos_action");

    public CrazyPhoneMyPhotosActionMessage(FriendlyByteBuf buffer) {
        this(Action.values()[buffer.readVarInt()], readIds(buffer), buffer.readUtf());
    }

    private static List<UUID> readIds(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            ids.add(buffer.readUUID());
        return ids;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(action.ordinal());
        buffer.writeVarInt(photoIds.size());
        for (UUID id : photoIds)
            buffer.writeUUID(id);
        buffer.writeUtf(conversationId);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, Action action, List<UUID> photoIds, String conversationId) {
        // Capped by the grid's own visible page in practice, but a modified client could send anything -
        // same defense-in-depth ceiling idea as the upload packets' byte caps.
        if (photoIds.isEmpty() || photoIds.size() > 300)
            return;
        Level world = player.level();
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (senderNumber.isEmpty())
            return;

        switch (action) {
            case DELETE -> PhotoSavedData.get(world).deletePhotos(senderNumber, new HashSet<>(photoIds));
            case TAKE -> {
                for (UUID photoId : photoIds)
                    CrazyPhoneGivePhotoItemPacket.handle(player, photoId);
            }
            case SEND -> {
                if (conversationId.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
                    return;
                PhotoSavedData data = PhotoSavedData.get(world);
                int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
                for (UUID photoId : photoIds) {
                    PhotoSavedData.PhotoEntry entry = data.getPhoto(photoId);
                    if (entry == null || !entry.owner().equals(senderNumber))
                        continue;
                    CrazyPhoneHelper.addImageMessage(world, conversationId, senderNumber, photoId, timestampInMinutes);
                }
            }
        }
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneMyPhotosActionMessage message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.action, message.photoIds, message.conversationId);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneMyPhotosActionMessage message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.action, message.photoIds, message.conversationId);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneMyPhotosActionMessage message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.action, message.photoIds, message.conversationId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneMyPhotosActionMessage::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneMyPhotosActionMessage::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneMyPhotosActionMessage::new, CrazyPhoneMyPhotosActionMessage::handleData);
            //?}
        }
    }
    //?}
}
