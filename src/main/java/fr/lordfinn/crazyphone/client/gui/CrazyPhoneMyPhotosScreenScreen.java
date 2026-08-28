package fr.lordfinn.crazyphone.client.gui;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneMyPhotosActionMessage;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMyPhotosScreenMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * amber selection border, row-scroll, Delete/Take/Send action bar) is an original re-implementation of this
 * mod's own pre-Camera-mod-removal picture grid, ported to render through {@link FabricPictureCache} instead
 * of a real container of ItemStacks - there's no Slot/AbstractContainerMenu grid backing this at all
 * anymore, just a plain resolved {@code List<UUID>}, so selection/scroll/rendering all work directly against
 * photo ids instead of slot indices.
 */
public class CrazyPhoneMyPhotosScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneMyPhotosScreenMenu> {
    private static final HashMap<String, Object> guistate = new HashMap<>();
    private static final int GRID_COLUMNS = 3;
    private static final int VISIBLE_ROWS = 3;
    private static final int THUMB_SIZE = 34;
    private static final int THUMB_PITCH = 36;
    private static final int GRID_TOP_Y = 30;
    private static final int SELECTED_BORDER_COLOR = CrazyPhoneColors.ACCENT_YELLOW;
    private static final int SELECTED_INSET = 2;

    private final Set<UUID> selectedPhotoIds = new HashSet<>();
    private int scrollRowOffset = 0;
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get()),
                Component.translatable("gui.crazyphone.crazy_phone_my_photos_screen.title"));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        super.renderBg(guiGraphics, partialTicks, gx, gy);
        renderThumbnails(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private int maxRowOffset() {
        int rowCount = (menu.photoIds.size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rowCount - VISIBLE_ROWS);
    }

    private void renderThumbnails(GuiGraphics guiGraphics) {
        int firstIndex = scrollRowOffset * GRID_COLUMNS;
        for (int visible = 0; visible < GRID_COLUMNS * VISIBLE_ROWS; visible++) {
            int index = firstIndex + visible;
            if (index >= menu.photoIds.size())
                break;
            UUID photoId = menu.photoIds.get(index);
            PhotoResolution resolution = fr.lordfinn.crazyphone.ClientConfig.phonePhotoListPixelated ? PhotoResolution.THUMBNAIL : PhotoResolution.FULL;
            FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, resolution);
            if (texture == null)
                continue;

            int col = visible % GRID_COLUMNS;
            int row = visible / GRID_COLUMNS;
            int x = this.leftPos + fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_X + col * THUMB_PITCH;
            int y = this.topPos + GRID_TOP_Y + row * THUMB_PITCH;

            boolean selected = selectedPhotoIds.contains(photoId);
            if (selected) {
                guiGraphics.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, SELECTED_BORDER_COLOR);
                drawCroppedThumbnail(guiGraphics, x + SELECTED_INSET, y + SELECTED_INSET,
                        THUMB_SIZE - SELECTED_INSET * 2, THUMB_SIZE - SELECTED_INSET * 2, texture);
            } else {
                drawCroppedThumbnail(guiGraphics, x, y, THUMB_SIZE, THUMB_SIZE, texture);
            }
        }
    }

    /** "Cover" crop: always fills the full width x height target - the source UV rect is shrunk to the
     * target's aspect ratio and centered, so the longer source dimension gets cropped rather than
     * letterboxed. */
    private static void drawCroppedThumbnail(GuiGraphics guiGraphics, int x, int y, int width, int height, FabricPictureCache.CachedTexture texture) {
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
        int firstIndex = scrollRowOffset * GRID_COLUMNS;
        for (int visible = 0; visible < GRID_COLUMNS * VISIBLE_ROWS; visible++) {
            int index = firstIndex + visible;
            if (index >= menu.photoIds.size())
                break;
            int col = visible % GRID_COLUMNS;
            int row = visible / GRID_COLUMNS;
            int x = this.leftPos + fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_X + col * THUMB_PITCH;
            int y = this.topPos + GRID_TOP_Y + row * THUMB_PITCH;
            if (mouseX < x || mouseX >= x + THUMB_SIZE || mouseY < y || mouseY >= y + THUMB_SIZE)
                continue;

            UUID photoId = menu.photoIds.get(index);
            if (button == 0) {
                playToggleSound();
                if (!selectedPhotoIds.add(photoId))
                    selectedPhotoIds.remove(photoId);
                updateActionButtonsState();
            } else {
                Minecraft.getInstance().setScreen(new CrazyPhonePhotoViewerScreen(photoId));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int newOffset = Math.max(0, Math.min(maxRowOffset(), scrollRowOffset - (int) Math.signum(scrollY)));
        if (newOffset != scrollRowOffset) {
            scrollRowOffset = newOffset;
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
        onClose();
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
