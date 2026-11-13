package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.ClientPrefs;
import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

/**
 * Splash shown over the phone's display the very first time the phone is opened on this game, and again
 * whenever a new phone (not registered yet) opens its registration screen: "A Sandbox creation by LordFinn /
 * for Mastu" at the top, LordFinn and Mastu (a render made with the lordfinn.fr scene editor) below, standing
 * on the bottom edge of the display.
 * It fades in, stays a few seconds and fades out; a click or a key skips it. While it is visible the phone
 * underneath gets no input (see CrazyPhoneDefaultScreenScreen). Remembered in
 * config/crazyphone-client.properties (launchSplashShown), so after that only a new phone brings it back.
 *
 * Only the launch screens (sign in, password, home) start it, never a call or photo screen that opens by
 * itself. One instance for the whole game: it keeps running across the screen changes of the same launch.
 */
public final class LaunchSplash {
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ IMAGE = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-splash.png");
    /** The phone's own blue display (cut out of phone-background.png, rounded corners kept): it hides the screen
     * underneath without changing the phone's look. */
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ SCREEN = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-splash-screen.png");

    /** The phone's display inside phone-background.png, relative to leftPos / topPos. */
    private static final int DISPLAY_X0 = 4, DISPLAY_Y0 = 9, DISPLAY_X1 = 118, DISPLAY_Y1 = 176;
    /** Drawn size of the 471 x 420 render: the whole display width, its cut (the legs) resting on the bottom
     * edge of the display. */
    private static final int IMAGE_W = DISPLAY_X1 - DISPLAY_X0, IMAGE_H = Math.round(IMAGE_W * 420f / 471f);

    private static final int TEXT_MAX_W = 106;
    private static final int LINE_GAP = 3;

    private static final int TEXT = 0x123A63;

    private static final long FADE_IN_MS = 250;
    /** 3 s in all: 250 ms fade in, held, 600 ms fade out. */
    private static final long HOLD_UNTIL_MS = 2400;
    private static final long FADE_OUT_MS = 600;

    /** 0 = not started (or not needed), otherwise when it started. */
    private static long startMillis;
    /** When a click or key asked to skip it, 0 if never. */
    private static long skipMillis;
    private static boolean finished;

    private LaunchSplash() {
    }

    /** Called when a launch screen opens: starts the splash if it was never shown on this game. */
    public static void startIfFirstLaunch() {
        if (finished || startMillis != 0)
            return;
        if (ClientPrefs.launchSplashShown()) {
            finished = true;
            return;
        }
        startMillis = System.currentTimeMillis();
        // Saved as soon as it shows: a crash or a closed phone never brings it back.
        ClientPrefs.markLaunchSplashShown();
    }

    /** Already played for the phone being registered: closing and reopening that same phone, or a resize
     * (init() runs again), must not replay it. Cleared when a registered phone opens (see phoneRegistered). */
    private static boolean playedForNewPhone;

    /** Called when the registration screen of a new phone opens: plays the splash again for that phone. */
    public static void startForNewPhone() {
        if (playedForNewPhone)
            return;
        playedForNewPhone = true;
        if (isActive())
            return;
        finished = false;
        skipMillis = 0;
        startMillis = System.currentTimeMillis();
        ClientPrefs.markLaunchSplashShown();
    }

    /** A registered phone opened (sign in or home screen): the next new phone gets the splash again. */
    public static void phoneRegistered() {
        playedForNewPhone = false;
    }

    /** True while the splash covers the display (the phone underneath must ignore input). */
    public static boolean isActive() {
        if (finished || startMillis == 0)
            return false;
        if (alpha(System.currentTimeMillis()) <= 0f) {
            finished = true;
            return false;
        }
        return true;
    }

    /** A click or a key: fades out now instead of waiting. */
    public static void skip() {
        if (isActive() && skipMillis == 0)
            skipMillis = System.currentTimeMillis();
    }

    private static float alpha(long now) {
        long t = now - startMillis;
        if (skipMillis != 0) {
            long since = now - skipMillis;
            float fromNow = 1f - since / (float) FADE_OUT_MS;
            return Math.max(0f, Math.min(fadeInOut(t), fromNow));
        }
        return fadeInOut(t);
    }

    private static float fadeInOut(long t) {
        if (t < FADE_IN_MS)
            return t / (float) FADE_IN_MS;
        if (t < HOLD_UNTIL_MS)
            return 1f;
        return Math.max(0f, 1f - (t - HOLD_UNTIL_MS) / (float) FADE_OUT_MS);
    }

    /** Draws the splash over the phone at (left, top) if it is running. Call after everything else. */
    public static void render(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int left, int top) {
        if (!isActive())
            return;
        float a = alpha(System.currentTimeMillis());
        Font font = Minecraft.getInstance().font;
        int alphaByte = Math.round(a * 255f);

        // Above the widgets and their labels.
        //? if <1.21.10 {
        GuiCompat.flush(guiGraphics);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        //?} else {
        /*guiGraphics.nextStratum();
        *///?}
        try {
            GuiCompat.blit(guiGraphics, SCREEN, left + DISPLAY_X0, top + DISPLAY_Y0, 0, DISPLAY_X1 - DISPLAY_X0, DISPLAY_Y1 - DISPLAY_Y0, a);

            Component line1 = Component.translatable("gui.crazyphone.launch_splash.line1").withStyle(net.minecraft.ChatFormatting.BOLD);
            Component line2 = Component.translatable("gui.crazyphone.launch_splash.line2").withStyle(net.minecraft.ChatFormatting.BOLD);
            // Full size, wrapped to the display width (shrinking the bold text to fit one line made it hard to read).
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>(font.split(line1, TEXT_MAX_W));
            lines.addAll(font.split(line2, TEXT_MAX_W));
            int lineH = font.lineHeight + LINE_GAP;
            int centerX = left + (DISPLAY_X0 + DISPLAY_X1) / 2;

            int imageTop = top + DISPLAY_Y1 - IMAGE_H;
            GuiCompat.blit(guiGraphics, IMAGE, left + DISPLAY_X0, imageTop, 0, IMAGE_W, IMAGE_H, a);
            // Centered in the space left above the characters.
            int textH = lineH * lines.size() - LINE_GAP;
            int y = top + DISPLAY_Y0 + (imageTop - (top + DISPLAY_Y0) - textH) / 2;

            // Font treats an alpha under 4 as fully opaque: the text simply stops at the very end of the fade.
            if (alphaByte >= 8) {
                int color = (alphaByte << 24) | TEXT;
                for (net.minecraft.util.FormattedCharSequence line : lines) {
                    guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, line, centerX - font.width(line) / 2, y, color, false);
                    y += lineH;
                }
            }
        } finally {
            //? if <1.21.10 {
            GuiCompat.flush(guiGraphics);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            guiGraphics.pose().popPose();
            //?}
        }
    }
}
