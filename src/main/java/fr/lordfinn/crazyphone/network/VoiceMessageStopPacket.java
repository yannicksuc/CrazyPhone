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
// Real, original Forge 1.20.1 - same FMLCommonSetupEvent/EventBusSubscriber/SubscribeEvent shape as
// NeoForge's own <1.20.5 branch above (see NetworkAccess.java's own doc comment) - PlayPayloadContext
// itself is NOT imported here: this file is in the same package as the same-package compat shim
// (fr.lordfinn.crazyphone.network.PlayPayloadContext), so it resolves with no import at all.
//? if legacyforge {
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}

import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
//? if fabric || neoforge {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
//? if neoforge || legacyforge {
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
//?}
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

/**
 * Client -> server: "stop whatever voice message is currently playing for me" - sent when the recipient
 * clicks the pause icon while their own playback is still in progress. No payload needed: only one voice
 * message plays at a time per player (see {@link SvcCallBridge#stopVoiceMessagePlayback}), so there's
 * nothing to identify beyond the requesting player themselves.
 */
//? if legacyforge {
/*public record VoiceMessageStopPacket() {
*///? } else {
public record VoiceMessageStopPacket() implements CustomPacketPayload {
//?}

    //? if >=1.20.5 {
    /*public static final Type<VoiceMessageStopPacket> TYPE = new Type<>(
            Crazyphone.resource("voice_message_stop")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceMessageStopPacket> STREAM_CODEC =
            StreamCodec.unit(new VoiceMessageStopPacket());

    @Override
    public Type<VoiceMessageStopPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("voice_message_stop");

    public VoiceMessageStopPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    //? if fabric || neoforge {
    @Override
    //?}
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge || legacyforge {
    private static void handle(ServerPlayer player) {
        if (!VoicechatIntegration.isAvailable())
            return;
        SvcCallBridge.stopVoiceMessagePlayback(player);
    }
    //?}
    //? if fabric {
    /*// SvcCallBridge isn't ported on Fabric this pass - see build.fabric.gradle.kts's note - so a stop
    // request is a deliberate no-op rather than a half-wired call.
    private static void handle(ServerPlayer player) {
    }
    *///?}

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*public static void handleData(final VoiceMessageStopPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final VoiceMessageStopPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(VoiceMessageStopPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player());
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, VoiceMessageStopPacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, VoiceMessageStopPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, VoiceMessageStopPacket::new, VoiceMessageStopPacket::handleData);
            //?}
        }
    }
    //?}


    //? if legacyforge {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class LegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(VoiceMessageStopPacket.class, VoiceMessageStopPacket::write, VoiceMessageStopPacket::new, VoiceMessageStopPacket::handleData);
        }
    }
    //?}
}