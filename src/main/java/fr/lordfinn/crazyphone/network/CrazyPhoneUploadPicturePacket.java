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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Client -> server: uploads a freshly-captured photo's two resolutions (thumbnail + full, both PNG,
 * produced from the same screenshot - see FabricPictureCapture) and immediately posts it as an image
 * message in the given conversation. Mirrors VoiceMessageUploadPacket's shape closely - same "receive
 * bytes, validate live membership, store, append a message" flow, just for PNG bytes ending up directly as
 * an image message instead of PCM ending up as a voice message.
 */
public record CrazyPhoneUploadPicturePacket(String conversationId, byte[] thumbnailPng, byte[] fullPng) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    // Generous but real ceilings - the client already downscales/compresses before sending (see
    // FabricPictureCapture), this is defense in depth against a modified client the same way
    // VoiceMessageUploadPacket caps PCM length.
    private static final int THUMBNAIL_MAX_BYTES = 200_000;
    private static final int FULL_MAX_BYTES = 2_000_000;

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneUploadPicturePacket> TYPE = new Type<>(
            Crazyphone.resource("upload_picture")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneUploadPicturePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneUploadPicturePacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeByteArray(message.thumbnailPng);
                        buffer.writeByteArray(message.fullPng);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneUploadPicturePacket(
                            buffer.readUtf(),
                            buffer.readByteArray(),
                            buffer.readByteArray()
                    )
            );

    @Override
    public Type<CrazyPhoneUploadPicturePacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final ResourceLocation ID = Crazyphone.resource("upload_picture");

    public CrazyPhoneUploadPicturePacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readByteArray(), buffer.readByteArray());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeByteArray(thumbnailPng);
        buffer.writeByteArray(fullPng);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, String conversationId, byte[] thumbnailPng, byte[] fullPng) {
        if (thumbnailPng.length == 0 || thumbnailPng.length > THUMBNAIL_MAX_BYTES) {
            LOGGER.warn("Picture upload rejected: thumbnail {} bytes", thumbnailPng.length);
            return;
        }
        if (fullPng.length == 0 || fullPng.length > FULL_MAX_BYTES) {
            LOGGER.warn("Picture upload rejected: full image {} bytes", fullPng.length);
            return;
        }
        Level world = player.level();
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (senderNumber.isEmpty())
            return;

        // Empty conversationId means a standalone shot (taken via the home screen's Photo icon or the
        // punch-to-shoot shortcut, neither of which has a target conversation) - saved to the phone's own
        // photo list only, never posted as a message anywhere. A real conversationId still needs the usual
        // live-membership check.
        boolean standalone = conversationId.isEmpty();
        if (!standalone && !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
            return;

        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        UUID photoId = PhotoSavedData.get(world).storePhoto(senderNumber, conversationId, thumbnailPng, fullPng, timestampInMinutes);
        if (!standalone)
            CrazyPhoneHelper.addImageMessage(world, conversationId, senderNumber, photoId, timestampInMinutes);
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneUploadPicturePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.thumbnailPng, message.fullPng);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneUploadPicturePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.thumbnailPng, message.fullPng);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneUploadPicturePacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.conversationId, message.thumbnailPng, message.fullPng);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneUploadPicturePacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneUploadPicturePacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneUploadPicturePacket::new, CrazyPhoneUploadPicturePacket::handleData);
            //?}
        }
    }
    //?}
}
