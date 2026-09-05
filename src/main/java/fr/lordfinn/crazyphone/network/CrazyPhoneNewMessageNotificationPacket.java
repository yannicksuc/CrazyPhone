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
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen;
import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Server -> client: notifies a single participant of a conversation that a new message arrived. This
 * packet is ALWAYS sent via a targeted {@code PacketDistributor.sendToPlayer} call from
 * {@link CrazyPhoneHelper#notifyContacts} to just the receiving player - it must never be broadcast to
 * every online player, unlike the old mod's full MapVariables sync that used to ship every conversation's
 * entire history to everyone on every message.
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record CrazyPhoneNewMessageNotificationPacket(
    CompoundTag messageTag,
    String senderName,
    // Computed server-side, per receiver, from CrazyPhoneHelper#isConversationMuted (see notifyContacts/
    // notifySystemMessage) - true suppresses just the sound/toast below for THIS specific receiver; the
    // unread-notification badge (a separate mechanism, see addNotificationBadge) is never touched by this.
    boolean muted
) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneNewMessageNotificationPacket> TYPE = new Type<>(
        Crazyphone.resource("new_message_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneNewMessageNotificationPacket> STREAM_CODEC =
        StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, CrazyPhoneNewMessageNotificationPacket message) -> {
                buffer.writeNbt(message.messageTag != null ? message.messageTag : new CompoundTag());
                buffer.writeUtf(message.senderName);
                buffer.writeBoolean(message.muted);
            },
            (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneNewMessageNotificationPacket(
                buffer.readNbt(),
                buffer.readUtf(),
                buffer.readBoolean()
            )
        );

    @Override
    public Type<CrazyPhoneNewMessageNotificationPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "new_message_notification");

    public CrazyPhoneNewMessageNotificationPacket(FriendlyByteBuf buffer) {
        this(buffer.readNbt(), buffer.readUtf(), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeNbt(messageTag != null ? messageTag : new CompoundTag());
        buffer.writeUtf(senderName);
        buffer.writeBoolean(muted);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    // See CrazyPhoneGroupMembershipNotificationPacket's own doc comment on this pattern - NeoForge 26.x
    // removed @OnlyIn's runtime stripping, so a genuinely separate, class-level-@EventBusSubscriber(Dist.
    // CLIENT)-annotated nested class is what keeps AutomaticEventSubscriber's dedicated-server scan from
    // ever loading this method's Minecraft.getInstance() reference at all.
    //? if neoforge && <1.20.5 {
    @OnlyIn(Dist.CLIENT)
    //?}
    //? if neoforge && >=1.20.5 <26 {
    /*@OnlyIn(Dist.CLIENT)
    *///?}
    // >=26: @OnlyIn intentionally absent - inert on this version (NeoForge dropped its runtime member
    // stripping there), and its mere presence in the jar triggers NeoForge's own OnlyInWarningsHandler at
    // mod-load time, popping a warning window for the player at every launch for zero actual effect.
    static class ClientHandler {
        static void applyNotification(CrazyPhoneNewMessageNotificationPacket messagePacket) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            MessageData message = CrazyPhoneHelper.getMessageFromTag(messagePacket.messageTag);
            if (message == null) return;

            // System events (group renamed/icon changed/member excluded/admin reassigned) aren't
            // "a message received from someone" - they get their own in-feed entry, not this toast.
            // messagePacket.muted() is the receiving player's own per-conversation mute state, computed
            // server-side (see CrazyPhoneHelper#notifyContacts/#notifySystemMessage) - the unread-notification
            // badge is a separate mechanism (addNotificationBadge) and keeps working regardless of this.
            if (!message.isSystem() && !messagePacket.muted()) {
                Component senderName = Component.literal(messagePacket.senderName)
                        .withStyle(style -> style.withBold(true).withColor(0x00FF55));
                Component notifText = Component.translatable("message.crazyphone.new_message_received", senderName)
                    .withStyle(style -> style.withColor(0x55FFFF).withItalic(true));
                //? if <1.21.10 {
                mc.player.sendSystemMessage(notifText);
                //? } else {
                /*CrazyPhoneHelper.sendClientMessage(mc.player, notifText, false);
                *///?}

                SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("block.note_block.pling"));
                if (sound != null) {
                    CrazyPhoneHelper.playNotifySound(mc.player, sound, SoundSource.PLAYERS, 0.6f, 1.0f);
                }
            }
            // Mise a jour de l'ecran s'il est ouvert
            if (mc./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhoneConversationScreen screen) {
                screen.addMessage(
                    messagePacket.senderName,
                    message
                );
            }
        }
    }

    //? if neoforge && >=1.20.5 {
    /*public static void handleData(final CrazyPhoneNewMessageNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientHandler.applyNotification(messagePacket));
        }
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final CrazyPhoneNewMessageNotificationPacket messagePacket, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> ClientHandler.applyNotification(messagePacket));
        }
    }
    //?}

    //? if neoforge {
    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        //? if >=1.20.5 {
        /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneNewMessageNotificationPacket::handleData);
        *///? } else {
        Crazyphone.addNetworkMessage(ID, CrazyPhoneNewMessageNotificationPacket::new, CrazyPhoneNewMessageNotificationPacket::handleData);
        //?}
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneNewMessageNotificationPacket messagePacket, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        ClientHandler.applyNotification(messagePacket);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhoneNewMessageNotificationPacket::handleDataFabric);
    }
    *///?}
}
