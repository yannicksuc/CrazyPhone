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

    public PhoneIconButton(int x, int y, int size, Supplier<Component> icon, OnPress onPress, Style style) {
        super(x, y, size, size, Component.empty(), onPress, style);
        this.icon = icon;
    }

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

    private void drawIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        Font font = Minecraft.getInstance().font;
        // Plain text of the icon: the emoji's own colours, whatever colour style the caller put on it.
        Component glyph = Component.literal(icon.get().getString());
        int drawnWidth = Math.max(0, font.width(glyph) - 1);
        float x = getX() + (getWidth() - drawnWidth) / 2f;
        int y = getY() + (getHeight() - 8) / 2;
        int whole = (int) Math.floor(x);
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, x - whole, 0f);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, glyph, whole, y, this.active ? 0xFFFFFFFF : 0x99FFFFFF, false);
        GuiCompat.popPose(guiGraphics);
    }
}
