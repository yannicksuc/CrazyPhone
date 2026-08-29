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
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

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
    /*public static final Type<CrazyPhoneIncomingCallNotificationPacket> TYPE = new Type<>(
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
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "incoming_call_notification");

    public CrazyPhoneIncomingCallNotificationPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readUUID());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeUtf(callerName);
        buffer.writeUUID(callId);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    // See CrazyPhoneGroupMembershipNotificationPacket's own doc comment on this pattern - nesting
    // Registration alone isn't enough, the risky method itself must live in its own separate class.
    //? if neoforge {
    //? if <1.20.5 {
    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber(value = Dist.CLIENT)
    *///?}
    //?}
    static class ClientHandler {
        static void showToast(CrazyPhoneIncomingCallNotificationPacket messagePacket) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null)
                return;

            Component callerName = Component.literal(messagePacket.callerName)
                    .withStyle(style -> style.withBold(true).withColor(0x55FFFF));
            Component toast = Component.translatable("message.crazyphone.incoming_call_toast", callerName)
                    .withStyle(style -> style.withColor(0x00FF55).withItalic(true));
            //? if <1.21.10 {
            mc.player.sendSystemMessage(toast);
            //? } else {
            /*CrazyPhoneHelper.sendClientMessage(mc.player, toast, false);
            *///?}

            SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("block.note_block.bell"));
            if (sound != null) {
                CrazyPhoneHelper.playNotifySound(mc.player, sound, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
    }

    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneIncomingCallNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientHandler.showToast(messagePacket)).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneIncomingCallNotificationPacket messagePacket, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> ClientHandler.showToast(messagePacket)).exceptionally(e -> {
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneIncomingCallNotificationPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneIncomingCallNotificationPacket::new, CrazyPhoneIncomingCallNotificationPacket::handleData);
            //?}
        }
    }
}
