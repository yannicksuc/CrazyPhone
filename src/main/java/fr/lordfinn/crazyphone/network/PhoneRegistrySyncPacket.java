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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

/**
 * Carries the bounded {@link PhoneRegistrySavedData} (phones/contacts/mayor state, NOT messages) to a client.
 * Sent on login and after registry mutations. Safe to broadcast in full because it does not include
 * message history - see ConversationRequestPacket/ConversationResponsePacket for that.
 */
public record PhoneRegistrySyncPacket(PhoneRegistrySavedData data) implements CustomPacketPayload {
    //? if >=1.20.5 {
    /*public static final Type<PhoneRegistrySyncPacket> TYPE = new Type<>(Crazyphone.resource("phone_registry_sync"));

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
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "phone_registry_sync");

    public PhoneRegistrySyncPacket(FriendlyByteBuf buffer) {
        this(readData(buffer));
    }

    private static PhoneRegistrySavedData readData(FriendlyByteBuf buffer) {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        CompoundTag nbt = buffer.readNbt();
        if (nbt != null)
            data.readFrom(nbt);
        return data;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeNbt(data.save(new CompoundTag()));
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final PhoneRegistrySyncPacket message, final IPayloadContext context) {
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
    *///? } else {
    @OnlyIn(Dist.CLIENT)
    private static void applySync(PhoneRegistrySyncPacket message) {
        PhoneRegistrySavedData clientSide = PhoneRegistrySavedData.get(Minecraft.getInstance().player.level());
        clientSide.readFrom(message.data.save(new CompoundTag()));
    }

    public static void handleData(final PhoneRegistrySyncPacket message, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> applySync(message)).exceptionally(e -> {
                context.packetHandler().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }
    //?}
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PhoneRegistrySyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, PhoneRegistrySyncPacket::new, PhoneRegistrySyncPacket::handleData);
            //?}
        }
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(PhoneRegistrySyncPacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        PhoneRegistrySavedData clientSide = PhoneRegistrySavedData.get(net.minecraft.client.Minecraft.getInstance().player.level());
        clientSide.readFrom(message.data.save(new CompoundTag(), net.minecraft.client.Minecraft.getInstance().player.registryAccess()));
    }
    // (Fabric's own >=1.20.5 payload carries a real registryAccess() the >=1.20.5 NeoForge branch's
    // applySync(PhoneRegistrySyncPacket) helper above doesn't need - kept as its own inline body here
    // rather than sharing that helper, since Fabric has no equivalent NeoForge dist-scan crash to guard
    // against in the first place (see PORTING-26x.md's own note on this whole dist-leak sweep).

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, PhoneRegistrySyncPacket::handleDataFabric);
    }
    *///?}
}
