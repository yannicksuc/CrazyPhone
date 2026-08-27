package fr.lordfinn.crazyphone.client.gui;

/**
 * Full-screen photo capture UI, opened from the conversation screen's camera button. Not a container
 * menu (there's no server-authoritative state behind "framing a shot") - a plain client Screen, closed and
 * reopened the same direct way {@code CrazyPhoneImageScreen} manages its own {@code previousScreen}, since
 * the server-side phone screen history stack is built entirely around reconstructing named menus and has no
 * slot for a one-off client-only view. The world stays visible behind this screen for free (a non-pause
 * Screen never stops the 3D world from rendering); on top of it this only draws a small original viewfinder
 * frame and a zoom readout - it does not attempt to reproduce any specific camera mod's HUD.
 *
 * Fabric-only for now: NeoForge's capture backend (the equivalent of FabricPictureCapture) doesn't exist
 * yet - see the implementation plan's rollout order (this is proven on Fabric first, then generalized).
 */
//? if fabric && >=1.20.5 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import fr.lordfinn.crazyphone.client.CrazyPhoneZoomController;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCapture;
import fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;

public class CrazyPhoneCaptureOverlayScreen extends Screen implements PhoneScreen {
    private final Screen previousScreen;
    private final String conversationId;
    private final CrazyPhoneZoomController zoom;
    private boolean capturing = false;

    public CrazyPhoneCaptureOverlayScreen(String conversationId) {
        super(Component.translatable("gui.crazyphone.capture_overlay.title"));
        this.conversationId = conversationId;
        Minecraft mc = Minecraft.getInstance();
        this.previousScreen = mc.screen;
        this.zoom = new CrazyPhoneZoomController(mc);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Ticks the zoom lerp every client tick this screen is open - called from the same shared tick hook
    // FabricPictureCapture already uses, not vanilla's own Screen#tick (which only fires while a menu-backed
    // screen keeps its container synced - this is a plain Screen, vanilla never calls tick() on it).
    public void onClientTick() {
        zoom.tick(Minecraft.getInstance());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Own chrome must vanish for the exact frame(s) a capture is in flight too, or the reticle/zoom
        // readout would bake into the photo itself.
        if (FabricPictureCapture.suppressPhoneRendering)
            return;
        drawReticle(guiGraphics);
        drawZoomReadout(guiGraphics);
    }

    private void drawReticle(GuiGraphics guiGraphics) {
        int size = 14;
        int inset = 24;
        int color = 0xB0FFFFFF;
        int left = inset, top = inset, right = width - inset, bottom = height - inset;
        // Four corner brackets, own design - not a reproduction of any specific camera mod's frame.
        guiGraphics.fill(left, top, left + size, top + 2, color);
        guiGraphics.fill(left, top, left + 2, top + size, color);
        guiGraphics.fill(right - size, top, right, top + 2, color);
        guiGraphics.fill(right - 2, top, right, top + size, color);
        guiGraphics.fill(left, bottom - 2, left + size, bottom, color);
        guiGraphics.fill(left, bottom - size, left + 2, bottom, color);
        guiGraphics.fill(right - size, bottom - 2, right, bottom, color);
        guiGraphics.fill(right - 2, bottom - size, right, bottom, color);
    }

    private void drawZoomReadout(GuiGraphics guiGraphics) {
        String text = Math.round(zoom.currentZoom() * 100) + "%";
        GuiCompat.pushPose(guiGraphics);
        guiGraphics.drawCenteredString(font, text, width / 2, height - 40, 0xFFFFFFFF);
        GuiCompat.popPose(guiGraphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        zoom.adjust(scrollY);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!capturing && (button == 0 || button == 1)) {
            triggerCapture();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void triggerCapture() {
        capturing = true;
        FabricPictureCapture.requestCapture((thumbnailPng, fullPng) -> {
            capturing = false;
            if (thumbnailPng != null && fullPng != null)
                NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket(conversationId, thumbnailPng, fullPng));
            Minecraft.getInstance().setScreen(previousScreen);
        });
    }

    @Override
    public void onClose() {
        zoom.restore(Minecraft.getInstance());
        Minecraft.getInstance().setScreen(previousScreen);
    }
}
*///?}
