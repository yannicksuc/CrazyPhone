package fr.lordfinn.crazyphone.voicechat;

/** Who can hear a call's participants - maps 1:1 onto Simple Voice Chat's own group types (see
 * SvcCallBridge#toSvcType), kept as its own SVC-free enum so packets and screens never touch SVC classes.
 * OPEN: participants hear each other AND nearby non-participants hear them (and vice versa).
 * NORMAL: only participants hear them, but they still hear players around them.
 * ISOLATED: only participants hear them and they hear nobody else. */
public enum CallVoiceMode {
    OPEN, NORMAL, ISOLATED;

    public CallVoiceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static CallVoiceMode fromOrdinal(int ordinal) {
        CallVoiceMode[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : OPEN;
    }
}
