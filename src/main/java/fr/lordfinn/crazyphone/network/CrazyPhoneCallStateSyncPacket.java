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

import java.util.UUID;

/**
 * Server -> one client: this player's own call state just changed. Drives the item's "in call" texture
 * state, the conversation screen's call-icon state, and the Calling screen's auto-transition into the
 * InCall screen the moment the call is answered. Always targeted via {@code PacketDistributor.sendToPlayer}
 * (never broadcast), same as every other packet in this mod.
 */
public record CrazyPhoneCallStateSyncPacket(String conversationId, UUID callId, State state) implements CustomPacketPayload {

    public enum State {
        CALLING, RINGING, ACTIVE, ENDED
    }

    public static final Type<CrazyPhoneCallStateSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "call_state_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneCallStateSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneCallStateSyncPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUUID(message.callId);
                        buffer.writeEnum(message.state);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneCallStateSyncPacket(
                            buffer.readUtf(),
                            buffer.readUUID(),
                            buffer.readEnum(State.class)
                    )
            );

    @Override
    public Type<CrazyPhoneCallStateSyncPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneCallStateSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientCallState.onPacket(message)).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneCallStateSyncPacket::handleData);
        }
    }
}
