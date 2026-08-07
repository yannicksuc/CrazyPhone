package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactInfoScreenScreen;

@EventBusSubscriber
public record UpdateContactInfoMessage(String name, String uuid, String number) implements CustomPacketPayload {

    public static final Type<UpdateContactInfoMessage> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "update_contact_info")
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

    public static void handleData(final UpdateContactInfoMessage message, final IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof CrazyPhoneContactInfoScreenScreen screen) {
                    screen.updateContactInfo(message.name, message.uuid, message.number);
                }
            });
        }
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        Crazyphone.addNetworkMessage(UpdateContactInfoMessage.TYPE, UpdateContactInfoMessage.STREAM_CODEC, UpdateContactInfoMessage::handleData);
    }
}
