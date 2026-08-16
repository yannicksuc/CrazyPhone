package fr.lordfinn.crazyphone.network;

/** Server -> client: one image's PNG bytes, in response to {@link CrazyPhonePictureRequestPacket}. */
//? if fabric && >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;

import java.util.UUID;

public record CrazyPhonePictureResponsePacket(UUID imageId, byte[] pngBytes) implements CustomPacketPayload {

    public static final Type<CrazyPhonePictureResponsePacket> TYPE = new Type<>(
            Crazyphone.resource("picture_response")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureResponsePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureResponsePacket message) -> {
                        buffer.writeUUID(message.imageId);
                        buffer.writeByteArray(message.pngBytes);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureResponsePacket(buffer.readUUID(), buffer.readByteArray())
            );

    @Override
    public Type<CrazyPhonePictureResponsePacket> type() {
        return TYPE;
    }

    public static void handleDataFabric(CrazyPhonePictureResponsePacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        FabricPictureCache.onBytesReceived(message.imageId, message.pngBytes);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhonePictureResponsePacket::handleDataFabric);
    }
}
*///?}
