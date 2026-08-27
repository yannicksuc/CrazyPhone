package fr.lordfinn.crazyphone.network;

/**
 * Client -> server: "send me the PNG bytes for this photo, at this resolution" - lazy fetch, sent only when
 * the recipient's client actually needs to render a given image bubble or open the full-size viewer
 * (mirrors VoiceMessageAudioRequestPacket's "only fetch on demand" shape). Response:
 * {@link CrazyPhonePictureResponsePacket}.
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
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import java.util.UUID;

public record CrazyPhonePictureRequestPacket(UUID photoId, PhotoResolution resolution) implements CustomPacketPayload {

    public static final Type<CrazyPhonePictureRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("picture_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureRequestPacket message) -> {
                        buffer.writeUUID(message.photoId);
                        buffer.writeByte(message.resolution.ordinal());
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureRequestPacket(
                            buffer.readUUID(),
                            PhotoResolution.values()[buffer.readByte()]
                    )
            );

    @Override
    public Type<CrazyPhonePictureRequestPacket> type() {
        return TYPE;
    }

    private static void handle(ServerPlayer player, UUID photoId, PhotoResolution resolution) {
        Level world = player.level();
        PhotoSavedData.PhotoEntry entry = PhotoSavedData.get(world).getPhoto(photoId);
        if (entry == null)
            return;

        // The owner can always fetch their own photo (e.g. opening the full-size viewer from the held item,
        // long after the conversation it was first sent in may have scrolled the message out of view) -
        // otherwise the same LIVE group-membership check every other conversation payload uses, checked
        // against the conversationId stored server-side at upload time, never a client-supplied one.
        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        boolean authorized = !requesterNumber.isEmpty() && (requesterNumber.equals(entry.owner())
                || CrazyPhoneHelper.getGroupMembers(world, entry.conversationId()).contains(requesterNumber));
        if (!authorized)
            return;

        byte[] bytes = resolution == PhotoResolution.THUMBNAIL ? entry.thumbnail() : entry.full();
        NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(photoId, resolution, bytes));
    }

    public static void handleDataFabric(CrazyPhonePictureRequestPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.photoId, message.resolution);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhonePictureRequestPacket::handleDataFabric);
    }
}
*///?}
