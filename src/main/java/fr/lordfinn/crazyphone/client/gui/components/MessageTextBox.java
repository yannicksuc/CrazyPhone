package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import fr.lordfinn.crazyphone.utils.GuiCompat;

/**
 * The conversation's message field, on several lines: Shift+Enter starts a new line (the screen calls
 * {@link #insertNewline()}), long lines wrap at the field's width, the field grows upward one line at a time up
 * to {@link #MAX_VISIBLE_LINES} (the screen sets its height from {@link #wantedHeight()}), and past that the text
 * scrolls inside it so the cursor line always shows. Up and Down move between lines ({@link #moveVertically}).
 *
 * Editing itself stays vanilla's (typing, deleting, selection, clipboard): the value is one string, with "\n"
 * between lines. Only the drawing, the click position and the up / down moves know about lines.
 */
public class MessageTextBox extends SmallTextEditBox {
    public static final int MAX_VISIBLE_LINES = 5;
    /** One line of text is 7 px of the phone screen (the text is drawn at 75 %), the field 14 px for one line. */
    public static final int ONE_LINE_HEIGHT = 14;
    public static final int LINE_STEP = 7;
    /** The same step in the field's own (unscaled text) units. */
    private static final float STEP = LINE_STEP / SCALE_FACTOR;
    /** Top of the first line inside the field, in its own units (where a one line field centers its text). */
    private static final float TOP = 5f;

    private record Line(int start, int end) {
    }

    /** First visual line shown (the text scrolls once it has more lines than the field shows). */
    private int firstLine;
    /** Room kept free inside the field on the left and right, in phone pixels, for the icons drawn in it
     * (the attach + on the left, the emoji button on the right): the text stays between them. */
    private int leftInset, rightInset;

    public void setInsets(int left, int right) {
        this.leftInset = left;
        this.rightInset = right;
    }

    /** Where the text starts, in the field's own units. */
    private float textLeft() {
        return TEXT_INSET + leftInset / SCALE_FACTOR;
    }

    public MessageTextBox(Font font, int x, int y, int width, Component message) {
        super(font, x, y, width, ONE_LINE_HEIGHT, message);
    }

    /** The height the field wants for its text: one more line step per line, at most MAX_VISIBLE_LINES. */
    public int wantedHeight() {
        int lines = Math.min(MAX_VISIBLE_LINES, layout().size());
        return ONE_LINE_HEIGHT + (lines - 1) * LINE_STEP;
    }

    /** Starts a new line at the cursor (replacing the selection, if any). */
    public void insertNewline() {
        String value = getValue();
        int[] sel = selection();
        String next = value.substring(0, sel[0]) + "\n" + value.substring(sel[1]);
        if (next.length() > maxLength())
            return;
        setValue(next);
        setCursorPosition(sel[0] + 1);
        setHighlightPos(sel[0] + 1);
    }

    /** Up (-1) or down (+1) one line, keeping the same horizontal place; extends the selection with Shift.
     * False when there is no line in that direction (the key is then left to the screen). */
    public boolean moveVertically(int direction, boolean select) {
        List<Line> lines = layout();
        int cursor = getCursorPosition();
        int index = lineOf(lines, cursor);
        int target = index + direction;
        if (target < 0 || target >= lines.size())
            return false;
        String value = getValue();
        int x = textFont.width(value.substring(lines.get(index).start(), cursor));
        int pos = indexAtX(value, lines.get(target), x);
        setCursorPosition(pos);
        if (!select)
            setHighlightPos(pos);
        return true;
    }

    // ---- layout ----

    private int innerWidth() {
        return Math.max(1, (int) (getWidth() - 2 * TEXT_INSET - (leftInset + rightInset) / SCALE_FACTOR));
    }

    private int maxLength() {
        return 32767;
    }

    /** The visual lines: the text cut at each "\n", then wrapped at the field's width (after a space when
     * possible, never inside a character). Always at least one line. */
    private List<Line> layout() {
        String value = getValue();
        int inner = innerWidth();
        List<Line> lines = new ArrayList<>();
        int i = 0, n = value.length();
        while (true) {
            int newline = value.indexOf('\n', i);
            int paragraphEnd = newline < 0 ? n : newline;
            int start = i;
            do {
                int fit = textFont.plainSubstrByWidth(value.substring(start, paragraphEnd), inner).length();
                int end = start + fit;
                if (end < paragraphEnd) {
                    if (fit == 0) {
                        end = start + Character.charCount(value.codePointAt(start));
                    } else {
                        int space = value.lastIndexOf(' ', end - 1);
                        if (space >= start)
                            end = space + 1;
                        else if (Character.isLowSurrogate(value.charAt(end)))
                            end--;
                    }
                }
                lines.add(new Line(start, end));
                start = end;
            } while (start < paragraphEnd);
            if (newline < 0)
                break;
            i = newline + 1;
        }
        return lines;
    }

