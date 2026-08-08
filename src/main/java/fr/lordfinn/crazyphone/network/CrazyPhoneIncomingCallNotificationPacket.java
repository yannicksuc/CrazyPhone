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
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Crazyphone;

import java.util.UUID;

/**
 * Server -> one callee: "someone is calling you" - a ring toast, deliberately NOT a forced screen open (the
 * phone might not even be held), so it works exactly like a real ringing phone sitting in a pocket. Joining
 * happens by using the phone (see {@link fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure}), not
 * from this toast. Mirrors {@link CrazyPhoneGroupMembershipNotificationPacket}'s toast/sound template -
 * always sent via a targeted {@code PacketDistributor.sendToPlayer} call, never broadcast.
 */
public record CrazyPhoneIncomingCallNotificationPacket(String conversationId, String callerName, UUID callId) implements CustomPacketPayload {

    public static final Type<CrazyPhoneIncomingCallNotificationPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "incoming_call_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneIncomingCallNotificationPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneIncomingCallNotificationPacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUtf(message.callerName);
                        buffer.writeUUID(message.callId);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneIncomingCallNotificationPacket(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readUUID()
                    )
            );

    @Override
    public Type<CrazyPhoneIncomingCallNotificationPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneIncomingCallNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null)
                return;

            Component toast = Component.literal("📞 ")
                    .append(Component.literal(messagePacket.callerName)
                            .withStyle(style -> style.withBold(true).withColor(0x55FFFF)))
                    .append(Component.literal(" vous appelle")
                            .withStyle(style -> style.withColor(0x00FF55).withItalic(true)));
            mc.player.sendSystemMessage(toast);

            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.note_block.bell"));
            if (sound != null) {
                mc.player.playNotifySound(sound, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneIncomingCallNotificationPacket::handleData);
        }
    }
}
