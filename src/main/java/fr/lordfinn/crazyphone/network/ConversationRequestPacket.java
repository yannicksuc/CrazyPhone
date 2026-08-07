package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.ConversationSavedData;

import java.util.List;

/**
 * Client -> server: "send me a page of this conversation". Sent when a player opens a conversation
 * screen, or scrolls up to load older messages ("skipFromEnd" grows). Replaces the old approach of the
 * client already having every message because it was included in the full-world sync blob.
 */
public record ConversationRequestPacket(String conversationId, int skipFromEnd) implements CustomPacketPayload {
    public static final Type<ConversationRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "conversation_request"));

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

    public static void handleData(final ConversationRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
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

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationRequestPacket::handleData);
        }
    }
}
