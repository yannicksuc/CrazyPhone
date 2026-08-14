package fr.lordfinn.crazyphone.network;

//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.ConversationSavedData;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.List;

/**
 * Client -> server: "send me a page of this conversation". Sent when a player opens a conversation
 * screen, or scrolls up to load older messages ("skipFromEnd" grows). Replaces the old approach of the
 * client already having every message because it was included in the full-world sync blob.
 */
public record ConversationRequestPacket(String conversationId, int skipFromEnd) implements CustomPacketPayload {
    //? if >=1.20.5 {
    /*public static final Type<ConversationRequestPacket> TYPE = new Type<>(Crazyphone.resource("conversation_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationRequestPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, ConversationRequestPacket message) -> {
                buffer.writeUtf(message.conversationId);
                buffer.writeVarInt(message.skipFromEnd);
            },
            (RegistryFriendlyByteBuf buffer) -> new ConversationRequestPacket(buffer.readUtf(), buffer.readVarInt()));

    @Override
    public Type<ConversationRequestPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "conversation_request");

    public ConversationRequestPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeVarInt(skipFromEnd);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}

    //? if >=1.20.5 {
    /*public static void handleData(final ConversationRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            // Conversation ids are just the participants' numbers sorted and joined, and every phone
            // number is publicly visible via the phone registry sync - without this check any player could
            // request any other conversation id and read its private message history. Checked against the
            // LIVE membership (getGroupMembers), not the numbers baked into the id itself: a group member
            // excluded via the settings screen must lose read access immediately even though the
            // conversationId (and its already-sent history) doesn't change.
            String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
            if (requesterNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(player.level(), message.conversationId).contains(requesterNumber))
                return;
            ConversationSavedData conversations = ConversationSavedData.get(player.level());
            int limit = Config.maxMessagesSentPerRequest;
            List<CompoundTag> page = conversations.getPage(message.conversationId, message.skipFromEnd, limit);
            boolean hasMore = message.skipFromEnd + page.size() < conversations.getMessageCount(message.conversationId);

            ListTag pageTag = new ListTag();
            page.forEach(pageTag::add);

            PacketDistributor.sendToPlayer(player, new ConversationResponsePacket(message.conversationId, message.skipFromEnd, pageTag, hasMore));
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final ConversationRequestPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
            if (requesterNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(player.level(), message.conversationId).contains(requesterNumber))
                return;
            ConversationSavedData conversations = ConversationSavedData.get(player.level());
            int limit = Config.maxMessagesSentPerRequest;
            List<CompoundTag> page = conversations.getPage(message.conversationId, message.skipFromEnd, limit);
            boolean hasMore = message.skipFromEnd + page.size() < conversations.getMessageCount(message.conversationId);

            ListTag pageTag = new ListTag();
            page.forEach(pageTag::add);

            PacketDistributor.PLAYER.with(player).send(new ConversationResponsePacket(message.conversationId, message.skipFromEnd, pageTag, hasMore));
        }).exceptionally(e -> {
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationRequestPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, ConversationRequestPacket::new, ConversationRequestPacket::handleData);
            //?}
        }
    }
}
