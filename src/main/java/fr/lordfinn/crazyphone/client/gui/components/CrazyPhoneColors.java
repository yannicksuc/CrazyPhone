package fr.lordfinn.crazyphone.client.gui.components;

/**
 * Shared UI colors, so the mod's one recurring accent color lives in exactly one place instead of being
 * redefined (and drifting slightly) at every call site - originally the photo-album selection border's
 * amber, reused as-is anywhere else that wants the same "highlighted/active" accent: the talking-participant
 * border in a call, the rejoin-call badge, and the voice message widget's hovered speed label / playing
 * waveform bars.
 */
public final class CrazyPhoneColors {
    /** ARGB (0xAARRGGBB - ready for GuiGraphics#fill and similar). For a text Style#withColor(int), which
     * takes plain RGB, use {@code ACCENT_YELLOW & 0xFFFFFF} to drop the alpha byte. */
    public static final int ACCENT_YELLOW = 0xFFFFC107;

    private CrazyPhoneColors() {
    }
}
