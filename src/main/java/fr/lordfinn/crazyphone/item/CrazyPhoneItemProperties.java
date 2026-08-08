package fr.lordfinn.crazyphone.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import fr.lordfinn.crazyphone.init.ModItems;

/**
 * Drives the phone's 3 item model states (see the "crazyphone:screen_on" / "crazyphone:in_call" overrides
 * in models/item/crazy_phone.json): dark/unlit by default, lit while any phone menu is open ({@link
 * PhoneScreen} is implemented by every one of them), and a distinct "in call" texture - checked as an
 * independent property rather than folded into screen_on so each predicate stays a simple boolean check;
 * "in call wins over lit-but-not-in-call" is expressed purely by the override array's order (last matching
 * entry wins), not by any priority logic here.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneItemProperties {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.CRAZY_PHONE.get(),
                    ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "screen_on"),
                    (stack, level, entity, seed) -> Minecraft.getInstance().screen instanceof PhoneScreen ? 1.0f : 0.0f
            );
            ItemProperties.register(
                    ModItems.CRAZY_PHONE.get(),
                    ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "in_call"),
                    (stack, level, entity, seed) -> ClientCallState.isInCall() ? 1.0f : 0.0f
            );
        });
    }
}
