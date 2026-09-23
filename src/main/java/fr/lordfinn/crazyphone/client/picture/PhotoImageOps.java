package fr.lordfinn.crazyphone.client.picture;

/**
 * Pure pixel math for the photo editor (CrazyPhonePhotoEditScreen) - every operation takes a source
 * {@link NativeImage} and returns a FRESH one, never mutating or closing its input, so {@link #apply} can
 * always re-derive the full result from the one, never-modified original image on every parameter change
 * (see {@link PhotoEditState}'s own doc comment for why that matters for reset/undo correctness). Every
 * method here is plain, GL-context-free pixel manipulation - safe to call off the render thread, and safe
 * to unit test directly (no Minecraft instance needed), unlike the texture-upload side of the editor.
 */
import com.mojang.blaze3d.platform.NativeImage;

public final class PhotoImageOps {
    private PhotoImageOps() {
    }

    // ---- Packed-pixel access (version-gated layout, not just a renamed method pair) -----------------------
    // Mirrors PixelArtDownscaler's own already-verified split (confirmed against the real decompiled
    // NativeImage.java for both shapes) - repeated here rather than shared because that class deliberately
    // drops alpha (a live capture is always opaque); this one must preserve it, since an edited photo can
    // easily be a transparent PNG/GIF frame.
    private static int readPixelARGB(NativeImage image, int x, int y) {
        //? if <26 {
        int p = image.getPixelRGBA(x, y);
        int a = (p >> 24) & 0xFF, r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
        //? } else {
        /*int p = image.getPixel(x, y);
        int a = (p >>> 24) & 0xFF, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        *///?}
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void writePixelARGB(NativeImage image, int x, int y, int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int packed = (a << 24) | (b << 16) | (g << 8) | r;
        //? if <26 {
        image.setPixelRGBA(x, y, packed);
        //? } else {
        /*image.setPixelABGR(x, y, packed);
        *///?}
    }

    private static NativeImage newImage(int width, int height) {
        return new NativeImage(Math.max(1, width), Math.max(1, height), false);
    }

    // Same version-gated technique FabricPictureCapture/AnimatedPhotoCodec/PhotoImporter each already have
    // their own copy of (NativeImage#asByteArray() was dropped on >=1.21.10 with no direct replacement other
    // than round-tripping through a throwaway temp file) - this is at least the photo EDITOR's own single
    // shared copy, rather than a further one per editor call site.
    //? if <1.21.10 {
    public static byte[] toPngBytes(NativeImage image) throws java.io.IOException {
        return image.asByteArray();
    }
    //? } else {
    /*public static byte[] toPngBytes(NativeImage image) throws java.io.IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("crazyphone-editops-", ".png");
        try {
            image.writeToFile(tmp);
            return java.nio.file.Files.readAllBytes(tmp);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
    *///?}

    // ---- Individual operations ------------------------------------------------------------------------

    /** Arbitrary-angle clockwise rotation around the image's own center, on a canvas the SAME size as the
     * source - a corner the rotated content no longer covers comes out fully transparent (alpha 0) rather
     * than any background color, so the crop rectangle drawn on top of this (which never rotates with the
     * image - see {@link PhotoEditState}'s own doc comment) can end up transparent there too, on purpose.
     * Bilinear-sampled (not nearest-neighbor) so a live drag doesn't look jagged; at exactly 0 degrees this
     * degenerates to an exact per-pixel copy (fx=fy=0 at every destination), so the common "not rotated at
     * all" case never loses sharpness to the interpolation. */
    public static NativeImage rotateArbitrary(NativeImage source, float degrees) {
        int w = source.getWidth(), h = source.getHeight();
        NativeImage target = newImage(w, h);
        if (Math.abs(degrees % 360f) < 0.01f) {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    writePixelARGB(target, x, y, readPixelARGB(source, x, y));
            return target;
        }
        // Inverse rotation: for each DESTINATION pixel, find the SOURCE coordinate that maps onto it, so
        // every destination pixel gets filled exactly once with no gaps (the forward direction would leave
        // holes wherever rounding skips a destination pixel).
        double rad = Math.toRadians(-degrees);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double cx = w / 2.0, cy = h / 2.0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                double sx = dx * cos - dy * sin + cx;
                double sy = dx * sin + dy * cos + cy;
                writePixelARGB(target, x, y, bilinearSample(source, sx - 0.5, sy - 0.5, w, h));
            }
        }
        return target;
    }

    private static int argbOrTransparent(NativeImage source, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h)
            return 0;
        return readPixelARGB(source, x, y);
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    /** 4-tap bilinear sample at a fractional source coordinate - a tap that falls outside the source's own
     * bounds is treated as fully transparent black rather than clamped to the nearest edge pixel, which is
     * what actually makes {@link #rotateArbitrary}'s exposed corners fade out to transparent instead of
     * showing a hard-edged duplicate of the image's own border pixels. */
    private static int bilinearSample(NativeImage source, double sx, double sy, int w, int h) {
        int x0 = (int) Math.floor(sx), y0 = (int) Math.floor(sy);
        double fx = sx - x0, fy = sy - y0;
        int c00 = argbOrTransparent(source, x0, y0, w, h);
        int c10 = argbOrTransparent(source, x0 + 1, y0, w, h);
        int c01 = argbOrTransparent(source, x0, y0 + 1, w, h);
        int c11 = argbOrTransparent(source, x0 + 1, y0 + 1, w, h);
        double w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
        int a = 0, r = 0, g = 0, b = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            double mixed = channel(c00, shift) * w00 + channel(c10, shift) * w10 + channel(c01, shift) * w01 + channel(c11, shift) * w11;
            int value = clamp255(Math.round((float) mixed));
            switch (shift) {
                case 24 -> a = value;
                case 16 -> r = value;
                case 8 -> g = value;
                default -> b = value;
            }
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Zooms the image in around its own center by {@code zoom} (>=1), on a canvas that stays the SAME pixel
     * size - implemented as a centered crop of the middle {@code 1/zoom} fraction followed by a resize back
     * up to the original dimensions, which is exactly equivalent to scaling the image up and letting the
     * excess spill past the frame. Existing crop fractions (defined against this same canvas size) stay
     * meaningful across a zoom change for exactly this reason - see {@link PhotoEditState#withZoom}. */
    public static NativeImage applyZoom(NativeImage source, float zoom) {
        int w = source.getWidth(), h = source.getHeight();
        int subW = Math.max(1, Math.round(w / zoom)), subH = Math.max(1, Math.round(h / zoom));
        int x0 = (w - subW) / 2, y0 = (h - subH) / 2;
        try (NativeImage sub = crop(source, x0, y0, x0 + subW, y0 + subH)) {
            return resize(sub, w, h);
        }
    }

    /** Slides the image around within a canvas that stays the SAME pixel size, by {@code panXFrac}/
     * {@code panYFrac} (fractions of that canvas's own width/height) - whatever edge this exposes comes out
     * fully transparent, the same convention {@link #rotateArbitrary} uses, so this composes cleanly with a
     * rotation that already left transparent corners. This is what lets the player reposition the photo
     * underneath a crop rectangle that itself never moves - see {@link PhotoEditState#withPan}. */
    public static NativeImage applyPan(NativeImage source, float panXFrac, float panYFrac) {
        int w = source.getWidth(), h = source.getHeight();
        int offsetX = Math.round(panXFrac * w), offsetY = Math.round(panYFrac * h);
        if (offsetX == 0 && offsetY == 0) {
            NativeImage copy = newImage(w, h);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    writePixelARGB(copy, x, y, readPixelARGB(source, x, y));
            return copy;
        }
        NativeImage target = newImage(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                writePixelARGB(target, x, y, argbOrTransparent(source, x - offsetX, y - offsetY, w, h));
        return target;
    }

    /** Crops to the pixel rectangle [x0,y0,x1,y1) (end-exclusive), clamped to the source's own bounds -
     * callers resolve {@link PhotoEditState}'s normalized fractions to pixel coordinates first (see
     * {@link #apply}), since only this method needs to know the source's real dimensions to do that safely. */
    public static NativeImage crop(NativeImage source, int x0, int y0, int x1, int y1) {
        int srcW = source.getWidth(), srcH = source.getHeight();
        x0 = Math.max(0, Math.min(srcW - 1, x0));
        y0 = Math.max(0, Math.min(srcH - 1, y0));
        x1 = Math.max(x0 + 1, Math.min(srcW, x1));
        y1 = Math.max(y0 + 1, Math.min(srcH, y1));
        NativeImage target = newImage(x1 - x0, y1 - y0);
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                writePixelARGB(target, x - x0, y - y0, readPixelARGB(source, x, y));
        return target;
    }

    /** Plain resize to an exact target size - same technique (NativeImage's own filtered resizeSubRectTo)
     * FabricPictureCapture/PixelArtDownscaler already use for the capture/thumbnail pipeline, reused here
     * instead of a second implementation. Deliberately does NOT preserve aspect ratio itself - the editor's
     * own lock-ratio toggle is what keeps width/height in sync before this is ever called (see
     * CrazyPhonePhotoEditScreen), so a caller that wants a stretched/letterboxed result can still get one by
     * passing a target that doesn't match the source's own ratio. */
    public static NativeImage resize(NativeImage source, int targetWidth, int targetHeight) {
        NativeImage target = newImage(targetWidth, targetHeight);
        source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), target);
        return target;
    }

    /** Hue rotation (degrees, wraps at +/-360) + contrast + brightness, all three in one pass so a preview
     * refresh only ever re-reads/re-writes each pixel once. Alpha passes through untouched.
     * <p>
     * contrast is a percentage (-100..100): -100 flattens everything to mid-gray, 0 leaves the image
     * unchanged, +100 doubles the distance every channel sits from mid-gray (128), clamped to 0..255.
     * brightness (-100..100) is added directly to each channel AFTER contrast, also clamped. Hue is applied
     * first, in HSB space, since shifting hue on top of an already brightness/contrast-adjusted color would
     * shift saturation/brightness right along with it in a way that doesn't match what a "hue" slider alone
     * should do. */
    public static NativeImage adjustColor(NativeImage source, float hueDegrees, float contrast, float brightness) {
        int width = source.getWidth(), height = source.getHeight();
        NativeImage target = newImage(width, height);
        float contrastFactor = 1f + contrast / 100f;
        boolean hueShift = Math.abs(hueDegrees % 360f) > 0.01f;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = readPixelARGB(source, x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                if (hueShift) {
                    float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                    hsb[0] = wrapHue(hsb[0] + hueDegrees / 360f);
                    int rgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
                    r = (rgb >> 16) & 0xFF;
                    g = (rgb >> 8) & 0xFF;
                    b = rgb & 0xFF;
                }
                r = clamp255(Math.round((r - 128) * contrastFactor + 128 + brightness));
                g = clamp255(Math.round((g - 128) * contrastFactor + 128 + brightness));
                b = clamp255(Math.round((b - 128) * contrastFactor + 128 + brightness));
                writePixelARGB(target, x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return target;
    }

    private static float wrapHue(float hue) {
        float wrapped = hue % 1f;
        return wrapped < 0 ? wrapped + 1f : wrapped;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** The full pipeline, in the one order that actually makes sense: rotate first (so the crop rectangle
     * the player drags on screen is always relative to what they're currently looking at, not the source
     * file's own original orientation), then zoom (still centered on the whole canvas, still ahead of crop -
     * see {@link PhotoEditState#withZoom}'s own doc comment on why that keeps crop fractions meaningful),
     * then pan (sliding that rotated/zoomed content around, still ahead of crop for the same reason), then
     * crop, then resize (scaling the CROPPED area, not the whole rotated/zoomed/panned image), then color
     * adjustment last (cheapest to run on whatever's smallest by that point, and order-independent from the
     * geometric steps either way - see {@link #adjustColor}'s own doc comment for why hue/contrast/brightness
     * are applied together in one pass). Returns a fresh image the caller owns; never closes {@code original}. */
    public static NativeImage apply(NativeImage original, PhotoEditState state) {
        NativeImage rotated = rotateArbitrary(original, state.rotationDegrees());
        NativeImage zoomed;
        if (state.hasZoom()) {
            zoomed = applyZoom(rotated, state.zoomScale());
            rotated.close();
        } else {
            zoomed = rotated;
        }
        NativeImage panned;
        if (state.hasPan()) {
            panned = applyPan(zoomed, state.panX(), state.panY());
            zoomed.close();
        } else {
            panned = zoomed;
        }
        NativeImage cropped;
        if (state.hasCrop()) {
            int width = panned.getWidth(), height = panned.getHeight();
            int x0 = Math.round(state.cropLeft() * width);
            int y0 = Math.round(state.cropTop() * height);
            int x1 = Math.round(state.cropRight() * width);
            int y1 = Math.round(state.cropBottom() * height);
            cropped = crop(panned, x0, y0, x1, y1);
            panned.close();
        } else {
            cropped = panned;
        }
        NativeImage resized;
        if (state.hasResize()) {
            resized = resize(cropped, state.targetWidth(), state.targetHeight());
            cropped.close();
        } else {
            resized = cropped;
        }
        boolean colorAdjusted = Math.abs(state.hueDegrees() % 360f) > 0.01f || Math.abs(state.contrast()) > 0.01f || Math.abs(state.brightness()) > 0.01f;
        if (!colorAdjusted)
            return resized;
        NativeImage colored = adjustColor(resized, state.hueDegrees(), state.contrast(), state.brightness());
        resized.close();
        return colored;
    }
}
