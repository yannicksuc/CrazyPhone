package fr.lordfinn.crazyphone.client;

/**
 * Shared pulse timing between the visual hand-shake ({@link CrazyPhoneVibrationRenderer}) and the buzzing
 * sound ({@link CallRingtoneManager}) for a ringing phone - kept in one place so the two stay in sync rather
 * than drifting apart if either one's constants are tuned independently.
 */
public final class CallVibrationTiming {
    /** Full pulse period, in ticks - one buzz burst + one silent gap. */
    public static final float CYCLE_TICKS = 16f;
    /** How much of each cycle is actually buzzing - the rest is the silent gap between pulses. */
    public static final float BUZZ_TICKS = 9f;

    private CallVibrationTiming() {
    }
}
