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

import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

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

    //? if >=1.20.5 {
    /*public static final Type<ConversationCallActivitySyncPacket> TYPE = new Type<>(
            Crazyphone.resource("conversation_call_activity_sync")
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
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "conversation_call_activity_sync");

    public ConversationCallActivitySyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeBoolean(active);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge && >=1.20.5 {
    /*public static void handleData(final ConversationCallActivitySyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientCallState.onConversationActivityChanged(message.conversationId, message.active))
                .exceptionally(e -> {
                    context.connection().disconnect(Component.literal(e.getMessage()));
                    return null;
                });
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final ConversationCallActivitySyncPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> ClientCallState.onConversationActivityChanged(message.conversationId, message.active))
                .exceptionally(e -> {
                    context.packetHandler().disconnect(Component.literal(e.getMessage()));
                    return null;
                });
    }
    //?}

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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationCallActivitySyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, ConversationCallActivitySyncPacket::new, ConversationCallActivitySyncPacket::handleData);
            //?}
        }
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(ConversationCallActivitySyncPacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        ClientCallState.onConversationActivityChanged(message.conversationId, message.active);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, ConversationCallActivitySyncPacket::handleDataFabric);
    }
    *///?}
}
