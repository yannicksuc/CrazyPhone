package fr.lordfinn.crazyphone.client.gui;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.client.gui.components.PhotoLoadingPlaceholder;
import fr.lordfinn.crazyphone.client.gui.components.ScrollingText;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.network.CrazyPhoneMyPhotosActionMessage;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMyPhotosScreenMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The flat "My Photos" gallery - every photo this phone owns, no album/folder layer above it (see
 * {@link CrazyPhoneMyPhotosScreenMenu}). Visual design (3x3 Instagram-style grid, center-crop thumbnails,
 * amber selection border, Delete/Take/Send action bar) is an original re-implementation of this mod's own
 * pre-Camera-mod-removal picture grid, ported to render through {@link FabricPictureCache} instead of a real
 * container of ItemStacks - there's no Slot/AbstractContainerMenu grid backing this at all anymore, just a
 * plain resolved {@code List<UUID>}, so selection/scroll/rendering all work directly against photo ids
 * instead of slot indices. Scrolling is continuous pixel movement, scissor-cropped to the grid's own rect
 * (see renderThumbnails), matching CrazyPhoneConversationScreen's own message-feed scroll rather than
 * snapping a whole row at a time per wheel tick.
 */
public class CrazyPhoneMyPhotosScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneMyPhotosScreenMenu> {
    private static final HashMap<String, Object> guistate = new HashMap<>();
    private static final int GRID_COLUMNS = 3;
    private static final int THUMB_SIZE = 34;
    private static final int THUMB_PITCH = 36;
    // Matches CrazyPhoneConversationScreen's own message crop zone top (topPos+27), same as GRID_HEIGHT below.
    private static final int GRID_TOP_Y = 26;
    private static final int GRID_WIDTH = GRID_COLUMNS * THUMB_PITCH;
    // Matches CrazyPhoneConversationScreen's own message crop zone height exactly (its enableScissor call
    // spans topPos+27 to topPos+158, 131px) - the grid already scrolls in continuous pixels, not snapped
    // rows, so a partial row peeking in at the crop edge is consistent with how it already behaves.
    private static final int GRID_HEIGHT = 131;
    // How many rows must actually be drawn/fetched to cover the crop window at ANY scroll offset - continuous
    // pixel scrolling (not row-snapped) means the window's top can land mid-row, so ceil(height/pitch) rows
    // fit at a row-aligned scroll position alone isn't enough; +1 covers the partial row that peeks in at
    // the bottom (or top) at every OTHER scroll position. Was VISIBLE_ROWS-based (assumed exactly 3 rows fit
    // the crop), which under-rendered once GRID_HEIGHT grew past an exact multiple of THUMB_PITCH - the
    // bottom-most row scrolling into view got skipped by this loop entirely instead of merely scissor-cropped
    // ("les images disparaissent trop tot en bas" - live report).
    private static final int RENDER_ROWS = (GRID_HEIGHT + THUMB_PITCH - 1) / THUMB_PITCH + 1;
    private static final int SELECTED_BORDER_COLOR = CrazyPhoneColors.ACCENT_YELLOW;
    private static final int SELECTED_INSET = 2;
    // Matches CrazyPhoneConversationScreen's own message-feed scroll step - continuous pixel scrolling
    // (scissor-cropped, see renderThumbnails) instead of snapping a whole row at a time per wheel tick.
    private static final int SCROLL_STEP = 10;

    // Header banner's right edge and the title's own y, both relative to leftPos/topPos - mirror
    // CrazyPhoneDefaultScreenScreen's own private HEADER_BANNER_RIGHT_X / title y (neither is exposed to
    // subclasses) so the "247/300" counter lines up flush with the banner's right edge, on the exact same
    // row as the title. Same technique CrazyPhoneConversationScreen uses to reserve header room for its
    // call icon (see its renderHeader(..., rightBoundX) call).
    private static final int HEADER_BANNER_RIGHT_X = 118;
    private static final int HEADER_TITLE_Y = 14;
    // Matches renderHeader's own title color.
    private static final int COUNTER_TEXT_COLOR = 0xFF404040;
    // Fraction of Config.maxPhotosStoredPerOwner at which the storage warning below the header kicks in -
    // 90% gives a clear heads-up before the server's own silent FIFO eviction of the oldest photos (see
    // Config.maxPhotosStoredPerOwner's own comment) actually starts discarding anything.
    private static final double STORAGE_WARNING_THRESHOLD_FRACTION = 0.9;
    private static final int STORAGE_WARNING_COLOR = CrazyPhoneColors.ACCENT_YELLOW;
    // One line of text tall - reserved between the header and the grid only while the warning is showing
    // (rather than always, since the grid has very little slack above the button row to spare otherwise).
    private static final int STORAGE_WARNING_LINE_HEIGHT = 10;

