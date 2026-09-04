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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client: a batch of photos' PNG bytes (one entry per (photoId, resolution) that was asked for),
 * in response to {@link CrazyPhonePictureRequestPacket}. */
public record CrazyPhonePictureResponsePacket(List<Entry> entries) implements CustomPacketPayload {

    public record Entry(UUID photoId, PhotoResolution resolution, byte[] pngBytes) {
    }

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhonePictureResponsePacket> TYPE = new Type<>(
            Crazyphone.resource("picture_response")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureResponsePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureResponsePacket message) -> {
                        buffer.writeVarInt(message.entries.size());
                        for (Entry entry : message.entries) {
                            buffer.writeUUID(entry.photoId());
                            buffer.writeByte(entry.resolution().ordinal());
                            buffer.writeByteArray(entry.pngBytes());
                        }
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureResponsePacket(readEntries(buffer))
            );

    @Override
    public Type<CrazyPhonePictureResponsePacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("picture_response");

    public CrazyPhonePictureResponsePacket(FriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.photoId());
            buffer.writeByte(entry.resolution().ordinal());
            buffer.writeByteArray(entry.pngBytes());
        }
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if >=1.20.5 {
    /*private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
    *///? } else {
    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
    //?}
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++)
            entries.add(new Entry(buffer.readUUID(), PhotoResolution.values()[buffer.readByte()], buffer.readByteArray()));
        return entries;
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhonePictureResponsePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> FabricPictureCache.onBatchReceived(message.entries));
    }
    *///? } else {
    public static void handleData(final CrazyPhonePictureResponsePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> FabricPictureCache.onBatchReceived(message.entries));
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhonePictureResponsePacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        FabricPictureCache.onBatchReceived(message.entries);
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
