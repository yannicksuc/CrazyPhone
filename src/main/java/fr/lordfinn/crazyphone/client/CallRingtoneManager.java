package fr.lordfinn.crazyphone.client;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
//?}
//?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
*///?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModSounds;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberProcedure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives the two call ringtones off the player's actual possession of the ringing/calling phone, not off
 * any screen being open - a real phone rings whether or not you've pulled it out of your pocket to look at
 * it. Runs every client tick, independent of CrazyPhoneCallingScreenScreen / CrazyPhoneIncomingCallScreenScreen
 * (which no longer touch these sounds themselves). While RINGING (being called, not calling), also
 * retriggers a short buzz every pulse cycle (see CallVibrationTiming), layered on top of the ringtone melody
 * and synced to CrazyPhoneVibrationRenderer's visual hand-shake pulses.
 */
//? if neoforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public class CallRingtoneManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static SoundInstance currentSound;
    private static State currentSoundFor;
    private static long lastBuzzCycle = -1;
    // TEMP diagnostic (see chat report of "no vibration/sound/ringtone at all") - remove once confirmed.
    private static long ringingStartedGameTime = -1;

    //? if neoforge {
    @SubscribeEvent
    //? if >=1.20.5 {
    /*public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }
    *///? } else {
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    *///?}

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            stopCurrent();
            return;
        }

        State state = ClientCallState.getState();
        boolean hasMatchingPhone = playerHasMatchingPhone(player, state);
        State wanted = (state == State.CALLING || state == State.RINGING) && hasMatchingPhone
                ? state
                : null;

        if (state == State.RINGING && !hasMatchingPhone)
            LOGGER.info("[callring-diag] ClientCallState is RINGING but no held/carried phone matched callNumbers");

        if (wanted != currentSoundFor) {
            if (wanted == State.RINGING && mc.level != null) {
                ringingStartedGameTime = mc.level.getGameTime();
                LOGGER.info("[callring-diag] RINGING started at gameTime={}", ringingStartedGameTime);
            } else if (currentSoundFor == State.RINGING && mc.level != null) {
                LOGGER.info("[callring-diag] RINGING ended at gameTime={} (lasted {} ticks)", mc.level.getGameTime(), mc.level.getGameTime() - ringingStartedGameTime);
            }
            stopCurrent();
            if (wanted != null) {
                SoundEvent sound = wanted == State.CALLING ? ModSounds.RINGBACK_TONE.get() : ModSounds.RINGTONE.get();
                currentSound = CallRingtonePlayer.play(sound);
                currentSoundFor = wanted;
            }
        }

        if (wanted == State.RINGING && mc.level != null) {
            long cycleIndex = mc.level.getGameTime() / (long) CallVibrationTiming.CYCLE_TICKS;
            if (cycleIndex != lastBuzzCycle) {
                lastBuzzCycle = cycleIndex;
                mc.getSoundManager().play(new SimpleSoundInstance(
                        //? if <1.21.10 {
                        ModSounds.PHONE_VIBRATING.get().getLocation(),
                        //? } else {
                        /*ModSounds.PHONE_VIBRATING.get().location(),
                        *///?}
                        SoundSource.RECORDS, 0.7f, 1.0f,
                        SoundInstance.createUnseededRandom(), false, 0,
                        SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
            }
        } else {
            lastBuzzCycle = -1; // so the buzz retriggers immediately the next time ringing starts
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
                    && ClientCallState.numberHasState(GetCrazyPhoneNumberProcedure.execute(stack, player.level()), state))
                return true;
        }
        return false;
    }
}
