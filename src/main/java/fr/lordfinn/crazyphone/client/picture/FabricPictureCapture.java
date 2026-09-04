package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side photo capture for the native picture pipeline. Uses only real vanilla APIs
 * (Screenshot.takeScreenshot, NativeImage) - no dependency on Camera mod or on Camerapture's own capture
 * pipeline, which turns out to be entirely wired to ITS OWN item/network/storage system and has no public
 * "just give me the bytes" hook (confirmed by inspecting its jar - PictureTaker's takePicture() always ends
 * by uploading through Camerapture's own NewPicturePacket, never returns bytes to a caller).
 */
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
//?}
//?}

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;

import java.io.IOException;
import java.util.function.BiConsumer;

//? if neoforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public final class FabricPictureCapture {
    // Small on purpose: this is a "phone camera" feature, not a photography app - keeps upload/storage/
    // texture-cache costs bounded regardless of the player's actual render resolution. Two resolutions are
    // derived from the SAME screenshot: the low-quality preview (target height set by
    // Config#photoThumbnailPixelHeight) is what's shown by default everywhere (chat bubbles, the photo
    // item's own icon), FULL (Config#photoFullMaxDimension) is only fetched on demand when a photo is
    // actually opened full-size. Was a fixed 512 constant - live-reported as noticeably low quality once
    // people actually opened a full-size photo, made configurable (default raised to 1024) instead of
    // just bumping the fixed value, matching every other capacity/quality knob in this file's own class
    // (Config). No local constant anymore - see Config#photoFullMaxDimension directly at each call site
    // below, read fresh every capture so a runtime config change (NeoForge) takes effect immediately.

    // Set for the span between requestCapture() being called and the deferred capture actually running -
    // CrazyPhoneConversationScreen checks this to skip its own render() for those frames (see its own
    // doc comment on the check). Without this, a straight captureAsPng() call from a button's onPress
    // photographs the phone's OWN conversation UI (the last thing drawn into the main render target),
    // not the world behind it - the same "hide the UI, wait a frame, then capture" problem Camerapture's
    // own PictureTaker solves internally (confirmed via javap: it tracks a hudWasHidden flag and defers
    // the actual capture to renderTickEnd()), just done here without depending on Camerapture at all.
    public static volatile boolean suppressPhoneRendering = false;

    // Fabric's Event<T> has no unregister() - a single always-on tick listener (see onClientTick, wired
    // once from each loader's client entrypoint) polls this instead of registering/unregistering a
    // one-shot one. NeoForge's Event bus does support unregistering, but polling the same way keeps this
    // class identical on both loaders.
    private static volatile BiConsumer<byte[], byte[]> pendingCallback = null;

    private FabricPictureCapture() {
    }

    //? if neoforge {
    @SubscribeEvent
    //? if >=1.20.5 {
    /*public static void onNeoForgeClientTick(ClientTickEvent.Post event) {
        tickAll();
    }
    *///? } else {
    public static void onNeoForgeClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickAll();
    }
    //?}
    //?}

    // Ticks pending capture requests plus the capture mode's own zoom lerp - called every client tick on
    // both loaders (NeoForge via the event above, Fabric via CrazyphoneFabricClient's own END_CLIENT_TICK
    // registration, mirroring CallRingtoneManager's shape). CrazyPhoneCaptureMode#tick is itself a no-op
    // when capture mode isn't active, so this is safe to call unconditionally every tick.
    public static void tickAll() {
        onClientTick();
        CrazyPhoneCaptureMode.tick();
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

    // Called every client tick (see CrazyphoneFabricClient / the NeoForge client tick handler) - a no-op
    // except on the tick right after requestCapture() leaves a callback pending. suppressPhoneRendering
    // itself is deliberately NOT reset here - see captureBothResolutions's own doc comment on why it stays
    // true until the actual pixel copy has genuinely happened, not just been requested.
    public static void onClientTick() {
        BiConsumer<byte[], byte[]> callback = pendingCallback;
        if (callback == null)
            return;
        pendingCallback = null;
        captureBothResolutions(callback);
    }

    // Captures the current frame once and derives both resolutions from it (one screenshot, not two) -
    // calls back with (null, null) if capture/encoding failed (e.g. an unusually small render target), which
    // the caller should treat as a silent best-effort skip, same as any other client action in this mod.
    //
    // suppressPhoneRendering is cleared right where the actual GPU->CPU pixel copy happens on each branch
    // (immediately after Screenshot.takeScreenshot hands back real pixels), not by the caller beforehand -
    // clearing it in onClientTick() before calling this worked fine on <1.21.10 (a synchronous call: the
    // buffer read completes before the next line runs), but 1.21.10's callback-based takeScreenshot
    // reportedly does NOT resolve synchronously despite this file's own prior assumption that it did - live-
    // reported on 26.1 as the reticle/zoom-readout overlay still baked into the captured photo. If the
    // overlay's own suppressPhoneRendering check (see CrazyPhoneCaptureMode#drawOverlay) goes false before
    // the real pixel copy runs, a frame can render (and get captured) with the overlay back on.
    //? if <1.21.10 {
    private static void captureBothResolutions(BiConsumer<byte[], byte[]> callback) {
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            suppressPhoneRendering = false;
            try (NativeImage fullScaled = downscale(full, Config.photoFullMaxDimension)) {
                byte[] fullBytes = fullScaled.asByteArray();
                int targetHeight = Config.photoThumbnailPixelHeight;
                // 0 means "no separate low-quality preview" and a target at/above the photo's own height
                // would only ever upscale it - both cases skip the resize and reuse the full bytes as the
                // preview too, so PhotoSavedData#storePhoto only stores the one copy (see its own doc).
                if (targetHeight <= 0 || targetHeight >= fullScaled.getHeight()) {
                    callback.accept(fullBytes, fullBytes);
                } else {
                    try (NativeImage thumbnail = downscaleToHeight(fullScaled, targetHeight)) {
                        callback.accept(thumbnail.asByteArray(), fullBytes);
                    }
                }
            }
        } catch (IOException e) {
            suppressPhoneRendering = false;
            org.slf4j.LoggerFactory.getLogger("crazyphone-capture-debug").warn("Screenshot capture failed", e);
            callback.accept(null, null);
        }
    }
    //? } else {
    /*// 1.21.10 reworked Screenshot.takeScreenshot into a callback-based API and dropped
    // NativeImage#asByteArray() entirely - the only remaining way to get PNG bytes out of a NativeImage is
    // writeToFile(Path), so toPngBytes() round-trips through a throwaway temp file instead. Same "fail
    // closed to (null, null)" shape as the <1.21.10 branch on any error.
    private static void captureBothResolutions(BiConsumer<byte[], byte[]> callback) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), full -> {
                // The first thing done with the real captured pixels, before any further processing that
                // could itself take long enough for another frame to render - see this method's own doc
                // comment on why this can't be reset any earlier.
                suppressPhoneRendering = false;
                try (NativeImage fullImg = full; NativeImage fullScaled = downscale(fullImg, Config.photoFullMaxDimension)) {
                    byte[] fullBytes = toPngBytes(fullScaled);
                    int targetHeight = Config.photoThumbnailPixelHeight;
                    if (targetHeight <= 0 || targetHeight >= fullScaled.getHeight()) {
                        callback.accept(fullBytes, fullBytes);
                    } else {
                        try (NativeImage thumbnail = downscaleToHeight(fullScaled, targetHeight)) {
                            callback.accept(toPngBytes(thumbnail), fullBytes);
                        }
                    }
                } catch (IOException e) {
                    org.slf4j.LoggerFactory.getLogger("crazyphone-capture-debug").warn("Screenshot capture failed", e);
                    callback.accept(null, null);
                }
            });
        } catch (RuntimeException e) {
            suppressPhoneRendering = false;
            org.slf4j.LoggerFactory.getLogger("crazyphone-capture-debug").warn("Screenshot capture failed", e);
            callback.accept(null, null);
        }
    }

    private static byte[] toPngBytes(NativeImage image) throws IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("crazyphone-capture-", ".png");
        try {
            image.writeToFile(tmp);
            return java.nio.file.Files.readAllBytes(tmp);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
    *///?}

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

    // Same idea as downscale() but anchored on an exact target height instead of "fit within N on the
    // longer side" - Config#photoThumbnailPixelHeight is specified in target height precisely so a low
    // value gives a consistently chunky, pixel-art-like preview regardless of the source photo's aspect
    // ratio. Callers must only pass a targetHeight smaller than source's own height (see
    // captureBothResolutions - a photo is never upscaled for its preview).
    private static NativeImage downscaleToHeight(NativeImage source, int targetHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = (double) targetHeight / height;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));

        NativeImage target = new NativeImage(targetWidth, targetHeight, false);
        source.resizeSubRectTo(0, 0, width, height, target);
        return target;
    }
}
