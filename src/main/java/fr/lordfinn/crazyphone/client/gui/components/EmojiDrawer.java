package fr.lordfinn.crazyphone.client.gui.components;

import com.mojang.blaze3d.platform.InputConstants;

import fr.lordfinn.crazyphone.client.EmojiCatalog;
import fr.lordfinn.crazyphone.client.EmojiCatalog.Category;
import fr.lordfinn.crazyphone.client.EmojiCatalog.Emoji;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Compact emoji drawer that slides up from the conversation screen's message field, inside the phone frame:
 * a row of category tabs (each drawn as an emoji, "recently used" first), a scrolling grid below, the
 * :shortcode: as hover tooltip, a click inserts the emoji where the text cursor is.
 *
 * Not a vanilla widget: the conversation screen owns it and forwards render / click / scroll / key events,
 * so it can sit above the message feed and decide itself what an outside click does. All coordinates are
 * the phone's own GUI coordinates (the bigger-phone zoom, see PhoneScale, is applied around it already).
 *
 * Keyboard: arrows move a highlight through the grid, Enter inserts the highlighted emoji, Tab and
 * Shift+Tab switch category, Escape closes. Typing still goes to the message field while it is open.
 */
public class EmojiDrawer {
    /** Drawer geometry, relative to the phone's leftPos/topPos: same 8px side margins as everything else
     * on the phone, sitting right on top of the message field (y 158). */
    public static final int X = 8;
    public static final int WIDTH = 106;
    public static final int BOTTOM = 157;
    public static final int HEIGHT = 74;

    private static final int CELL = 11;
    /** An emoji glyph's top row is drawn one pixel above the y it is given (its font's ascent is 8, plain text 7):
     * measured in game with the highlight box around a square emoji. */
    private static final float EMOJI_TOP_OFFSET = 1f;
    private static final int COLUMNS = 9;
    private static final int VISIBLE_ROWS = 5;
    private static final int TAB_WIDTH = 10;
    private static final int TAB_Y = 2;
    private static final int TAB_HEIGHT = 11;
    private static final int GRID_X = 2;
    private static final int GRID_Y = 16;
    private static final int SCROLLBAR_X = WIDTH - 4;
    private static final long SLIDE_MILLIS = 110;

    // Same palette as the phone's icon buttons (crazyphone-send-voice.png): teal frame, cream panel.
    private static final int FRAME = 0xFF007EA2;
    private static final int PANEL = 0xFFFFF7EE;
    private static final int TAB_STRIP = 0xFFE3F1F4;
    private static final int SEPARATOR = 0xFFB9D7DE;
    private static final int HOVER = 0x66FFC107;
    private static final int KEYBOARD_HIGHLIGHT = 0xFFFFC107;
    private static final int SCROLL_TRACK = 0xFFD5E6EA;
    private static final int SCROLL_THUMB = 0xFF007EA2;
    private static final int HINT_TEXT = 0xFF8A9599;

    /** Tab index 0 is always "recently used", the catalog's categories follow. */
    private static final int RECENTS_TAB = 0;
    private static final String RECENTS_ICON_SHORTCODE = ":clock3:";

    private final Consumer<Emoji> onPick;
    private int left;
    private int top;

    private boolean open;
    private long animationStart;
    private float animationFrom;
    private int tab = RECENTS_TAB;
    private int scrollRow;
    /** -1 when nothing is keyboard-highlighted (mouse users never see a highlight). */
    private int keyboardIndex = -1;

    /** Dev only: highlights this emoji of the grid as if reached with the arrow keys. */
    public void devHighlight(int index) {
        keyboardIndex = index;
    }

    public EmojiDrawer(Consumer<Emoji> onPick) {
        this.onPick = onPick;
    }

    /** Re-anchors the drawer on the phone (called from the screen's init(), also after a resize). */
    /** How far the message field has grown above its one line height: the drawer sits that much higher. */
    private int bottomShift;
    private int originTop;

    public void setBottomShift(int shift) {
        if (shift != bottomShift) {
            bottomShift = shift;
            this.top = originTop + BOTTOM - HEIGHT - bottomShift;
        }
    }

