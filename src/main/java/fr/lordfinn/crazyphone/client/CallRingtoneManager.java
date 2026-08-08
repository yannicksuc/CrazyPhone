package fr.lordfinn.crazyphone.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModSounds;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberProcedure;

/**
 * Drives the two call ringtones off the player's actual possession of the ringing/calling phone, not off
 * any screen being open - a real phone rings whether or not you've pulled it out of your pocket to look at
 * it. Runs every client tick, independent of CrazyPhoneCallingScreenScreen / CrazyPhoneIncomingCallScreenScreen
 * (which no longer touch these sounds themselves).
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CallRingtoneManager {
    private static SoundInstance currentSound;
    private static State currentSoundFor;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            stopCurrent();
            return;
        }

        State state = ClientCallState.getState();
        State wanted = (state == State.CALLING || state == State.RINGING) && playerHasMatchingPhone(player, state)
                ? state
                : null;

        if (wanted == currentSoundFor)
            return;
        stopCurrent();
        if (wanted != null) {
            SoundEvent sound = wanted == State.CALLING ? ModSounds.RINGBACK_TONE.get() : ModSounds.RINGTONE.get();
            currentSound = CallRingtonePlayer.play(sound);
            currentSoundFor = wanted;
        }
    }

    private static void stopCurrent() {
        if (currentSound != null)
            CallRingtonePlayer.stop(currentSound);
        currentSound = null;
        currentSoundFor = null;
    }

    /** Anywhere in the inventory, not just the main hand - a ringing phone in your pocket should still be
     * heard. Matches the same "which specific phone" gating as the item's calling/called_in texture
     * (ClientCallState.numberHasState), so only the phone that's actually part of this call rings, not
     * every phone the player happens to be carrying. */
    private static boolean playerHasMatchingPhone(LocalPlayer player, State state) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.CRAZY_PHONE.get()
                    && ClientCallState.numberHasState(GetCrazyPhoneNumberProcedure.execute(stack), state))
                return true;
        }
        return false;
    }
}
