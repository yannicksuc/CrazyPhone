package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

/**
 * Client -> server: "stop whatever voice message is currently playing for me" - sent when the recipient
 * clicks the pause icon while their own playback is still in progress. No payload needed: only one voice
 * message plays at a time per player (see {@link SvcCallBridge#stopVoiceMessagePlayback}), so there's
 * nothing to identify beyond the requesting player themselves.
 */
public record VoiceMessageStopPacket() implements CustomPacketPayload {

    public static final Type<VoiceMessageStopPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "voice_message_stop")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceMessageStopPacket> STREAM_CODEC =
            StreamCodec.unit(new VoiceMessageStopPacket());

    @Override
    public Type<VoiceMessageStopPacket> type() {
        return TYPE;
    }

    public static void handleData(final VoiceMessageStopPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !VoicechatIntegration.isAvailable())
                return;
            SvcCallBridge.stopVoiceMessagePlayback(player);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, VoiceMessageStopPacket::handleData);
        }
    }
}
