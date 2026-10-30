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
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.video.CallVideoCodec;
import fr.lordfinn.crazyphone.voicechat.CallVideoRelay;

/**
 * Client -> server half of the call camera video (see {@link CallVideoRelay} for the whole picture). One generic
 * shape for every message kind, so the feature costs two packet types in total instead of one per message:
 * <ul>
 * <li>{@link #HELLO}: "this client understands camera video", value = protocol version. Only ever sent in answer
 * to the server's own CONFIG, so an older server never receives a packet it does not know.</li>
 * <li>{@link #FRAME}: one encoded frame (see {@link CallVideoCodec}) of the sender's own camera.</li>
 * <li>{@link #PREFS}: the client's own setting, value bit 0 = "show and send camera video".</li>
 * <li>{@link #STOPPED}: the sender stopped capturing (screen closed, camera unavailable...), so receivers can
 * fall back right away instead of waiting for their timeout.</li>
 * </ul>
 * The byte array is capped at decode time ({@link CallVideoCodec#MAX_FRAME_BYTES}), before anything else looks at
 * it: a forged huge length can never make the server allocate more than that.
 */
//? if legacyforge {
/*public record CallVideoC2SPacket(int kind, int value, byte[] data) {
*///? } else {
public record CallVideoC2SPacket(int kind, int value, byte[] data) implements CustomPacketPayload {
//?}
    public static final int HELLO = 0;
    public static final int FRAME = 1;
    public static final int PREFS = 2;
    public static final int STOPPED = 3;

    //? if >=1.20.5 {
    /*public static final Type<CallVideoC2SPacket> TYPE = new Type<>(Crazyphone.resource("call_video_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CallVideoC2SPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CallVideoC2SPacket message) -> message.write(buffer),
                    (RegistryFriendlyByteBuf buffer) -> new CallVideoC2SPacket(buffer)
            );

    @Override
    public Type<CallVideoC2SPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("call_video_c2s");

    //? if fabric || neoforge {
    @Override
    //?}
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    public CallVideoC2SPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CallVideoCodec.MAX_FRAME_BYTES));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(kind);
        buffer.writeVarInt(value);
        buffer.writeByteArray(data == null ? new byte[0] : data);
    }

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CallVideoC2SPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                CallVideoRelay.onClientPacket(player, message.kind, message.value, message.data);
        });
    }
    *///? } else {
    public static void handleData(final CallVideoC2SPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (context.player().orElse(null) instanceof ServerPlayer player)
                CallVideoRelay.onClientPacket(player, message.kind, message.value, message.data);
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CallVideoC2SPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        CallVideoRelay.onClientPacket(context.player(), message.kind, message.value, message.data);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CallVideoC2SPacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CallVideoC2SPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CallVideoC2SPacket::new, CallVideoC2SPacket::handleData);
            //?}
        }
    }
    //?}

    // Ids 60/61 leave room for packets other branches may add after the current last one (35).
    //? if legacyforge {
    /*@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class CallVideoC2SPacketLegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(CallVideoC2SPacket.class, 60, CallVideoC2SPacket::write, CallVideoC2SPacket::new, CallVideoC2SPacket::handleData);
        }
    }
    *///?}
}
