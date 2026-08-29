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

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;

public record PlayerPhoneStateSyncPacket(PlayerPhoneState data) implements CustomPacketPayload {
    //? if neoforge && >=1.20.5 <1.21.10 {
    /*public static final Type<PlayerPhoneStateSyncPacket> TYPE = new Type<>(Crazyphone.resource("player_phone_state_sync"));

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
    *///?}
    //? if neoforge && >=1.21.10 {
    /*// PlayerPhoneState implements ValueIOSerializable here (serialize/deserialize), not INBTSerializable -
    // TagValueOutput/TagValueInput are vanilla's own bridge between that and a plain CompoundTag for wire
    // transmission, same round trip the old serializeNBT/deserializeNBT calls did.
    public static final Type<PlayerPhoneStateSyncPacket> TYPE = new Type<>(Crazyphone.resource("player_phone_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPhoneStateSyncPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, PlayerPhoneStateSyncPacket message) -> {
                net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, buffer.registryAccess());
                message.data().serialize(output);
                buffer.writeNbt(output.buildResult());
            },
            (RegistryFriendlyByteBuf buffer) -> {
                PlayerPhoneStateSyncPacket message = new PlayerPhoneStateSyncPacket(new PlayerPhoneState());
                net.minecraft.world.level.storage.ValueInput input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, buffer.registryAccess(), buffer.readNbt());
                message.data.deserialize(input);
                return message;
            });

    @Override
    public Type<PlayerPhoneStateSyncPacket> type() {
        return TYPE;
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static final ResourceLocation ID = Crazyphone.resource("player_phone_state_sync");

    public PlayerPhoneStateSyncPacket(FriendlyByteBuf buffer) {
        this(readState(buffer));
    }

    private static PlayerPhoneState readState(FriendlyByteBuf buffer) {
        PlayerPhoneState state = new PlayerPhoneState();
        state.deserializeNBT(buffer.readNbt());
        return state;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeNbt(data.serializeNBT());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*// Fabric's PlayerPhoneState has no serializeNBT/serialize method (see PlayerPhoneState.java) - its
    // CODEC round-trips through NbtOps instead, same shape as ConversationSavedData's Fabric branches.
    public static final Type<PlayerPhoneStateSyncPacket> TYPE = new Type<>(Crazyphone.resource("player_phone_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPhoneStateSyncPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, PlayerPhoneStateSyncPacket message) -> buffer.writeNbt(
                    (net.minecraft.nbt.CompoundTag) PlayerPhoneState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, message.data()).result().orElse(new net.minecraft.nbt.CompoundTag())),
            (RegistryFriendlyByteBuf buffer) -> {
                net.minecraft.nbt.CompoundTag nbt = buffer.readNbt();
                PlayerPhoneState state = nbt == null ? new PlayerPhoneState()
                        : PlayerPhoneState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, nbt).result().orElseGet(PlayerPhoneState::new);
                return new PlayerPhoneStateSyncPacket(state);
            });

    @Override
    public Type<PlayerPhoneStateSyncPacket> type() {
        return TYPE;
    }
    *///?}

    //? if neoforge && >=1.20.5 <1.21.10 {
    /*public static void handleData(final PlayerPhoneStateSyncPacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> context.player().getData(fr.lordfinn.crazyphone.data.PhoneAttachmentTypes.PLAYER_PHONE_STATE)
                    .deserializeNBT(context.player().registryAccess(), message.data.serializeNBT(context.player().registryAccess()))).exceptionally(e -> {
                context.connection().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }
    *///?}
    //? if neoforge && >=1.21.10 {
    /*public static void handleData(final PlayerPhoneStateSyncPacket message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, context.player().registryAccess());
                message.data.serialize(output);
                net.minecraft.world.level.storage.ValueInput input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, context.player().registryAccess(), output.buildResult());
                context.player().getData(fr.lordfinn.crazyphone.data.PhoneAttachmentTypes.PLAYER_PHONE_STATE).deserialize(input);
            }).exceptionally(e -> {
                context.connection().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }
    *///?}
    //? if neoforge && <1.20.5 {
    public static void handleData(final PlayerPhoneStateSyncPacket message, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> Minecraft.getInstance().player.getData(fr.lordfinn.crazyphone.data.PhoneAttachmentTypes.PLAYER_PHONE_STATE)
                    .deserializeNBT(message.data.serializeNBT())).exceptionally(e -> {
                context.packetHandler().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }
    //?}

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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PlayerPhoneStateSyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, PlayerPhoneStateSyncPacket::new, PlayerPhoneStateSyncPacket::handleData);
            //?}
        }
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(PlayerPhoneStateSyncPacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        ((net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) player).setAttached(fr.lordfinn.crazyphone.data.PhoneAttachmentTypes.PLAYER_PHONE_STATE, message.data());
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, PlayerPhoneStateSyncPacket::handleDataFabric);
    }
    *///?}
}
