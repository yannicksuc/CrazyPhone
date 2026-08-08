package fr.lordfinn.crazyphone.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberProcedure;

/**
 * Drives the phone's 5 item model states (see the overrides in models/item/crazy_phone.json): dark/unlit by
 * default, lit while any phone menu is open ({@link PhoneScreen} is implemented by every one of them), and
 * 3 distinct call textures - "calling" (this phone is the one waiting for an answer), "called_in" (this
 * phone is the one being called, not yet answered/declined), and "in_call" (actually connected) - each its
 * own independent property rather than folded into screen_on, so each predicate stays a simple boolean
 * check; priority between them is expressed purely by the override array's order (last matching entry
 * wins), not by any priority logic here.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneItemProperties {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.CRAZY_PHONE.get(),
                    ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "screen_on"),
                    (stack, level, entity, seed) -> isTheOpenPhone(stack) ? 1.0f : 0.0f
            );
            registerCallState("calling", State.CALLING);
            registerCallState("called_in", State.RINGING);
            registerCallState("in_call", State.ACTIVE);
        });
    }

    /** Every phone screen derives "the relevant number" from whatever's in the LOCAL player's main hand
     * (see GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null), used the same way by
     * every screen class) - matching that convention here is what makes this specific stack's own number
     * the one compared, instead of just "is any phone screen open at all", which lit up every registered
     * phone a player happened to be carrying the moment they opened any one of them. */
    private static boolean isTheOpenPhone(ItemStack stack) {
        if (!(Minecraft.getInstance().screen instanceof PhoneScreen))
            return false;
        var localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null)
            return false;
        String openNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(localPlayer, null);
        return !openNumber.isEmpty() && openNumber.equals(GetCrazyPhoneNumberProcedure.execute(stack));
    }

    /** Not just "is the player in a call at all" - a player can physically hold several registered phones
     * at once, so this specific stack's own number has to actually be one of the numbers on a call that's
     * currently in exactly {@code targetState}, or every phone the player carries would light up together
     * regardless of which one is actually calling/being called/connected. */
    private static void registerCallState(String propertyName, State targetState) {
        ItemProperties.register(
                ModItems.CRAZY_PHONE.get(),
                ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, propertyName),
                (stack, level, entity, seed) -> ClientCallState.numberHasState(GetCrazyPhoneNumberProcedure.execute(stack), targetState) ? 1.0f : 0.0f
        );
    }
}
