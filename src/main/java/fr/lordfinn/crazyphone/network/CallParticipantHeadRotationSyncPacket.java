package fr.lordfinn.crazyphone.network;

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
 * Server -> one participant, sent a few times a second while a call is active (see
 * fr.lordfinn.crazyphone.voicechat.CallHeadRotationSync): every OTHER participant's current live pose state -
 * head-vs-body yaw deviation, pitch, {@link net.minecraft.world.entity.Pose}, sneak/sprint/swim flags, and a
 * walk-animation speed input - so the InCall screen's bust portraits mirror what the real player is actually
 * doing (sneaking, swimming, running...) in real time instead of standing frozen. Deliberately NOT the raw
 * head/body yaw - the fake preview entity's body is fixed facing the camera (a deliberate design choice, not
 * a bug), so only the DEVIATION between the real player's head and body yaw is meaningful to reapply on top
 * of that fixed body facing; sending raw yaw would need the client to also track the real body yaw just to
 * re-derive this same value.
 */
public record CallParticipantHeadRotationSyncPacket(String conversationId, List<UUID> playerIds,
                                                      List<Float> headYawDeltas, List<Float> pitches,
                                                      List<Integer> poseOrdinals, List<Boolean> crouching,
                                                      List<Boolean> sprinting, List<Boolean> swimming,
                                                      List<Float> walkAnimationSpeeds) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CallParticipantHeadRotationSyncPacket> TYPE = new Type<>(
            Crazyphone.resource("call_participant_head_rotation_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CallParticipantHeadRotationSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CallParticipantHeadRotationSyncPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeCollection(message.playerIds, (buf, id) -> buf.writeUUID(id));
                        buffer.writeCollection(message.headYawDeltas, (buf, v) -> buf.writeFloat(v));
                        buffer.writeCollection(message.pitches, (buf, v) -> buf.writeFloat(v));
                        buffer.writeCollection(message.poseOrdinals, (buf, v) -> buf.writeVarInt(v));
                        buffer.writeCollection(message.crouching, (buf, v) -> buf.writeBoolean(v));
                        buffer.writeCollection(message.sprinting, (buf, v) -> buf.writeBoolean(v));
                        buffer.writeCollection(message.swimming, (buf, v) -> buf.writeBoolean(v));
                        buffer.writeCollection(message.walkAnimationSpeeds, (buf, v) -> buf.writeFloat(v));
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CallParticipantHeadRotationSyncPacket(
                            buffer.readUtf(),
                            buffer.readList(buf -> buf.readUUID()),
                            buffer.readList(buf -> buf.readFloat()),
                            buffer.readList(buf -> buf.readFloat()),
                            buffer.readList(buf -> buf.readVarInt()),
                            buffer.readList(buf -> buf.readBoolean()),
                            buffer.readList(buf -> buf.readBoolean()),
                            buffer.readList(buf -> buf.readBoolean()),
                            buffer.readList(buf -> buf.readFloat())
                    )
            );

    @Override
    public Type<CallParticipantHeadRotationSyncPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "call_participant_head_rotation_sync");

    public CallParticipantHeadRotationSyncPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readUtf(),
                buffer.readList(buf -> buf.readUUID()),
                buffer.readList(buf -> buf.readFloat()),
                buffer.readList(buf -> buf.readFloat()),
                buffer.readList(buf -> buf.readVarInt()),
                buffer.readList(buf -> buf.readBoolean()),
                buffer.readList(buf -> buf.readBoolean()),
                buffer.readList(buf -> buf.readBoolean()),
                buffer.readList(buf -> buf.readFloat())
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeCollection(playerIds, (buf, id) -> buf.writeUUID(id));
        buffer.writeCollection(headYawDeltas, (buf, v) -> buf.writeFloat(v));
        buffer.writeCollection(pitches, (buf, v) -> buf.writeFloat(v));
        buffer.writeCollection(poseOrdinals, (buf, v) -> buf.writeVarInt(v));
        buffer.writeCollection(crouching, (buf, v) -> buf.writeBoolean(v));
        buffer.writeCollection(sprinting, (buf, v) -> buf.writeBoolean(v));
        buffer.writeCollection(swimming, (buf, v) -> buf.writeBoolean(v));
        buffer.writeCollection(walkAnimationSpeeds, (buf, v) -> buf.writeFloat(v));
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if >=1.20.5 {
    /*public static void handleData(final CallParticipantHeadRotationSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> {
            for (int i = 0; i < message.playerIds.size(); i++)
                ClientCallState.setLiveState(message.playerIds.get(i), message.headYawDeltas.get(i), message.pitches.get(i),
                        message.poseOrdinals.get(i), message.crouching.get(i), message.sprinting.get(i),
                        message.swimming.get(i), message.walkAnimationSpeeds.get(i));
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CallParticipantHeadRotationSyncPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            for (int i = 0; i < message.playerIds.size(); i++)
                ClientCallState.setLiveState(message.playerIds.get(i), message.headYawDeltas.get(i), message.pitches.get(i),
                        message.poseOrdinals.get(i), message.crouching.get(i), message.sprinting.get(i),
                        message.swimming.get(i), message.walkAnimationSpeeds.get(i));
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}

    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CallParticipantHeadRotationSyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CallParticipantHeadRotationSyncPacket::new, CallParticipantHeadRotationSyncPacket::handleData);
            //?}
        }
    }
}