    /** The line the cursor is on: the last one starting at or before it. */
    private static int lineOf(List<Line> lines, int cursor) {
        int index = 0;
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i).start() <= cursor)
                index = i;
        return index;
    }

    /** The position in that line nearest to x (text units from the line start). */
    private int indexAtX(String value, Line line, int x) {
        int best = line.start();
        int bestDistance = Math.abs(x);
        for (int i = line.start(); i < line.end(); ) {
            i += Character.charCount(value.codePointAt(i));
            int distance = Math.abs(textFont.width(value.substring(line.start(), i)) - x);
            if (distance <= bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** {start, end} of the selection (start == end when nothing is selected). */
    private int[] selection() {
        String value = getValue();
        int cursor = Math.max(0, Math.min(value.length(), getCursorPosition()));
        String selected = getHighlighted();
        if (selected.isEmpty())
            return new int[]{cursor, cursor};
        if (cursor + selected.length() <= value.length() && value.startsWith(selected, cursor))
            return new int[]{cursor, cursor + selected.length()};
        return new int[]{Math.max(0, cursor - selected.length()), cursor};
    }

    // ---- drawing (in the field's own units, at 0,0: see SmallTextEditBox) ----

    @Override
    protected void drawInner(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick) {
        String value = getValue();
        List<Line> lines = layout();
        int cursor = Math.max(0, Math.min(value.length(), getCursorPosition()));
        int cursorLine = lineOf(lines, cursor);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_LINES, Math.round((getHeight() * SCALE_FACTOR - ONE_LINE_HEIGHT) / LINE_STEP) + 1));
        firstLine = Math.max(0, Math.min(firstLine, lines.size() - visible));
        if (cursorLine < firstLine)
            firstLine = cursorLine;
        else if (cursorLine >= firstLine + visible)
            firstLine = cursorLine - visible + 1;

        int[] sel = selection();
        int tx = getX() + Math.round(textLeft());
        int color = isEditableField() ? TEXT : TEXT_LOCKED;
        for (int k = 0; k < visible && firstLine + k < lines.size(); k++) {
            Line line = lines.get(firstLine + k);
            String text = value.substring(line.start(), line.end());
            float ty = getY() + TOP + k * STEP;
            GuiCompat.pushPose(guiGraphics);
            GuiCompat.translate(guiGraphics, 0f, ty);
            int s = Math.max(sel[0], line.start()), e = Math.min(sel[1], line.end());
            if (e > s) {
                int x0 = tx + textFont.width(value.substring(line.start(), s));
                int x1 = tx + textFont.width(value.substring(line.start(), e));
                guiGraphics.fill(x0, -1, x1, 9, SELECTION);
            }
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, text, tx, 0, color, false);
            if (firstLine + k == cursorLine && isFocused() && isEditableField() && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = tx + textFont.width(value.substring(line.start(), cursor));
                fillInFront(guiGraphics, cx, -1, cx + 1, 9, TEXT);
            }
            GuiCompat.popPose(guiGraphics);
        }

        int inner = innerWidth();
        float ty = getY() + TOP;
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, 0f, ty);
        if (value.isEmpty() && !isFocused() && hint != null) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, textFont.plainSubstrByWidth(hint.getString(), inner), tx, 0, PLACEHOLDER, false);
        } else if (suggestion != null && !suggestion.isEmpty() && value.isEmpty()) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(textFont, textFont.plainSubstrByWidth(suggestion, inner), tx, 0, PLACEHOLDER, false);
        }
        GuiCompat.popPose(guiGraphics);
    }

    // ---- clicks: the line under the mouse, then the nearest place in it ----

    private void placeCursorAt(double mouseX, double mouseY) {
        float renderedHeight = getHeight() * SCALE_FACTOR;
        float top = getY() + (requestedHeight - renderedHeight);
        double lx = (mouseX - getX()) / SCALE_FACTOR - Math.round(textLeft());
        double ly = (mouseY - top) / SCALE_FACTOR - TOP;
        List<Line> lines = layout();
        int index = Math.max(0, Math.min(lines.size() - 1, firstLine + (int) Math.floor((ly + 1) / STEP)));
        int pos = indexAtX(getValue(), lines.get(index), (int) Math.round(lx));
        setCursorPosition(pos);
        setHighlightPos(pos);
    }

    //? if <1.21.10 {
    @Override
    public void onClick(double mouseX, double mouseY) {
        placeCursorAt(mouseX, mouseY);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        placeCursorAt(event.x(), event.y());
    }
    *///?}
}
