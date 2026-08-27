package fr.lordfinn.crazyphone.network;

/**
 * Client -> server: uploads a freshly-captured photo's two resolutions (thumbnail + full, both PNG,
 * produced from the same screenshot - see FabricPictureCapture) and immediately posts it as an image
 * message in the given conversation. Fabric-native picture pipeline - there is no NeoForge side to this
 * file at all, unlike every other dual-loader packet in this package: NeoForge's camera feature still goes
 * through Camera mod's own item/upload/disk-cache pipeline (see CrazyPhoneTakePhotoProcedure), so this type
 * is simply never registered or sent there. Mirrors VoiceMessageUploadPacket's shape closely - same
 * "receive bytes, validate live membership, store, append a message" flow, just for PNG bytes ending up
 * directly as an image message instead of PCM ending up as a voice message.
 */
//? if fabric && >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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

public record CrazyPhoneUploadPicturePacket(String conversationId, byte[] thumbnailPng, byte[] fullPng) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    // Generous but real ceilings - the client already downscales/compresses before sending (see
    // FabricPictureCapture), this is defense in depth against a modified client the same way
    // VoiceMessageUploadPacket caps PCM length.
    private static final int THUMBNAIL_MAX_BYTES = 200_000;
    private static final int FULL_MAX_BYTES = 2_000_000;

    public static final Type<CrazyPhoneUploadPicturePacket> TYPE = new Type<>(
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
        if (senderNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
            return;

        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        java.util.UUID photoId = PhotoSavedData.get(world).storePhoto(senderNumber, conversationId, thumbnailPng, fullPng, timestampInMinutes);
        CrazyPhoneHelper.addImageMessage(world, conversationId, senderNumber, photoId, timestampInMinutes);
    }

    public static void handleDataFabric(CrazyPhoneUploadPicturePacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.conversationId, message.thumbnailPng, message.fullPng);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneUploadPicturePacket::handleDataFabric);
    }
}
*///?}
