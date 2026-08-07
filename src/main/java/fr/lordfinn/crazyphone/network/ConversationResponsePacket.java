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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ConversationClientCache;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: one page of a conversation, in response to {@link ConversationRequestPacket}. */
public record ConversationResponsePacket(String conversationId, int skipFromEnd, ListTag messages, boolean hasMore) implements CustomPacketPayload {
    public static final Type<ConversationResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "conversation_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationResponsePacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, ConversationResponsePacket message) -> {
                buffer.writeUtf(message.conversationId);
                buffer.writeVarInt(message.skipFromEnd);
                buffer.writeNbt(message.messages);
                buffer.writeBoolean(message.hasMore);
            },
            (RegistryFriendlyByteBuf buffer) -> {
                String conversationId = buffer.readUtf();
                int skipFromEnd = buffer.readVarInt();
                CompoundTag wrapper = new CompoundTag();
                net.minecraft.nbt.Tag raw = RegistryFriendlyByteBuf.readNbt(buffer, NbtAccounter.create(2097152L));
                ListTag messages = raw instanceof ListTag list ? list : new ListTag();
                boolean hasMore = buffer.readBoolean();
                return new ConversationResponsePacket(conversationId, skipFromEnd, messages, hasMore);
            });

    @Override
    public Type<ConversationResponsePacket> type() {
        return TYPE;
    }

    public static void handleData(final ConversationResponsePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> {
            List<CompoundTag> messages = new ArrayList<>();
            for (int i = 0; i < message.messages.size(); i++) {
                messages.add(message.messages.getCompound(i));
            }
            ConversationClientCache.onPageReceived(message.conversationId,
                    new ConversationClientCache.ConversationPage(messages, message.hasMore, message.skipFromEnd));
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationResponsePacket::handleData);
        }
    }
}
