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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

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
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "group_membership_notification");

    public CrazyPhoneGroupMembershipNotificationPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(groupLabel);
        buffer.writeUtf(actorName);
        buffer.writeBoolean(added);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    // NeoForge 26.x removed @OnlyIn's runtime member-stripping entirely (confirmed via its own
    // OnlyInWarningsHandler log warning) - a method-level annotation no longer keeps a client-only method
    // body's bytecode out of the class the dedicated server loads, so AutomaticEventSubscriber's
    // Class.forName(...) scan of this record (needed unconditionally on both sides, for registerMessage)
    // fully verifies EVERY method on it, including one that touches Minecraft.getInstance() - crashing the
    // dedicated server with NoClassDefFoundError: LocalPlayer. The fix has to be structural: showToast's
    // body now lives in a genuinely separate nested class, carrying its OWN @EventBusSubscriber(Dist.CLIENT)
    // - the same class-level pattern CrazyPhonePhotoItemClientBinding already established - so NeoForge's
    // dist-aware scanner skips loading THIS class entirely on the dedicated server, the same way it already
    // skips that one. See PORTING-26x.md for the full sweep across every packet class with this shape.
    //? if neoforge && <1.20.5 {
    @OnlyIn(Dist.CLIENT)
    //?}
    //? if neoforge && >=1.20.5 <26 {
    /*@OnlyIn(Dist.CLIENT)
    *///?}
    // >=26: @OnlyIn intentionally absent here - it's inert on this version (see the doc comment above) and
    // its mere presence in the jar triggers NeoForge's own OnlyInWarningsHandler at mod-load time, which
    // shows the player a warning popup on every launch for something with zero actual effect.
    static class ClientHandler {
        static void showToast(CrazyPhoneGroupMembershipNotificationPacket messagePacket) {
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
            /*CrazyPhoneHelper.sendClientMessage(mc.player, toast, false);
            *///?}

            /*$ res_loc {*/ResourceLocation/*$}*/ soundId = messagePacket.added
                ? Crazyphone.parseId("block.note_block.pling")
                : Crazyphone.parseId("entity.villager.no");
            SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, soundId);
            if (sound != null) {
                CrazyPhoneHelper.playNotifySound(mc.player, sound, SoundSource.PLAYERS, 0.6f, 1.0f);
            }
        }
    }

    //? if neoforge && >=1.20.5 {
    /*public static void handleData(final CrazyPhoneGroupMembershipNotificationPacket messagePacket, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientHandler.showToast(messagePacket));
        }
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final CrazyPhoneGroupMembershipNotificationPacket messagePacket, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> ClientHandler.showToast(messagePacket));
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
        ClientHandler.showToast(messagePacket);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhoneGroupMembershipNotificationPacket::handleDataFabric);
    }
    *///?}
}
