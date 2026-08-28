package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.core.registries.Registries;
//?}
//? if fabric {
/*import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import fr.lordfinn.crazyphone.utils.RegistryEntry;
*///?}

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import fr.lordfinn.crazyphone.Crazyphone;

/** The two call ringtones (see sounds.json for the actual .ogg files) - ringback plays for the caller while
 * waiting on the Calling screen, ringtone plays for the callee on the Incoming Call screen. Both loop on the
 * jukebox channel (SoundSource.RECORDS) for as long as their screen is open. */
public class ModSounds {
    //? if neoforge {
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
    /** Shutter click, played locally the moment a photo capture is triggered (see CrazyPhoneCaptureMode#triggerCapture). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAKE_PICTURE = REGISTRY.register("take_picture",
            () -> SoundEvent.createVariableRangeEvent(Crazyphone.resource("take_picture")));
    //?}
    //? if fabric {
    /*public static RegistryEntry<SoundEvent> RINGBACK_TONE;
    public static RegistryEntry<SoundEvent> RINGTONE;
    public static RegistryEntry<SoundEvent> PHONE_VIBRATING;
    public static RegistryEntry<SoundEvent> TAKE_PICTURE;

    public static void register() {
        RINGBACK_TONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.SOUND_EVENT, Crazyphone.resource("ringback_tone"), SoundEvent.createVariableRangeEvent(Crazyphone.resource("ringback_tone"))));
        RINGTONE = new RegistryEntry<>(Registry.register(BuiltInRegistries.SOUND_EVENT, Crazyphone.resource("ringtone"), SoundEvent.createVariableRangeEvent(Crazyphone.resource("ringtone"))));
        PHONE_VIBRATING = new RegistryEntry<>(Registry.register(BuiltInRegistries.SOUND_EVENT, Crazyphone.resource("phone_vibrating"), SoundEvent.createVariableRangeEvent(Crazyphone.resource("phone_vibrating"))));
        TAKE_PICTURE = new RegistryEntry<>(Registry.register(BuiltInRegistries.SOUND_EVENT, Crazyphone.resource("take_picture"), SoundEvent.createVariableRangeEvent(Crazyphone.resource("take_picture"))));
    }
    *///?}
}
