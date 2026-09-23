package fr.lordfinn.crazyphone.client.picture;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the pixel-level geometry (rotate/crop/resize) against known coordinates and the color math
 * against known inputs - the photo editor has no other safety net (there's no server-side validation of
 * "does this look right", it's purely client-side pixel manipulation), so getting the ARGB packing and the
 * rotation direction right here is the only thing standing between a slider and a corrupted photo. */
class PhotoImageOpsTest {

    private static NativeImage image(int width, int height) {
        return new NativeImage(width, height, false);
    }

    //? if <26 {
    private static void setPixel(NativeImage image, int x, int y, int a, int r, int g, int b) {
        image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private static int[] getPixelARGB(NativeImage image, int x, int y) {
        int p = image.getPixelRGBA(x, y);
        return new int[]{(p >> 24) & 0xFF, p & 0xFF, (p >> 8) & 0xFF, (p >> 16) & 0xFF};
    }
    //? } else {
    /*private static void setPixel(NativeImage image, int x, int y, int a, int r, int g, int b) {
        image.setPixelABGR(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private static int[] getPixelARGB(NativeImage image, int x, int y) {
        int p = image.getPixel(x, y);
        return new int[]{(p >>> 24) & 0xFF, (p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF};
    }
    *///?}

    @Test
    void crop_extractsExactSubRectangle() {
        try (NativeImage source = image(4, 4)) {
            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++)
                    setPixel(source, x, y, 255, x * 10, y * 10, 0);
            try (NativeImage cropped = PhotoImageOps.crop(source, 1, 1, 3, 3)) {
                assertEquals(2, cropped.getWidth());
                assertEquals(2, cropped.getHeight());
                assertArrayEqualsARGB(new int[]{255, 10, 10, 0}, getPixelARGB(cropped, 0, 0));
                assertArrayEqualsARGB(new int[]{255, 20, 20, 0}, getPixelARGB(cropped, 1, 1));
            }
        }
    }

    @Test
    void crop_clampsOutOfBoundsRectangle() {
        try (NativeImage source = image(4, 4)) {
            try (NativeImage cropped = PhotoImageOps.crop(source, -5, -5, 100, 100)) {
                assertEquals(4, cropped.getWidth());
                assertEquals(4, cropped.getHeight());
            }
        }
    }

    @Test
    void resize_producesExactTargetDimensions() {
        try (NativeImage source = image(10, 10)) {
            try (NativeImage resized = PhotoImageOps.resize(source, 3, 7)) {
                assertEquals(3, resized.getWidth());
                assertEquals(7, resized.getHeight());
            }
        }
    }

    @Test
    void adjustColor_zeroHueContrastBrightness_isANoOp() {
        try (NativeImage source = image(2, 2)) {
            setPixel(source, 0, 0, 255, 123, 45, 200);
            try (NativeImage adjusted = PhotoImageOps.adjustColor(source, 0f, 0f, 0f)) {
                assertArrayEqualsARGB(new int[]{255, 123, 45, 200}, getPixelARGB(adjusted, 0, 0));
            }
        }
    }

    @Test
    void adjustColor_brightnessAddsToEveryChannelAndClamps() {
        try (NativeImage source = image(1, 1)) {
            setPixel(source, 0, 0, 200, 250, 10, 0);
            try (NativeImage adjusted = PhotoImageOps.adjustColor(source, 0f, 0f, 20f)) {
                int[] pixel = getPixelARGB(adjusted, 0, 0);
                assertEquals(200, pixel[0], "alpha must pass through untouched");
                assertEquals(255, pixel[1], "250+20 must clamp to 255");
                assertEquals(30, pixel[2]);
                assertEquals(20, pixel[3]);
            }
        }
    }

    @Test
    void adjustColor_contrastMinusOneHundred_flattensToMidGray() {
        try (NativeImage source = image(1, 1)) {
            setPixel(source, 0, 0, 255, 255, 0, 128);
            try (NativeImage adjusted = PhotoImageOps.adjustColor(source, 0f, -100f, 0f)) {
                int[] pixel = getPixelARGB(adjusted, 0, 0);
                assertEquals(128, pixel[1]);
                assertEquals(128, pixel[2]);
                assertEquals(128, pixel[3]);
            }
        }
    }

    @Test
    void adjustColor_hueShift_rotatesRedTowardGreen() {
        // A pure, fully-saturated red shifted +120 degrees of hue lands on pure green (standard HSB wheel).
        try (NativeImage source = image(1, 1)) {
            setPixel(source, 0, 0, 255, 255, 0, 0);
            try (NativeImage adjusted = PhotoImageOps.adjustColor(source, 120f, 0f, 0f)) {
                int[] pixel = getPixelARGB(adjusted, 0, 0);
                assertTrue(pixel[2] > 200, "green channel should now dominate, was " + pixel[2]);
                assertTrue(pixel[1] < 50, "red channel should have faded, was " + pixel[1]);
            }
        }
    }

    @Test
    void apply_defaultState_returnsAPixelIdenticalCopy() {
        try (NativeImage source = image(3, 3)) {
            setPixel(source, 1, 1, 255, 44, 55, 66);
            try (NativeImage result = PhotoImageOps.apply(source, PhotoEditState.DEFAULT)) {
                assertEquals(3, result.getWidth());
                assertEquals(3, result.getHeight());
                assertArrayEqualsARGB(new int[]{255, 44, 55, 66}, getPixelARGB(result, 1, 1));
            }
        }
    }

    @Test
    void apply_cropThenResize_producesRequestedFinalSize() {
        try (NativeImage source = image(10, 10)) {
            PhotoEditState state = PhotoEditState.DEFAULT.withCrop(0.2f, 0.2f, 0.8f, 0.8f).withResize(4, 4);
            try (NativeImage result = PhotoImageOps.apply(source, state)) {
                assertEquals(4, result.getWidth());
                assertEquals(4, result.getHeight());
            }
        }
    }

    @Test
    void apply_neverMutatesTheOriginal() {
        try (NativeImage source = image(2, 2)) {
            setPixel(source, 0, 0, 255, 1, 2, 3);
            try (NativeImage ignored = PhotoImageOps.apply(source, PhotoEditState.DEFAULT.withBrightness(50f).withRotationDegrees(37f))) {
                assertArrayEqualsARGB(new int[]{255, 1, 2, 3}, getPixelARGB(source, 0, 0));
            }
        }
    }

    @Test
    void rotateArbitrary_zeroDegrees_isAPlainCopySameSize() {
        try (NativeImage source = image(3, 2)) {
            setPixel(source, 0, 0, 255, 10, 20, 30);
            setPixel(source, 2, 1, 255, 40, 50, 60);
            try (NativeImage rotated = PhotoImageOps.rotateArbitrary(source, 0f)) {
                assertEquals(3, rotated.getWidth());
                assertEquals(2, rotated.getHeight());
                assertArrayEqualsARGB(new int[]{255, 10, 20, 30}, getPixelARGB(rotated, 0, 0));
                assertArrayEqualsARGB(new int[]{255, 40, 50, 60}, getPixelARGB(rotated, 2, 1));
            }
        }
    }

    @Test
    void rotateArbitrary_keepsCanvasSizeAndExposesTransparentCorners() {
        // A fully opaque square rotated by 45 degrees must still report the SAME canvas size (unlike the
        // old 90-degree-stepped rotate90, which swapped width/height) - and its own corners, no longer
        // covered by the rotated content, must come out fully transparent (alpha 0), not any fill color.
        try (NativeImage source = image(20, 20)) {
            for (int y = 0; y < 20; y++)
                for (int x = 0; x < 20; x++)
                    setPixel(source, x, y, 255, 255, 255, 255);
            try (NativeImage rotated = PhotoImageOps.rotateArbitrary(source, 45f)) {
                assertEquals(20, rotated.getWidth());
                assertEquals(20, rotated.getHeight());
                assertEquals(0, getPixelARGB(rotated, 0, 0)[0], "corner should be fully transparent after a 45-degree rotation");
                assertTrue(getPixelARGB(rotated, 10, 10)[0] > 200, "center should still be opaque");
            }
        }
    }

    @Test
    void applyZoom_zoomsInAroundCenterKeepingCanvasSize() {
        try (NativeImage source = image(10, 10)) {
            for (int y = 0; y < 10; y++)
                for (int x = 0; x < 10; x++)
                    setPixel(source, x, y, 255, x * 20, y * 20, 0);
            try (NativeImage zoomed = PhotoImageOps.applyZoom(source, 2f)) {
                assertEquals(10, zoomed.getWidth());
                assertEquals(10, zoomed.getHeight());
                // Zoomed 2x crops the middle half [2,7)x[2,7) of the source (red 40..120 there) then
                // stretches it back up to the full 10x10 canvas - the zoomed image's own LEFT/RIGHT edges
                // should read well into that cropped range, not anywhere near the unzoomed source's own
                // edge values (0 and 180), confirming the crop-then-resize actually zoomed IN rather than
                // being a no-op (the previous version of this test only asserted alpha==255, true for
                // every single pixel in the fixture regardless of whether zoom did anything at all).
                int[] left = getPixelARGB(zoomed, 0, 5);
                assertTrue(left[1] > 20 && left[1] < 70, "left edge should read well into the cropped range after zooming in, was " + left[1]);
                int[] right = getPixelARGB(zoomed, 9, 5);
                assertTrue(right[1] > 90 && right[1] < 150, "right edge should read well short of the source's own uncropped max after zooming in, was " + right[1]);
            }
        }
    }

    @Test
    void applyPan_slidesContentAndExposesTransparentEdge() {
        try (NativeImage source = image(4, 4)) {
            for (int y = 0; y < 4; y++)
                for (int x = 0; x < 4; x++)
                    setPixel(source, x, y, 255, x * 10, y * 10, 0);
            // Panning by +0.25 in x (1 pixel of a 4-wide canvas) slides content 1px to the right - the
            // pixel that was at source (0,y) should now read at target (1,y), and the newly-exposed LEFT
            // column must be fully transparent, not a duplicate of the old edge.
            try (NativeImage panned = PhotoImageOps.applyPan(source, 0.25f, 0f)) {
                assertEquals(4, panned.getWidth());
                assertEquals(4, panned.getHeight());
                assertArrayEqualsARGB(new int[]{255, 0, 20, 0}, getPixelARGB(panned, 1, 2));
                assertEquals(0, getPixelARGB(panned, 0, 2)[0], "exposed left edge should be fully transparent");
            }
        }
    }

    private static void assertArrayEqualsARGB(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "alpha");
        assertEquals(expected[1], actual[1], "red");
        assertEquals(expected[2], actual[2], "green");
        assertEquals(expected[3], actual[3], "blue");
    }
}
