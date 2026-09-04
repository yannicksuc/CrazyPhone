package fr.lordfinn.crazyphone.client.picture;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Random;

/**
 * Shared downscale-to-height step for both {@link FabricPictureCapture} (capture time) and
 * {@link FabricPictureCache} (deriving a thumbnail from an already-decoded full image) - previously each had
 * its own copy of a plain {@code NativeImage#resizeSubRectTo} call, which reads as pixel art only by
 * accident of being small, not because anything about it is actually organized into flat, deliberate color
 * regions (live-compared against a real in-game capture: it's a soft, blurry, still-24-bit-color thumbnail,
 * not pixel art).
 *
 * Below {@link #PIXEL_ART_MAX_HEIGHT}, this instead runs a real photo-to-pixel-art pipeline researched and
 * benchmarked against a real capture (see this session's own comparison of nine candidate pipelines): an
 * area-average downscale, a k-means palette (luma-weighted distance, the cheap-but-effective stand-in for a
 * full CIEDE2000/Lab metric) capped small enough to force flat, deliberate color regions, and Floyd-Steinberg
 * error diffusion so gradients (sky, lighting) survive the small palette instead of banding. Above the
 * threshold, a preview is closer to "a smaller photo" than "pixel art" - the classic resize is the right
 * tool there, and running a 32-color palette over it would make a large preview look needlessly degraded.
 */
public final class PixelArtDownscaler {
    // Above this target height a preview reads as a (soft, full-color) small photo, not pixel art - the
    // classic resize already suits that better than a heavily palette-reduced one would. At/below it, the
    // grid is small enough that committing to a deliberate small palette is what makes the result read as
    // pixel art instead of a blurry thumbnail. Config#photoThumbnailPixelHeight defaults to 16, well under
    // this - the pixel-art pipeline is what most players will actually see day to day.
    public static final int PIXEL_ART_MAX_HEIGHT = 64;

    // Small on purpose: the whole point is FEWER, more deliberate colors than the source photo ever had -
    // once the target grid is tiny (a few hundred pixels), even 32 colors already exceeds what a human pixel
    // artist would typically commit to for something this size. Clamped to the pixel count itself below so a
    // very small grid (e.g. a few dozen pixels) never asks k-means for more clusters than there are pixels.
    private static final int PALETTE_SIZE = 32;
    private static final int KMEANS_ITERATIONS = 10;

    private PixelArtDownscaler() {
    }

    /** Downscales to an exact target height (matching both call sites' own prior contract - only ever called
     *  with a targetHeight smaller than source's own height, a photo is never upscaled for its preview),
     *  routing to the pixel-art pipeline or the classic resize depending on {@link #PIXEL_ART_MAX_HEIGHT}. */
    public static NativeImage downscaleToHeight(NativeImage source, int targetHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = Math.max(1, (int) Math.round(width * ((double) targetHeight / height)));

        if (targetHeight > PIXEL_ART_MAX_HEIGHT) {
            NativeImage target = new NativeImage(targetWidth, targetHeight, false);
            source.resizeSubRectTo(0, 0, width, height, target);
            return target;
        }
        return pixelArtDownscale(source, targetWidth, targetHeight);
    }

    private static NativeImage pixelArtDownscale(NativeImage source, int outW, int outH) {
        int[] rgb = areaAverage(source, outW, outH);
        int k = Math.min(PALETTE_SIZE, outW * outH);
        int[] palette = kMeansPalette(rgb, k);
        floydSteinbergInPlace(rgb, outW, outH, palette);

        NativeImage target = new NativeImage(outW, outH, false);
        for (int y = 0; y < outH; y++)
            for (int x = 0; x < outW; x++)
                writePixel(target, x, y, rgb[y * outW + x]);
        return target;
    }

    // ---- Downscale filter ---------------------------------------------------------------------------------

