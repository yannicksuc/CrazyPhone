package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;

/** Shared by the three call screens' participant-name line: centered when it fits, otherwise a marquee
 * (slow scroll back and forth, clipped to the phone screen) instead of spilling past the phone's edges. */
public final class CallScreenText {
    private static final long PAUSE_MILLIS = 800;
    private static final double PIXELS_PER_SECOND = 22.0;

    private CallScreenText() {
    }

    public static void drawCenteredOrScrolling(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, text, centerX - textWidth / 2, y, color, false);
            return;
        }
        int overflow = textWidth - maxWidth;
        long travelMillis = (long) (overflow / PIXELS_PER_SECOND * 1000.0);
        long cycle = 2 * (PAUSE_MILLIS + travelMillis);
        long t = System.currentTimeMillis() % cycle;
        double progress;
        if (t < PAUSE_MILLIS)
            progress = 0;
        else if (t < PAUSE_MILLIS + travelMillis)
            progress = (double) (t - PAUSE_MILLIS) / travelMillis;
        else if (t < 2 * PAUSE_MILLIS + travelMillis)
            progress = 1;
        else
            progress = 1.0 - (double) (t - 2 * PAUSE_MILLIS - travelMillis) / travelMillis;
        int left = centerX - maxWidth / 2;
        guiGraphics.enableScissor(left, y - 1, left + maxWidth, y + font.lineHeight + 1);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, text, left - (int) Math.round(overflow * progress), y, color, false);
        guiGraphics.disableScissor();
    }
}
