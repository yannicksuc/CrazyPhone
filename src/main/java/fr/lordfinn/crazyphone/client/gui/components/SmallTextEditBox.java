package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SmallTextEditBox extends EditBox {

    private static final float SCALE_FACTOR = 0.75F; // Scale down the font size

    /**
     * The height actually requested by the caller (e.g. 14, to align with a 14px-tall sibling button).
     * The superclass EditBox only stores integer dimensions, and (int)(height / SCALE_FACTOR) then
     * re-scaled by SCALE_FACTOR at render time loses precision - e.g. requesting 14 stores 18
     * (truncated from 18.67) which renders at 18 * 0.75 = 13.5px, half a pixel short. renderWidget
     * compensates by shifting the render-only translation down by that shortfall so the BOTTOM edge
     * still lands exactly where the caller asked for, without changing the (still-integer) click bounds.
     */
    private final int requestedHeight;

    public SmallTextEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, (int)(width * (1/SCALE_FACTOR)), (int)(height * (1/SCALE_FACTOR)), message);
        this.requestedHeight = height;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        int x = this.getX();
        int y = this.getY();
        float renderedHeight = this.getHeight() * SCALE_FACTOR;
        float bottomAlignOffset = requestedHeight - renderedHeight;
        guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y + bottomAlignOffset, 0); // move to position, bottom-aligned to the requested height
		guiGraphics.pose().scale(SCALE_FACTOR, SCALE_FACTOR, 1.0f); // shrink by 50%

		// Render at 0,0 now that we've translated
		this.setX(0);
		this.setY(0);

        // Adjust the rendering position to account for scaling
        super.renderWidget(guiGraphics, (int)(mouseX * (1/SCALE_FACTOR) - x * (1/SCALE_FACTOR)),(int)(mouseY * (1/SCALE_FACTOR) - y * (1/SCALE_FACTOR)), partialTick);

        guiGraphics.pose().popPose();
        this.setX(x);
		this.setY(y);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // Adjust the click position to account for scaling
//        int w = this.getWidth();
//        int h = this.getHeight();
//        this.setWidth((int)(w * SCALE_FACTOR));
//        this.setHeight((int)(h * SCALE_FACTOR));
        super.onClick((mouseX - this.getX())*(1/SCALE_FACTOR) + this.getX(), (mouseY - this.getY())*(1/SCALE_FACTOR) + this.getY());
//        this.setWidth(w);
//        this.setHeight(h);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease((mouseX - this.getX())*(1/SCALE_FACTOR) + this.getX(), (mouseY - this.getY())*(1/SCALE_FACTOR) + this.getY());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
