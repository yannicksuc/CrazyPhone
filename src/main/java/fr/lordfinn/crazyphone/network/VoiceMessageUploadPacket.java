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

import net.minecraft.resources.ResourceLocation;
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

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Client -> server: sends a finished voice-message recording once, when the player clicks Send. This is
 * the one point where the audio DOES travel over the network eagerly - unavoidably, since the recording
 * only exists on the recorder's own client. Everyone else only ever gets it via the lazy per-listener fetch
 * ({@link VoiceMessageAudioRequestPacket}), never from this upload.
 */
public record VoiceMessageUploadPacket(String conversationId, UUID voiceId, byte[] audioPcm, int durationTicks, byte[] envelope) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

    /** Matches VoiceMessageRecorder's own capture rate. */
    private static final int SAMPLE_RATE = 48000;

    //? if >=1.20.5 {
    /*public static final Type<VoiceMessageUploadPacket> TYPE = new Type<>(
            Crazyphone.resource("voice_message_upload")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VoiceMessageUploadPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, VoiceMessageUploadPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUUID(message.voiceId);
                        buffer.writeByteArray(message.audioPcm);
                        buffer.writeVarInt(message.durationTicks);
                        buffer.writeByteArray(message.envelope);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new VoiceMessageUploadPacket(
                            buffer.readUtf(),
                            buffer.readUUID(),
                            buffer.readByteArray(),
                            buffer.readVarInt(),
                            buffer.readByteArray()
                    )
            );

    @Override
    public Type<VoiceMessageUploadPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final ResourceLocation ID = Crazyphone.resource("voice_message_upload");

    public VoiceMessageUploadPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readUtf(),
                buffer.readUUID(),
                buffer.readByteArray(),
                buffer.readVarInt(),
                buffer.readByteArray()
        );
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeUUID(voiceId);
        buffer.writeByteArray(audioPcm);
        buffer.writeVarInt(durationTicks);
        buffer.writeByteArray(envelope);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, String conversationId, UUID voiceId, byte[] audioPcm, int durationTicks, byte[] envelope) {
        if (!VoicechatIntegration.isAvailable())
            return;
        if (!FeatureFlag.VOICE_MESSAGES.isEnabledFor(player))
            return;
        if (audioPcm.length == 0)
            return;

        // Defense in depth: the client already auto-stops a recording at maxVoiceMessageRecordingSeconds
        // (CrazyPhoneConversationScreen's render loop), but a modified client could skip that and upload
        // an arbitrarily long clip - reject anything meaningfully over the cap here too. A few seconds
        // of slack accounts for the client's own check being per-frame, not sample-exact.
        long maxSamples = (long) (Config.maxVoiceMessageRecordingSeconds + 2) * SAMPLE_RATE;
        if (audioPcm.length / 2L > maxSamples) {
            LOGGER.warn("Voice message upload rejected: {} samples exceeds the {}s cap", audioPcm.length / 2, Config.maxVoiceMessageRecordingSeconds);
            return;
        }

        Level world = player.level();
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (senderNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
            return;

        LOGGER.info("Voice message upload: {} bytes ({} samples), {}", audioPcm.length,
                audioPcm.length / 2, describeAmplitude(audioPcm));

        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        CrazyPhoneHelper.addVoiceMessage(world, conversationId, senderNumber, voiceId, audioPcm,
                durationTicks, envelope, timestampInMinutes);
    }

    //? if >=1.20.5 {
    /*public static void handleData(final VoiceMessageUploadPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.voiceId, message.audioPcm, message.durationTicks, message.envelope);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final VoiceMessageUploadPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.voiceId, message.audioPcm, message.durationTicks, message.envelope);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}

    /** min/max/first-few sample values - a quick sanity check that captured audio looks like real speech
     * (varying values in a plausible range) rather than silence (all ~0) or garbage (implausible values). */
    private static String describeAmplitude(byte[] pcm) {
        if (pcm.length < 2)
            return "no samples";
        short min = Short.MAX_VALUE, max = Short.MIN_VALUE;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            short sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            if (sample < min) min = sample;
            if (sample > max) max = sample;
        }
        return "amplitude range [" + min + ", " + max + "]";
    }

    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, VoiceMessageUploadPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, VoiceMessageUploadPacket::new, VoiceMessageUploadPacket::handleData);
            //?}
        }
    }
}
