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

import net.minecraft.resources.ResourceLocation;
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

/**
 * Server -> client: notifies a single player that their membership in a group conversation just changed -
 * either added (a brand new group, or invited into an existing one) or removed (excluded by the admin;
 * voluntarily leaving doesn't need this, the leaver already knows). Mirrors
 * {@link CrazyPhoneNewMessageNotificationPacket}'s toast/sound so it reads as the same kind of phone
 * notification - always sent via a targeted {@code PacketDistributor.sendToPlayer} call, never broadcast.
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record CrazyPhoneGroupMembershipNotificationPacket(String groupLabel, String actorName, boolean added) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneGroupMembershipNotificationPacket> TYPE = new Type<>(
        Crazyphone.resource("group_membership_notification")
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
    *///? } else {
    public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "group_membership_notification");

    public CrazyPhoneGroupMembershipNotificationPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(groupLabel);
        buffer.writeUtf(actorName);
        buffer.writeBoolean(added);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}

    private static void showToast(CrazyPhoneGroupMembershipNotificationPacket messagePacket) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Component actorName = Component.literal(messagePacket.actorName)
                .withStyle(style -> style.withBold(true).withColor(messagePacket.added ? 0x00FF55 : 0xFF5555));
        Component groupLabel = Component.literal(messagePacket.groupLabel)
                .withStyle(style -> style.withBold(true).withColor(0xFFAA00));
        Component toast = Component.translatable(
                messagePacket.added ? "message.crazyphone.group_added" : "message.crazyphone.group_removed",
                actorName, groupLabel)
            .withStyle(style -> style.withColor(messagePacket.added ? 0x55FFFF : 0xFF5555).withItalic(true));
        //? if <1.21.10 {
        mc.player.sendSystemMessage(toast);
        //? } else {
        /*mc.player.displayClientMessage(toast, false);
        *///?}

        ResourceLocation soundId = messagePacket.added
            ? Crazyphone.parseId("block.note_block.pling")
            : Crazyphone.parseId("entity.villager.no");
        SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, soundId);
        if (sound != null) {
            mc.player.playNotifySound(sound, SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    //? if neoforge && >=1.20.5 {
    /*public static void handleData(final CrazyPhoneGroupMembershipNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> showToast(messagePacket));
        }
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final CrazyPhoneGroupMembershipNotificationPacket messagePacket, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> showToast(messagePacket));
        }
    }
    //?}

    //? if neoforge {
    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        //? if >=1.20.5 {
        /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneGroupMembershipNotificationPacket::handleData);
        *///? } else {
        Crazyphone.addNetworkMessage(ID, CrazyPhoneGroupMembershipNotificationPacket::new, CrazyPhoneGroupMembershipNotificationPacket::handleData);
        //?}
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneGroupMembershipNotificationPacket messagePacket, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        showToast(messagePacket);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhoneGroupMembershipNotificationPacket::handleDataFabric);
    }
    *///?}
}
