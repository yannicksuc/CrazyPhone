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

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

/**
 * Carries the bounded {@link PhoneRegistrySavedData} (phones/contacts/mayor state, NOT messages) to a client.
 * Sent on login and after registry mutations. Safe to broadcast in full because it does not include
 * message history - see ConversationRequestPacket/ConversationResponsePacket for that.
 */
public record PhoneRegistrySyncPacket(PhoneRegistrySavedData data) implements CustomPacketPayload {
    public static final Type<PhoneRegistrySyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "phone_registry_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneRegistrySyncPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, PhoneRegistrySyncPacket message) -> buffer.writeNbt(message.data.save(new CompoundTag(), buffer.registryAccess())),
            (RegistryFriendlyByteBuf buffer) -> {
                PhoneRegistrySyncPacket message = new PhoneRegistrySyncPacket(new PhoneRegistrySavedData());
                CompoundTag nbt = buffer.readNbt();
                if (nbt != null)
                    message.data.readFrom(nbt);
                return message;
            });

    @Override
    public Type<PhoneRegistrySyncPacket> type() {
        return TYPE;
    }

    public static void handleData(final PhoneRegistrySyncPacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                PhoneRegistrySavedData clientSide = PhoneRegistrySavedData.get(context.player().level());
                clientSide.readFrom(message.data.save(new CompoundTag(), context.player().registryAccess()));
            }).exceptionally(e -> {
                context.connection().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PhoneRegistrySyncPacket::handleData);
        }
    }
}
