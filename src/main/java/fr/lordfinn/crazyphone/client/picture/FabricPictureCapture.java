package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side photo capture for the native picture pipeline. Uses only real vanilla APIs
 * (Screenshot.takeScreenshot, NativeImage) - no dependency on Camera mod or on Camerapture's own capture
 * pipeline, which turns out to be entirely wired to ITS OWN item/network/storage system and has no public
 * "just give me the bytes" hook (confirmed by inspecting its jar - PictureTaker's takePicture() always ends
 * by uploading through Camerapture's own NewPicturePacket, never returns bytes to a caller).
 */
//? if fabric && >=1.20.5 {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.util.function.BiConsumer;

public final class FabricPictureCapture {
    // Small on purpose: this is a "phone camera" feature, not a photography app - keeps upload/storage/
    // texture-cache costs bounded regardless of the player's actual render resolution. Two resolutions are
    // captured from the SAME screenshot: THUMBNAIL is what's shown by default everywhere (chat bubbles, the
    // photo item's own icon), FULL is only fetched on demand when a photo is actually opened full-size.
    public static final int THUMBNAIL_MAX_DIMENSION = 128;
    public static final int FULL_MAX_DIMENSION = 512;

    // Set for the span between requestCapture() being called and the deferred capture actually running -
    // CrazyPhoneConversationScreen checks this to skip its own render() for those frames (see its own
    // doc comment on the check). Without this, a straight captureAsPng() call from a button's onPress
    // photographs the phone's OWN conversation UI (the last thing drawn into the main render target),
    // not the world behind it - the same "hide the UI, wait a frame, then capture" problem Camerapture's
    // own PictureTaker solves internally (confirmed via javap: it tracks a hudWasHidden flag and defers
    // the actual capture to renderTickEnd()), just done here without depending on Camerapture at all.
    public static volatile boolean suppressPhoneRendering = false;

    // Fabric's Event<T> has no unregister() - a single always-on tick listener (see onClientTick, wired
    // once from CrazyphoneFabricClient) polls this instead of registering/unregistering a one-shot one.
    private static volatile BiConsumer<byte[], byte[]> pendingCallback = null;

    private FabricPictureCapture() {
    }

    // Hides the phone screen for a moment, captures a clean shot of the world once that's actually taken
    // effect, then hands (thumbnailPng, fullPng) - either both non-null or both null on failure - to
    // onCaptured and restores rendering. A client tick always follows several rendered frames (ticks run at
    // a fixed 20 Hz, well below any normal framerate), so by the time onClientTick fires, the screen has
    // genuinely been absent from at least one fully rendered frame - unlike calling captureBothResolutions()
    // synchronously from the button click itself, which would just photograph the still-visible phone screen.
    public static void requestCapture(BiConsumer<byte[], byte[]> onCaptured) {
        suppressPhoneRendering = true;
        pendingCallback = onCaptured;
    }

    // Called every client tick (see CrazyphoneFabricClient) - a no-op except on the tick right after
    // requestCapture() leaves a callback pending.
    public static void onClientTick() {
        BiConsumer<byte[], byte[]> callback = pendingCallback;
        if (callback == null)
            return;
        pendingCallback = null;
        suppressPhoneRendering = false;
        captureBothResolutions(callback);
    }

    // Captures the current frame once and derives both resolutions from it (one screenshot, not two) -
    // calls back with (null, null) if capture/encoding failed (e.g. an unusually small render target), which
    // the caller should treat as a silent best-effort skip, same as any other client action in this mod.
    private static void captureBothResolutions(BiConsumer<byte[], byte[]> callback) {
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            try (NativeImage thumbnail = downscale(full, THUMBNAIL_MAX_DIMENSION);
                 NativeImage fullScaled = downscale(full, FULL_MAX_DIMENSION)) {
                callback.accept(thumbnail.asByteArray(), fullScaled.asByteArray());
            }
        } catch (IOException e) {
            callback.accept(null, null);
        }
    }

    // Nearest-neighbor downscale (via NativeImage's own resizeSubRectTo) to fit within maxDimension on the
    // longer side, preserving aspect ratio - good enough for a phone-camera photo, doesn't need the
    // smoother filtering a real screenshot tool would want.
    private static NativeImage downscale(NativeImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        NativeImage target = new NativeImage(targetWidth, targetHeight, false);
        source.resizeSubRectTo(0, 0, width, height, target);
        return target;
    }
}
*///?}
