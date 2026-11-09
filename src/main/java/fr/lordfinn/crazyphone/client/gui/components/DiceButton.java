package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.network.chat.Component;

/**
 * The "new random number" button of the registration screen: a yellow phone button with a white die on
 * it that keeps rolling through its six faces, so it reads as "pick at random".
 *
 * Everything is centered to the pixel: the button is 14 x 14, the die 10 x 10 in its middle (corners cut),
 * and the pips are 2 x 2 on columns / rows 1-2, 4-5 and 7-8 of the die, 1 px apart, so the middle pip (4-5)
 * sits exactly in the center and every face is symmetric.
 */
public class DiceButton extends PhoneButton {
    private static final int DIE = 0xFFFFFFFF, PIP = 0xFF3A4456;
    private static final int DIE_SIZE = 10;
    private static final long FACE_MS = 350;

    // Pip positions inside the die (top-left of each 2 x 2 pip).
    private static final int[][] TL = {{1, 1}}, TR = {{7, 1}}, ML = {{1, 4}}, MR = {{7, 4}}, C = {{4, 4}},
            BL = {{1, 7}}, BR = {{7, 7}};
    private static final int[][][] FACES = {
            join(C),
            join(TL, BR),
            join(TL, C, BR),
            join(TL, TR, BL, BR),
            join(TL, TR, C, BL, BR),
            join(TL, TR, ML, MR, BL, BR)};

    public DiceButton(int x, int y, int size, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, Style.PRIMARY);
    }

    private static int[][] join(int[][]... pips) {
        int[][] out = new int[pips.length][];
        for (int i = 0; i < pips.length; i++)
            out[i] = pips[i][0];
        return out;
    }

    //? if >=26 {
    /*@Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        drawDie(guiGraphics);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        drawDie(guiGraphics);
    }
    //?}

    private void drawDie(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int x0 = getX() + (getWidth() - DIE_SIZE) / 2, y0 = getY() + (getHeight() - DIE_SIZE) / 2;
        int x1 = x0 + DIE_SIZE, y1 = y0 + DIE_SIZE;
        guiGraphics.fill(x0 + 1, y0, x1 - 1, y1, DIE);
        guiGraphics.fill(x0, y0 + 1, x1, y1 - 1, DIE);
        int face = (int) ((System.currentTimeMillis() / FACE_MS) % FACES.length);
        int pip = this.active ? PIP : 0xFF9AA6B2;
        for (int[] p : FACES[face])
            guiGraphics.fill(x0 + p[0], y0 + p[1], x0 + p[0] + 2, y0 + p[1] + 2, pip);
    }
}
