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
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactInfoScreenScreen;

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record UpdateContactInfoMessage(String name, String uuid, String number) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<UpdateContactInfoMessage> TYPE = new Type<>(
        Crazyphone.resource("update_contact_info")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateContactInfoMessage> STREAM_CODEC =
        StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, UpdateContactInfoMessage message) -> {
                buffer.writeUtf(message.name);
                buffer.writeUtf(message.uuid);
                buffer.writeUtf(message.number);
            },
            (RegistryFriendlyByteBuf buffer) -> new UpdateContactInfoMessage(
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf()
            )
        );

    @Override
    public Type<UpdateContactInfoMessage> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("update_contact_info");

    public UpdateContactInfoMessage(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(name);
        buffer.writeUtf(uuid);
        buffer.writeUtf(number);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final UpdateContactInfoMessage message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof CrazyPhoneContactInfoScreenScreen screen) {
                    screen.updateContactInfo(message.name, message.uuid, message.number);
                }
            });
        }
    }
    *///? } else {
    public static void handleData(final UpdateContactInfoMessage message, final PlayPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.workHandler().submitAsync(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof CrazyPhoneContactInfoScreenScreen screen) {
                    screen.updateContactInfo(message.name, message.uuid, message.number);
                }
            });
        }
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(UpdateContactInfoMessage message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CrazyPhoneContactInfoScreenScreen screen) {
            screen.updateContactInfo(message.name, message.uuid, message.number);
        }
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, UpdateContactInfoMessage::handleDataFabric);
    }
    *///?}

    //? if neoforge {
    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        //? if >=1.20.5 {
        /*Crazyphone.addNetworkMessage(UpdateContactInfoMessage.TYPE, UpdateContactInfoMessage.STREAM_CODEC, UpdateContactInfoMessage::handleData);
        *///? } else {
        Crazyphone.addNetworkMessage(UpdateContactInfoMessage.ID, UpdateContactInfoMessage::new, UpdateContactInfoMessage::handleData);
        //?}
    }
    //?}
}