    public void setPhoneOrigin(int leftPos, int topPos) {
        this.originTop = topPos;
        this.left = leftPos + X;
        this.top = topPos + BOTTOM - HEIGHT - bottomShift;
    }

    public boolean isOpen() {
        return open;
    }

    /** True while open or still sliding closed - the drawer covers the feed during that time. */
    public boolean isVisible() {
        return open || progress() > 0f;
    }

    public void open() {
        if (open)
            return;
        startAnimation();
        open = true;
        keyboardIndex = -1;
        // Nothing used yet: start on the first real category instead of an empty recents row.
        if (tab == RECENTS_TAB && EmojiCatalog.recents().isEmpty() && !EmojiCatalog.categories().isEmpty())
            selectTab(1);
        clampScroll();
    }

    public void close() {
        if (!open)
            return;
        startAnimation();
        open = false;
        keyboardIndex = -1;
    }

    /** Closes immediately, no slide (screen going away, voice recording taking the row over...). */
    public void closeNow() {
        open = false;
        keyboardIndex = -1;
        animationStart = 0;
        animationFrom = 0f;
    }

    public void toggle() {
        if (open)
            close();
        else
            open();
    }

    private void startAnimation() {
        animationFrom = progress();
        animationStart = System.currentTimeMillis();
    }

    /** 0 = fully hidden, 1 = fully open. */
    private float progress() {
        if (animationStart == 0)
            return open ? 1f : 0f;
        float t = Math.min(1f, Math.max(0f, (System.currentTimeMillis() - animationStart) / (float) SLIDE_MILLIS));
        float eased = 1f - (1f - t) * (1f - t);
        float target = open ? 1f : 0f;
        return animationFrom + (target - animationFrom) * eased;
    }

    /** Whether a point is on the drawer as currently shown. */
    public boolean contains(double mouseX, double mouseY) {
        if (!isVisible())
            return false;
        int visibleTop = top + Math.round(HEIGHT * (1f - progress()));
        return mouseX >= left && mouseX < left + WIDTH && mouseY >= visibleTop && mouseY < top + HEIGHT;
    }

    /** Recents + categories, never more than fit across the drawer. */
    private int tabCount() {
        return Math.min(1 + EmojiCatalog.categories().size(), (WIDTH - 2) / TAB_WIDTH);
    }

    private List<Emoji> currentEmojis() {
        if (tab == RECENTS_TAB)
            return EmojiCatalog.recents();
        List<Category> cats = EmojiCatalog.categories();
        int i = tab - 1;
        return i >= 0 && i < cats.size() ? cats.get(i).emojis() : List.of();
    }

