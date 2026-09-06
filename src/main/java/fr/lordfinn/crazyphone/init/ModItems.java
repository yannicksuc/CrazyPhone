package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if fabric {
/*import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import fr.lordfinn.crazyphone.utils.RegistryEntry;
*///?}
// Real, original Forge 1.20.1 - DeferredRegister.register(name, supplier) here returns a RegistryObject<T>
// instead of NeoForge's newer DeferredItem<T>, but both are Supplier<T>-shaped with the same .get(), so the
// exact same field/usage pattern below works unchanged - only the type name itself differs (see the
// registry field/list below). Item capability attachment has no RegisterCapabilitiesEvent equivalent in old
// Forge though (that's a NeoForge-only rewrite of the older Capability system, present from NeoForge's very
// first release) - the idiomatic old-Forge way to attach a per-ItemStack capability is
// AttachCapabilitiesEvent<ItemStack>, wrapping the same CrazyPhoneInventoryCapability (an ItemStackHandler)
// in an ICapabilityProvider keyed off ForgeCapabilities.ITEM_HANDLER.
//? if legacyforge {
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.core.Direction;
//?}

import net.minecraft.world.item.Item;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
//? if neoforge || legacyforge {
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;
//?}
//? if fabric && >=1.20.5 {
/*import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;
*///?}
//? if neoforge || legacyforge {
import fr.lordfinn.crazyphone.item.inventory.CrazyPhoneInventoryCapability;
//?}

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
// AttachCapabilitiesEvent fires on the default FORGE (game) event bus, not the MOD bus - unlike NeoForge's
// own RegisterCapabilitiesEvent above (a MOD-bus lifecycle event as part of that API's rework), old Forge's
// original AttachCapabilitiesEvent stayed tied to actual entity/itemstack instantiation timing. Confirmed
// the hard way: Bus.MOD here failed mod loading with "takes an argument that is not a subtype of ...
// IModBusEvent" at actual server startup.
//? if legacyforge {
@EventBusSubscriber
//?}
public class ModItems {
    //? if neoforge {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(Crazyphone.MODID);

    public static final DeferredItem<Item> CRAZY_PHONE = REGISTRY.registerItem("crazy_phone", CrazyPhoneItem::new);
    public static final DeferredItem<Item> CRAZY_PHONE_PHOTO = REGISTRY.registerItem("crazy_phone_photo", CrazyPhonePhotoItem::new);

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        //? if >=1.21.10 {
        /*event.registerItem(Capabilities.Item.ITEM, (stack, context) -> new CrazyPhoneInventoryCapability(stack), CRAZY_PHONE.get());
        *///? } else {
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> new CrazyPhoneInventoryCapability(stack), CRAZY_PHONE.get());
        //?}
    }
    //?}
    //? if legacyforge {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, Crazyphone.MODID);

    public static final RegistryObject<Item> CRAZY_PHONE = REGISTRY.register("crazy_phone", () -> new CrazyPhoneItem(new Item.Properties()));
    public static final RegistryObject<Item> CRAZY_PHONE_PHOTO = REGISTRY.register("crazy_phone_photo", () -> new CrazyPhonePhotoItem(new Item.Properties()));

    @SubscribeEvent
    public static void onAttachItemCapabilities(AttachCapabilitiesEvent<net.minecraft.world.item.ItemStack> event) {
        if (event.getObject().getItem() != CRAZY_PHONE.get())
            return;
        event.addCapability(Crazyphone.resource("inventory"), new ICapabilityProvider() {
            private final LazyOptional<CrazyPhoneInventoryCapability> instance =
                    LazyOptional.of(() -> new CrazyPhoneInventoryCapability(event.getObject()));

            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return cap == ForgeCapabilities.ITEM_HANDLER ? instance.cast() : LazyOptional.empty();
            }
        });
    }
    //?}
    // Fabric has no deferred-registration lifecycle - Registry#register just performs the registration
    // immediately, so this needs to run during CrazyphoneFabric#onInitialize (not at class-init/static-field
    // time, which can run too early relative to Fabric's own registry-freeze ordering). RegistryEntry wraps
    // the result so every existing ".get()" call site across the codebase keeps compiling unchanged.
    //? if fabric {
    /*public static RegistryEntry<Item> CRAZY_PHONE;
    *///?}
    // Photo item ships on Fabric >=1.20.5 only - CustomPacketPayload networking (and everything the native
    // picture pipeline needs) doesn't exist before that at all, so 1.20.1-fabric stays at its narrower
    // walking-skeleton scope (see build.fabric.gradle.kts's own doc comment on that boundary).
    //? if fabric && >=1.20.5 {
    /*public static RegistryEntry<Item> CRAZY_PHONE_PHOTO;
    *///?}

    //? if fabric && >=1.20.5 {
    /*public static void register() {
        // >=26 requires Item.Properties to carry its own id before construction (confirmed live -
        // Item.Properties#itemIdOrThrow crashes with "Item id not set" otherwise; NeoForge's own
        // DeferredRegister.Items already does this internally, which is why the NeoForge branch above never
        // needed it) - setId(...) needs the same ResourceKey the Registry.register(...) call below also
        // uses, so both ultimately agree on the same identity.
        //? if >=26 {
        /^CRAZY_PHONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone"),
                new CrazyPhoneItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Crazyphone.resource("crazy_phone"))))));
        CRAZY_PHONE_PHOTO = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone_photo"),
                new CrazyPhonePhotoItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, Crazyphone.resource("crazy_phone_photo"))))));
        ^///? } else {
        CRAZY_PHONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone"), new CrazyPhoneItem(new Item.Properties())));
        CRAZY_PHONE_PHOTO = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone_photo"), new CrazyPhonePhotoItem(new Item.Properties())));
        //?}
    }
    *///?}
    //? if fabric && <1.20.5 {
    /*public static void register() {
        CRAZY_PHONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone"), new CrazyPhoneItem(new Item.Properties())));
    }
    *///?}
}
