package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class WrappedTextWidget extends AbstractWidget {

    private final Font font;
    private final float textScale;
    private int textColor = 0xFFFFFF;
    private int backgroundColor = 0x80000000;

    private final int paddingLeft;
    private final int paddingRight;
    private final int paddingTop;
    private final int paddingBottom;

    private int minHeight = 0;

    public WrappedTextWidget(Font font, int x, int y, int width, Component message,
                             float textScale, int textColor, int backgroundColor,
                             int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
        super(x, y, width, 0, message); // Height 0 for now, we'll set correct height below
        this.font = font;
        this.textScale = textScale;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;

        this.height = calculateHeight();
    }

    // Optional constructor for default padding
    public WrappedTextWidget(Font font, int x, int y, int width, Component message,
                             float textScale, int textColor, int backgroundColor) {
        this(font, x, y, width, message, textScale, textColor, backgroundColor,
             3, 3, 4, 3);
    }

    private int calculateHeight() {
        int effectiveWidth = (int)((width - paddingLeft - paddingRight) / textScale);
        List<FormattedCharSequence> lines = font.split(getMessage(), effectiveWidth);
        int textHeight = Math.round(Math.max(1, lines.size()) * font.lineHeight * textScale) + paddingTop + paddingBottom;
        return Math.max(minHeight, textHeight);
    }

    /**
     * Set the minimum height for this widget.
     * Automatically recalculates and updates the widget height.
     */
    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        this.height = calculateHeight();
    }

    /**
     * Update message and recalculate height to respect minHeight.
     */
    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.height = calculateHeight();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int renderX = getX();
        int renderY = getY();
        int renderWidth = width;
        int renderHeight = height;

        guiGraphics.fill(renderX, renderY, renderX + renderWidth, renderY + renderHeight, backgroundColor);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(textScale, textScale, 1.0F);

        List<FormattedCharSequence> lines = font.split(getMessage(), (int) ((width - paddingLeft - paddingRight) / textScale));
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineY = (int) ((getY() + paddingTop) / textScale) + i * font.lineHeight;
            if (lineY + font.lineHeight > renderY / textScale && lineY < (renderY + renderHeight) / textScale) {
                guiGraphics.drawString(font, line, (int) ((getX() + paddingLeft) / textScale), lineY, textColor, false);
            }
        }

        guiGraphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Optional: Implement accessibility narration if needed
    }
}