    private final Set<UUID> selectedPhotoIds = new HashSet<>();
    private int scrollPosition = 0;
    private Button buttonDelete;
    private Button buttonTake;
    private Button buttonSend;

    public CrazyPhoneMyPhotosScreenScreen(CrazyPhoneMyPhotosScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    /** The client-side snapshot of photo ids this grid was opened with, mutable in place - CrazyPhonePhotoEditScreen
     * patches this directly right after a successful Replace/Create Copy so the grid reflects it immediately
     * on return, instead of only after leaving and reopening this screen. AbstractContainerScreen's own
     * {@code menu} field is `protected`, declared in a DIFFERENT package (vanilla) - same-package access
     * from a sibling class here still needs this class to actually own the access, hence the getter. */
    public java.util.List<UUID> getPhotoIds() {
        return menu.photoIds;
    }

    @Override
    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, ItemStack.EMPTY,
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.title"), HEADER_BANNER_RIGHT_X, true);
        renderPhotoCountIcon(guiGraphics, mouseX, mouseY);
        renderPhotoCountInfo(guiGraphics);
        renderImportButton(guiGraphics, mouseX, mouseY);
        renderLinkButton(guiGraphics, mouseX, mouseY);
        java.util.List<Component> importTooltip = photoCountTooltipAt(mouseX, mouseY);
        if (importTooltip == null)
            importTooltip = importTooltipAt(mouseX, mouseY);
        if (importTooltip == null)
            importTooltip = linkTooltipAt(mouseX, mouseY);
        if (importTooltip != null)
            guiGraphics.setComponentTooltipForNextFrame(this.font, importTooltip, mouseX, mouseY);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, ItemStack.EMPTY,
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.title"), HEADER_BANNER_RIGHT_X, true);
        renderPhotoCountIcon(guiGraphics, mouseX, mouseY);
        renderPhotoCountInfo(guiGraphics);
        renderImportButton(guiGraphics, mouseX, mouseY);
        renderLinkButton(guiGraphics, mouseX, mouseY);
        java.util.List<Component> importTooltip = photoCountTooltipAt(mouseX, mouseY);
        if (importTooltip == null)
            importTooltip = importTooltipAt(mouseX, mouseY);
        if (importTooltip == null)
            importTooltip = linkTooltipAt(mouseX, mouseY);
        if (importTooltip != null)
            guiGraphics.renderComponentTooltip(this.font, importTooltip, mouseX, mouseY);
    }
    //?}

    @Override
    protected void drawScreenBackground(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        super.drawScreenBackground(guiGraphics);
        renderThumbnails(guiGraphics);
    }

    //? if >=26 {
    /*@Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    }
    *///? } else {
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
    //?}

    private int maxScrollPosition() {
        int rowCount = (menu.photoIds.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rowCount * THUMB_PITCH - GRID_HEIGHT);
    }

    /** Row-index (not pixel) of the topmost row any part of which is currently visible - shared by
     * rendering, hit-testing and prefetch so all three agree on exactly the same window as scrollPosition
     * moves continuously instead of snapping row-to-row. */
    private int topVisibleRow() {
        return scrollPosition / THUMB_PITCH;
    }

