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
// Forge 1.20.1: same shape as NeoForge's <1.20.5 branch; PlayPayloadContext is the same-package compat shim.
//? if legacyforge {
/*import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
*///?}

//? if fabric || neoforge {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.video.CallVideoCodec;

import java.util.UUID;

/**
 * Server -> client half of the call camera video (see fr.lordfinn.crazyphone.voicechat.CallVideoRelay):
 * <ul>
 * <li>{@link #CONFIG}: what this server allows. value = flags ({@link #CONFIG_ALLOWED}), value2 = max fps in the
 * high 16 bits, max frame side in pixels in the low 16 bits.</li>
 * <li>{@link #DEMAND}: whether anyone is watching this client's camera right now. value = target fps (0 = stop
 * capturing for others), value2 bit 0 = please send a keyframe next.</li>
 * <li>{@link #FRAME}: one encoded frame from {@code subject}.</li>
 * <li>{@link #STOPPED}: {@code subject} stopped sending (or left the call): drop its picture now.</li>
 * </ul>
 * The receiving side is client-only code, reached by name only from inside the client-side handler.
 */
//? if legacyforge {
/*public record CallVideoS2CPacket(int kind, UUID subject, int value, int value2, byte[] data) {
*///? } else {
public record CallVideoS2CPacket(int kind, UUID subject, int value, int value2, byte[] data) implements CustomPacketPayload {
//?}
    public static final int CONFIG = 0;
    public static final int DEMAND = 1;
    public static final int FRAME = 2;
    public static final int STOPPED = 3;

    public static final int CONFIG_ALLOWED = 1;
    public static final int DEMAND_KEYFRAME = 1;
    public static final UUID NOBODY = new UUID(0L, 0L);

    //? if >=1.20.5 {
    /*public static final Type<CallVideoS2CPacket> TYPE = new Type<>(Crazyphone.resource("call_video_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CallVideoS2CPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CallVideoS2CPacket message) -> message.write(buffer),
                    (RegistryFriendlyByteBuf buffer) -> new CallVideoS2CPacket(buffer)
            );

    @Override
    public Type<CallVideoS2CPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("call_video_s2c");

    //? if fabric || neoforge {
    @Override
    //?}
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    public CallVideoS2CPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(CallVideoCodec.MAX_FRAME_BYTES));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(kind);
        buffer.writeUUID(subject == null ? NOBODY : subject);
        buffer.writeVarInt(value);
        buffer.writeVarInt(value2);
        buffer.writeByteArray(data == null ? new byte[0] : data);
    }

    private void handleOnClient() {
        fr.lordfinn.crazyphone.client.video.CallVideoClient.onServerPacket(kind, subject, value, value2, data);
    }

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CallVideoS2CPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(message::handleOnClient);
    }
    *///? } else {
    public static void handleData(final CallVideoS2CPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(message::handleOnClient);
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CallVideoS2CPacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        message.handleOnClient();
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CallVideoS2CPacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CallVideoS2CPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CallVideoS2CPacket::new, CallVideoS2CPacket::handleData);
            //?}
        }
    }
    //?}

    //? if legacyforge {
    /*@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class CallVideoS2CPacketLegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(CallVideoS2CPacket.class, 61, CallVideoS2CPacket::write, CallVideoS2CPacket::new, CallVideoS2CPacket::handleData);
        }
    }
    *///?}
}
