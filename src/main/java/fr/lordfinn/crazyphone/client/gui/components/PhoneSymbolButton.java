package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.network.chat.Component;

/**
 * A small square {@link PhoneButton} showing a plus or a minus drawn in pixels rather than with the font: the
 * font's "+" and "-" are 5 px wide (odd) in an even button and sit high in their line, so they could never
 * be centered. These are 6 px wide with 2 px strokes: even sizes, exactly centered in an even button.
 */
public class PhoneSymbolButton extends PhoneButton {
    public enum Symbol { PLUS, MINUS }

    private static final int SIZE = 6, STROKE = 2;
    private final Symbol symbol;

    public PhoneSymbolButton(int x, int y, int size, Symbol symbol, Component narration, OnPress onPress, Style style) {
        super(x, y, size, size, narration, onPress, style);
        this.symbol = symbol;
    }

    //? if >=26 {
    /*@Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawSymbol(guiGraphics);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawSymbol(guiGraphics);
    }
    //?}

    private void drawSymbol(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int color = this.active ? 0xFF3A4456 : 0xFF6E7781;
        int x0 = getX() + (getWidth() - SIZE) / 2, y0 = getY() + (getHeight() - SIZE) / 2;
        int mid = (SIZE - STROKE) / 2;
        // Horizontal stroke (both symbols), vertical stroke (plus only).
        guiGraphics.fill(x0, y0 + mid, x0 + SIZE, y0 + mid + STROKE, color);
        if (symbol == Symbol.PLUS)
            guiGraphics.fill(x0 + mid, y0, x0 + mid + STROKE, y0 + SIZE, color);
    }
}
