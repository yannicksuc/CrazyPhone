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
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import java.util.UUID;

/** Server -> client: one photo's PNG bytes at one resolution, in response to {@link CrazyPhonePictureRequestPacket}. */
public record CrazyPhonePictureResponsePacket(UUID photoId, PhotoResolution resolution, byte[] pngBytes) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhonePictureResponsePacket> TYPE = new Type<>(
            Crazyphone.resource("picture_response")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureResponsePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureResponsePacket message) -> {
                        buffer.writeUUID(message.photoId);
                        buffer.writeByte(message.resolution.ordinal());
                        buffer.writeByteArray(message.pngBytes);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureResponsePacket(
                            buffer.readUUID(),
                            PhotoResolution.values()[buffer.readByte()],
                            buffer.readByteArray()
                    )
            );

    @Override
    public Type<CrazyPhonePictureResponsePacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("picture_response");

    public CrazyPhonePictureResponsePacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), PhotoResolution.values()[buffer.readByte()], buffer.readByteArray());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(photoId);
        buffer.writeByte(resolution.ordinal());
        buffer.writeByteArray(pngBytes);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhonePictureResponsePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> FabricPictureCache.onBytesReceived(message.photoId, message.resolution, message.pngBytes));
    }
    *///? } else {
    public static void handleData(final CrazyPhonePictureResponsePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> FabricPictureCache.onBytesReceived(message.photoId, message.resolution, message.pngBytes));
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhonePictureResponsePacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        FabricPictureCache.onBytesReceived(message.photoId, message.resolution, message.pngBytes);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhonePictureResponsePacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhonePictureResponsePacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhonePictureResponsePacket::new, CrazyPhonePictureResponsePacket::handleData);
            //?}
        }
    }
    //?}
}
