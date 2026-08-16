package fr.lordfinn.crazyphone.network;

/**
 * Client -> server: uploads a captured photo's PNG bytes and immediately posts it as an image message in
 * the given conversation. Fabric-native picture pipeline (task #165) - there is no NeoForge side to this
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
import fr.lordfinn.crazyphone.data.ConversationSavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

public record CrazyPhoneUploadPicturePacket(String conversationId, byte[] pngBytes) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    // A generous but real ceiling - the client already downscales/compresses before sending (see
    // FabricPictureCapture), this is defense in depth against a modified client the same way
    // VoiceMessageUploadPacket caps PCM length.
    private static final int MAX_PNG_BYTES = 1_500_000;

    public static final Type<CrazyPhoneUploadPicturePacket> TYPE = new Type<>(
            Crazyphone.resource("upload_picture")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneUploadPicturePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneUploadPicturePacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeByteArray(message.pngBytes);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneUploadPicturePacket(
                            buffer.readUtf(),
                            buffer.readByteArray()
                    )
            );

    @Override
    public Type<CrazyPhoneUploadPicturePacket> type() {
        return TYPE;
    }

    private static void handle(ServerPlayer player, String conversationId, byte[] pngBytes) {
        if (pngBytes.length == 0 || pngBytes.length > MAX_PNG_BYTES) {
            LOGGER.warn("Picture upload rejected: {} bytes", pngBytes.length);
            return;
        }
        Level world = player.level();
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (senderNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
            return;

        UUID imageId = UUID.randomUUID();
        ConversationSavedData.get(world).storeImageBytes(imageId, conversationId, pngBytes);
        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        CrazyPhoneHelper.addImageMessage(world, conversationId, senderNumber, imageId, timestampInMinutes);
    }

    public static void handleDataFabric(CrazyPhoneUploadPicturePacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.conversationId, message.pngBytes);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneUploadPicturePacket::handleDataFabric);
    }
}
*///?}
