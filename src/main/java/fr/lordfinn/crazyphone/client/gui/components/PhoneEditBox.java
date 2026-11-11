package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The phone's own text field, in the same pixel-art style as {@link PhoneButton}: a white field carved into
 * the screen (a soft shade along its top and left edges inside the frame), a 1 px frame with the corners cut,
 * teal while typing, dark slate otherwise, light grey and greyed out when it can't be edited.
 *
 * Only the editing logic is vanilla's (typing, selection, clipboard, cursor moves). The look is drawn here:
 * the frame, then the text WITHOUT a shadow (vanilla's EditBox always draws a dark drop shadow, which made
 * dark text on a white field look bold and smudged, and older versions have no switch for it), the
 * selection, and a dark blinking cursor (vanilla's light grey one barely shows on white). The text starts
 * 4 px in and is vertically centered, where a bordered vanilla field puts it; clicks are shifted by the same
 * 4 px since vanilla's own frame is turned off.
 */
public class PhoneEditBox extends EditBox {
    static final int FILL = 0xFFFFFFFF, FILL_LOCKED = 0xFFE6ECF2;
    static final int SHADE = 0xFFB8C7D2, SHADE_LOCKED = 0xFFC8D0D8;
    static final int FRAME = 0xFF5A6B7D, FRAME_FOCUSED = 0xFF007EA2, FRAME_LOCKED = 0xFF9AA6B2;
    static final int TEXT = 0xFF3A4456, TEXT_LOCKED = 0xFF6E7781;
    static final int SELECTION = 0xFFA6DAF6;
    /** Suggestion (grey text after the value) and hint (shown in an empty field). */
    static final int PLACEHOLDER = 0xFF9FB4C4;
    /** Where a bordered EditBox starts its text. */
    static final int TEXT_INSET = 4;
    private static final long BLINK_MS = 500;

    protected final Font textFont;
    private boolean editable = true;
    /** First character shown: the text scrolls so the cursor always stays visible. */
    private int scroll;
    protected String suggestion;
    protected Component hint;

    public PhoneEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.textFont = font;
        setBordered(false);
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        this.editable = editable;
    }

    @Override
    public void setSuggestion(String suggestion) {
        super.setSuggestion(suggestion);
        this.suggestion = suggestion;
    }

    @Override
    public void setHint(Component hint) {
        super.setHint(hint);
        this.hint = hint;
    }

    protected boolean isEditableField() {
        return editable;
    }

    //? if >=26 {
    /*@Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible())
            return;
        drawFrame(guiGraphics, getX(), getY(), getWidth(), getHeight(), isFocused(), editable);
        drawInner(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible())
            return;
        drawFrame(guiGraphics, getX(), getY(), getWidth(), getHeight(), isFocused(), editable);
        drawInner(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}

    /** The text, selection and cursor, inside the frame (relative to the current getX() / getY()). */
    protected void drawInner(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick) {
        String value = getValue();
        int length = value.length();
        int inner = Math.max(1, getWidth() - 2 * TEXT_INSET);
        int cursor = Math.max(0, Math.min(length, getCursorPosition()));

        // Keep the cursor in view, the way vanilla scrolls its own field.
        scroll = Math.max(0, Math.min(scroll, length));
        if (cursor < scroll)
            scroll = cursor;
        while (scroll < cursor && textFont.width(value.substring(scroll, cursor)) > inner)
            scroll++;
        String visible = textFont.plainSubstrByWidth(value.substring(scroll), inner);

        int tx = getX() + TEXT_INSET;
        int ty = getY() + (getHeight() - 8) / 2;

        // Selection: the highlighted text is next to the cursor, on one side or the other.
        String selected = getHighlighted();
        if (!selected.isEmpty()) {
            int start, end;
            if (cursor + selected.length() <= length && value.startsWith(selected, cursor)) {
                start = cursor;
                end = cursor + selected.length();
            } else {
                start = Math.max(0, cursor - selected.length());
                end = cursor;
            }
            int visStart = Math.max(start, scroll) - scroll;
            int visEnd = Math.min(end, scroll + visible.length()) - scroll;
            if (visEnd > visStart) {
                int x0 = tx + textFont.width(visible.substring(0, visStart));
                int x1 = tx + textFont.width(visible.substring(0, visEnd));
                guiGraphics.fill(x0, ty - 1, x1, ty + 9, SELECTION);
            }
        }

        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, visible, tx, ty, editable ? TEXT : TEXT_LOCKED, false);
        int textEnd = tx + textFont.width(visible);
        if (length == 0 && !isFocused() && hint != null) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, textFont.plainSubstrByWidth(hint.getString(), inner), tx, ty, PLACEHOLDER, false);
        } else if (suggestion != null && !suggestion.isEmpty() && scroll + visible.length() == length) {
            int room = tx + inner - textEnd;
            if (room > 0)
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, textFont.plainSubstrByWidth(suggestion, room), textEnd, ty, PLACEHOLDER, false);
        }

        if (isFocused() && editable && (System.currentTimeMillis() / BLINK_MS) % 2 == 0) {
            int cx = tx + textFont.width(value.substring(scroll, cursor));
            guiGraphics.fill(cx, ty - 1, cx + 1, ty + 9, TEXT);
        }
    }

    /** The carved field: frame with cut corners, white fill, a shade along the inner top and left edges. */
    public static void drawFrame(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int w, int h, boolean focused, boolean editable) {
        int frame = !editable ? FRAME_LOCKED : focused ? FRAME_FOCUSED : FRAME;
        int fill = editable ? FILL : FILL_LOCKED;
        int shade = editable ? SHADE : SHADE_LOCKED;
        guiGraphics.fill(x + 1, y, x + w - 1, y + h, frame);
        guiGraphics.fill(x, y + 1, x + w, y + h - 1, frame);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, shade);
        guiGraphics.fill(x + 1, y + 2, x + 2, y + h - 1, shade);
    }

    // Clicks: vanilla measures the cursor position from the text start, 4 px in for a bordered field, which
    // this field is not (it draws its own frame), so the click is moved by those 4 px.
    //? if <1.21.10 {
    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX - TEXT_INSET, mouseY);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        super.onClick(new net.minecraft.client.input.MouseButtonEvent(event.x() - TEXT_INSET, event.y(), event.buttonInfo()), doubleClick);
    }
    *///?}
}