    /** Box/area-average downscale: every source pixel inside a cell contributes to that cell's output color,
     *  unlike a nearest/bilinear resize which only samples a handful of them - the correct decimation filter,
     *  and the first of the three fixes a naive resize is missing (see this class's own doc comment). Returns
     *  plain 0xRRGGBB ints (no alpha - a real in-game capture is always fully opaque, confirmed live by
     *  {@link FabricPictureCache#hasTransparency}'s own doc comment). */
    private static int[] areaAverage(NativeImage source, int outW, int outH) {
        int sw = source.getWidth(), sh = source.getHeight();
        int[] out = new int[outW * outH];
        for (int oy = 0; oy < outH; oy++) {
            int y0 = oy * sh / outH, y1 = Math.max(y0 + 1, (oy + 1) * sh / outH);
            for (int ox = 0; ox < outW; ox++) {
                int x0 = ox * sw / outW, x1 = Math.max(x0 + 1, (ox + 1) * sw / outW);
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1; y++)
                    for (int x = x0; x < x1; x++) {
                        int rgb = readPixel(source, x, y);
                        r += (rgb >> 16) & 0xFF; g += (rgb >> 8) & 0xFF; b += rgb & 0xFF; n++;
                    }
                out[oy * outW + ox] = ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
            }
        }
        return out;
    }

    // ---- Palette (k-means, luma-weighted distance) ---------------------------------------------------------

    /** A fixed seed (not System's own randomness) so the same photo always downscales to the same result -
     *  a capture happens once per photo, and a nondeterministic palette would mean re-deriving a thumbnail
     *  from a FULL image later (see FabricPictureCache#deriveThumbnailFromFull) could visibly disagree with
     *  the one generated at capture time for no reason a player could make sense of. */
    private static final long PALETTE_SEED = 0x93B2A7L;

    private static int[] kMeansPalette(int[] pixels, int k) {
        Random rnd = new Random(PALETTE_SEED);
        double[][] centroids = new double[k][3];
        for (int i = 0; i < k; i++) {
            int p = pixels[rnd.nextInt(pixels.length)];
            centroids[i] = new double[]{(p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF};
        }

        int[] assignment = new int[pixels.length];
        for (int it = 0; it < KMEANS_ITERATIONS; it++) {
            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i];
                double r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                int best = 0; double bestD = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double d = weightedDist(r, g, b, centroids[c][0], centroids[c][1], centroids[c][2]);
                    if (d < bestD) { bestD = d; best = c; }
                }
                assignment[i] = best;
            }
            double[][] sum = new double[k][3];
            int[] count = new int[k];
            for (int i = 0; i < pixels.length; i++) {
                int rgb = pixels[i], c = assignment[i];
                sum[c][0] += (rgb >> 16) & 0xFF; sum[c][1] += (rgb >> 8) & 0xFF; sum[c][2] += rgb & 0xFF;
                count[c]++;
            }
            for (int c = 0; c < k; c++)
                if (count[c] > 0)
                    centroids[c] = new double[]{sum[c][0] / count[c], sum[c][1] / count[c], sum[c][2] / count[c]};
        }
        int[] palette = new int[k];
        for (int c = 0; c < k; c++)
            palette[c] = ((int) centroids[c][0] << 16) | ((int) centroids[c][1] << 8) | (int) centroids[c][2];
        return palette;
    }

    /** Luma-weighted Euclidean RGB distance (0.30/0.59/0.11) - human vision weighs green highest and blue
     *  lowest; plain Euclidean RGB treats all three equally and visibly mispicks palette entries as a result.
     *  A full perceptual metric (CIEDE2000 in Lab space) is the documented "correct" upgrade if this ever
     *  needs to go further, at real extra cost this runs-once-per-capture pipeline doesn't need yet. */
    private static double weightedDist(double r1, double g1, double b1, double r2, double g2, double b2) {
        double dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
        return 0.30 * dr * dr + 0.59 * dg * dg + 0.11 * db * db;
    }

    private static int nearestInPalette(int[] palette, double r, double g, double b) {
        int best = palette[0]; double bestD = Double.MAX_VALUE;
        for (int p : palette) {
            double d = weightedDist(r, g, b, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    // ---- Dithering -----------------------------------------------------------------------------------------

    /** Floyd-Steinberg error diffusion at palette-match time - a flat nearest-palette match alone crushes
     *  smooth gradients (sky, lighting falloff) into hard bands once the palette is this small; diffusing
     *  each pixel's quantization error into its not-yet-processed neighbors (7/16 right, 3/16 below-left,
     *  5/16 below, 1/16 below-right) recovers most of that gradient back as a fine dither pattern instead. */
    private static void floydSteinbergInPlace(int[] rgb, int w, int h, int[] palette) {
        float[] r = new float[rgb.length], g = new float[rgb.length], b = new float[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            r[i] = (rgb[i] >> 16) & 0xFF; g[i] = (rgb[i] >> 8) & 0xFF; b[i] = rgb[i] & 0xFF;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int match = nearestInPalette(palette, r[i], g[i], b[i]);
                float er = r[i] - ((match >> 16) & 0xFF), eg = g[i] - ((match >> 8) & 0xFF), eb = b[i] - (match & 0xFF);
                rgb[i] = match;
                diffuse(r, g, b, w, h, x, y, er, eg, eb);
            }
        }
    }

    private static void diffuse(float[] r, float[] g, float[] b, int w, int h, int x, int y, float er, float eg, float eb) {
        addErr(r, g, b, w, h, x + 1, y, er, eg, eb, 7 / 16f);
        addErr(r, g, b, w, h, x - 1, y + 1, er, eg, eb, 3 / 16f);
        addErr(r, g, b, w, h, x, y + 1, er, eg, eb, 5 / 16f);
        addErr(r, g, b, w, h, x + 1, y + 1, er, eg, eb, 1 / 16f);
    }

    private static void addErr(float[] r, float[] g, float[] b, int w, int h, int x, int y, float er, float eg, float eb, float weight) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        int i = y * w + x;
        r[i] += er * weight; g[i] += eg * weight; b[i] += eb * weight;
    }

    // ---- NativeImage pixel access (version-gated packed-int layout, not just a method rename) -------------

    // Reading and writing use DIFFERENT method pairs on >=26, not just a renamed pair of the same one -
    // confirmed against the real decompiled NativeImage.java (26.1.2.100): getPixelABGR is private there
    // (compile-checked live, not guessed - a first attempt at calling it here failed with "has private access
    // in NativeImage"), so reading has to go through the public getPixel(x,y) instead, which returns plain
    // ARGB (R bits 16-23, B bits 0-7, via ARGB.fromABGR's own R/B swap) rather than <26's getPixelRGBA's ABGR
    // (R bits 0-7, B bits 16-23). Writing has no such gap - setPixelABGR IS public on >=26, and packs exactly
    // like <26's setPixelRGBA (R bits 0-7, B bits 16-23, both confirmed via FastColor.ABGR32/NativeImage's own
    // memPutInt call), so the packed int this class builds for writing needs no version-specific shape at all.
    private static int readPixel(NativeImage image, int x, int y) {
        //? if <26 {
        int p = image.getPixelRGBA(x, y);
        int r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
        //? } else {
        /*int p = image.getPixel(x, y);
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        *///?}
        return (r << 16) | (g << 8) | b;
    }

    private static void writePixel(NativeImage image, int x, int y, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int packed = (0xFF << 24) | (b << 16) | (g << 8) | r;
        //? if <26 {
        image.setPixelRGBA(x, y, packed);
        //? } else {
        /*image.setPixelABGR(x, y, packed);
        *///?}
    }
}
