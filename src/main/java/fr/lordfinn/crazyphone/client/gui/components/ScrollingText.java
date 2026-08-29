package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;

/**
 * Draws a single line of text that scrolls horizontally within a fixed-width, scissor-cropped rectangle
 * when it's too wide to fit, and just draws normally when it isn't - used for page titles across the mod
 * (see CrazyPhoneDefaultScreenScreen#renderHeader) so a long contact/group name never overlaps a header
 * icon or spills past the phone's frame instead of being cropped.
 *
 * Stateless by design: the scroll position is a deterministic function of wall-clock time rather than
 * per-instance state, so any screen can call {@link #render} directly every frame with no setup - multiple
 * differently-scrolling titles on screen at once (unlikely here, but not precluded) stay independent for
 * free since each is keyed only by its own text/width, never a shared mutable field.
 */
public final class ScrollingText {
    /** Blank gap, in pixels, between the end of one copy of the text and the start of the next when it
     * loops - without this the wrap-around reads as the text abruptly jumping rather than continuously
     * scrolling. */
    private static final int LOOP_GAP_PX = 24;
    private static final float PIXELS_PER_SECOND = 20f;
    /** How long the text dwells unscrolled at its start position before each scroll pass - mirrors the
     * pause most real marquee/ticker UIs use so a short glance still reads the beginning. */
    private static final long PAUSE_MS = 1200;

    private ScrollingText() {
    }

    /** Draws {@code text} left-aligned at ({@code x}, {@code y}), scrolling within {@code width} pixels if
     * it doesn't fit, else drawn as-is (not centered - callers that want centering when it fits should
     * check {@code font.width(text) <= width} themselves and use a centered draw in that branch instead). */
    public static void render(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Font font, Component text, int x, int y, int width, int color) {
        int textWidth = font.width(text);
        if (textWidth <= width) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, text, x, y, color, false);
            return;
        }

        guiGraphics.enableScissor(x, y, x + width, y + font.lineHeight + 1);
        int loopWidth = textWidth + LOOP_GAP_PX;
        long cycleMs = PAUSE_MS + Math.round(loopWidth / PIXELS_PER_SECOND * 1000);
        long t = System.currentTimeMillis() % cycleMs;
        int offset = t < PAUSE_MS ? 0 : Math.round((t - PAUSE_MS) / 1000f * PIXELS_PER_SECOND);

        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, text, x - offset, y, color, false);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, text, x - offset + loopWidth, y, color, false);
        guiGraphics.disableScissor();
    }
}
