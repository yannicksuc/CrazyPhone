package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.GuiCompat;

/**
 * The gray box + animated ring spinner shown in place of a photo that {@link
 * fr.lordfinn.crazyphone.client.picture.FabricPictureCache#getOrRequest} hasn't resolved yet - one shared
 * draw call so every 2D spot a photo can appear (gallery grid, chat bubble, full-size viewer, ...) shows the
 * same "still loading" cue instead of blank space or a stale frame left over from whatever rendered last.
 *
 * The spinner texture itself (crazyphone-photo-loading-spinner.png, 32x256, transparent background) is a
 * plain GUI icon under textures/screens/, not a block/item atlas sprite - its own .mcmeta plays no role here
 * (vanilla's PNG-animation controller only drives atlas sprites), so the 8 stacked frames are picked by hand
 * from wall-clock time and blitted as a UV sub-rectangle, the same technique CrazyPhoneMyPhotosScreenScreen's
 * own drawCroppedThumbnail already uses for a fixed crop instead of a moving one.
 */
public final class PhotoLoadingPlaceholder {
    private static final /*$ res_loc {*/ResourceLocation/*$}*/ SPINNER =
            Crazyphone.parseId("crazyphone:textures/screens/crazyphone-photo-loading-spinner.png");
    // mc-core-grey-5 - a neutral "empty slot" backing behind the spinner, not meant to read as content.
    private static final int BACKGROUND_COLOR = 0xFF3D3938;
    private static final int FRAME_COUNT = 8;
    private static final int FRAME_MILLIS = 100;
    // The sprite is a native 32x32 - never drawn larger (a bigger box just gets more padding around it, not
    // a blurrier upscaled spinner) and never smaller than a floor that keeps its 6px squares legible.
    private static final int NATIVE_SIZE = 32;
    private static final int MIN_SIZE = 12;

    private PhotoLoadingPlaceholder() {
    }

    /** Fills the given box with the placeholder background, then draws the spinner centered inside it with
     * padding proportional to the box size. Safe to call every frame - which frame of the spritesheet to
     * show is derived fresh from the system clock each time, not tracked as state on any caller. */
    public static void draw(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);

        int padding = Math.max(2, Math.min(width, height) / 8);
        int available = Math.min(width, height) - padding * 2;
        int size = Math.max(MIN_SIZE, Math.min(NATIVE_SIZE, available));
        int spinnerX = x + (width - size) / 2;
        int spinnerY = y + (height - size) / 2;

        int frame = (int) ((System.currentTimeMillis() / FRAME_MILLIS) % FRAME_COUNT);
        float vOffset = frame / (float) FRAME_COUNT;
        float vSpan = 1f / FRAME_COUNT;

        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, spinnerX, spinnerY);
        GuiCompat.drawTexturedQuad(guiGraphics, SPINNER, 0, 0, size, size, 0f, vOffset, 1f, vOffset + vSpan);
        GuiCompat.popPose(guiGraphics);
    }
}
