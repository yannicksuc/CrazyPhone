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
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;
//? if <1.20.5 {
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
//?} else {
/*import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
*///?}
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
    private static final org.slf4j.Logger DEBUG_LOGGER = org.slf4j.LoggerFactory.getLogger("crazyphone-capture-debug");
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private static final float ZOOM_STEP = 0.25f;
    private static final float LERP_SPEED = 0.35f;

    private static boolean active = false;
    private static String conversationId = "";
    private static Screen previousScreen = null;
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
        mc.setScreen(null);
        targetZoom = MIN_ZOOM;
        currentZoom = MIN_ZOOM;
        capturing = false;
        active = true;
        DEBUG_LOGGER.info("enter() activated capture mode, conversationId='{}'", newConversationId);
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
        DEBUG_LOGGER.info("triggerCapture() firing, requesting capture");
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.playSound(fr.lordfinn.crazyphone.init.ModSounds.TAKE_PICTURE.get(), 1.0f, 1.0f);
        FabricPictureCapture.requestCapture((thumbnailPng, fullPng) -> {
            capturing = false;
            DEBUG_LOGGER.info("capture callback: thumbnailPng={} bytes, fullPng={} bytes",
                    thumbnailPng == null ? -1 : thumbnailPng.length, fullPng == null ? -1 : fullPng.length);
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

    // Escape while framing a shot has no Screen to fall back on (mc.setScreen(null) in enter()), so vanilla
    // opens the pause menu the same way it would on any other in-game Escape press - fired right before that
    // actually happens, letting this redirect to a normal exit() (mouse-grab restored, previous screen if
    // any reopened) instead.
    @SubscribeEvent
    public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (active && event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            event.setCanceled(true);
            exit();
        }
    }

    // NeoForge's own Minecraft instance actually runs an ExtendedGui (a subclass that fully overrides
    // render(), implementing its own per-overlay dispatch to fire RenderGuiOverlayEvent/RenderGuiLayerEvent
    // for each vanilla element) - a Mixin targeting the parent Gui#render, tried first, silently never fires
    // at all on NeoForge as a result (virtual dispatch always resolves to the override), which is what a
    // Fabric-only equivalent of this exists for instead (see CrazyPhoneCaptureGuiMixin). RenderGuiEvent.Post
    // itself DOES still fire correctly (it's raised from within ExtendedGui's own real method body), so this
    // draws the overlay the same way the old pre-mixin version of this class did.
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        drawOverlay(event.getGuiGraphics());
    }

    // Suppresses every vanilla HUD element (hotbar, crosshair, health/food/xp bars, chat, etc.) individually
    // while framing a shot, instead of the options.hideGui toggle this used to rely on - hideGui turned out
    // to ALSO gate whether ExtendedGui fires RenderGuiEvent at all (confirmed live: the overlay above only
    // ever drew while hideGui was OFF, the opposite of what this needs), so suppressing each element by
    // name here leaves hideGui completely untouched.
    //? if <1.20.5 {
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> HIDDEN_OVERLAYS = java.util.Set.of(
            VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.CROSSHAIR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(), VanillaGuiOverlay.AIR_LEVEL.id(),
            VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.POTION_ICONS.id(),
            VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), VanillaGuiOverlay.CHAT_PANEL.id(),
            VanillaGuiOverlay.SUBTITLES.id(), VanillaGuiOverlay.RECORD_OVERLAY.id(),
            VanillaGuiOverlay.SCOREBOARD.id(), VanillaGuiOverlay.TITLE_TEXT.id(),
            VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.SLEEP_FADE.id(),
            VanillaGuiOverlay.DEMO_OVERLAY.id(), VanillaGuiOverlay.DEBUG_SCREEN.id(),
            VanillaGuiOverlay.PLAYER_LIST.id()
    );

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (active && HIDDEN_OVERLAYS.contains(event.getOverlay().id()))
            event.setCanceled(true);
    }
    //?}
    //? if >=1.20.5 <1.21.10 {
    /*private static final java.util.Set<net.minecraft.resources.ResourceLocation> HIDDEN_LAYERS = java.util.Set.of(
            VanillaGuiLayers.HOTBAR, VanillaGuiLayers.CROSSHAIR,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.JUMP_METER,
            VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EFFECTS,
            VanillaGuiLayers.BOSS_OVERLAY, VanillaGuiLayers.CHAT,
            VanillaGuiLayers.SUBTITLE_OVERLAY, VanillaGuiLayers.OVERLAY_MESSAGE,
            VanillaGuiLayers.SCOREBOARD_SIDEBAR, VanillaGuiLayers.TITLE,
            VanillaGuiLayers.SPECTATOR_TOOLTIP, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.SLEEP_OVERLAY, VanillaGuiLayers.DEMO_OVERLAY,
            VanillaGuiLayers.DEBUG_OVERLAY, VanillaGuiLayers.TAB_LIST,
            VanillaGuiLayers.SAVING_INDICATOR, VanillaGuiLayers.CAMERA_OVERLAYS
    );

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (active && HIDDEN_LAYERS.contains(event.getName()))
            event.setCanceled(true);
    }
    *///?}
    // >=1.21.10: no-op - VanillaGuiLayers was reworked on that version (several fields used above no
    // longer exist, e.g. JUMP_METER/EXPERIENCE_BAR/DEBUG_OVERLAY/SAVING_INDICATOR), and the capture
    // feature as a whole is already known not to work there yet (see FabricPictureCapture/CrazyPhonePhotoItem's
    // own 1.21.10 TODOs) - not worth chasing the new field names until that backport happens.
    //?}
    // tick() is called from FabricPictureCapture#tickAll instead of its own event subscriber here - that
    // class already has a client-tick hook registered on both loaders (mirroring CallRingtoneManager's
    // shape), so a second, separate tick registration here would just double-tick the zoom lerp on NeoForge.
}
