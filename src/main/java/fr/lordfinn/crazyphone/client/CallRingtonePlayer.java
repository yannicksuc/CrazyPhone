package fr.lordfinn.crazyphone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Shared start/stop for the two call ringtones (CrazyPhoneCallingScreenScreen's ringback,
 * CrazyPhoneIncomingCallScreenScreen's ringtone) - both loop on the jukebox channel (SoundSource.RECORDS),
 * unattenuated and listener-relative (like a phone in your own hand, not a positional world sound), for as
 * long as their screen is open. Stopping relies on the caller holding onto the exact SoundInstance returned
 * here - SoundManager#stop(SoundInstance) needs that same reference, not just the SoundEvent. */
public final class CallRingtonePlayer {
    private CallRingtonePlayer() {
    }

    public static SoundInstance play(SoundEvent sound) {
        SoundInstance instance = new SimpleSoundInstance(
                //? if <1.21.10 {
                sound.getLocation(),
                //? } else {
                /*sound.location(),
                *///?}
                SoundSource.RECORDS,
                1.0f, 1.0f,
                SoundInstance.createUnseededRandom(),
                true, 0,
                SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0,
                true
        );
        Minecraft.getInstance().getSoundManager().play(instance);
        return instance;
    }

    public static void stop(SoundInstance instance) {
        if (instance != null)
            Minecraft.getInstance().getSoundManager().stop(instance);
    }
}
