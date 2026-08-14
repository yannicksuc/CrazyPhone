package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

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
    /** Optional icon drawn at the left edge, inside the bubble (used by system messages) - reserves an
     * extra 18px of left padding for the text so it doesn't overlap. EMPTY (default) draws nothing. */
    private final ItemStack leadingIcon;

    private int minHeight = 0;

    public WrappedTextWidget(Font font, int x, int y, int width, Component message,
                             float textScale, int textColor, int backgroundColor,
                             int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
        this(font, x, y, width, message, textScale, textColor, backgroundColor,
                paddingLeft, paddingRight, paddingTop, paddingBottom, ItemStack.EMPTY);
    }

    public WrappedTextWidget(Font font, int x, int y, int width, Component message,
                             float textScale, int textColor, int backgroundColor,
                             int paddingLeft, int paddingRight, int paddingTop, int paddingBottom,
                             ItemStack leadingIcon) {
        super(x, y, width, 0, message); // Height 0 for now, we'll set correct height below
        this.font = font;
        this.textScale = textScale;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.leadingIcon = leadingIcon == null ? ItemStack.EMPTY : leadingIcon;
        this.paddingLeft = paddingLeft + (this.leadingIcon.isEmpty() ? 0 : 18);
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
        return Math.max(Math.max(minHeight, leadingIcon.isEmpty() ? 0 : 18), textHeight);
    }

    /**
     * Set the minimum height for this widget.
     * Automatically recalculates and updates the widget height.
     */
    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        this.height = calculateHeight();
    }

    /** The scale this bubble's own text renders at - read by MessageWidget so custom-drawn content (e.g.
     * the voice message widget) can match the surrounding chat text's size instead of drawing at 1:1. */
    public float getTextScale() {
        return textScale;
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

        if (!leadingIcon.isEmpty()) {
            int iconY = renderY + (renderHeight - 16) / 2;
            guiGraphics.renderItem(leadingIcon, renderX + 2, iconY);
        }

        List<FormattedCharSequence> lines = font.split(getMessage(), (int) ((width - paddingLeft - paddingRight) / textScale));
        // Vertically centers the text block within the box - matters when the box is taller than the text
        // needs (e.g. a leading icon forcing a minimum height for a single short line), where anchoring at
        // paddingTop alone left the text pinned to the top with empty space below it.
        int textBlockHeight = Math.round(lines.size() * font.lineHeight * textScale);
        int textStartY = renderY + Math.max(paddingTop, (renderHeight - textBlockHeight) / 2);

        GuiCompat.pushPose(guiGraphics);
        GuiCompat.scale(guiGraphics, textScale, textScale);

        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lineY = (int) (textStartY / textScale) + i * font.lineHeight;
            guiGraphics.drawString(font, line, (int) ((getX() + paddingLeft) / textScale), lineY, textColor, false);
        }

        GuiCompat.popPose(guiGraphics);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Optional: Implement accessibility narration if needed
    }
}
