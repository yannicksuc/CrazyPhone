package fr.lordfinn.crazyphone.video;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The call video wire format must survive anything a broken or modified client can send. */
class CallVideoCodecTest {
    private static final int W = 64;
    private static final int H = 64;

    private static int[] gradient(int shift) {
        int[] rgb = new int[W * H];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                rgb[y * W + x] = (((x + shift) * 4 & 0xFF) << 16) | ((y * 4) << 8) | 0x40;
        return rgb;
    }

    private static byte[] deflate(byte[] body) {
        Deflater deflater = new Deflater();
        deflater.setInput(body);
        deflater.finish();
        byte[] buf = new byte[1 << 16];
        int n = deflater.deflate(buf);
        deflater.end();
        return Arrays.copyOf(buf, n);
    }

    @Test
    void keyframeThenDeltaRoundTrip() {
        CallVideoCodec.Encoder encoder = new CallVideoCodec.Encoder();
        CallVideoCodec.Decoder decoder = new CallVideoCodec.Decoder();
        byte[] key = encoder.encode(gradient(0), W, H, false, 0);
        assertTrue(CallVideoCodec.readHeader(key).keyframe());
        CallVideoCodec.Decoder.Frame first = decoder.decode(key);
        assertNotNull(first);
        assertEquals(W, first.width());

        int[] next = gradient(0);
        next[100] += 0x040404;
        byte[] delta = encoder.encode(next, W, H, true, 2_000_000_000L);
        // The camera changed (front): a fresh keyframe, never a delta on the other camera's palette.
        assertTrue(CallVideoCodec.readHeader(delta).keyframe());
        assertTrue(CallVideoCodec.readHeader(delta).frontCamera());
        assertNotNull(decoder.decode(delta));

        byte[] realDelta = encoder.encode(gradient(1), W, H, true, 3_000_000_000L);
        assertFalse(CallVideoCodec.readHeader(realDelta).keyframe());
        assertNotNull(decoder.decode(realDelta));
        // A receiver that missed the base frame keeps its last picture instead of showing garbage.
        assertNull(new CallVideoCodec.Decoder().decode(realDelta));
    }

    @Test
    void unchangedPictureIsNotResentBeforeTheHeartbeat() {
        CallVideoCodec.Encoder encoder = new CallVideoCodec.Encoder();
        assertNotNull(encoder.encode(gradient(0), W, H, false, 0));
        assertNull(encoder.encode(gradient(0), W, H, false, 100_000_000L));
        assertNotNull(encoder.encode(gradient(0), W, H, false, 2_000_000_000L));
    }

    @Test
    void rejectsMalformedHeaders() {
        byte[] key = new CallVideoCodec.Encoder().encode(gradient(0), W, H, false, 0);
        assertNull(CallVideoCodec.readHeader(null));
        assertNull(CallVideoCodec.readHeader(new byte[0]));
        byte[] version = key.clone();
        version[0] = 9;
        assertNull(CallVideoCodec.readHeader(version));
        byte[] tooWide = key.clone();
        tooWide[2] = (byte) 200;
        assertNull(CallVideoCodec.readHeader(tooWide));
        byte[] palette = key.clone();
        palette[8] = (byte) 200;
        assertNull(CallVideoCodec.readHeader(palette));
        assertNull(CallVideoCodec.readHeader(new byte[CallVideoCodec.MAX_FRAME_BYTES + 1]));
    }

    @Test
    void rejectsDecompressionBombsAndOutOfPaletteIndices() {
        byte[] key = new CallVideoCodec.Encoder().encode(gradient(0), W, H, false, 0);
        byte[] bombBody = deflate(new byte[1 << 20]);
        byte[] bomb = new byte[12 + bombBody.length];
        System.arraycopy(key, 0, bomb, 0, 8);
        bomb[1] = CallVideoCodec.FLAG_KEYFRAME;
        bomb[8] = 1;
        System.arraycopy(bombBody, 0, bomb, 12, bombBody.length);
        assertNull(new CallVideoCodec.Decoder().decode(bomb));

        byte[] indices = new byte[W * H];
        Arrays.fill(indices, (byte) 5);
        byte[] body = deflate(indices);
        byte[] outOfPalette = new byte[12 + body.length];
        System.arraycopy(key, 0, outOfPalette, 0, 8);
        outOfPalette[1] = CallVideoCodec.FLAG_KEYFRAME;
        outOfPalette[8] = 1;
        System.arraycopy(body, 0, outOfPalette, 12, body.length);
        assertNull(new CallVideoCodec.Decoder().decode(outOfPalette));
    }

    @Test
    void neverThrowsOnGarbage() {
        Random random = new Random(42);
        byte[] key = new CallVideoCodec.Encoder().encode(gradient(0), W, H, false, 0);
        CallVideoCodec.Decoder decoder = new CallVideoCodec.Decoder();
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5000; i++) {
                byte[] garbage = new byte[random.nextInt(3000)];
                random.nextBytes(garbage);
                if (garbage.length > 0)
                    garbage[0] = CallVideoCodec.VERSION;
                decoder.decode(garbage);
                byte[] mutated = key.clone();
                for (int j = 0; j < 5; j++)
                    mutated[8 + random.nextInt(mutated.length - 8)] = (byte) random.nextInt();
                decoder.decode(mutated);
            }
        });
    }
}
