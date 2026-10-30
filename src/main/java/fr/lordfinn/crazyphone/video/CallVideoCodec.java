package fr.lordfinn.crazyphone.video;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Wire format of one call video frame, shared by the sender (encoder), the server (header check only, it never
 * inflates anything) and the receivers (decoder). Plain Java on purpose: no Minecraft class, so the whole format
 * is unit-testable and the server side stays trivially cheap.
 *
 * The look is the same pixel art as photos (see PixelArtDownscaler): a small palette, but with an ORDERED
 * (Bayer) dither instead of Floyd-Steinberg, because error diffusion makes every pixel shimmer from one frame to
 * the next in a moving picture, while an ordered dither stays put when the scene does. That stability is also
 * what makes the delta frames tiny.
 *
 * Layout:
 * <pre>
 * [0]    version (1)
 * [1]    flags: bit0 keyframe, bit1 front camera
 * [2]    width, [3] height (pixels, 1..{@link #MAX_SIDE})
 * [4..5] sequence number (u16), [6..7] base sequence of a delta frame (u16, unused on a keyframe)
 * keyframe only: [8] palette size n (1..{@link #MAX_PALETTE}), then n * 3 bytes RGB
 * rest:  deflated width*height palette indices; a delta frame stores (index XOR previous index)
 * </pre>
 * A delta frame only applies on top of the frame whose sequence number it names: a receiver that missed one
 * keeps showing its last good picture until the next keyframe (the sender sends one at least every
 * {@link Encoder#KEYFRAME_INTERVAL} frames, and on request).
 */
public final class CallVideoCodec {
    public static final int VERSION = 1;
    public static final int FLAG_KEYFRAME = 1;
    public static final int FLAG_FRONT_CAMERA = 2;
    public static final int MAX_SIDE = 128;
    public static final int MAX_PALETTE = 32;
    public static final int HEADER_BYTES = 8;
    /** Hard ceiling on one encoded frame, whatever the configured resolution: the worst case of a 128x128 frame
     * that deflate cannot shrink at all, plus header and palette. */
    public static final int MAX_FRAME_BYTES = HEADER_BYTES + 1 + MAX_PALETTE * 3 + MAX_SIDE * MAX_SIDE + 1024;

    private CallVideoCodec() {
    }

    /** What the server (and a receiver, before decoding) needs to know about a frame without inflating it. */
    public record Header(int flags, int width, int height, int seq, int baseSeq) {
        public boolean keyframe() {
            return (flags & FLAG_KEYFRAME) != 0;
        }

        public boolean frontCamera() {
            return (flags & FLAG_FRONT_CAMERA) != 0;
        }
    }

    /** Parses and sanity checks the fixed header, or returns null for anything malformed. */
    public static Header readHeader(byte[] data) {
        if (data == null || data.length < HEADER_BYTES + 1 || data.length > MAX_FRAME_BYTES)
            return null;
        if ((data[0] & 0xFF) != VERSION)
            return null;
        int flags = data[1] & 0xFF;
        int w = data[2] & 0xFF;
        int h = data[3] & 0xFF;
        if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE || (flags & ~(FLAG_KEYFRAME | FLAG_FRONT_CAMERA)) != 0)
            return null;
        int seq = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int base = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        if ((flags & FLAG_KEYFRAME) != 0) {
            int n = data[8] & 0xFF;
            if (n < 1 || n > MAX_PALETTE || data.length < HEADER_BYTES + 1 + n * 3 + 1)
                return null;
        }
        return new Header(flags, w, h, seq, base);
    }

    // ---- Encoder ----------------------------------------------------------------------------------------------

    /** One per outgoing stream. Not thread-safe: the client drives it from a single worker thread. */
    public static final class Encoder {
        /** A keyframe at least this often, so a receiver that missed a frame (or just joined) recovers fast. */
        public static final int KEYFRAME_INTERVAL = 12;
        private static final int PALETTE_SIZE = 32;
        /** A frame with no change at all is still sent this often, as a heartbeat for the receivers' timeout. */
        private static final long HEARTBEAT_NANOS = 900_000_000L;

        private final Deflater deflater = new Deflater(6);
        private final byte[] deflateBuffer = new byte[MAX_FRAME_BYTES];
        private int[] palette;
        private byte[] previous;
        private int width = -1;
        private int height = -1;
        private int seq;
        private int sinceKeyframe;
        private double keyframeError = -1;
        private int keyframeBadPixels;
        private boolean forceKeyframe = true;
        private boolean lastFront;
        private long lastSentNanos;

        public void requestKeyframe() {
            forceKeyframe = true;
        }

        /** Encodes one frame of plain 0xRRGGBB pixels, or returns null when there is nothing worth sending (an
         * unchanged picture shortly after the last one sent). */
        public byte[] encode(int[] rgb, int w, int h, boolean frontCamera, long nowNanos) {
            if (w < 1 || h < 1 || w > MAX_SIDE || h > MAX_SIDE || rgb.length < w * h)
                throw new IllegalArgumentException("bad frame size " + w + "x" + h);
            boolean key = forceKeyframe || palette == null || w != width || h != height || frontCamera != lastFront
                    || sinceKeyframe >= KEYFRAME_INTERVAL - 1;
            if (key)
                palette = kMeans(rgb, w * h, Math.min(PALETTE_SIZE, w * h), palette);
            byte[] indices = new byte[w * h];
            double error = quantize(rgb, w, h, palette, indices);
            // The picture drifted away from the colors the palette was built for (turned around, walked into a
            // cave, a flower came into view): rebuild it now instead of waiting for the next scheduled keyframe.
            // Judged on how many more pixels are badly matched than on the keyframe, not just the mean, so a small
            // but bright new object (which the mean barely notices) still gets its color, while a very colorful
            // scene the palette could never match well does not turn every frame into a keyframe.
            if (!key && keyframeError >= 0 && (error > keyframeError * 2.0 + 60.0 || BAD_PIXELS.get()[0] > keyframeBadPixels + Math.max(6, w * h / 400))) {
                key = true;
                palette = kMeans(rgb, w * h, Math.min(PALETTE_SIZE, w * h), palette);
                error = quantize(rgb, w, h, palette, indices);
            }

            byte[] body;
            if (key) {
                body = indices;
            } else {
                body = new byte[indices.length];
                boolean changed = false;
                for (int i = 0; i < indices.length; i++) {
                    body[i] = (byte) (indices[i] ^ previous[i]);
                    changed |= body[i] != 0;
                }
                if (!changed && nowNanos - lastSentNanos < HEARTBEAT_NANOS)
                    return null;
            }

            deflater.reset();
            deflater.setInput(body);
            deflater.finish();
            int deflated = 0;
            while (!deflater.finished() && deflated < deflateBuffer.length)
                deflated += deflater.deflate(deflateBuffer, deflated, deflateBuffer.length - deflated);
            if (!deflater.finished())
                throw new IllegalStateException("frame does not fit");

            int base = seq;
            seq = (seq + 1) & 0xFFFF;
            int paletteBytes = key ? 1 + palette.length * 3 : 0;
            byte[] out = new byte[HEADER_BYTES + paletteBytes + deflated];
            out[0] = (byte) VERSION;
            out[1] = (byte) ((key ? FLAG_KEYFRAME : 0) | (frontCamera ? FLAG_FRONT_CAMERA : 0));
            out[2] = (byte) w;
            out[3] = (byte) h;
            out[4] = (byte) (seq >> 8);
            out[5] = (byte) seq;
            out[6] = (byte) (base >> 8);
            out[7] = (byte) base;
            int p = HEADER_BYTES;
            if (key) {
                out[p++] = (byte) palette.length;
                for (int c : palette) {
                    out[p++] = (byte) (c >> 16);
                    out[p++] = (byte) (c >> 8);
                    out[p++] = (byte) c;
                }
            }
            System.arraycopy(deflateBuffer, 0, out, p, deflated);

            previous = indices;
            width = w;
            height = h;
            lastFront = frontCamera;
            lastSentNanos = nowNanos;
            if (key) {
                forceKeyframe = false;
                sinceKeyframe = 0;
                keyframeError = error;
                keyframeBadPixels = BAD_PIXELS.get()[0];
            } else {
                sinceKeyframe++;
            }
            return out;
        }

        public void close() {
            deflater.end();
        }
    }

    private static final int KMEANS_ITERATIONS = 4;

    // 4x4 Bayer matrix, centered on zero: the ordered dither offsets (see this class's own doc comment).
    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
    private static final float DITHER_STRENGTH = 1.4f;

    /** Ordered-dithered nearest-palette match into {@code out}, returning the mean squared (luma-weighted) error,
     * which the encoder uses to notice a palette that no longer fits the picture. */
    static double quantize(int[] rgb, int w, int h, int[] palette, byte[] out) {
        int n = palette.length;
        int[] pr = new int[n], pg = new int[n], pb = new int[n];
        for (int c = 0; c < n; c++) {
            pr[c] = (palette[c] >> 16) & 0xFF;
            pg[c] = (palette[c] >> 8) & 0xFF;
            pb[c] = palette[c] & 0xFF;
        }
        double total = 0;
        int bad = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int v = rgb[i];
                float d = (BAYER[(y & 3) * 4 + (x & 3)] - 7.5f) * DITHER_STRENGTH;
                float r = ((v >> 16) & 0xFF) + d, g = ((v >> 8) & 0xFF) + d, b = (v & 0xFF) + d;
                int best = 0;
                float bestD = Float.MAX_VALUE;
                for (int c = 0; c < n; c++) {
                    float dr = r - pr[c], dg = g - pg[c], db = b - pb[c];
                    float dist = 0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db;
                    if (dist < bestD) {
                        bestD = dist;
                        best = c;
                    }
                }
                out[i] = (byte) best;
                total += bestD;
                if (bestD > BAD_MATCH)
                    bad++;
            }
        }
        BAD_PIXELS.get()[0] = bad;
        return total / (w * h);
    }

    /** A pixel whose closest palette color is this far off (luma-weighted squared distance, about 22 levels on
     * every channel) is "badly matched" - see the encoder's palette drift check. */
    private static final float BAD_MATCH = 500f;
    /** Badly matched pixel count of the last {@link #quantize} call on this thread (the encoder runs on one
     * worker thread; a thread local keeps the unit tests and any second encoder honest anyway). */
    private static final ThreadLocal<int[]> BAD_PIXELS = ThreadLocal.withInitial(() -> new int[1]);

    /** Small k-means palette (same luma-weighted distance as PixelArtDownscaler), seeded from the previous
     * palette when there is one: consecutive keyframes then pick nearly the same colors, so the picture does not
     * flash every time the palette is rebuilt. */
    static int[] kMeans(int[] rgb, int count, int k, int[] seed) {
        float[][] centroids = new float[k][3];
        for (int c = 0; c < k; c++) {
            int p = seed != null && c < seed.length ? seed[c] : rgb[(int) ((long) c * (count - 1) / Math.max(1, k - 1))];
            centroids[c][0] = (p >> 16) & 0xFF;
            centroids[c][1] = (p >> 8) & 0xFF;
            centroids[c][2] = p & 0xFF;
        }
        // Sampling every other pixel is plenty for the palette and halves the cost.
        int step = count > 2048 ? 2 : 1;
        float[][] sum = new float[k][3];
        int[] cnt = new int[k];
        for (int it = 0; it < KMEANS_ITERATIONS; it++) {
            for (float[] s : sum)
                java.util.Arrays.fill(s, 0);
            java.util.Arrays.fill(cnt, 0);
            for (int i = 0; i < count; i += step) {
                int v = rgb[i];
                float r = (v >> 16) & 0xFF, g = (v >> 8) & 0xFF, b = v & 0xFF;
                int best = 0;
                float bestD = Float.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    float dr = r - centroids[c][0], dg = g - centroids[c][1], db = b - centroids[c][2];
                    float dist = 0.30f * dr * dr + 0.59f * dg * dg + 0.11f * db * db;
                    if (dist < bestD) {
                        bestD = dist;
                        best = c;
                    }
                }
                sum[best][0] += r;
                sum[best][1] += g;
                sum[best][2] += b;
                cnt[best]++;
            }
            for (int c = 0; c < k; c++) {
                if (cnt[c] > 0) {
                    centroids[c][0] = sum[c][0] / cnt[c];
                    centroids[c][1] = sum[c][1] / cnt[c];
                    centroids[c][2] = sum[c][2] / cnt[c];
                } else {
                    // An empty cluster is re-seeded on a pixel spread across the frame, so it can still be useful.
                    int v = rgb[(int) ((long) (c * 7919 + it * 104729) % count)];
                    centroids[c][0] = (v >> 16) & 0xFF;
                    centroids[c][1] = (v >> 8) & 0xFF;
                    centroids[c][2] = v & 0xFF;
                }
            }
        }
        int[] out = new int[k];
        for (int c = 0; c < k; c++)
            out[c] = (clamp(centroids[c][0]) << 16) | (clamp(centroids[c][1]) << 8) | clamp(centroids[c][2]);
        return out;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    // ---- Decoder ----------------------------------------------------------------------------------------------

    /** One per incoming stream (per sender). Not thread-safe: driven from a single worker thread. */
    public static final class Decoder {
        private final Inflater inflater = new Inflater();
        private int[] palette;
        private byte[] indices;
        private int width = -1;
        private int height = -1;
        private int lastSeq = -1;

        /** Decoded picture: 0xRRGGBB per pixel. */
        public record Frame(int width, int height, boolean frontCamera, int[] rgb) {
        }

        /** Decodes a frame, or returns null if it is malformed or a delta whose base frame this decoder never got
         * (the caller just keeps showing the last good picture). Never throws on bad input. */
        public Frame decode(byte[] data) {
            Header header = readHeader(data);
            if (header == null)
                return null;
            int w = header.width(), h = header.height();
            int p = HEADER_BYTES;
            int[] framePalette = palette;
            if (header.keyframe()) {
                int n = data[p++] & 0xFF;
                framePalette = new int[n];
                for (int c = 0; c < n; c++, p += 3)
                    framePalette[c] = ((data[p] & 0xFF) << 16) | ((data[p + 1] & 0xFF) << 8) | (data[p + 2] & 0xFF);
            } else if (palette == null || indices == null || w != width || h != height || header.baseSeq() != lastSeq) {
                return null;
            }

            byte[] body = new byte[w * h];
            try {
                inflater.reset();
                inflater.setInput(data, p, data.length - p);
                int got = 0;
                while (got < body.length && !inflater.finished()) {
                    int r = inflater.inflate(body, got, body.length - got);
                    if (r == 0 && (inflater.needsInput() || inflater.needsDictionary()))
                        break;
                    got += r;
                }
                // Exactly w*h bytes, no more: anything else is a broken or forged frame.
                if (got != body.length || !inflater.finished())
                    return null;
            } catch (DataFormatException e) {
                return null;
            }

            byte[] frameIndices;
            if (header.keyframe()) {
                frameIndices = body;
            } else {
                frameIndices = new byte[body.length];
                for (int i = 0; i < body.length; i++)
                    frameIndices[i] = (byte) (body[i] ^ indices[i]);
            }
            int n = framePalette.length;
            int[] rgb = new int[w * h];
            for (int i = 0; i < rgb.length; i++) {
                int idx = frameIndices[i] & 0xFF;
                if (idx >= n)
                    return null;
                rgb[i] = framePalette[idx];
            }
            palette = framePalette;
            indices = frameIndices;
            width = w;
            height = h;
            lastSeq = header.seq();
            return new Frame(w, h, header.frontCamera(), rgb);
        }

        public void close() {
            inflater.end();
        }
    }

    /** Test/diagnostic helper: deflates a byte array (used by the unit tests to forge frames). */
    static byte[] deflate(byte[] body) {
        Deflater d = new Deflater();
        d.setInput(body);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished())
            out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }
}
