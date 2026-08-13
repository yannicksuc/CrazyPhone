package fr.lordfinn.crazyphone.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import fr.lordfinn.crazyphone.Crazyphone;

/** The two call ringtones (see sounds.json for the actual .ogg files) - ringback plays for the caller while
 * waiting on the Calling screen, ringtone plays for the callee on the Incoming Call screen. Both loop on the
 * jukebox channel (SoundSource.RECORDS) for as long as their screen is open. */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, Crazyphone.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> RINGBACK_TONE = REGISTRY.register("ringback_tone",
            () -> SoundEvent.createVariableRangeEvent(Crazyphone.resource("ringback_tone")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RINGTONE = REGISTRY.register("ringtone",
            () -> SoundEvent.createVariableRangeEvent(Crazyphone.resource("ringtone")));
    /** Short buzz, retriggered every pulse cycle while a phone is ringing (see CallRingtoneManager /
     * CallVibrationTiming) - layered on top of the ringtone melody, distinct from it, matching the visual
     * hand-shake pulses. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PHONE_VIBRATING = REGISTRY.register("phone_vibrating",
            () -> SoundEvent.createVariableRangeEvent(Crazyphone.resource("phone_vibrating")));
}
