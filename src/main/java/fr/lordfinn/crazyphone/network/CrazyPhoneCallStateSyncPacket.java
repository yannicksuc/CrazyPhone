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

import java.util.List;
import java.util.UUID;

/**
 * Server -> one client: this player's own call state just changed. Drives the item's "in call" texture
 * state, the conversation screen's call-icon state, and the Calling screen's auto-transition into the
 * InCall screen the moment the call is answered. Always targeted via {@code PacketDistributor.sendToPlayer}
 * (never broadcast), same as every other packet in this mod.
 *
 * {@code callNumbers} carries the call's conversation's member phone numbers - a call is one Simple Voice
 * Chat connection per PLAYER, but a phone's number lives in that specific item's own NBT and a player can
 * physically hold several registered phones at once. Without this, the item's "in call" texture (and
 * anything else client-side gating on "is THIS phone in the call") could only check "is this player in a
 * call at all", lighting up every phone the player held rather than just the one actually on the call.
 *
 * {@code participantIds}/{@code participantNames} (parallel lists, always the same length, always excluding
 * the recipient themselves) carry the OTHER players actually on the call right now - the InCall screen's
 * bust-portrait grid reads this. Piggybacking on the packet that's already resent to every affected player on
 * every join/leave/answer (see CallRegistry) means the grid updates live with zero new sync call sites.
 */
public record CrazyPhoneCallStateSyncPacket(String conversationId, UUID callId, State state, List<String> callNumbers,
                                             List<UUID> participantIds, List<String> participantNames) implements CustomPacketPayload {

    public enum State {
        CALLING, RINGING, ACTIVE, ENDED
    }

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneCallStateSyncPacket> TYPE = new Type<>(
            Crazyphone.resource("call_state_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneCallStateSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneCallStateSyncPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUUID(message.callId);
                        buffer.writeEnum(message.state);
                        buffer.writeCollection(message.callNumbers, (buf, number) -> buf.writeUtf(number));
                        buffer.writeCollection(message.participantIds, (buf, id) -> buf.writeUUID(id));
                        buffer.writeCollection(message.participantNames, (buf, name) -> buf.writeUtf(name));
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneCallStateSyncPacket(
                            buffer.readUtf(),
                            buffer.readUUID(),
                            buffer.readEnum(State.class),
                            buffer.readList(buf -> buf.readUtf()),
                            buffer.readList(buf -> buf.readUUID()),
                            buffer.readList(buf -> buf.readUtf())
                    )
            );

    @Override
    public Type<CrazyPhoneCallStateSyncPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "call_state_sync");

    public CrazyPhoneCallStateSyncPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readUtf(),
                buffer.readUUID(),
                buffer.readEnum(State.class),
                buffer.readList(buf -> buf.readUtf()),
                buffer.readList(buf -> buf.readUUID()),
                buffer.readList(buf -> buf.readUtf())
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeUUID(callId);
        buffer.writeEnum(state);
        buffer.writeCollection(callNumbers, (buf, number) -> buf.writeUtf(number));
        buffer.writeCollection(participantIds, (buf, id) -> buf.writeUUID(id));
        buffer.writeCollection(participantNames, (buf, name) -> buf.writeUtf(name));
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge && >=1.20.5 {
    /*public static void handleData(final CrazyPhoneCallStateSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientCallState.onPacket(message)).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final CrazyPhoneCallStateSyncPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> ClientCallState.onPacket(message)).exceptionally(e -> {
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneCallStateSyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneCallStateSyncPacket::new, CrazyPhoneCallStateSyncPacket::handleData);
            //?}
        }
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneCallStateSyncPacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        ClientCallState.onPacket(message);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhoneCallStateSyncPacket::handleDataFabric);
    }
    *///?}
}
