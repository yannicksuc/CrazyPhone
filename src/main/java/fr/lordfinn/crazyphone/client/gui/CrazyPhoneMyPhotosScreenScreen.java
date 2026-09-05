package fr.lordfinn.crazyphone.client.gui;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.client.gui.components.PhotoLoadingPlaceholder;
import fr.lordfinn.crazyphone.client.gui.components.ScrollingText;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.init.ModItems;
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
    private static final int VISIBLE_ROWS = 3;
    private static final int THUMB_SIZE = 34;
    private static final int THUMB_PITCH = 36;
    private static final int GRID_TOP_Y = 30;
    private static final int GRID_WIDTH = GRID_COLUMNS * THUMB_PITCH;
    private static final int GRID_HEIGHT = VISIBLE_ROWS * THUMB_PITCH;
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

    @Override
    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get()),
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.title"));
        renderPhotoCountInfo(guiGraphics);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get()),
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.title"));
        renderPhotoCountInfo(guiGraphics);
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
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * (VISIBLE_ROWS + 2));
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

    /** Draws the "247/300" photo counter flush against the header banner's right edge, on the title's own
     * row (see the HEADER_BANNER_RIGHT_X/HEADER_TITLE_Y comment above), and - once the owner's photo count
     * gets within STORAGE_WARNING_THRESHOLD_FRACTION of Config.maxPhotosStoredPerOwner - a short one-line
     * warning just below the header that the oldest photos will soon be auto-evicted. The warning reuses
     * ScrollingText (same as the title itself) so an overly long translation scrolls instead of overflowing
     * past the phone's frame. */
    private void renderPhotoCountInfo(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int max = fr.lordfinn.crazyphone.Config.maxPhotosStoredPerOwner;
        int count = menu.photoIds.size();
        Component counterText = Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.photo_count", count, max);
        int counterX = this.leftPos + HEADER_BANNER_RIGHT_X - this.font.width(counterText) - 2;
        // Explicit alpha byte (0xFF......) - on >=26, GuiGraphicsExtractor#text silently drops any call
        // whose color has a zero alpha byte instead of treating it as opaque like pre-26's drawString did.
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, counterText, counterX, this.topPos + HEADER_TITLE_Y, COUNTER_TEXT_COLOR, false);

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
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * (VISIBLE_ROWS + 1));
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
        if (button != 0 && button != 1)
            return false;
        if (!isWithinGridCropZone(mouseX, mouseY))
            return false;

        int gridLeft = gridLeft(), gridTop = gridTop();
        int topRow = topVisibleRow();
        int firstIndex = topRow * GRID_COLUMNS;
        int lastIndex = Math.min(menu.photoIds.size(), firstIndex + GRID_COLUMNS * (VISIBLE_ROWS + 1));
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
                Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(new CrazyPhonePhotoViewerScreen(photoId));
            }
            return true;
        }
        return false;
    }

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
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(message);
        *///? } else {
        PacketDistributor.SERVER.noArg().send(message);
        //?}
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
