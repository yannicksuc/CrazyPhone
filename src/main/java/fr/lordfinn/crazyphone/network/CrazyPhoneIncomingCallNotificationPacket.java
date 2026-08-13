package fr.lordfinn.crazyphone.network;

//? if >=1.20.5 {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//? } else {
/*import net.neoforged.neoforge.network.handling.PlayPayloadContext;
*///?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
//? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
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

    //? if >=1.20.5 {
    public static final Type<CrazyPhoneIncomingCallNotificationPacket> TYPE = new Type<>(
            Crazyphone.resource("incoming_call_notification")
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
    //? } else {
    /*public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "incoming_call_notification");

    public CrazyPhoneIncomingCallNotificationPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readUUID());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeUtf(callerName);
        buffer.writeUUID(callId);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    *///?}

    private static void showToast(CrazyPhoneIncomingCallNotificationPacket messagePacket) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        Component toast = Component.literal("📞 ")
                .append(Component.literal(messagePacket.callerName)
                        .withStyle(style -> style.withBold(true).withColor(0x55FFFF)))
                .append(Component.literal(" vous appelle")
                        .withStyle(style -> style.withColor(0x00FF55).withItalic(true)));
        mc.player.sendSystemMessage(toast);

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(Crazyphone.parseId("block.note_block.bell"));
        if (sound != null) {
            mc.player.playNotifySound(sound, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    //? if >=1.20.5 {
    public static void handleData(final CrazyPhoneIncomingCallNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> showToast(messagePacket)).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //? } else {
    /*public static void handleData(final CrazyPhoneIncomingCallNotificationPacket messagePacket, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> showToast(messagePacket)).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///?}

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneIncomingCallNotificationPacket::handleData);
            //? } else {
            /*Crazyphone.addNetworkMessage(ID, CrazyPhoneIncomingCallNotificationPacket::new, CrazyPhoneIncomingCallNotificationPacket::handleData);
            *///?}
        }
    }
}
