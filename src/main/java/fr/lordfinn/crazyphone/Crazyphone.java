package fr.lordfinn.crazyphone;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
*///? } else {
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPlayPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
//?}

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
*///? }
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

    //? if >=1.20.5 {
    /*public static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static ResourceLocation parseId(String id) {
        return ResourceLocation.parse(id);
    }
    *///? } else {
    public static ResourceLocation resource(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static ResourceLocation parseId(String id) {
        return new ResourceLocation(id);
    }
    //?}

    public Crazyphone(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerNetworking);

        ModItems.REGISTRY.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        PhoneAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);
        //? if >=1.20.5 {
        /*fr.lordfinn.crazyphone.init.ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        *///?}

        //? if >=1.20.5 {
        /*modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        *///? } else {
        net.neoforged.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        //?}
    }

    private static boolean networkingRegistered = false;
    //? if >=1.20.5 <1.21.10 {
    /*private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

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
    *///?}
    //? if >=1.21.10 {
    /*private static final Map<CustomPacketPayload.Type<?>, NetworkMessage<?>> MESSAGES = new HashMap<>();

    private record NetworkMessage<T extends CustomPacketPayload>(StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
    }

    public static <T extends CustomPacketPayload> void addNetworkMessage(CustomPacketPayload.Type<T> id, StreamCodec<? extends FriendlyByteBuf, T> reader, IPayloadHandler<T> handler) {
        if (networkingRegistered)
            throw new IllegalStateException("Cannot register new network messages after networking has been registered");
        MESSAGES.put(id, new NetworkMessage<>(reader, handler));
    }

    // The 3-arg playBidirectional(type, codec, handler) here only registers the server-side handler on
    // 1.21.10 and silently leaves every payload's client-side handler unset (client-side handling must
    // now be registered separately via RegisterClientPayloadHandlersEvent, or passed explicitly here as
    // the 4-arg overload's clientHandler) - every one of this mod's handleData methods already branches
    // on context.flow() internally, so passing the same handler for both directions restores the old
    // one-handler-for-both-flows behavior instead.
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MODID);
        MESSAGES.forEach((id, networkMessage) -> registrar.playBidirectional(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler(), ((NetworkMessage) networkMessage).handler()));
        networkingRegistered = true;
    }
    *///?}
    //? if <1.20.5 {
    private static final Map<ResourceLocation, NetworkMessage<?>> MESSAGES = new HashMap<>();

    private record NetworkMessage<T extends CustomPacketPayload>(FriendlyByteBuf.Reader<T> reader, IPlayPayloadHandler<T> handler) {
    }

    public static <T extends CustomPacketPayload> void addNetworkMessage(ResourceLocation id, FriendlyByteBuf.Reader<T> reader, IPlayPayloadHandler<T> handler) {
        if (networkingRegistered)
            throw new IllegalStateException("Cannot register new network messages after networking has been registered");
        MESSAGES.put(id, new NetworkMessage<>(reader, handler));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerNetworking(final RegisterPayloadHandlerEvent event) {
        final IPayloadRegistrar registrar = event.registrar(MODID);
        MESSAGES.forEach((id, networkMessage) -> registrar.play(id, ((NetworkMessage) networkMessage).reader(), ((NetworkMessage) networkMessage).handler()));
        networkingRegistered = true;
    }
    //?}
}
