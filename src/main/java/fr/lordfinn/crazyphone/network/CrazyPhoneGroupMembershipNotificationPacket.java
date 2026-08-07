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

/**
 * Server -> client: notifies a single player that their membership in a group conversation just changed -
 * either added (a brand new group, or invited into an existing one) or removed (excluded by the admin;
 * voluntarily leaving doesn't need this, the leaver already knows). Mirrors
 * {@link CrazyPhoneNewMessageNotificationPacket}'s toast/sound so it reads as the same kind of phone
 * notification - always sent via a targeted {@code PacketDistributor.sendToPlayer} call, never broadcast.
 */
@EventBusSubscriber
public record CrazyPhoneGroupMembershipNotificationPacket(String groupLabel, String actorName, boolean added) implements CustomPacketPayload {

    public static final Type<CrazyPhoneGroupMembershipNotificationPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "group_membership_notification")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneGroupMembershipNotificationPacket> STREAM_CODEC =
        StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, CrazyPhoneGroupMembershipNotificationPacket message) -> {
                buffer.writeUtf(message.groupLabel);
                buffer.writeUtf(message.actorName);
                buffer.writeBoolean(message.added);
            },
            (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneGroupMembershipNotificationPacket(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readBoolean()
            )
        );

    @Override
    public Type<CrazyPhoneGroupMembershipNotificationPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneGroupMembershipNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;

                Component toast = messagePacket.added
                    ? Component.literal("👥 ")
                        .append(Component.literal(messagePacket.actorName)
                            .withStyle(style -> style.withBold(true).withColor(0x00FF55)))
                        .append(Component.literal(" vous a ajouté au groupe ")
                            .withStyle(style -> style.withColor(0x55FFFF).withItalic(true)))
                        .append(Component.literal(messagePacket.groupLabel)
                            .withStyle(style -> style.withBold(true).withColor(0xFFAA00)))
                    : Component.literal("🚪 ")
                        .append(Component.literal(messagePacket.actorName)
                            .withStyle(style -> style.withBold(true).withColor(0xFF5555)))
                        .append(Component.literal(" vous a retiré du groupe ")
                            .withStyle(style -> style.withColor(0xFF5555).withItalic(true)))
                        .append(Component.literal(messagePacket.groupLabel)
                            .withStyle(style -> style.withBold(true).withColor(0xFFAA00)));
                mc.player.sendSystemMessage(toast);

                ResourceLocation soundId = messagePacket.added
                    ? ResourceLocation.parse("block.note_block.pling")
                    : ResourceLocation.parse("entity.villager.no");
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
                if (sound != null) {
                    mc.player.playNotifySound(sound, SoundSource.PLAYERS, 0.6f, 1.0f);
                }
            });
        }
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneGroupMembershipNotificationPacket::handleData);
    }
}
