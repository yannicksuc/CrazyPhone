package fr.lordfinn.crazyphone.client.gui;

import com.mojang.blaze3d.platform.Window;

import fr.lordfinn.crazyphone.client.ClientPrefs;
import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.client.gui.screens.Screen;

/**
 * Makes every phone screen take a sensible share of the window whatever the player's GUI scale, in one
 * place instead of per screen.
 *
 * How it works: a phone screen picks a zoom factor {@code f} (see {@link #computeScale}) when it is laid
 * out, then pretends the window is {@code f} times smaller than it really is ({@link #applyVirtualSize}):
 * every screen keeps building its widgets at the same fixed offsets around leftPos/topPos, in a smaller
 * "virtual" coordinate space. Three mixins then bridge that space and the real one:
 * <ul>
 * <li>PhoneScaleScreenMixin scales the whole render pass by {@code f} (one pose push around the screen's
 * top-level render entry point, so subclass render overrides, tooltips and deferred elements all land
 * inside it) and hands the screen virtual mouse coordinates;</li>
 * <li>PhoneScaleMouseMixin converts every mouse event (click, release, drag, scroll, move) to virtual
 * coordinates before the screen sees it;</li>
 * <li>PhoneScaleGuiGraphicsMixin reports the virtual size from guiWidth()/guiHeight() while a scaled
 * screen renders (tooltip placement and wrapping) and, before 1.21.10 where scissor ignores the pose,
 * converts scissor rectangles.</li>
 * </ul>
 * Picture-in-picture renders (3D entity previews on 1.21.10+, which ignore the pose) are converted in
 * GuiCompat#renderEntityInInventory.
 *
 * Safety: the factor is always finite and in [1, MAX_FACTOR] (1 means "do nothing at all"), the phone is
 * never made smaller than it is at the vanilla GUI scale and never larger than the window, and the render
 * state is tied to the exact GuiGraphics instance it was pushed on, so a stale state (a render that threw
 * half-way) can never leak into another frame or another screen.
 */
public final class PhoneScale {
    /** The phone frame's own height in GUI pixels (phone-background.png). Every phone screen is sized
     * from this, so moving between apps keeps the phone the same size even when a screen is wider. */
    public static final int PHONE_HEIGHT = 195;
    /** Smallest virtual canvas the full-window phone screens (photo viewer and editor) are given, so their
     * layout always has room around the content, even at a large phone size on a small window. */
    public static final int FULL_WINDOW_MIN_WIDTH = 400;
    public static final int FULL_WINDOW_MIN_HEIGHT = 250;
    /** Hard ceiling on the zoom factor, whatever the window: guards against absurd values (e.g. a
     * huge window with GUI scale 1) ever producing a degenerate virtual canvas. */
    private static final double MAX_FACTOR = 12.0;
    /** The phone never covers more than this share of the window in either direction. */
    private static final double MAX_WINDOW_SHARE = 0.96;
    /** A whole-number scale within this ratio of the target is preferred over an exact fractional one,
     * so the pixel art stays crisp (every texture pixel the same number of screen pixels). */
    private static final double WHOLE_STEP_TOLERANCE_LOW = 0.85;
    private static final double WHOLE_STEP_TOLERANCE_HIGH = 1.2;

    private static double activeFactor = 1.0;
    private static int activeVirtualWidth;
    private static int activeVirtualHeight;
    private static Object activeGraphics;

    private PhoneScale() {
    }

