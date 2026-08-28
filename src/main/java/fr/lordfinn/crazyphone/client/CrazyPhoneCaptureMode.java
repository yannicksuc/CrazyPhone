package fr.lordfinn.crazyphone.client;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if <1.20.5 {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?} else {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///?}
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.lwjgl.glfw.GLFW;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.client.picture.FabricPictureCapture;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket;
import fr.lordfinn.crazyphone.procedures.IsPhoneOpenProcedure;
import fr.lordfinn.crazyphone.procedures.IsPhoneSetupProcedure;
import fr.lordfinn.crazyphone.utils.NetworkAccess;

/**
 * Event-driven replacement for the capture overlay's old {@code Screen}-based design: a real {@code Screen}
 * inherently releases the mouse and shows the cursor (that's how vanilla's own mouse-grab logic works -
 * there's no public way to keep a Screen open AND have the mouse control the camera), which is why the old
 * design couldn't look around while framing a shot, showed a free cursor, and left the vanilla HUD/held-item
 * drawn on top. This instead stays in normal "in-game" input mode the whole time (mouse still grabbed, camera
 * still turns normally) and hooks into the render/input pipeline directly for exactly this: FOV scaled live
 * at render time (never touching the persisted options.fov() value at all, unlike the retired
 * CrazyPhoneZoomController - which is what let zoom silently cap out once options.fov()'s own 30-110 slider
 * clamp was hit, and what left FOV stuck if the player disconnected mid-shot instead of properly exiting),
 * mouse scroll for zoom, left-click-cancels/right-click-shoots, and hiding the held phone + rest of the HUD
 * while framing, the same way vanilla's own F1 does.
 *
 * The state/trigger logic below (enter/exit/tick/triggerCapture/drawReticle/drawZoomReadout) is loader-
 * neutral; only the actual hook wiring differs (NeoForge: ViewportEvent.ComputeFov/InputEvent/RenderHandEvent/
 * RenderGuiEvent below; Fabric: mixins, since Fabric API has no direct equivalent of any of them - see
 * CrazyPhoneCaptureFovMixin/CrazyPhoneCaptureScrollMixin/CrazyPhoneCapturePressMixin/CrazyPhoneCaptureHandMixin/
 * CrazyPhoneCaptureGuiMixin).
 */
//? if neoforge {
//? if <1.20.5 {
// No bus= here (unlike this project's packet-registration classes, which subscribe to a genuine
// mod-lifecycle event and belong on Bus.MOD): every @SubscribeEvent below is a regular client
// render/input event (RenderGuiEvent, ViewportEvent, InputEvent, RenderHandEvent), which only exists
// on the default game event bus - Bus.MOD rejects them at registration time with "This bus only
// accepts subclasses of IModBusEvent".
@EventBusSubscriber(value = Dist.CLIENT)
//?} else {
/*@EventBusSubscriber(value = Dist.CLIENT)
*///?}
//?}
public final class CrazyPhoneCaptureMode {
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private static final float ZOOM_STEP = 0.25f;
    private static final float LERP_SPEED = 0.35f;

    private static boolean active = false;
    private static String conversationId = "";
    private static Screen previousScreen = null;
    private static boolean previousHideGui = false;
    private static boolean capturing = false;
    private static float targetZoom = MIN_ZOOM;
    private static float currentZoom = MIN_ZOOM;

    private CrazyPhoneCaptureMode() {
    }

    public static boolean isActive() {
        return active;
    }

    public static float currentZoom() {
        return currentZoom;
    }

    /** {@code newConversationId} empty means a standalone shot (see CrazyPhoneUploadPicturePacket). Closes
     * whatever screen is currently open (a container screen keeps the mouse released, so there's no way to
     * both keep one open and regain camera control) - the same screen is reopened by {@link #exit()}. */
    public static void enter(String newConversationId) {
        if (active)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (!heldPhoneCanCapture(mc)) {
            if (mc.player != null)
                mc.player.displayClientMessage(Component.translatable("message.crazyphone.phone_locked_no_photo"), true);
            return;
        }
        conversationId = newConversationId;
        previousScreen = mc.screen;
        previousHideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setScreen(null);
        targetZoom = MIN_ZOOM;
        currentZoom = MIN_ZOOM;
        capturing = false;
        active = true;
    }

    /** Single choke point for every capture entry point (punch-to-shoot, the home screen's Photo icon, the
     * conversation camera icon) - the latter two can only be reached by already going through setup/sign-in,
     * but punch-to-shoot bypasses that whole flow (it's a raw attack-key shortcut), so a phone that was just
     * crafted or is still locked could otherwise jump straight into framing a shot. */
    private static boolean heldPhoneCanCapture(Minecraft mc) {
        if (mc.player == null)
            return false;
        ItemStack held = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        return held.getItem() == ModItems.CRAZY_PHONE.get()
                && IsPhoneSetupProcedure.execute(held)
                && IsPhoneOpenProcedure.execute(held);
    }

    public static void exit() {
        if (!active)
            return;
        Minecraft mc = Minecraft.getInstance();
        active = false;
        mc.options.hideGui = previousHideGui;
        mc.setScreen(previousScreen);
        previousScreen = null;
    }

    public static void adjustZoom(double scrollDeltaY) {
        if (!active)
            return;
        targetZoom = Mth.clamp(targetZoom + (float) scrollDeltaY * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
    }

    /** Ticks the zoom lerp - called every client tick on both loaders (NeoForge via the event below, Fabric
     * via CrazyphoneFabricClient's own END_CLIENT_TICK registration through FabricPictureCapture#tickAll). */
    public static void tick() {
        if (!active)
            return;
        currentZoom += (targetZoom - currentZoom) * LERP_SPEED;
    }

    public static void triggerCapture() {
        if (!active || capturing)
            return;
        capturing = true;
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.playSound(fr.lordfinn.crazyphone.init.ModSounds.TAKE_PICTURE.get(), 1.0f, 1.0f);
        FabricPictureCapture.requestCapture((thumbnailPng, fullPng) -> {
            capturing = false;
            if (thumbnailPng != null && fullPng != null)
                NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket(conversationId, thumbnailPng, fullPng));
            exit();
        });
    }

    public static void drawOverlay(GuiGraphics guiGraphics) {
        if (!active || FabricPictureCapture.suppressPhoneRendering)
            return;
        drawReticle(guiGraphics);
        drawZoomReadout(guiGraphics);
    }

    private static void drawReticle(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
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

    private static void drawZoomReadout(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        String text = Math.round(currentZoom * 100) + "%";
        guiGraphics.drawCenteredString(mc.font, text, width / 2, height - 40, 0xFFFFFFFF);
    }

    //? if neoforge {
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (active)
            event.setFOV(event.getFOV() / currentZoom);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active)
            return;
        adjustZoom(event.getScrollDeltaY());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (!active || event.getAction() != GLFW.GLFW_PRESS)
            return;
        event.setCanceled(true);
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT)
            exit();
        else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            triggerCapture();
    }

    // Hides the held phone (and any other held item) while framing a shot - the world render's own
    // first-person hand pass, not part of the GUI hideGui suppresses.
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        drawOverlay(event.getGuiGraphics());
    }
    //?}
    // tick() is called from FabricPictureCapture#tickAll instead of its own event subscriber here - that
    // class already has a client-tick hook registered on both loaders (mirroring CallRingtoneManager's
    // shape), so a second, separate tick registration here would just double-tick the zoom lerp on NeoForge.
}
