package fr.lordfinn.crazyphone.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.item.Item;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
import fr.lordfinn.crazyphone.item.inventory.CrazyPhoneInventoryCapability;

@EventBusSubscriber
public class ModItems {
    public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(Crazyphone.MODID);

    public static final DeferredItem<Item> CRAZY_PHONE = REGISTRY.register("crazy_phone", CrazyPhoneItem::new);

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> new CrazyPhoneInventoryCapability(stack), CRAZY_PHONE.get());
    }
}
