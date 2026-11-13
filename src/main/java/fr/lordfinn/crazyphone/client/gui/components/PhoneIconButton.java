package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A small square {@link PhoneButton} showing one emoji (the bundled Pixel Twemoji font) in the middle: the
 * phone's flat button background in its role colour, the emoji in its own colours (no tint), centered to the
 * half pixel (a glyph's advance includes one pixel of spacing, so the drawn width is one less; an odd
 * leftover is split with a half pixel nudge). The icon comes from a supplier so a toggle can show its
 * current state.
 */
public class PhoneIconButton extends PhoneButton {
    private final Supplier<Component> icon;
    /** Optional white pixel art drawn instead of the emoji ('X' = pixel), for an icon that must stand out on
     * its role colour (the grey trash emoji was hard to see on red). */
    private String[] pixels;

    public PhoneIconButton(int x, int y, int size, Supplier<Component> icon, OnPress onPress, Style style) {
        super(x, y, size, size, Component.empty(), onPress, style);
        this.icon = icon;
    }

    /** White pixel art, centered to the half pixel, drawn instead of the emoji. */
    public PhoneIconButton withPixels(String... rows) {
        this.pixels = rows;
        return this;
    }

    /** A white trash can, 7 x 8. */
    public static final String[] TRASH = {
            "..XXX..",
            "XXXXXXX",
            ".......",
            ".XXXXX.",
            ".X.X.X.",
            ".X.X.X.",
            ".X.X.X.",
            ".XXXXX."};

    @Override
    public Component getMessage() {
        // Narration / tooltips still get the icon's text.
        return icon == null ? super.getMessage() : icon.get();
    }

    //? if >=26 {
    /*@Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawIcon(guiGraphics);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(guiGraphics);
        drawIcon(guiGraphics);
    }
    //?}

    private void drawPixels(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int w = pixels[0].length(), h = pixels.length;
        float x = getX() + (getWidth() - w) / 2f;
        float y = getY() + (getHeight() - h) / 2f;
        int color = this.active ? 0xFFFFFFFF : 0x99FFFFFF;
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, x, y);
        for (int row = 0; row < h; row++)
            for (int col = 0; col < w; col++)
                if (pixels[row].charAt(col) == 'X')
                    guiGraphics.fill(col, row, col + 1, row + 1, color);
        GuiCompat.popPose(guiGraphics);
    }

    private void drawIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (pixels != null) {
            drawPixels(guiGraphics);
            return;
        }
        Font font = Minecraft.getInstance().font;
        // Plain text of the icon: the emoji's own colours, whatever colour style the caller put on it.
        Component glyph = Component.literal(icon.get().getString());
        int drawnWidth = Math.max(0, font.width(glyph) - 1);
        float x = getX() + (getWidth() - drawnWidth) / 2f;
        int y = getY() + (getHeight() - 8) / 2;
        int whole = (int) Math.floor(x);
        GuiCompat.pushPose(guiGraphics);
        // The emoji glyphs are 9 px tall and start a pixel above the line: half a pixel lower centres them.
        GuiCompat.translate(guiGraphics, x - whole, 0.5f);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, glyph, whole, y, this.active ? 0xFFFFFFFF : 0x99FFFFFF, false);
        GuiCompat.popPose(guiGraphics);
    }
}
