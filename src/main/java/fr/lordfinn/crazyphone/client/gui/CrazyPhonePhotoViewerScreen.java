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
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
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
    // Rolled once per screen instance (not per frame) so the tilt stays fixed while this preview is open,
    // and re-rolls the next time a photo is opened - a fresh little "polaroid dropped on a table" touch.
    private final float tiltDegrees;

    public CrazyPhonePhotoViewerScreen(UUID photoId) {
        this(photoId, false);
    }

    public CrazyPhonePhotoViewerScreen(UUID photoId, boolean openedFromInventory) {
        super(Component.translatable("gui.crazyphone.photo_viewer.title"));
        this.photoId = photoId;
        this.openedFromInventory = openedFromInventory;
        this.previousScreen = Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/;
        this.tiltDegrees = (java.util.concurrent.ThreadLocalRandom.current().nextFloat() * 8f) - 4f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        // A window resize re-invokes init() (Screen#resize calls it again with the new width/height) -
        // without clearing here first, the old buttons' still-registered click/focus dispatch (addRenderableWidget)
        // and stale-positioned render (ownButtons) both stuck around alongside the freshly-added ones,
        // showing as the same button doubled up and overlapping at two different positions.
        ownButtons.clear();
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

    // Deliberately NOT calling super.render()/super.extractRenderState() here - Screen's own default render body
    // calls renderBackground()/extractBackground(), a completely different method from renderTransparentBackground()
    // below: it triggers the REAL blur post-process (renderBlurredBackground -> GameRenderer#processBlurEffect)
    // plus a tiled gray menu-background texture (renderMenuBackground), both drawn over the WHOLE screen
    // - which, called after our photo above, painted over it every frame regardless of how the photo
    // itself was drawn (found live: this reproduced identically across three completely different photo
    // draw techniques, which only made sense once traced back to this second, redundant background pass
    // rather than anything about the photo's own draw call). Replicate just the part of Screen#render
    // this screen actually needs - rendering its own widgets - without that redundant background call
    // (see ownButtons's own doc comment for why this iterates that instead of Screen's own renderables).
    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this./^$ gui_render_transparent_background {^/renderTransparentBackground/^$}^/(guiGraphics);
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, PhotoResolution.FULL);
        if (texture != null)
            drawFitted(guiGraphics, texture);
        for (Button button : ownButtons)
            button.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this./*$ gui_render_transparent_background {*/renderTransparentBackground/*$}*/(guiGraphics);
        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(photoId, PhotoResolution.FULL);
        if (texture != null)
            drawFitted(guiGraphics, texture);
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}

    // White "photo paper" border - live-requested so the preview reads as a physical photo, not a bare
    // texture floating on the dimmed background. A few pixels on every side, drawn as one fill() rect
    // BEHIND the image (see drawFitted's own call site) rather than four separate strips - simpler, and the
    // image itself covers the middle so only the border ring stays visible.
    private static final int BORDER_PX = 6;

    // Material-Design-style elevation shadow behind the photo card: several progressively larger, fainter
    // rects stacked behind the card (not one hard-edged rect) to fake a soft drop shadow without a real blur
    // pass - GuiGraphics has no blur primitive available here (renderTransparentBackground's blur is a
    // whole-screen post-process, not something you can scope to one rect). Centered - grows evenly on every
    // side rather than skewed toward one corner.
    private static final int[] SHADOW_LAYER_SPREAD = {0, 3, 7, 12};
    private static final int[] SHADOW_LAYER_ALPHA = {0x50, 0x30, 0x18, 0x0A};

    // Fits the image within an 80%-of-window box, preserving aspect ratio and centering it - same box
    // proportions the old CrazyPhoneImageScreen used, just computed from the real cached dimensions now
    // (that screen's Camera-mod-derived image data didn't reliably expose real width/height either).
    private void drawFitted(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, FabricPictureCache.CachedTexture texture) {
        int boxWidth = (int) (width * 0.8f);
        int boxHeight = (int) (height * 0.8f);
        double scale = Math.min((double) boxWidth / texture.width(), (double) boxHeight / texture.height());
        int drawWidth = (int) Math.round(texture.width() * scale);
        int drawHeight = (int) Math.round(texture.height() * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;

        // Rotated about the card's own center so the shadow and border tilt along with the photo, like a
        // real polaroid dropped at a slight angle - not just the image texture skewed inside a straight box.
        int centerX = x + drawWidth / 2;
        int centerY = y + drawHeight / 2;
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, centerX, centerY);
        GuiCompat.rotateDegrees(guiGraphics, tiltDegrees);
        GuiCompat.translate(guiGraphics, -centerX, -centerY);

        for (int i = SHADOW_LAYER_SPREAD.length - 1; i >= 0; i--) {
            int spread = SHADOW_LAYER_SPREAD[i];
            int argb = (SHADOW_LAYER_ALPHA[i] << 24);
            guiGraphics.fill(
                    x - BORDER_PX - spread, y - BORDER_PX - spread,
                    x + drawWidth + BORDER_PX + spread, y + drawHeight + BORDER_PX + spread,
                    argb);
        }

        guiGraphics.fill(x - BORDER_PX, y - BORDER_PX, x + drawWidth + BORDER_PX, y + drawHeight + BORDER_PX, 0xFFFFFFFF);
        // GuiGraphics#blit (a batched call) is what lost to the background here - checked against the real
        // Camera mod jar's own ImageScreen#drawImage (de.maxhenkel.camera.gui.ImageScreen, javap'd from
        // libs/camera-neoforge-1.21.1-1.0.21.jar) for how a screen showing a full-size in-memory image over a
        // dimmed background is supposed to work: it never uses GuiGraphics#blit at all, it draws via a raw
        // immediate-mode Tesselator quad (RenderSystem.setShader + BufferBuilder + BufferUploader.drawWithShader),
        // submitted synchronously the instant it's called, with no batching/ordering ambiguity possible.
        // GuiCompat#drawTexturedQuad is that exact same technique, already used (and working) for
        // CrazyPhoneMyPhotosScreenScreen's gallery thumbnails.
        GuiCompat.drawTexturedQuad(guiGraphics, texture.location(), x, y, x + drawWidth, y + drawHeight, 0f, 0f, 1f, 1f);

        GuiCompat.popPose(guiGraphics);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(previousScreen);
    }
}
