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
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
@EventBusSubscriber
public record CrazyPhoneNewMessageNotificationPacket(
    CompoundTag messageTag,
    String senderName
) implements CustomPacketPayload {

    public static final Type<CrazyPhoneNewMessageNotificationPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "new_message_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneNewMessageNotificationPacket> STREAM_CODEC =
        StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, CrazyPhoneNewMessageNotificationPacket message) -> {
                buffer.writeNbt(message.messageTag != null ? message.messageTag : new CompoundTag());
                buffer.writeUtf(message.senderName);
            },
            (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneNewMessageNotificationPacket(
                buffer.readNbt(),
                buffer.readUtf()
            )
        );

    @Override
    public Type<CrazyPhoneNewMessageNotificationPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneNewMessageNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                MessageData message = CrazyPhoneHelper.getMessageFromTag(messagePacket.messageTag);
                if (message == null) return;

                // System events (group renamed/icon changed/member excluded/admin reassigned) aren't
                // "a message received from someone" - they get their own in-feed entry, not this toast.
                if (!message.isSystem()) {
                    mc.player.sendSystemMessage(Component.literal("📨 Nouveau message reçu de ")
                        .withStyle(style -> style.withColor(0x55FFFF).withItalic(true))
                        .append(Component.literal(messagePacket.senderName)
                            .withStyle(style -> style.withBold(true).withColor(0x00FF55)))
                    );

                    SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.note_block.pling"));
                    if (sound != null) {
                        mc.player.playNotifySound(sound, SoundSource.PLAYERS, 0.6f, 1.0f);
                    }
                }
                // Mise à jour de l'écran s'il est ouvert
                if (mc.screen instanceof CrazyPhoneConversationScreen screen) {
                    screen.addMessage(
                        messagePacket.senderName,
                        message
                    );
                }
            });
        }
    }

    private static Component readComponent(RegistryFriendlyByteBuf buffer) {
        return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
    }

    private static void writeComponent(RegistryFriendlyByteBuf buffer, Component component) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, component);
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneNewMessageNotificationPacket::handleData);
    }
}