    private int rowCount() {
        return (currentEmojis().size() + COLUMNS - 1) / COLUMNS;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, rowCount() - VISIBLE_ROWS);
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
    }

    public void selectTab(int newTab) {
        int count = tabCount();
        if (count <= 0)
            return;
        tab = ((newTab % count) + count) % count;
        scrollRow = 0;
        if (keyboardIndex >= 0)
            keyboardIndex = currentEmojis().isEmpty() ? -1 : 0;
    }

    // ---- rendering ----

    public void render(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        float p = progress();
        if (!open && p <= 0f) {
            animationStart = 0;
            return;
        }
        Font font = Minecraft.getInstance().font;
        int slide = Math.round(HEIGHT * (1f - p));
        int y0 = top + slide;

        // Above everything already drawn in the feed (bubbles, heads, the floating send-column icons).
        //? if <1.21.10 {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 350);
        //?} else {
        /*guiGraphics.nextStratum();
        *///?}
        // The slide: only the part that has come up above the message field is visible.
        guiGraphics.enableScissor(left, top, left + WIDTH, top + HEIGHT);
        Emoji hovered = null;
        Component hoveredTab = null;
        try {
            drawPanel(guiGraphics, y0);
            boolean interactive = open && p >= 0.999f;

            // Tabs
            List<Category> cats = EmojiCatalog.categories();
            int tabs = tabCount();
            int ty = y0 + TAB_Y;
            for (int i = 0; i < tabs; i++) {
                // Every tab gets the same box, centered on its own equal share of the strip (half pixels allowed),
                // so the gaps between tabs and the margins at both ends are all the same.
                float center = tabCenter(i, tabs);
                boolean tabHovered = interactive && i == tabAt(mouseX, mouseY, ty, tabs);
                pushOffset(guiGraphics, center - TAB_WIDTH / 2f);
                if (i == tab) {
                    guiGraphics.fill(0, ty, TAB_WIDTH, ty + TAB_HEIGHT, 0x33FFC107);
                    guiGraphics.fill(1, ty + TAB_HEIGHT - 1, TAB_WIDTH - 1, ty + TAB_HEIGHT, KEYBOARD_HIGHLIGHT);
                } else if (tabHovered) {
                    guiGraphics.fill(0, ty, TAB_WIDTH, ty + TAB_HEIGHT, HOVER);
                }
                popOffset(guiGraphics);
                String icon = i == RECENTS_TAB ? recentsIcon() : cats.get(i - 1).icon().glyph();
                // The icon centered in its box on its own drawn pixels (measured in the font image), both ways.
                float[] bounds = icon == null || icon.isEmpty() ? null : fr.lordfinn.crazyphone.client.EmojiGlyphs.bounds(icon.codePointAt(0));
                if (bounds != null) {
                    float gx = center - (bounds[0] + bounds[2]) / 2f;
                    float gy = ty + (TAB_HEIGHT - (bounds[3] - bounds[1])) / 2f - bounds[1] + EMOJI_TOP_OFFSET;
                    pushOffsetXY(guiGraphics, gx, gy);
                    drawGlyph(guiGraphics, font, icon, 0, 0);
                    popOffset(guiGraphics);
                } else {
                    String shown = icon != null ? icon : "*";
                    pushOffset(guiGraphics, center - (font.width(shown) - 1) / 2f);
                    guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.literal(shown), 0, ty + 2, icon != null ? 0xFFFFFFFF : HINT_TEXT, false);
                    popOffset(guiGraphics);
                }
                if (tabHovered)
                    hoveredTab = Component.translatable(i == RECENTS_TAB
                            ? "gui.crazyphone.emoji_drawer.tab.recent"
                            : "gui.crazyphone.emoji_drawer.tab." + cats.get(i - 1).id());
            }
            guiGraphics.fill(left + 1, y0 + GRID_Y - 2, left + WIDTH - 1, y0 + GRID_Y - 1, SEPARATOR);

            // Grid
            List<Emoji> emojis = currentEmojis();
            int gridLeft = left + GRID_X;
            int gridTop = y0 + GRID_Y;
            if (emojis.isEmpty()) {
                Component hint = Component.translatable(tab == RECENTS_TAB
                        ? "gui.crazyphone.emoji_drawer.no_recent"
                        : "gui.crazyphone.emoji_drawer.empty");
                int textWidth = font.width(hint);
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, hint, left + (WIDTH - textWidth) / 2, gridTop + 18, HINT_TEXT, false);
            }
            int first = scrollRow * COLUMNS;
            int last = Math.min(emojis.size(), first + VISIBLE_ROWS * COLUMNS);
            for (int i = first; i < last; i++) {
                int cx = gridLeft + ((i - first) % COLUMNS) * CELL;
                int cy = gridTop + ((i - first) / COLUMNS) * CELL;
                boolean cellHovered = interactive && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                if (i == keyboardIndex) {
                    guiGraphics.fill(cx, cy, cx + CELL, cy + CELL, HOVER);
                    outline(guiGraphics, cx, cy, CELL, CELL, KEYBOARD_HIGHLIGHT);
                } else if (cellHovered) {
                    guiGraphics.fill(cx, cy, cx + CELL, cy + CELL, HOVER);
                }
                if (cellHovered)
                    hovered = emojis.get(i);
                // Centered in its cell on its own drawn pixels, so the hover box shows the same margin all around.
                String glyph = emojis.get(i).glyph();
                float[] bounds = glyph.isEmpty() ? null : fr.lordfinn.crazyphone.client.EmojiGlyphs.bounds(glyph.codePointAt(0));
                if (bounds != null) {
                    pushOffsetXY(guiGraphics, cx + (CELL - (bounds[2] - bounds[0])) / 2f - bounds[0],
                            cy + (CELL - (bounds[3] - bounds[1])) / 2f - bounds[1] + EMOJI_TOP_OFFSET);
                    drawGlyph(guiGraphics, font, glyph, 0, 0);
                    popOffset(guiGraphics);
                } else {
                    drawGlyph(guiGraphics, font, glyph, cx + 1, cy + 1);
                }
            }

            // Scrollbar, only when there is something to scroll.
            int rows = rowCount();
            if (rows > VISIBLE_ROWS) {
                int trackTop = gridTop;
                int trackHeight = VISIBLE_ROWS * CELL;
                int thumbHeight = Math.max(6, trackHeight * VISIBLE_ROWS / rows);
                int maxScroll = rows - VISIBLE_ROWS;
                int thumbTop = trackTop + (trackHeight - thumbHeight) * scrollRow / Math.max(1, maxScroll);
                int sx = left + SCROLLBAR_X;
                guiGraphics.fill(sx, trackTop, sx + 2, trackTop + trackHeight, SCROLL_TRACK);
                guiGraphics.fill(sx, thumbTop, sx + 2, thumbTop + thumbHeight, SCROLL_THUMB);
            }
        } finally {
            guiGraphics.disableScissor();
        }
        // Outside the slide scissor: a tooltip near the drawer's edge must not be cut off.
        try {
            if (hovered != null)
                tooltip(guiGraphics, font, List.of(Component.literal(hovered.shortcode()),
                        Component.translatable("gui.crazyphone.emoji_drawer.insert.lore").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            else if (hoveredTab != null)
                tooltip(guiGraphics, font, List.of(hoveredTab), mouseX, mouseY);
        } finally {
            //? if <1.21.10 {
            guiGraphics.pose().popPose();
            //?}
        }
    }

    private String recentsIcon() {
        String glyph = fr.lordfinn.crazyphone.client.EmojiShortcodes.glyphFor(RECENTS_ICON_SHORTCODE);
        return glyph != null && glyph.codePointCount(0, glyph.length()) == 1 ? glyph : null;
    }

    /** The middle of tab {@code i}: the strip inside the frame is cut into {@code tabs} equal shares. */
    private float tabCenter(int i, int tabs) {
        float share = (WIDTH - 2) / (float) tabs;
        return left + 1 + (i + 0.5f) * share;
    }

    /** The tab whose box is under the mouse (the same box drawn for hover and selection), or -1. */
    private int tabAt(double mouseX, double mouseY, int tabTop, int tabs) {
        if (mouseY < tabTop || mouseY >= tabTop + TAB_HEIGHT)
            return -1;
        for (int i = 0; i < tabs; i++) {
            float x0 = tabCenter(i, tabs) - TAB_WIDTH / 2f;
            if (mouseX >= x0 && mouseX < x0 + TAB_WIDTH)
                return i;
        }
        return -1;
    }

    private static void pushOffset(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, float dx) {
        //? if <1.21.10 {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(dx, 0f, 0f);
        //?} else {
        /*guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(dx, 0f);
        *///?}
    }

    private static void pushOffsetXY(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, float dx, float dy) {
        //? if <1.21.10 {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(dx, dy, 0f);
        //?} else {
        /*guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(dx, dy);
        *///?}
    }

    private static void popOffset(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.pose().popPose();
        //?} else {
        /*guiGraphics.pose().popMatrix();
        *///?}
    }

    private void drawPanel(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int y0) {
        int x0 = left, x1 = left + WIDTH, y1 = y0 + HEIGHT;
        // Rounded 1px frame (corners left out), like the phone's own icon buttons.
        guiGraphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, FRAME);
        guiGraphics.fill(x0, y0 + 1, x0 + 1, y1, FRAME);
        guiGraphics.fill(x1 - 1, y0 + 1, x1, y1, FRAME);
        guiGraphics.fill(x0 + 1, y0 + 1, x1 - 1, y1, PANEL);
        guiGraphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + TAB_Y + TAB_HEIGHT + 1, TAB_STRIP);
    }

    private static void outline(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + 1, color);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, color);
        guiGraphics.fill(x, y + 1, x + 1, y + h - 1, color);
        guiGraphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Twemoji glyphs are full-color bitmaps: drawn white (untinted), no shadow. */
    private static void drawGlyph(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Font font, String glyph, int x, int y) {
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.literal(glyph), x, y, 0xFFFFFFFF, false);
    }

    private static void tooltip(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Font font, List<Component> lines, int mouseX, int mouseY) {
        guiGraphics./*$ gui_render_component_tooltip {*/renderComponentTooltip/*$}*/(font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(font, lines), mouseX, mouseY);
    }

    // ---- input ----

    /** Click on the drawer: true when consumed (any click inside the drawer is, so nothing behind reacts). */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || !contains(mouseX, mouseY))
            return false;
        if (button != InputConstants.MOUSE_BUTTON_LEFT || progress() < 0.999f)
            return true;
        int clickedTab = tabAt(mouseX, mouseY, top + TAB_Y, tabCount());
        if (clickedTab >= 0) {
            selectTab(clickedTab);
            playClick();
            return true;
        }
        int index = cellAt(mouseX, mouseY);
        if (index >= 0)
            pick(currentEmojis().get(index));
        return true;
    }

    private int cellAt(double mouseX, double mouseY) {
        int gridLeft = left + GRID_X;
        int gridTop = top + GRID_Y;
        if (mouseX < gridLeft || mouseY < gridTop)
            return -1;
        int col = (int) ((mouseX - gridLeft) / CELL);
        int row = (int) ((mouseY - gridTop) / CELL);
        if (col >= COLUMNS || row >= VISIBLE_ROWS)
            return -1;
        int index = (scrollRow + row) * COLUMNS + col;
        return index < currentEmojis().size() ? index : -1;
    }

    private void pick(Emoji emoji) {
        if (emoji == null)
            return;
        playClick();
        onPick.accept(emoji);
    }

    public static void playClick() {
        try {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } catch (RuntimeException ignored) {
        }
    }

    /** Wheel over the drawer scrolls it (and never the feed behind it). */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open || !contains(mouseX, mouseY))
            return false;
        if (delta > 0)
            scrollRow--;
        else if (delta < 0)
            scrollRow++;
        clampScroll();
        return true;
    }

    /** Keys while open; true when consumed. Escape closes, arrows/Enter/Tab browse and pick. */
    public boolean keyPressed(int key, boolean shift) {
        if (!open)
            return false;
        if (key == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            selectTab(tab + (shift ? -1 : 1));
            return true;
        }
        List<Emoji> emojis = currentEmojis();
        boolean arrow = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                || key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN;
        if (arrow) {
            if (emojis.isEmpty())
                return true;
            if (keyboardIndex < 0) {
                keyboardIndex = Math.min(emojis.size() - 1, scrollRow * COLUMNS);
            } else {
                int next = keyboardIndex;
                if (key == InputConstants.KEY_LEFT) next--;
                else if (key == InputConstants.KEY_RIGHT) next++;
                else if (key == InputConstants.KEY_UP) next -= COLUMNS;
                else next += COLUMNS;
                keyboardIndex = Math.max(0, Math.min(emojis.size() - 1, next));
            }
            int row = keyboardIndex / COLUMNS;
            if (row < scrollRow)
                scrollRow = row;
            else if (row >= scrollRow + VISIBLE_ROWS)
                scrollRow = row - VISIBLE_ROWS + 1;
            clampScroll();
            return true;
        }
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && keyboardIndex >= 0) {
            if (keyboardIndex < emojis.size())
                pick(emojis.get(keyboardIndex));
            return true;
        }
        return false;
    }

    /** Short lines for the emoji button's own tooltip (title + lore, like every other phone button). */
    public static List<Component> buttonTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.crazyphone.emoji_drawer.button"));
        lines.add(Component.translatable("gui.crazyphone.emoji_drawer.button.lore").withStyle(ChatFormatting.GRAY));
        return lines;
    }
}
