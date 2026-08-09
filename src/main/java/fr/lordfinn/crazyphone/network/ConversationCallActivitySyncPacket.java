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

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ClientCallState;

/**
 * Server -> every ONLINE member of a conversation (not just the call's own participants/ringers, unlike
 * {@link CrazyPhoneCallStateSyncPacket}): "this conversation's call just started/ended" - lets someone who
 * was never on the call, or who left it earlier, still see (contacts-list badge, conversation screen's call
 * icon) that a call is live there and rejoinable, before they've opened anything. Only fires on the two
 * transitions that actually change this boolean (a brand-new session starting, or the session fully ending)
 * - a participant merely joining/leaving an already-active group call doesn't change conversation-level
 * liveness, so it doesn't need a resend. Always targeted via {@code PacketDistributor.sendToPlayer}, never
 * broadcast, same as every other packet in this mod.
 */
public record ConversationCallActivitySyncPacket(String conversationId, boolean active) implements CustomPacketPayload {

    public static final Type<ConversationCallActivitySyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "conversation_call_activity_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationCallActivitySyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, ConversationCallActivitySyncPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeBoolean(message.active);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new ConversationCallActivitySyncPacket(
                            buffer.readUtf(),
                            buffer.readBoolean()
                    )
            );

    @Override
    public Type<ConversationCallActivitySyncPacket> type() {
        return TYPE;
    }

    public static void handleData(final ConversationCallActivitySyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientCallState.onConversationActivityChanged(message.conversationId, message.active))
                .exceptionally(e -> {
                    context.connection().disconnect(Component.literal(e.getMessage()));
                    return null;
                });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationCallActivitySyncPacket::handleData);
        }
    }
}
