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
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen;

import java.util.UUID;

/**
 * Server -> one conversation member: a call's real, final duration, sent the moment
 * {@link fr.lordfinn.crazyphone.utils.CrazyPhoneHelper#finalizeCallMessage} runs. Closes a gap the original
 * "no packet needed" design left open: a client who lived through the call themselves freezes its
 * live-ticking chat entry locally the instant they get the call's own ENDED sync, but a bystander merely
 * watching someone else's call in a group conversation was never "live" for it and had no way to learn the
 * real duration except reloading the conversation from scratch - this reaches them (and re-confirms the
 * value for the two actual participants) without needing that. Always targeted, never broadcast, same as
 * every other per-conversation packet in this mod.
 */
public record CrazyPhoneNewCallDurationNotificationPacket(String conversationId, UUID callId, long durationMillis) implements CustomPacketPayload {

    public static final Type<CrazyPhoneNewCallDurationNotificationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "new_call_duration_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneNewCallDurationNotificationPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneNewCallDurationNotificationPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUUID(message.callId);
                        buffer.writeVarLong(message.durationMillis);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneNewCallDurationNotificationPacket(
                            buffer.readUtf(),
                            buffer.readUUID(),
                            buffer.readVarLong()
                    )
            );

    @Override
    public Type<CrazyPhoneNewCallDurationNotificationPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneNewCallDurationNotificationPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CrazyPhoneConversationScreen screen)
                screen.updateCallDuration(message.conversationId, message.callId, message.durationMillis);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneNewCallDurationNotificationPacket::handleData);
        }
    }
}
