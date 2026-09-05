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
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Client -> server: toggles whether the sending player has muted one specific conversation (see
 * {@link CrazyPhoneHelper#toggleMutedConversation}) - mirrors {@link CrazyPhoneCallActionMessage}'s shape
 * (a single conversationId payload, server-authoritative). No optimistic client-side echo is needed: the
 * conversation screen's mute icon re-reads {@link CrazyPhoneHelper#isConversationMuted} fresh every frame
 * off the player's own {@link PhoneRegistrySavedData} copy, which {@link #handleToggle} refreshes via
 * {@code syncTo} right after the toggle - same one-round-trip pattern as any other registry mutation here.
 */
public record CrazyPhoneMuteConversationMessage(String conversationId) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneMuteConversationMessage> TYPE = new Type<>(
            Crazyphone.resource("mute_conversation")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneMuteConversationMessage> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneMuteConversationMessage message) -> buffer.writeUtf(message.conversationId),
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneMuteConversationMessage(buffer.readUtf())
            );

    @Override
    public Type<CrazyPhoneMuteConversationMessage> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "mute_conversation");

    public CrazyPhoneMuteConversationMessage(FriendlyByteBuf buffer) {
        this(buffer.readUtf());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneMuteConversationMessage message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                handleToggle(player, message.conversationId);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneMuteConversationMessage message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (context.player().orElse(null) instanceof ServerPlayer player)
                handleToggle(player, message.conversationId);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}

    /**
     * Toggles mute for the sending player, keyed by their OWN phone number - conversationId is
     * client-supplied but only ever used as an opaque key into that player's own mutedConversations list,
     * never to read/write anyone else's data or any conversation content, so no live-membership check is
     * needed here (unlike e.g. CrazyPhoneConversationButtonMessage's addMessage path).
     */
    public static void handleToggle(ServerPlayer player, String conversationId) {
        if (conversationId == null || conversationId.isEmpty())
            return;
        Level world = player.level();
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (ownerNumber.isEmpty())
            return;
        CrazyPhoneHelper.toggleMutedConversation(world, ownerNumber, conversationId);
        PhoneRegistrySavedData.get(world).syncTo(player);
    }

    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneMuteConversationMessage message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handleToggle(context.player(), message.conversationId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneMuteConversationMessage::handleDataFabric);
    }
    *///?}

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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneMuteConversationMessage::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneMuteConversationMessage::new, CrazyPhoneMuteConversationMessage::handleData);
            //?}
        }
    }
    //?}
}
