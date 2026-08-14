package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Every phone "page" is backed by its own AbstractContainerMenu/Screen pair, so navigating within the
 * phone (home -> contacts -> conversation, sending a message, etc.) goes through
 * Player#openMenu(...), which ALWAYS closes the current container before opening the new one - as TWO
 * SEPARATE Minecraft.setScreen() calls: setScreen(null) (closing, triggers MouseHandler#grabMouse) then
 * setScreen(newScreen) (reopening, triggers MouseHandler#releaseMouse), each centering the OS cursor.
 *
 * IMPORTANT: vanilla's Minecraft#setScreen only constructs/posts a ScreenEvent.Opening
 * "if (newScreen != null)" - it is NEVER fired for the close half (setScreen(null)) of that cycle. An
 * earlier version of this class tried to detect the close via Opening(phoneScreen, null), which can
 * never match. ScreenEvent.Closing is what actually fires unconditionally whenever a non-null screen is
 * being replaced by anything (including null) - that's the correct hook for the close half.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class PhoneCursorStabilizer {
    private static boolean pendingRestore = false;
    private static boolean awaitingPhoneReopen = false;
    private static double savedX;
    private static double savedY;

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof PhoneScreen) {
            // Might be the close-half of a phone menu switch, or a genuine standalone close - we can't
            // tell yet. Capture the real cursor position now, before grabMouse() warps it.
            captureCursorPosition();
            awaitingPhoneReopen = true;
        } else {
            awaitingPhoneReopen = false;
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        boolean wasAwaitingReopen = awaitingPhoneReopen;
        awaitingPhoneReopen = false;

        if (wasAwaitingReopen && event.getNewScreen() instanceof PhoneScreen) {
            // Reopening, immediately following a phone-screen close: this is a phone-internal
            // navigation, not a standalone open - restore what was captured a moment ago.
            pendingRestore = true;
        } else {
            pendingRestore = false;
        }
    }

    private static void captureCursorPosition() {
        //? if <1.21.10 {
        long window = Minecraft.getInstance().getWindow().getWindow();
        //? } else {
        /*long window = Minecraft.getInstance().getWindow().handle();
        *///?}
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        savedX = x[0];
        savedY = y[0];
    }

    /** Called by MouseHandlerCursorMixin right after vanilla's own centering logic runs. */
    public static boolean consumePendingRestore(double[] outXY) {
        if (!pendingRestore)
            return false;
        pendingRestore = false;
        outXY[0] = savedX;
        outXY[1] = savedY;
        return true;
    }
}
