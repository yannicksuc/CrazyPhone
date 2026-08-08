package fr.lordfinn.crazyphone;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.init.ModSounds;
import fr.lordfinn.crazyphone.init.ModTabs;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

@Mod(Crazyphone.MODID)
public class Crazyphone {
    public static final String MODID = "crazyphone";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Crazyphone(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerNetworking);

        ModItems.REGISTRY.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        PhoneAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static boolean networkingRegistered = false;
    private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
    }

    public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
        if (networkingRegistered)
            throw new IllegalStateException("Cannot register new network messages after networking has been registered");
        MESSAGES.put(id, new NetworkMessage<>(reader, handler));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MODID);
        MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
        networkingRegistered = true;
    }
}