    /**
     * Zoom factor (relative to the current vanilla GUI scale) for a phone screen whose content is
     * {@code contentWidth x contentHeight} GUI pixels.
     *
     * @param minVirtualWidth  the screen needs at least this many virtual pixels across (full-window
     *                         layouts such as the photo editor need room around their content)
     * @param minVirtualHeight same, vertically
     */
    public static double computeScale(int contentWidth, int contentHeight, int minVirtualWidth, int minVirtualHeight) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null)
                return 1.0;
            Window window = mc.getWindow();
            int fbWidth = window.getWidth();
            int fbHeight = window.getHeight();
            double guiScale = window.getGuiScale();
            int percent = ClientPrefs.phoneSizePercent();
            return computeScale(fbWidth, fbHeight, guiScale, percent, contentWidth, contentHeight, minVirtualWidth, minVirtualHeight);
        } catch (RuntimeException e) {
            // Never let a sizing problem break the phone itself: fall back to the vanilla size.
            return 1.0;
        }
    }

    /** Pure version of {@link #computeScale(int, int, int, int)}, all inputs explicit (unit-testable). */
    public static double computeScale(int fbWidth, int fbHeight, double guiScale, int percent,
                                      int contentWidth, int contentHeight, int minVirtualWidth, int minVirtualHeight) {
        // Minimized window (0x0), not created yet, or nonsense inputs: vanilla size.
        if (fbWidth <= 0 || fbHeight <= 0 || !(guiScale > 0) || Double.isInfinite(guiScale)
                || contentWidth <= 0 || contentHeight <= 0 || percent <= 0)
            return 1.0;
        int clampedPercent = ClientPrefs.clampPhoneSizePercent(percent);
        if (clampedPercent <= 0)
            return 1.0;

        // Real (framebuffer) pixels per phone pixel the player asked for.
        double ideal = fbHeight * (clampedPercent / 100.0) / PHONE_HEIGHT;
        double scale = preferWholeStep(ideal);

        // Never wider/taller than the window, and leave full-window layouts their minimum canvas.
        double maxFit = Math.min(fbWidth * MAX_WINDOW_SHARE / contentWidth, fbHeight * MAX_WINDOW_SHARE / contentHeight);
        if (minVirtualWidth > 0)
            maxFit = Math.min(maxFit, fbWidth / (double) minVirtualWidth);
        if (minVirtualHeight > 0)
            maxFit = Math.min(maxFit, fbHeight / (double) minVirtualHeight);
        if (scale > maxFit) {
            double whole = Math.floor(maxFit);
            scale = whole >= 1 && whole >= maxFit * WHOLE_STEP_TOLERANCE_LOW ? whole : maxFit;
        }

        // Never smaller than the phone already was at the player's own GUI scale.
        if (scale < guiScale)
            scale = guiScale;
        double factor = scale / guiScale;
        if (!Double.isFinite(factor) || factor < 1.0)
            return 1.0;
        if (factor > MAX_FACTOR)
            factor = MAX_FACTOR;
        // Snap tiny float noise so "same size as vanilla" really is a no-op.
        if (factor - 1.0 < 1e-4)
            return 1.0;
        return factor;
    }

    private static double preferWholeStep(double ideal) {
        double low = Math.floor(ideal);
        if (low >= 1 && low >= ideal * WHOLE_STEP_TOLERANCE_LOW)
            return low;
        double high = low + 1;
        if (high <= ideal * WHOLE_STEP_TOLERANCE_HIGH)
            return high;
        return ideal;
    }

    /** Sanitizes whatever a PhoneScreen reports, so a broken value can never reach a pose/mouse transform. */
    public static double sanitize(double factor) {
        if (!Double.isFinite(factor) || factor <= 1.0)
            return 1.0;
        return Math.min(factor, MAX_FACTOR);
    }

    /** The zoom factor of the given screen, 1 for anything that isn't a (scaled) phone screen. */
    public static double factorOf(Screen screen) {
        if (screen instanceof PhoneScreen phoneScreen) {
            try {
                return sanitize(phoneScreen.phoneGuiScale());
            } catch (RuntimeException e) {
                return 1.0;
            }
        }
        return 1.0;
    }

    /** Zoom factor of the screen currently shown, 1 if none or not a phone screen. */
    public static double currentScreenFactor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return 1.0;
        return factorOf(mc./*$ mc_get_screen {*/screen/*$}*/);
    }

    /** Virtual size for a real GUI-scaled size: always large enough that size * factor covers the window. */
    public static int toVirtual(int guiScaled, double factor) {
        if (factor <= 1.0)
            return guiScaled;
        return Math.max(1, (int) Math.ceil(guiScaled / factor));
    }

    /** Shrinks the screen's own width/height to the virtual canvas, to be called at the very start of
     * the screen's init(), before anything is laid out from width/height. Always recomputed from the real
     * window (never from the screen's current, possibly already virtual, size). */
    public static void applyVirtualSize(Screen screen, double factor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null)
            return;
        Window window = mc.getWindow();
        screen.width = toVirtual(window.getGuiScaledWidth(), factor);
        screen.height = toVirtual(window.getGuiScaledHeight(), factor);
    }

    /** Converts a GUI-scaled mouse coordinate from vanilla into the current phone screen's virtual space -
     * used by PhoneScaleMouseMixin through the window's scaled size (the mouse handler computes
     * {@code pos * guiScaledSize / screenSize}; feeding it the virtual size yields virtual coordinates). */
    public static int virtualGuiScaledSize(int guiScaled) {
        return toVirtual(guiScaled, currentScreenFactor());
    }

    /** Same conversion, for 1.21.10+ where the mouse handler already returns a GUI-scaled double. */
    public static double toVirtualCoordinate(double guiCoordinate) {
        double factor = currentScreenFactor();
        return factor <= 1.0 ? guiCoordinate : guiCoordinate / factor;
    }

    /** The mouse position in the given screen's virtual space, recomputed from the raw cursor position so
     * it is right regardless of what the caller passed (vanilla's coordinates are GUI-scaled on older
     * versions and already virtual on 1.21.10+, where the mouse handler itself is converted). */
    public static int virtualMouse(boolean horizontal, int passed, double factor) {
        // NeoForge renders background screen layers with an impossible mouse position on purpose
        // ("not hovered") - keep it that way.
        if (passed == Integer.MAX_VALUE || passed == Integer.MIN_VALUE || factor <= 1.0)
            return passed;
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int screenSize = horizontal ? window.getScreenWidth() : window.getScreenHeight();
        if (screenSize <= 0)
            return passed;
        double raw = horizontal ? mc.mouseHandler.xpos() : mc.mouseHandler.ypos();
        int guiScaled = horizontal ? window.getGuiScaledWidth() : window.getGuiScaledHeight();
        return (int) Math.floor(raw * guiScaled / screenSize / factor);
    }

    /** Pushes the zoom for one render pass of {@code screen}. Returns true when something was pushed, in
     * which case {@link #endRender} must be called with the same graphics. */
    public static boolean beginRender(Screen screen, /*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        // A stale state (a previous pass that threw before its end hook) is simply dropped: it belonged to
        // another GuiGraphics, which vanilla never reuses across frames.
        activeGraphics = null;
        activeFactor = 1.0;
        double factor = factorOf(screen);
        if (factor <= 1.0)
            return false;
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.scale(guiGraphics, (float) factor, (float) factor);
        Minecraft mc = Minecraft.getInstance();
        activeVirtualWidth = toVirtual(mc.getWindow().getGuiScaledWidth(), factor);
        activeVirtualHeight = toVirtual(mc.getWindow().getGuiScaledHeight(), factor);
        activeFactor = factor;
        activeGraphics = guiGraphics;
        return true;
    }

    public static void endRender(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (activeGraphics != guiGraphics)
            return;
        activeGraphics = null;
        activeFactor = 1.0;
        GuiCompat.popPose(guiGraphics);
    }

    /** Zoom factor currently applied to this exact graphics instance, 1 if none. */
    public static double activeFactor(Object guiGraphics) {
        return guiGraphics != null && guiGraphics == activeGraphics ? activeFactor : 1.0;
    }

    public static int activeVirtualWidth(Object guiGraphics, int fallback) {
        return activeFactor(guiGraphics) > 1.0 ? activeVirtualWidth : fallback;
    }

    public static int activeVirtualHeight(Object guiGraphics, int fallback) {
        return activeFactor(guiGraphics) > 1.0 ? activeVirtualHeight : fallback;
    }
}
