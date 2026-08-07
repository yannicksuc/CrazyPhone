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
import net.minecraft.network.chat.ComponentSerialization;
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
                writeComponent(buffer, Component.literal(message.name));
                writeComponent(buffer, Component.literal(message.uuid));
                writeComponent(buffer, Component.literal(message.number));
            },
            (RegistryFriendlyByteBuf buffer) -> new UpdateContactInfoMessage(
                readComponent(buffer).getString(),
                readComponent(buffer).getString(),
                readComponent(buffer).getString()
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

    private static Component readComponent(RegistryFriendlyByteBuf buffer) {
        return ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
    }

    private static void writeComponent(RegistryFriendlyByteBuf buffer, Component component) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, component);
    }

    @SubscribeEvent
    public static void registerMessage(FMLCommonSetupEvent event) {
        Crazyphone.addNetworkMessage(UpdateContactInfoMessage.TYPE, UpdateContactInfoMessage.STREAM_CODEC, UpdateContactInfoMessage::handleData);
    }
}
