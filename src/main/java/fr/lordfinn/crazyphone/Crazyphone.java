package fr.lordfinn.crazyphone;

//? if neoforge {
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
//?}

import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
//? if neoforge {
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
//? if <1.21.10 {
import fr.lordfinn.crazyphone.init.ModRecipes;
//?}

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
//?}
// Real, original Forge 1.20.1 (predates NeoForge's own fork/rebrand) - same @Mod-annotated entrypoint idea
// as NeoForge's, just without the modern (IEventBus, ModContainer) auto-injected constructor convenience
// (that's a NeoForge-only addition) - the well-established old-Forge 1.20.1 pattern is a plain no-arg
// constructor pulling the mod event bus from FMLJavaModLoadingContext instead.
//? if legacyforge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import net.minecraft.network.FriendlyByteBuf;

import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.init.ModSounds;
import fr.lordfinn.crazyphone.init.ModTabs;
import fr.lordfinn.crazyphone.init.ModRecipes;
import fr.lordfinn.crazyphone.network.PlayPayloadContext;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.function.BiConsumer;
import java.util.function.Function;
//?}

/**
 * MODID/resource()/parseId() are shared across both loaders (referenced from ~everywhere in the codebase
 * that builds a ResourceLocation) - only the actual mod-lifecycle registration below (constructor, network
 * message registrar) is loader-specific. Fabric's own entrypoint/registration glue lives in
 * fr.lordfinn.crazyphone.fabric.CrazyphoneFabric instead, since Fabric's entrypoint mechanism (interface
 * implementation) has no equivalent of NeoForge/Forge's @Mod-annotated constructor injection to share code
 * with.
 */
//? if neoforge || legacyforge {
@Mod(Crazyphone.MODID)
//?}
public class Crazyphone {
    public static final String MODID = "crazyphone";
    //? if neoforge || legacyforge {
    private static final Logger LOGGER = LogUtils.getLogger();
    //?}

    //? if >=1.20.5 {
    /*public static /^$ res_loc {^/ResourceLocation/^$}^/ resource(String path) {
        return /^$ res_loc {^/ResourceLocation/^$}^/.fromNamespaceAndPath(MODID, path);
    }

    public static /^$ res_loc {^/ResourceLocation/^$}^/ parseId(String id) {
        return /^$ res_loc {^/ResourceLocation/^$}^/.parse(id);
    }
    *///? } else {
    public static /*$ res_loc {*/ResourceLocation/*$}*/ resource(String path) {
        return new /*$ res_loc {*/ResourceLocation/*$}*/(MODID, path);
    }

    public static /*$ res_loc {*/ResourceLocation/*$}*/ parseId(String id) {
        return new /*$ res_loc {*/ResourceLocation/*$}*/(id);
    }
    //?}

    //? if neoforge {
    public Crazyphone(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerNetworking);

        ModItems.REGISTRY.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        PhoneAttachmentTypes.ATTACHMENT_TYPES.register(modEventBus);
        //? if <1.21.10 {
        ModRecipes.REGISTRY.register(modEventBus);
        //?}
        //? if >=1.20.5 {
        /*fr.lordfinn.crazyphone.init.ModEntities.REGISTRY.register(modEventBus);
        *///?}
        fr.lordfinn.crazyphone.recipe.CrazyPhoneCraftingCondition.REGISTRY.register(modEventBus);
        //? if >=1.20.5 {
        /*fr.lordfinn.crazyphone.init.ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        *///?}
        //? if >=26 {
        /*modEventBus.addListener(fr.lordfinn.crazyphone.gametest.CrazyPhoneGameTests::registerTestFunctions);
        modEventBus.addListener(fr.lordfinn.crazyphone.gametest.CrazyPhoneGameTests::registerGameTests);
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
    private static final Map</*$ res_loc {*/ResourceLocation/*$}*/, NetworkMessage<?>> MESSAGES = new HashMap<>();

    private record NetworkMessage<T extends CustomPacketPayload>(FriendlyByteBuf.Reader<T> reader, IPlayPayloadHandler<T> handler) {
    }

    public static <T extends CustomPacketPayload> void addNetworkMessage(/*$ res_loc {*/ResourceLocation/*$}*/ id, FriendlyByteBuf.Reader<T> reader, IPlayPayloadHandler<T> handler) {
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
    //?}

    // Real, original Forge 1.20.1 - no (IEventBus, ModContainer) auto-injected constructor convenience (a
    // NeoForge-only addition), and no RegisterPayloadHandler(s)Event to defer registration to either - old
    // Forge's SimpleChannel.registerMessage(int discriminator, ...) can (and, since nothing else drives it
    // here, must) be called directly as each packet's own FMLCommonSetupEvent subscriber fires, with no
    // intermediate MESSAGES map/two-phase registerNetworking step needed at all.
    //? if legacyforge {
    public Crazyphone() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.REGISTRY.register(modEventBus);
        ModTabs.REGISTRY.register(modEventBus);
        ModMenus.REGISTRY.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        ModRecipes.REGISTRY.register(modEventBus);
        // CrazyPhoneCraftingCondition is NOT ported here: it's built on NeoForge's Codec-based ICondition
        // registry (net.neoforged.neoforge.common.conditions), which old Forge 1.20.1 has no equivalent of -
        // its own, older condition system predates the Codec-based rewrite entirely and would need its own
        // datapack JSON key (crazy_phone.json only carries "neoforge:conditions"/"fabric:load_conditions"
        // today) on top of a differently-shaped Java API. Follow-up if this toggle turns out to matter on
        // 1.20.1: for now, crazyPhoneCraftingEnabled=false does not remove the recipe on this target.

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(resource("main"),
            () -> "1", "1"::equals, "1"::equals);
    private static int nextMessageId = 0;

    public static SimpleChannel channel() {
        return CHANNEL;
    }

    public static <T> void addNetworkMessage(Class<T> clazz, BiConsumer<T, FriendlyByteBuf> writer,
                                              Function<FriendlyByteBuf, T> reader,
                                              BiConsumer<T, PlayPayloadContext> handler) {
        CHANNEL.registerMessage(nextMessageId++, clazz, writer, reader, (msg, ctxSupplier) -> {
            NetworkEvent.Context ctx = ctxSupplier.get();
            handler.accept(msg, new PlayPayloadContext(ctx));
            ctx.setPacketHandled(true);
        });
    }
    //?}
}
