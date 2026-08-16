package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side photo capture for the Fabric-native picture pipeline (task #165). Uses only real vanilla
 * APIs (Screenshot.takeScreenshot, NativeImage) - no dependency on Camera mod (NeoForge-only) or on
 * Camerapture's own capture pipeline, which turns out to be entirely wired to ITS OWN item/network/storage
 * system and has no public "just give me the bytes" hook (confirmed by inspecting its jar - PictureTaker's
 * takePicture() always ends by uploading through Camerapture's own NewPicturePacket, never returns bytes to
 * a caller). NeoForge keeps using Camera mod unchanged - see CrazyPhoneTakePhotoProcedure.
 */
//? if fabric && >=1.20.5 {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;

public final class FabricPictureCapture {
    // Small on purpose: this is a "phone camera" feature, not a photography app - keeps upload/storage/
    // texture-cache costs bounded regardless of the player's actual render resolution.
    private static final int MAX_DIMENSION = 256;

    private FabricPictureCapture() {
    }

    // Captures the current frame and returns downscaled PNG bytes, or null if capture/encoding failed (e.g.
    // an unusually small render target) - the caller should just silently skip sending in that case, same
    // as any other best-effort client action in this mod.
    public static byte[] captureAsPng() {
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            try (NativeImage scaled = downscale(full)) {
                return scaled.asByteArray();
            }
        } catch (IOException e) {
            return null;
        }
    }

    // Nearest-neighbor downscale (via NativeImage's own resizeSubRectTo) to fit within MAX_DIMENSION on the
    // longer side, preserving aspect ratio - good enough for a small chat-bubble/thumbnail photo, doesn't
    // need the smoother filtering a real screenshot tool would want.
    private static NativeImage downscale(NativeImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        NativeImage target = new NativeImage(targetWidth, targetHeight, false);
        source.resizeSubRectTo(0, 0, width, height, target);
        return target;
    }
}
*///?}
