package fr.lordfinn.crazyphone.client.gui;

/**
 * Full-size photo viewer with contextual buttons - structurally modeled on the old CrazyPhoneImageScreen
 * (plain client Screen, remembered previousScreen, centered button row near the bottom) but 100% original
 * code with no Camera-mod inheritance and no album button (albums are gone). Two entry points: a chat
 * bubble click (MessageWidget#onImageClick) and a held Photo item's right-click use action.
 */
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import java.util.UUID;

public class CrazyPhonePhotoViewerScreen extends Screen implements PhoneScreen {
    private final UUID photoId;
    private final Screen previousScreen;

    public CrazyPhonePhotoViewerScreen(UUID photoId) {
        super(Component.translatable("gui.crazyphone.photo_viewer.title"));
        this.photoId = photoId;
        this.previousScreen = Minecraft.getInstance().screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int buttonWidth = 90, buttonHeight = 20, spacing = 5;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.crazyphone.photo_viewer.back"), b -> onClose())
                .bounds(startX, y, buttonWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.crazyphone.photo_viewer.save"), b -> {
            NetworkAccess.sendToServer(new CrazyPhoneGivePhotoItemPacket(photoId));
            Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1f, 1f);
        }).bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, PhotoResolution.FULL);
        if (texture != null)
            drawFitted(guiGraphics, texture);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // Fits the image within an 80%-of-window box, preserving aspect ratio and centering it - same box
    // proportions the old CrazyPhoneImageScreen used, just computed from the real cached dimensions now
    // (that screen's Camera-mod-derived image data didn't reliably expose real width/height either).
    private void drawFitted(GuiGraphics guiGraphics, FabricPictureCache.CachedTexture texture) {
        int boxWidth = (int) (width * 0.8f);
        int boxHeight = (int) (height * 0.8f);
        double scale = Math.min((double) boxWidth / texture.width(), (double) boxHeight / texture.height());
        int drawWidth = (int) Math.round(texture.width() * scale);
        int drawHeight = (int) Math.round(texture.height() * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        GuiCompat.blit(guiGraphics, texture.location(), x, y, 0, drawWidth, drawHeight);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previousScreen);
    }
}
