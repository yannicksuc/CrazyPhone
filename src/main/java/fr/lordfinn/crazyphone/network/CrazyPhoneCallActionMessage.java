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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.voicechat.CallRegistry;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> server: an action from the conversation screen's call icon or one of the call screens' buttons.
 * There is no meaningful client-side optimistic handling here (unlike the generic button-message packets
 * elsewhere in this mod) - opening a menu is server-authoritative, so every action just waits for the
 * server's response (a {@link CrazyPhoneCallStateSyncPacket} and/or an actual screen open).
 */
public record CrazyPhoneCallActionMessage(int action, String conversationId) implements CustomPacketPayload {
    public static final int START_CALL = 0;
    public static final int HANGUP = 1;
    public static final int OPEN_CALL_SCREEN = 2;
    public static final int ANSWER = 3;

    public static final Type<CrazyPhoneCallActionMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "call_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneCallActionMessage> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneCallActionMessage message) -> {
                        buffer.writeVarInt(message.action);
                        buffer.writeUtf(message.conversationId);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneCallActionMessage(buffer.readVarInt(), buffer.readUtf())
            );

    @Override
    public Type<CrazyPhoneCallActionMessage> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneCallActionMessage message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                handleAction(player, message.action, message.conversationId);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    public static void handleAction(ServerPlayer player, int action, String conversationId) {
        if (!VoicechatIntegration.isAvailable())
            return;
        // Only gated for the two actions that create NEW call involvement - hanging up or reopening a call
        // a player is already on should keep working even if the feature (or their permission for it) gets
        // turned off mid-game, same as any other "let people leave, don't strand them" consideration.
        if ((action == START_CALL || action == ANSWER) && !FeatureFlag.CALLS.isEnabledFor(player))
            return;
        switch (action) {
            case START_CALL -> startCall(player, conversationId);
            case HANGUP -> CallRegistry.leave(player);
            case OPEN_CALL_SCREEN -> ScreenMenuUtils.openCallScreenForPlayer(player);
            case ANSWER -> {
                // The Accept button on the Incoming Call screen - explicit, unlike the old "using the phone
                // while ringing auto-answers" behavior this replaces (see CrazyPhoneOnUseProcedure), so a
                // ringing player gets to actually see who's calling and choose, instead of being connected
                // the instant they touch the phone.
                CallRegistry.answer(player);
                ScreenMenuUtils.openCallScreenForPlayer(player);
            }
            default -> {
            }
        }
    }

    private static void startCall(ServerPlayer player, String conversationId) {
        Level world = player.level();
        // conversationId is client-supplied - without checking the caller is really a live participant
        // (same live-membership check used everywhere else conversation data is touched), a modified
        // client could start a call attached to a conversation it has no business being in.
        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        List<String> memberNumbers = CrazyPhoneHelper.getGroupMembers(world, conversationId);
        if (requesterNumber.isEmpty() || !memberNumbers.contains(requesterNumber))
            return;

        List<ServerPlayer> callees = new ArrayList<>();
        for (String number : memberNumbers) {
            if (number.equals(requesterNumber))
                continue;
            Contact contact = CrazyPhoneHelper.getContact(world, number);
            if (contact == null || contact.getUuid() == null)
                continue;
            ServerPlayer callee = player.getServer().getPlayerList().getPlayer(UUID.fromString(contact.getUuid()));
            if (callee != null)
                callees.add(callee);
        }

        CallRegistry.CallSession session = CallRegistry.startCall(conversationId, player, callees);
        if (session != null)
            ScreenMenuUtils.openCallScreenForPlayer(player);
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneCallActionMessage::handleData);
        }
    }
}
