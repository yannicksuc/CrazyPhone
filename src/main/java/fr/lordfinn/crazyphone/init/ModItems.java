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

import net.minecraft.world.item.Item;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
//? if neoforge {
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;
//?}
//? if fabric && >=1.20.5 {
/*import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;
*///?}
//? if neoforge {
import fr.lordfinn.crazyphone.item.inventory.CrazyPhoneInventoryCapability;
//?}

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
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
        CRAZY_PHONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone"), new CrazyPhoneItem(new Item.Properties())));
        CRAZY_PHONE_PHOTO = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone_photo"), new CrazyPhonePhotoItem(new Item.Properties())));
    }
    *///?}
    //? if fabric && <1.20.5 {
    /*public static void register() {
        CRAZY_PHONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.ITEM, Crazyphone.resource("crazy_phone"), new CrazyPhoneItem(new Item.Properties())));
    }
    *///?}
}
