package fr.lordfinn.crazyphone.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import fr.lordfinn.crazyphone.init.ModItems;

/**
 * Drives the phone's screen-on/screen-off item model swap (see the "crazyphone:screen_on" override in
 * models/item/crazy_phone.json) - the screen lights up while any phone menu is open ({@link PhoneScreen}
 * is implemented by every one of them) and is otherwise a dark, unlit panel.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneItemProperties {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.CRAZY_PHONE.get(),
                ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "screen_on"),
                (stack, level, entity, seed) -> Minecraft.getInstance().screen instanceof PhoneScreen ? 1.0f : 0.0f
        ));
    }
}
