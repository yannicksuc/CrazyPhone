package fr.lordfinn.crazyphone.network;

/**
 * Client -> server: "send me the PNG bytes for this image message" - lazy fetch, sent only when the
 * recipient's client actually needs to render a given image bubble (mirrors
 * VoiceMessageAudioRequestPacket's "only fetch on demand" shape, and CrazyPhoneUploadPicturePacket's own
 * doc comment on why this whole pipeline is Fabric-only). Response: {@link CrazyPhonePictureResponsePacket}.
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
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import java.util.UUID;

public record CrazyPhonePictureRequestPacket(UUID imageId) implements CustomPacketPayload {

    public static final Type<CrazyPhonePictureRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("picture_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureRequestPacket message) -> buffer.writeUUID(message.imageId),
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureRequestPacket(buffer.readUUID())
            );

    @Override
    public Type<CrazyPhonePictureRequestPacket> type() {
        return TYPE;
    }

    private static void handle(ServerPlayer player, UUID imageId) {
        Level world = player.level();
        ConversationSavedData.ImageBytesEntry entry = ConversationSavedData.get(world).getImageBytes(imageId);
        if (entry == null)
            return;

        // Ownership checked the same way as voice audio - against the conversationId stored server-side
        // at upload time, never a client-supplied one, and against LIVE membership so an excluded group
        // member loses read access immediately.
        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (requesterNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, entry.conversationId()).contains(requesterNumber))
            return;

        NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(imageId, entry.bytes()));
    }

    public static void handleDataFabric(CrazyPhonePictureRequestPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.imageId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhonePictureRequestPacket::handleDataFabric);
    }
}
*///?}