    // Warms the cache for every row that's even partially on screen plus one full row of lookahead below,
    // so scrolling usually finds thumbnails already resolved (or at least already in flight as one shared
    // batch request) instead of each photo triggering its own separate fetch the moment renderThumbnails
    // first asks for it. Called once up front from init() and again on every scroll tick.
    private void prefetchVisible() {
        int firstIndex = topVisibleRow() * GRID_COLUMNS;
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * (RENDER_ROWS + 1));
        if (firstIndex >= lastIndex)
            return;
        PhotoResolution resolution = fr.lordfinn.crazyphone.ClientConfig.phonePhotoListPixelated ? PhotoResolution.THUMBNAIL : PhotoResolution.FULL;
        FabricPictureCache.prefetch(menu.photoIds.subList(firstIndex, lastIndex), resolution);
    }

    private int gridLeft() {
        return this.leftPos + fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_X;
    }

    private int gridTop() {
        return this.topPos + GRID_TOP_Y + (isNearStorageCap() ? STORAGE_WARNING_LINE_HEIGHT : 0);
    }

    private boolean isNearStorageCap() {
        int max = fr.lordfinn.crazyphone.Config.maxPhotosStoredPerOwner;
        return max > 0 && menu.photoIds.size() >= max * STORAGE_WARNING_THRESHOLD_FRACTION;
    }

    // "Import from PC" button in the yellow header, right at the banner's right edge with the counter to its
    // left - same emoji-font icon technique as the in-call screen's header buttons.
    private static final Component IMPORT_ICON = Component.literal("📥");
    private static final int IMPORT_ICON_GAP = 3;

    private int importIconX() {
        return this.leftPos + HEADER_BANNER_RIGHT_X - this.font.width(IMPORT_ICON) - 2;
    }

    private boolean isHoveringImportIcon(double mouseX, double mouseY) {
        int iconX = importIconX();
        int iconY = this.topPos + HEADER_TITLE_Y;
        return mouseX >= iconX - 1 && mouseX < iconX + this.font.width(IMPORT_ICON) + 1
                && mouseY >= iconY - 1 && mouseY < iconY + this.font.lineHeight + 1;
    }

    private void renderImportButton(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        int iconX = importIconX();
        int iconY = this.topPos + HEADER_TITLE_Y;
        if (isHoveringImportIcon(mouseX, mouseY)) {
            fr.lordfinn.crazyphone.client.CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX - 1, iconY - 1, iconX + this.font.width(IMPORT_ICON) + 1, iconY + this.font.lineHeight + 1, 0x80FFFFFF);
        }
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, IMPORT_ICON, iconX, iconY, 0xFFFFFFFF, true);
    }

    private java.util.List<Component> importTooltipAt(double mouseX, double mouseY) {
        if (!isHoveringImportIcon(mouseX, mouseY))
            return null;
        return java.util.List.of(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.import"),
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.import.lore").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private boolean handleImportClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHoveringImportIcon(mouseX, mouseY))
            return false;
        java.util.List<java.util.UUID> imported = fr.lordfinn.crazyphone.client.picture.PhotoImporter.importFromDisk();
        if (!imported.isEmpty()) {
            // The server-sent list this screen was opened with doesn't know about them yet. It is ordered
            // newest first, so each one goes in at the top (the last imported ends up first), and the grid
            // scrolls back up so the result is visible.
            for (java.util.UUID id : imported)
                menu.photoIds.add(0, id);
            scrollPosition = 0;
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1f, 1f);
                fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(player,
                        Component.translatable("message.crazyphone.photos_imported", imported.size()), true);
            }
        }
        return true;
    }

    // "Import from link" button, left of the import one: imports the image whose http(s) link is currently in
    // the clipboard (no text field needed, so no extra screen).
    private static final Component LINK_ICON = Component.literal("🔗");

    private int linkIconX() {
        return importIconX() - IMPORT_ICON_GAP - this.font.width(LINK_ICON);
    }

    private boolean isHoveringLinkIcon(double mouseX, double mouseY) {
        int iconX = linkIconX();
        int iconY = this.topPos + HEADER_TITLE_Y;
        return mouseX >= iconX - 1 && mouseX < iconX + this.font.width(LINK_ICON) + 1
                && mouseY >= iconY - 1 && mouseY < iconY + this.font.lineHeight + 1;
    }

    private void renderLinkButton(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        int iconX = linkIconX();
        int iconY = this.topPos + HEADER_TITLE_Y;
        if (isHoveringLinkIcon(mouseX, mouseY)) {
            fr.lordfinn.crazyphone.client.CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX - 1, iconY - 1, iconX + this.font.width(LINK_ICON) + 1, iconY + this.font.lineHeight + 1, 0x80FFFFFF);
        }
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, LINK_ICON, iconX, iconY, 0xFFFFFFFF, true);
    }

    private java.util.List<Component> linkTooltipAt(double mouseX, double mouseY) {
        if (!isHoveringLinkIcon(mouseX, mouseY))
            return null;
        return java.util.List.of(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.import_link"),
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.import_link.lore").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private boolean handleLinkClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHoveringLinkIcon(mouseX, mouseY))
            return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null)
            return true;
        String clipboard = mc.keyboardHandler.getClipboard();
        if (!fr.lordfinn.crazyphone.client.picture.PhotoImporter.isHttpLink(clipboard)) {
            fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(player,
                    Component.translatable("message.crazyphone.photo_link_no_link"), true);
            return true;
        }
        fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(player,
                Component.translatable("message.crazyphone.photo_link_downloading"), true);
        fr.lordfinn.crazyphone.client.picture.PhotoImporter.importFromLink(clipboard, id -> {
            net.minecraft.client.player.LocalPlayer current = net.minecraft.client.Minecraft.getInstance().player;
            if (current == null)
                return;
            if (id == null) {
                fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(current,
                        Component.translatable("message.crazyphone.photo_link_failed"), true);
                return;
            }
            // Only if this screen is still the one showing - the download can finish after leaving it.
            if (net.minecraft.client.Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ == this) {
                menu.photoIds.add(0, id);
                scrollPosition = 0;
            }
            current.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1f, 1f);
            fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(current,
                    Component.translatable("message.crazyphone.photos_imported", 1), true);
        });
        return true;
    }

    // Photo-count icon, in the header's own icon slot (left of the title, x=HEADER_ICON_X, matching
    // renderHeader's own showIcon=true layout - see the ItemStack.EMPTY call above) - the "247/300" text
    // only shows as this icon's tooltip now (live request: a page icon here instead of the number sitting
    // in the banner itself).
    private static final Component PHOTO_COUNT_ICON = Component.literal("📄");
    private static final int PHOTO_COUNT_ICON_X = 7;
    private static final int PHOTO_COUNT_ICON_SIZE = 16;

    private boolean isHoveringPhotoCountIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + PHOTO_COUNT_ICON_X;
        int iconY = this.topPos + 9;
        return mouseX >= iconX && mouseX < iconX + PHOTO_COUNT_ICON_SIZE && mouseY >= iconY && mouseY < iconY + PHOTO_COUNT_ICON_SIZE;
    }

    private void renderPhotoCountIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        int iconX = this.leftPos + PHOTO_COUNT_ICON_X;
        int iconY = this.topPos + 9;
        if (isHoveringPhotoCountIcon(mouseX, mouseY)) {
            fr.lordfinn.crazyphone.client.CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX - 1, iconY - 1, iconX + PHOTO_COUNT_ICON_SIZE + 1, iconY + PHOTO_COUNT_ICON_SIZE + 1, 0x80FFFFFF);
        }
        // Centered inside the 16x16 slot a real item icon would have filled.
        int textX = iconX + (PHOTO_COUNT_ICON_SIZE - this.font.width(PHOTO_COUNT_ICON)) / 2;
        int textY = iconY + (PHOTO_COUNT_ICON_SIZE - this.font.lineHeight) / 2;
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, PHOTO_COUNT_ICON, textX, textY, COUNTER_TEXT_COLOR, false);
    }

    private java.util.List<Component> photoCountTooltipAt(double mouseX, double mouseY) {
        if (!isHoveringPhotoCountIcon(mouseX, mouseY))
            return null;
        int max = fr.lordfinn.crazyphone.Config.maxPhotosStoredPerOwner;
        int count = menu.photoIds.size();
        return java.util.List.of(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.photo_count", count, max));
    }

    /** Once the owner's photo count gets within STORAGE_WARNING_THRESHOLD_FRACTION of
     * Config.maxPhotosStoredPerOwner, draws a short one-line warning just below the header that the oldest
     * photos will soon be auto-evicted - the "247/300" counter itself is rendered separately (see
     * renderPhotoCountIcon/photoCountTooltipAt). Reuses ScrollingText (same as the title itself) so an
     * overly long translation scrolls instead of overflowing past the phone's frame. */
    private void renderPhotoCountInfo(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (isNearStorageCap()) {
            Component warning = Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.storage_warning");
            ScrollingText.render(guiGraphics, this.font, warning, gridLeft(), this.topPos + HEADER_HEIGHT, GRID_WIDTH, STORAGE_WARNING_COLOR);
        }
    }

    private boolean isWithinGridCropZone(double mouseX, double mouseY) {
        int x0 = gridLeft(), y0 = gridTop();
        return mouseX >= x0 && mouseX < x0 + GRID_WIDTH && mouseY >= y0 && mouseY < y0 + GRID_HEIGHT;
    }

    // Continuous pixel scroll (see mouseScrolled) instead of snapping a whole row at a time - drawn one row
    // of overscan past the bottom of the viewport so a partially-scrolled-in row isn't missing until it's
    // fully aligned, then scissor-cropped to the grid's own rect (same technique as
    // CrazyPhoneConversationScreen's message feed) so that overscan row - and any row scrolled half off the
    // top - is cleanly clipped instead of spilling into the header/action-bar areas above/below the grid.
    private void renderThumbnails(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int gridLeft = gridLeft(), gridTop = gridTop();
        guiGraphics.enableScissor(gridLeft, gridTop, gridLeft + GRID_WIDTH, gridTop + GRID_HEIGHT);

        int topRow = topVisibleRow();
        int firstIndex = topRow * GRID_COLUMNS;
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * RENDER_ROWS);
        PhotoResolution resolution = fr.lordfinn.crazyphone.ClientConfig.phonePhotoListPixelated ? PhotoResolution.THUMBNAIL : PhotoResolution.FULL;
        for (int index = firstIndex; index < lastIndex; index++) {
            UUID photoId = menu.photoIds.get(index);
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, resolution);

            int rel = index - firstIndex;
            int col = rel % GRID_COLUMNS;
            int row = topRow + rel / GRID_COLUMNS;
            int x = gridLeft + col * THUMB_PITCH;
            int y = gridTop + row * THUMB_PITCH - scrollPosition;

            if (texture == null) {
                PhotoLoadingPlaceholder.draw(guiGraphics, x, y, THUMB_SIZE, THUMB_SIZE);
                continue;
            }

            boolean selected = selectedPhotoIds.contains(photoId);
            if (selected) {
                guiGraphics.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, SELECTED_BORDER_COLOR);
                drawCroppedThumbnail(guiGraphics, x + SELECTED_INSET, y + SELECTED_INSET,
                        THUMB_SIZE - SELECTED_INSET * 2, THUMB_SIZE - SELECTED_INSET * 2, texture);
            } else {
                drawCroppedThumbnail(guiGraphics, x, y, THUMB_SIZE, THUMB_SIZE, texture);
            }
        }

        guiGraphics.disableScissor();
    }

    /** "Cover" crop: always fills the full width x height target - the source UV rect is shrunk to the
     * target's aspect ratio and centered, so the longer source dimension gets cropped rather than
     * letterboxed. */
    private static void drawCroppedThumbnail(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int width, int height, FabricPictureCache.CachedTexture texture) {
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, x, y);

        float srcWidth = texture.width();
        float srcHeight = texture.height();
        float uSpan = 1f, vSpan = 1f, uOffset = 0f, vOffset = 0f;
        if (srcWidth > srcHeight) {
            uSpan = srcHeight / srcWidth;
            uOffset = (1f - uSpan) / 2f;
        } else if (srcHeight > srcWidth) {
            vSpan = srcWidth / srcHeight;
            vOffset = (1f - vSpan) / 2f;
        }

        GuiCompat.drawTexturedQuad(guiGraphics, texture.location(), 0, 0, width, height, uOffset, vOffset, uOffset + uSpan, vOffset + vSpan);

        GuiCompat.popPose(guiGraphics);
    }

    //? if <1.21.10 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseClickedImpl(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (mouseClickedImpl(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }
    *///?}

    private boolean mouseClickedImpl(double mouseX, double mouseY, int button) {
        if (handleImportClick(mouseX, mouseY, button) || handleLinkClick(mouseX, mouseY, button))
            return true;
        if (button != 0 && button != 1)
            return false;
        if (!isWithinGridCropZone(mouseX, mouseY))
            return false;

        int gridLeft = gridLeft(), gridTop = gridTop();
        int topRow = topVisibleRow();
        int firstIndex = topRow * GRID_COLUMNS;
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * RENDER_ROWS);
        for (int index = firstIndex; index < lastIndex; index++) {
            int rel = index - firstIndex;
            int col = rel % GRID_COLUMNS;
            int row = topRow + rel / GRID_COLUMNS;
            int x = gridLeft + col * THUMB_PITCH;
            int y = gridTop + row * THUMB_PITCH - scrollPosition;
            if (mouseX < x || mouseX >= x + THUMB_SIZE || mouseY < y || mouseY >= y + THUMB_SIZE)
                continue;

            UUID photoId = menu.photoIds.get(index);
            if (button == 0) {
                playToggleSound();
                if (!selectedPhotoIds.add(photoId))
                    selectedPhotoIds.remove(photoId);
                updateActionButtonsState();
            } else {
                Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(new CrazyPhonePhotoViewerScreen(photoId, CrazyPhonePhotoViewerScreen.Origin.GALLERY, 0xFFFFFF));
            }
            return true;
        }
        return false;
    }

    // Real 1.20.1 vanilla predates GuiEventListener's horizontal-scroll parameter (added by 1.20.4) - only
    // the vertical delta is ever used here either way.
    //? if >=1.20.4 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int newPosition = Math.max(0, Math.min(maxScrollPosition(), scrollPosition - (int) (scrollY * SCROLL_STEP)));
        if (newPosition != scrollPosition) {
            scrollPosition = newPosition;
            prefetchVisible();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?}
    //? if <1.20.4 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int newPosition = Math.max(0, Math.min(maxScrollPosition(), scrollPosition - (int) (scrollY * SCROLL_STEP)));
        if (newPosition != scrollPosition) {
            scrollPosition = newPosition;
            prefetchVisible();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
    //?}

    @Override
    public void init() {
        super.init();

        boolean sendMode = !menu.conversationId.isEmpty();
        if (sendMode) {
            buttonSend = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.button_send"), b -> {
                sendSelected();
            }).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
            guistate.put("button:button_send", buttonSend);
            this.addRenderableWidget(buttonSend);
        } else {
            buttonDelete = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.button_delete"), b -> {
                deleteSelected();
            }).bounds(this.leftPos + 62, this.topPos + 158, 52, 14).build();
            guistate.put("button:button_delete", buttonDelete);
            this.addRenderableWidget(buttonDelete);

            buttonTake = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.button_take"), b -> {
                takeSelected();
            }).bounds(this.leftPos + 8, this.topPos + 158, 52, 14).build();
            guistate.put("button:button_take", buttonTake);
            this.addRenderableWidget(buttonTake);
        }
        updateActionButtonsState();
        prefetchVisible();
    }

    private void deleteSelected() {
        if (selectedPhotoIds.isEmpty())
            return;
        sendAction(CrazyPhoneMyPhotosActionMessage.Action.DELETE);
        //? if <1.21.10 {
        Minecraft.getInstance().player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
        //? } else {
        /*Minecraft.getInstance().player.playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 1.0F);
        *///?}
        // The list this screen was opened with is a client-side snapshot - drop the deleted photos from it
        // right away so the grid updates live instead of only after leaving and reopening the screen.
        menu.photoIds.removeAll(selectedPhotoIds);
        scrollPosition = Math.min(scrollPosition, maxScrollPosition());
        selectedPhotoIds.clear();
        updateActionButtonsState();
    }

    private void takeSelected() {
        if (selectedPhotoIds.isEmpty())
            return;
        sendAction(CrazyPhoneMyPhotosActionMessage.Action.TAKE);
        Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
        selectedPhotoIds.clear();
        updateActionButtonsState();
    }

    private void sendSelected() {
        if (selectedPhotoIds.isEmpty())
            return;
        sendAction(CrazyPhoneMyPhotosActionMessage.Action.SEND);
        Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        selectedPhotoIds.clear();
        updateActionButtonsState();
        // Not onClose() - that closes the whole phone UI rather than returning to the conversation this
        // gallery was opened from to send into. Same "pop one entry off the screen history" navigation the
        // Back button itself uses.
        onBackButtonPressed();
    }

    private void sendAction(CrazyPhoneMyPhotosActionMessage.Action action) {
        var message = new CrazyPhoneMyPhotosActionMessage(action, java.util.List.copyOf(selectedPhotoIds), menu.conversationId);
        NetworkAccess.sendToServer(message);
    }

    private void updateActionButtonsState() {
        boolean hasSelection = !selectedPhotoIds.isEmpty();
        Tooltip selectHint = Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.tooltip_select_photo"));
        if (buttonDelete != null) {
            buttonDelete.active = hasSelection;
            buttonDelete.setTooltip(hasSelection
                    ? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.tooltip_delete_selected"))
                    : selectHint);
        }
        if (buttonTake != null) {
            buttonTake.active = hasSelection;
            buttonTake.setTooltip(hasSelection
                    ? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.tooltip_take_selected"))
                    : selectHint);
        }
        if (buttonSend != null) {
            buttonSend.active = hasSelection;
            buttonSend.setTooltip(hasSelection
                    ? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.tooltip_send_selected"))
                    : selectHint);
        }
    }

    private void playToggleSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
