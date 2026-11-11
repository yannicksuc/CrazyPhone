package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.network.chat.Component;
//? if neoforge && <1.21.10 {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

//? if neoforge && <1.21.10 {
@OnlyIn(Dist.CLIENT)
//?}
public class SmallTextEditBox extends PhoneEditBox {

    protected static final float SCALE_FACTOR = 0.75F; // Scale down the font size

    /**
     * The height actually requested by the caller (e.g. 14, to align with a 14px-tall sibling button).
     * The superclass EditBox only stores integer dimensions, and (int)(height / SCALE_FACTOR) then
     * re-scaled by SCALE_FACTOR at render time loses precision - e.g. requesting 14 stores 18
     * (truncated from 18.67) which renders at 18 * 0.75 = 13.5px, half a pixel short. renderWidget
     * compensates by shifting the render-only translation down by that shortfall so the BOTTOM edge
     * still lands exactly where the caller asked for, without changing the (still-integer) click bounds.
     */
    protected int requestedHeight;
    /** Same for the width: the carved frame is drawn at the size the caller asked for, unscaled, so its
     * 1 px edges stay sharp; only the text inside is drawn at SCALE_FACTOR. */
    protected int requestedWidth;

    /** Changes the drawn width (the message field is shorter while the attach buttons sit next to it). */
    public void setRequestedWidth(int width) {
        if (width == requestedWidth)
            return;
        this.requestedWidth = width;
        setWidth((int) (width * (1 / SCALE_FACTOR)));
    }

    public int getRequestedWidth() {
        return requestedWidth;
    }

    /** Changes the drawn height (the field grows or shrinks, see MessageTextBox). */
    public void setRequestedHeight(int height) {
        this.requestedHeight = height;
        setHeight((int) (height * (1 / SCALE_FACTOR)));
    }

    public SmallTextEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, (int)(width * (1/SCALE_FACTOR)), (int)(height * (1/SCALE_FACTOR)), message);
        this.requestedHeight = height;
        this.requestedWidth = width;
    }

    //? if >=26 {
    /*@Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible())
            return;
        int x = this.getX();
        int y = this.getY();
        PhoneEditBox.drawFrame(guiGraphics, x, y, requestedWidth, requestedHeight, isFocused(), isEditableField());
        float renderedHeight = this.getHeight() * SCALE_FACTOR;
        float bottomAlignOffset = requestedHeight - renderedHeight;
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, x, y + bottomAlignOffset);
        GuiCompat.scale(guiGraphics, SCALE_FACTOR, SCALE_FACTOR);

        this.setX(0);
        this.setY(0);

        drawInner(guiGraphics, (int)(mouseX * (1/SCALE_FACTOR) - x * (1/SCALE_FACTOR)),(int)(mouseY * (1/SCALE_FACTOR) - y * (1/SCALE_FACTOR)), partialTick);

        GuiCompat.popPose(guiGraphics);
        this.setX(x);
        this.setY(y);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible())
            return;
        int x = this.getX();
        int y = this.getY();
        PhoneEditBox.drawFrame(guiGraphics, x, y, requestedWidth, requestedHeight, isFocused(), isEditableField());
        float renderedHeight = this.getHeight() * SCALE_FACTOR;
        float bottomAlignOffset = requestedHeight - renderedHeight;
        GuiCompat.pushPose(guiGraphics);
		GuiCompat.translate(guiGraphics, x, y + bottomAlignOffset); // move to position, bottom-aligned to the requested height
		GuiCompat.scale(guiGraphics, SCALE_FACTOR, SCALE_FACTOR); // shrink by 50%

		// Render at 0,0 now that we've translated
		this.setX(0);
		this.setY(0);

        // Adjust the rendering position to account for scaling
        drawInner(guiGraphics, (int)(mouseX * (1/SCALE_FACTOR) - x * (1/SCALE_FACTOR)),(int)(mouseY * (1/SCALE_FACTOR) - y * (1/SCALE_FACTOR)), partialTick);

        GuiCompat.popPose(guiGraphics);
        this.setX(x);
		this.setY(y);
    }
    //?}

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
