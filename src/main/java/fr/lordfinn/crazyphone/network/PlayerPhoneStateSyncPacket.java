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

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;

public record PlayerPhoneStateSyncPacket(PlayerPhoneState data) implements CustomPacketPayload {
    public static final Type<PlayerPhoneStateSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "player_phone_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPhoneStateSyncPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, PlayerPhoneStateSyncPacket message) -> buffer.writeNbt(message.data().serializeNBT(buffer.registryAccess())),
            (RegistryFriendlyByteBuf buffer) -> {
                PlayerPhoneStateSyncPacket message = new PlayerPhoneStateSyncPacket(new PlayerPhoneState());
                message.data.deserializeNBT(buffer.registryAccess(), buffer.readNbt());
                return message;
            });

    @Override
    public Type<PlayerPhoneStateSyncPacket> type() {
        return TYPE;
    }

    public static void handleData(final PlayerPhoneStateSyncPacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> context.player().getData(fr.lordfinn.crazyphone.data.PhoneAttachmentTypes.PLAYER_PHONE_STATE)
                    .deserializeNBT(context.player().registryAccess(), message.data.serializeNBT(context.player().registryAccess()))).exceptionally(e -> {
                context.connection().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PlayerPhoneStateSyncPacket::handleData);
        }
    }
}
