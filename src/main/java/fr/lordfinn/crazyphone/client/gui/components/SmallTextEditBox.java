package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

//? if <1.21.10 {
@OnlyIn(Dist.CLIENT)
//?}
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
        GuiCompat.pushPose(guiGraphics);
		GuiCompat.translate(guiGraphics, x, y + bottomAlignOffset); // move to position, bottom-aligned to the requested height
		GuiCompat.scale(guiGraphics, SCALE_FACTOR, SCALE_FACTOR); // shrink by 50%

		// Render at 0,0 now that we've translated
		this.setX(0);
		this.setY(0);

        // Adjust the rendering position to account for scaling
        super.renderWidget(guiGraphics, (int)(mouseX * (1/SCALE_FACTOR) - x * (1/SCALE_FACTOR)),(int)(mouseY * (1/SCALE_FACTOR) - y * (1/SCALE_FACTOR)), partialTick);

        GuiCompat.popPose(guiGraphics);
        this.setX(x);
		this.setY(y);
    }

    //? if <1.21.10 {
    @Override
    public void onClick(double mouseX, double mouseY) {
        // Adjust the click position to account for scaling
        super.onClick((mouseX - this.getX())*(1/SCALE_FACTOR) + this.getX(), (mouseY - this.getY())*(1/SCALE_FACTOR) + this.getY());
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double scaledX = (event.x() - this.getX()) * (1 / SCALE_FACTOR) + this.getX();
        double scaledY = (event.y() - this.getY()) * (1 / SCALE_FACTOR) + this.getY();
        super.onClick(new net.minecraft.client.input.MouseButtonEvent(scaledX, scaledY, event.buttonInfo()), doubleClick);
    }
    *///?}

    //? if <1.21.10 {
    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease((mouseX - this.getX())*(1/SCALE_FACTOR) + this.getX(), (mouseY - this.getY())*(1/SCALE_FACTOR) + this.getY());
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        double scaledX = (event.x() - this.getX()) * (1 / SCALE_FACTOR) + this.getX();
        double scaledY = (event.y() - this.getY()) * (1 / SCALE_FACTOR) + this.getY();
        super.onRelease(new net.minecraft.client.input.MouseButtonEvent(scaledX, scaledY, event.buttonInfo()));
    }
    *///?}
}
