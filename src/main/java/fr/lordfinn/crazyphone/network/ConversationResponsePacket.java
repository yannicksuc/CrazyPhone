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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ConversationClientCache;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: one page of a conversation, in response to {@link ConversationRequestPacket}. */
public record ConversationResponsePacket(String conversationId, int skipFromEnd, ListTag messages, boolean hasMore) implements CustomPacketPayload {
    //? if >=1.20.5 {
    /*public static final Type<ConversationResponsePacket> TYPE = new Type<>(Crazyphone.resource("conversation_response"));

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
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "conversation_response");

    public ConversationResponsePacket(FriendlyByteBuf buffer) {
        this(
                buffer.readUtf(),
                buffer.readVarInt(),
                readMessagesList(buffer),
                buffer.readBoolean()
        );
    }

    private static ListTag readMessagesList(FriendlyByteBuf buffer) {
        net.minecraft.nbt.Tag raw = buffer.readNbt(NbtAccounter.create(2097152L));
        return raw instanceof ListTag list ? list : new ListTag();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeVarInt(skipFromEnd);
        buffer.writeNbt(messages);
        buffer.writeBoolean(hasMore);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final ConversationResponsePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> {
            List<CompoundTag> messages = new ArrayList<>();
            for (int i = 0; i < message.messages.size(); i++) {
                messages.add(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(message.messages, i));
            }
            ConversationClientCache.onPageReceived(message.conversationId,
                    new ConversationClientCache.ConversationPage(messages, message.hasMore, message.skipFromEnd));
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final ConversationResponsePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            List<CompoundTag> messages = new ArrayList<>();
            for (int i = 0; i < message.messages.size(); i++) {
                messages.add(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(message.messages, i));
            }
            ConversationClientCache.onPageReceived(message.conversationId,
                    new ConversationClientCache.ConversationPage(messages, message.hasMore, message.skipFromEnd));
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(ConversationResponsePacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        List<CompoundTag> messages = new ArrayList<>();
        for (int i = 0; i < message.messages.size(); i++) {
            messages.add(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(message.messages, i));
        }
        ConversationClientCache.onPageReceived(message.conversationId,
                new ConversationClientCache.ConversationPage(messages, message.hasMore, message.skipFromEnd));
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, ConversationResponsePacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, ConversationResponsePacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, ConversationResponsePacket::new, ConversationResponsePacket::handleData);
            //?}
        }
    }
    //?}
}
