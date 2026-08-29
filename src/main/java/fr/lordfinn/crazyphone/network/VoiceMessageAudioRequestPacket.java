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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.ConversationSavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
//? if neoforge {
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
//?}
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

import java.util.UUID;

/**
 * Client -> server: "play this voice message for me" - sent ONLY when the recipient clicks the play widget,
 * never proactively (opening the conversation or scrolling a voice message into view only ever carries the
 * lightweight id+duration metadata - see MessageData/CrazyPhoneHelper#getMessageFromTag). Playback itself is
 * server-authoritative and addressed only at the requester (see SvcCallBridge#playAudioToPlayer), so no
 * audio bytes are ever sent back to the client for this - that round trip would be exactly the packet waste
 * this whole lazy-fetch design exists to avoid.
 */
public record VoiceMessageAudioRequestPacket(UUID voiceMessageId, float speed, int startTick) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<VoiceMessageAudioRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("voice_message_audio_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceMessageAudioRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, VoiceMessageAudioRequestPacket message) -> {
                        buffer.writeUUID(message.voiceMessageId);
                        buffer.writeFloat(message.speed);
                        buffer.writeVarInt(message.startTick);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new VoiceMessageAudioRequestPacket(buffer.readUUID(), buffer.readFloat(), buffer.readVarInt())
            );

    @Override
    public Type<VoiceMessageAudioRequestPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("voice_message_audio_request");

    public VoiceMessageAudioRequestPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readFloat(), buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(voiceMessageId);
        buffer.writeFloat(speed);
        buffer.writeVarInt(startTick);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    private static void handle(ServerPlayer player, UUID voiceMessageId, float speedIn, int startTick) {
        if (!VoicechatIntegration.isAvailable())
            return;

        Level world = player.level();
        ConversationSavedData.VoiceAudioEntry entry = ConversationSavedData.get(world).getVoiceAudio(voiceMessageId);
        if (entry == null)
            return;

        // Ownership is checked against the conversationId stored server-side at upload time, never a
        // client-supplied one - same live-membership pattern used by every other conversation packet.
        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (requesterNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, entry.conversationId()).contains(requesterNumber))
            return;

        short[] pcm = bytesToPcm(entry.bytes());
        int startSample = Math.max(0, Math.min(pcm.length, startTick * SAMPLE_RATE / 20));
        short[] fromStart = startSample == 0 ? pcm : java.util.Arrays.copyOfRange(pcm, startSample, pcm.length);
        float speed = speedIn <= 0 ? 1f : speedIn;
        short[] toPlay = speed == 1f ? fromStart : resample(fromStart, speed);

        // Changing speed mid-playback (or replaying from a seek point) always supersedes whatever this
        // player already has playing - without stopping it first the old and new AudioPlayer would
        // overlap and play simultaneously until the old one finishes on its own.
        SvcCallBridge.stopVoiceMessagePlayback(player);
        SvcCallBridge.playAudioToPlayer(player, padToFrameBoundary(toPlay));
    }
    //?}
    //? if fabric {
    /*// SvcCallBridge (server-side audio playback via SVC) isn't ported on Fabric this pass - see
    // build.fabric.gradle.kts's note - so a play request is a deliberate no-op rather than a half-wired call.
    private static void handle(ServerPlayer player, UUID voiceMessageId, float speedIn, int startTick) {
    }
    *///?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final VoiceMessageAudioRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.voiceMessageId, message.speed, message.startTick);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final VoiceMessageAudioRequestPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.voiceMessageId, message.speed, message.startTick);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(VoiceMessageAudioRequestPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.voiceMessageId, message.speed, message.startTick);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, VoiceMessageAudioRequestPacket::handleDataFabric);
    }
    *///?}

    /** Matches VoiceMessageRecorder's own capture rate. */
    private static final int SAMPLE_RATE = 48000;

    private static short[] bytesToPcm(byte[] bytes) {
        short[] samples = new short[bytes.length / 2];
        for (int i = 0; i < samples.length; i++) {
            int lo = bytes[2 * i] & 0xFF;
            int hi = bytes[2 * i + 1];
            samples[i] = (short) ((hi << 8) | lo);
        }
        return samples;
    }

    /** Opus only accepts exact frame lengths (2.5/5/10/20/40/60ms) - 960 samples (20ms @ 48kHz) is the
     * standard frame size these Minecraft voice mods use, matching Minecraft's own 20-tick/second cadence.
     * A recording's total sample count is essentially never an exact multiple of that, so without this the
     * final partial frame handed to the encoder is malformed - which is exactly what produced the white-
     * noise/static playback this fixes: garbage input to Opus encodes as garbage-sounding output, not silence. */
    private static final int OPUS_FRAME_SAMPLES = 960;

    private static short[] padToFrameBoundary(short[] input) {
        int remainder = input.length % OPUS_FRAME_SAMPLES;
        if (remainder == 0)
            return input;
        short[] padded = new short[input.length + (OPUS_FRAME_SAMPLES - remainder)];
        System.arraycopy(input, 0, padded, 0, input.length);
        return padded;
    }

    /** Simple nearest-neighbor resample to change playback speed - changes pitch along with speed (like a
     * tape/vinyl), same tradeoff as most lightweight speed-switch implementations; a proper pitch-preserving
     * time-stretch would need real DSP work disproportionate to this being a bonus feature. */
    private static short[] resample(short[] input, float speed) {
        int outLength = Math.max(1, Math.round(input.length / speed));
        short[] output = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            int sourceIndex = Math.min(input.length - 1, Math.round(i * speed));
            output[i] = input[sourceIndex];
        }
        return output;
    }

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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, VoiceMessageAudioRequestPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, VoiceMessageAudioRequestPacket::new, VoiceMessageAudioRequestPacket::handleData);
            //?}
        }
    }
    //?}
}
