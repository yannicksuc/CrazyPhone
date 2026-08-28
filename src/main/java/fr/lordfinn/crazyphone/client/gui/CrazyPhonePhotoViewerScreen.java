package fr.lordfinn.crazyphone.client.gui;

/**
 * Full-size photo viewer with contextual buttons - structurally modeled on the old CrazyPhoneImageScreen
 * (plain client Screen, remembered previousScreen, centered button row near the bottom) but 100% original
 * code with no Camera-mod inheritance and no album button (albums are gone). Two entry points: a chat
 * bubble click (MessageWidget#onImageClick) and a held Photo item's right-click use action - the latter
 * passes openedFromInventory=true to hide Back/Save (there's no prior screen to go "back" to, and the
 * player already owns this exact item, so "save to inventory" would just make a redundant copy).
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CrazyPhonePhotoViewerScreen extends Screen implements PhoneScreen {
    private final UUID photoId;
    private final boolean openedFromInventory;
    private final Screen previousScreen;
    // Screen's own `renderables` field is private under Fabric's Yarn mappings (public under NeoForge's
    // official ones) - rather than chase a mapping-safe accessor, this screen just tracks its own two
    // buttons directly, since it never has more than that and render() must skip Screen's own widget-render
    // loop anyway (see render()'s own doc comment on why super.render() can't be called here).
    private final List<Button> ownButtons = new ArrayList<>();

    public CrazyPhonePhotoViewerScreen(UUID photoId) {
        this(photoId, false);
    }

    public CrazyPhonePhotoViewerScreen(UUID photoId, boolean openedFromInventory) {
        super(Component.translatable("gui.crazyphone.photo_viewer.title"));
        this.photoId = photoId;
        this.openedFromInventory = openedFromInventory;
        this.previousScreen = Minecraft.getInstance().screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (openedFromInventory)
            return;
        int buttonWidth = 90, buttonHeight = 20, spacing = 5;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 30;
        // addRenderableWidget (not just addWidget) still matters here for its OTHER effect - registering the
        // button for click/focus dispatch (Screen#children()) - only its own contribution to the private
        // renderables list goes unused, since render() below iterates ownButtons instead.
        Button backButton = Button.builder(Component.translatable("gui.crazyphone.photo_viewer.back"), b -> onClose())
                .bounds(startX, y, buttonWidth, buttonHeight).build();
        Button saveButton = Button.builder(Component.translatable("gui.crazyphone.photo_viewer.save"), b -> {
            NetworkAccess.sendToServer(new CrazyPhoneGivePhotoItemPacket(photoId));
            Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1f, 1f);
        }).bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight).build();
        addRenderableWidget(backButton);
        addRenderableWidget(saveButton);
        ownButtons.add(backButton);
        ownButtons.add(saveButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, PhotoResolution.FULL);
        if (texture != null)
            drawFitted(guiGraphics, texture);
        // Deliberately NOT calling super.render() here - Screen's own default render() body calls
        // renderBackground(), a completely different method from renderTransparentBackground() above: it
        // triggers the REAL blur post-process (renderBlurredBackground -> GameRenderer#processBlurEffect)
        // plus a tiled gray menu-background texture (renderMenuBackground), both drawn over the WHOLE screen
        // - which, called after our photo above, painted over it every frame regardless of how the photo
        // itself was drawn (found live: this reproduced identically across three completely different photo
        // draw techniques, which only made sense once traced back to this second, redundant background pass
        // rather than anything about the photo's own draw call). Replicate just the part of Screen#render
        // this screen actually needs - rendering its own widgets - without that redundant background call
        // (see ownButtons's own doc comment for why this iterates that instead of Screen's own renderables).
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
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
        // GuiGraphics#blit (a batched call) is what lost to the background here - checked against the real
        // Camera mod jar's own ImageScreen#drawImage (de.maxhenkel.camera.gui.ImageScreen, javap'd from
        // libs/camera-neoforge-1.21.1-1.0.21.jar) for how a screen showing a full-size in-memory image over a
        // dimmed background is supposed to work: it never uses GuiGraphics#blit at all, it draws via a raw
        // immediate-mode Tesselator quad (RenderSystem.setShader + BufferBuilder + BufferUploader.drawWithShader),
        // submitted synchronously the instant it's called, with no batching/ordering ambiguity possible.
        // GuiCompat#drawTexturedQuad is that exact same technique, already used (and working) for
        // CrazyPhoneMyPhotosScreenScreen's gallery thumbnails.
        GuiCompat.drawTexturedQuad(guiGraphics, texture.location(), x, y, x + drawWidth, y + drawHeight, 0f, 0f, 1f, 1f);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(previousScreen);
    }
}
