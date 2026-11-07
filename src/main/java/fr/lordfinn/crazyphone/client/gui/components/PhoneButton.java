package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * The phone's own flat button, in the style of the yellow "new number" button (reset.png): a flat fill, a
 * lighter line on the top and left edges, a darker one on the bottom and right, the corners cut by one pixel
 * like the rest of the phone's pixel art. One {@link Style} per role so every screen reads the same: yellow
 * for the main action, green to confirm / answer, red to cancel / hang up / delete, blue to send, grey for a
 * secondary action. Hovered: a lighter fill; disabled: grey whatever the role.
 *
 * Drawn entirely by hand (no vanilla sprite), the same way on every version: {@code renderWidget} before
 * 26, {@code extractContents} from 26 (where AbstractButton no longer draws a default background itself).
 * Built like a vanilla button: {@code PhoneButton.builder(Style.CONFIRM, label, onPress).bounds(...).build()}.
 */
public class PhoneButton extends Button {
    public enum Style {
        /** The screen's main action (yellow, dark label), like the phone's yellow header and reset button. */
        PRIMARY(0xFFFED200, 0xFFFFEF00, 0xFFFFA500, 0xFF3A4456, 0x00000000),
        /** Validate, answer, accept. */
        CONFIRM(0xFF2ECC71, 0xFF6FE39C, 0xFF1E9E55, 0xFFFFFFFF, 0xFF1E9E55),
        /** Cancel, decline, hang up, delete. */
        DANGER(0xFFE74C3C, 0xFFF08A7E, 0xFFB83227, 0xFFFFFFFF, 0xFFB83227),
        /** Send. */
        INFO(0xFF0084FF, 0xFF5CB0FF, 0xFF0062C4, 0xFFFFFFFF, 0xFF0062C4),
        /** A secondary action next to a main one (back, cancel a form). */
        NEUTRAL(0xFFC8D2DC, 0xFFE6ECF2, 0xFF9AA6B2, 0xFF3A4456, 0x00000000);

        final int fill, light, dark, text, textShade;

        Style(int fill, int light, int dark, int text, int textShade) {
            this.fill = fill;
            this.light = light;
            this.dark = dark;
            this.text = text;
            this.textShade = textShade;
        }
    }

    private static final int DISABLED_FILL = 0xFFB4BCC4, DISABLED_LIGHT = 0xFFC8CED4, DISABLED_DARK = 0xFF959DA6,
            DISABLED_TEXT = 0xFF6E7781;

    private final Style style;

    protected PhoneButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    public static Builder builder(Style style, Component message, OnPress onPress) {
        return new Builder(style, message, onPress);
    }

    //? if >=26 {
    /*@Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        draw(guiGraphics);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        draw(guiGraphics);
    }
    //?}

    private void draw(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int x0 = getX(), y0 = getY(), x1 = x0 + getWidth(), y1 = y0 + getHeight();
        boolean enabled = this.active;
        int fill = enabled ? (isHoveredOrFocused() ? lighten(style.fill) : style.fill) : DISABLED_FILL;
        int light = enabled ? style.light : DISABLED_LIGHT;
        int dark = enabled ? style.dark : DISABLED_DARK;

        // Body with the four corners cut.
        guiGraphics.fill(x0 + 1, y0, x1 - 1, y1, fill);
        guiGraphics.fill(x0, y0 + 1, x1, y1 - 1, fill);
        // Light top and left edges, dark bottom and right edges.
        guiGraphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, light);
        guiGraphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, light);
        guiGraphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, dark);
        guiGraphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, dark);

        Font font = Minecraft.getInstance().font;
        Component label = getMessage();
        int textWidth = font.width(label);
        int available = getWidth() - 6;
        float scale = textWidth > available ? Math.max(0.5f, available / (float) textWidth) : 1f;
        int color = enabled ? style.text : DISABLED_TEXT;
        boolean shade = enabled && (style.textShade >>> 24) != 0;
        if (scale == 1f) {
            // Whole pixels, so the label stays crisp; glyphs are 8 px tall (7 + descent row).
            int tx = x0 + (getWidth() - textWidth + 1) / 2;
            int ty = y0 + (getHeight() - 8 + 1) / 2;
            drawLabel(guiGraphics, font, label, tx, ty, color, shade);
            return;
        }
        // Too long for the button: scaled down to fit, around the button's center.
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, x0 + getWidth() / 2f, y0 + (getHeight() - 8 * scale) / 2f);
        GuiCompat.scale(guiGraphics, scale, scale);
        drawLabel(guiGraphics, font, label, -textWidth / 2, 0, color, shade);
        GuiCompat.popPose(guiGraphics);
    }

    private void drawLabel(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Font font, Component label, int x, int y, int color, boolean shade) {
        // A one pixel shade under the label, like the grey under the white arrow of reset.png.
        if (shade)
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, label, x, y + 1, style.textShade, false);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, label, x, y, color, false);
    }

    private static int lighten(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (255 - r) / 4;
        g += (255 - g) / 4;
        b += (255 - b) / 4;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Same chain as vanilla's Button.Builder, for a drop-in replacement. */
    public static final class Builder {
        private final Style style;
        private final Component message;
        private final OnPress onPress;
        private Tooltip tooltip;
        private int x, y, width = 150, height = 20;

        private Builder(Style style, Component message, OnPress onPress) {
            this.style = style;
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            return pos(x, y).size(width, height);
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public PhoneButton build() {
            PhoneButton button = new PhoneButton(x, y, width, height, message, onPress, style);
            if (tooltip != null)
                button.setTooltip(tooltip);
            return button;
        }
    }
}
