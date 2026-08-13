package fr.lordfinn.crazyphone.item;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Drives the phone's 5 item model states (see the overrides in models/item/crazy_phone.json): dark/unlit by
 * default, lit while its own screen is open, and 3 distinct call textures - "calling" (this phone is the one
 * waiting for an answer), "called_in" (this phone is the one being called, not yet answered/declined), and
 * "in_call" (actually connected) - each its own independent property rather than folded into screen_on, so
 * each predicate stays a simple boolean check; priority between them is expressed purely by the override
 * array's order (last matching entry wins), not by any priority logic here.
 *
 * Every predicate here reads directly off the specific rendered {@code stack}'s own persisted data (see
 * CrazyPhoneHelper#isPhoneScreenOpen/getPhoneCallState), not any client-local field - that's what makes this
 * correct for EVERY renderer of the item, not just the owning player's own client. Vanilla's own
 * equipment-sync already replicates a held item's data to nearby tracking players purely because the
 * ItemStack changed, so writing the state into the item itself (done server-side, in
 * CrazyPhoneDefaultScreenMenu and CallRegistry) makes it visible to bystanders for free, with zero new
 * packets.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneItemProperties {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.CRAZY_PHONE.get(),
                    Crazyphone.resource("screen_on"),
                    (stack, level, entity, seed) -> CrazyPhoneHelper.isPhoneScreenOpen(stack) ? 1.0f : 0.0f
            );
            registerCallState("calling", State.CALLING);
            registerCallState("called_in", State.RINGING);
            registerCallState("in_call", State.ACTIVE);
        });
    }

    private static void registerCallState(String propertyName, State targetState) {
        ItemProperties.register(
                ModItems.CRAZY_PHONE.get(),
                Crazyphone.resource(propertyName),
                (stack, level, entity, seed) -> targetState.name().equals(CrazyPhoneHelper.getPhoneCallState(stack)) ? 1.0f : 0.0f
        );
    }
}
