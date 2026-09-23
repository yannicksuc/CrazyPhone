package fr.lordfinn.crazyphone.client.picture;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real javax.imageio GIF reader/writer round trip against AnimatedPhotoCodec's own decode
 * (disposal-method compositing) and its "CPAG" container encode/decode - not just asserting against
 * hand-written expectations, since the exact metadata attribute names/values the codec relies on
 * ("disposalMethod", "restoreToBackgroundColor", ...) are undocumented JDK plugin internals worth actually
 * checking against a real encoder/decoder pair rather than assuming. */
class AnimatedPhotoCodecTest {

    private static byte[] gif(int width, int height, int frames, String disposalMethod, boolean transparentBackground) throws IOException {
        return gif(width, height, frames, disposalMethod, transparentBackground, "7");
    }

    private static byte[] gif(int width, int height, int frames, String disposalMethod, boolean transparentBackground, String delayCentis) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames; i++) {
                BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = frame.createGraphics();
                if (!transparentBackground) {
                    g.setColor(new Color(20 + i * 60 % 200, 120, 200));
                    g.fillRect(0, 0, width, height);
                }
                g.setColor(i % 2 == 0 ? Color.RED : Color.GREEN);
                g.fillOval(width / 4, height / 4, width / 2, height / 2);
                g.dispose();
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", disposalMethod);
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", transparentBackground ? "TRUE" : "FALSE");
                control.setAttribute("delayTime", delayCentis);
                control.setAttribute("transparentColorIndex", "0");
                root.appendChild(control);
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        }
        return out.toByteArray();
    }

    @Test
    void isMultiFrameGif_trueForRealAnimation_falseForSingleFrame() throws IOException {
        assertTrue(AnimatedPhotoCodec.isMultiFrameGif(gif(20, 20, 3, "none", false)));
        assertFalse(AnimatedPhotoCodec.isMultiFrameGif(gif(20, 20, 1, "none", false)));
        assertFalse(AnimatedPhotoCodec.isMultiFrameGif(new byte[]{1, 2, 3}));
        assertFalse(AnimatedPhotoCodec.isMultiFrameGif(null));
    }

    @Test
    void decodeSourceGif_readsEveryFrameAtDeclaredDelay_none() throws IOException {
        byte[] source = gif(30, 20, 5, "none", false);
        try (AnimatedPhotoCodec.Animation animation = AnimatedPhotoCodec.decodeSourceGif(source)) {
            assertEquals(5, animation.frames().size());
            assertEquals(30, animation.width());
            assertEquals(20, animation.height());
            for (AnimatedPhotoCodec.RawFrame frame : animation.frames()) {
                assertEquals(30, frame.image().getWidth());
                assertEquals(20, frame.image().getHeight());
                // delayTime=7 (centiseconds) -> 70ms, well above the codec's own MIN_FRAME_DELAY_MILLIS floor.
                assertEquals(70, frame.delayMillis());
            }
        }
    }

    @Test
    void decodeSourceGif_zeroDelay_fallsBackToOneHundredMillis() throws IOException {
        // delayTime "0" - real-world GIFs frequently leave it unset like this, and every mainstream browser
        // treats it as 100ms rather than the literal near-zero value (see the codec's own doc comment).
        byte[] source = gif(20, 20, 3, "none", false, "0");
        try (AnimatedPhotoCodec.Animation animation = AnimatedPhotoCodec.decodeSourceGif(source)) {
            for (AnimatedPhotoCodec.RawFrame frame : animation.frames())
                assertEquals(100, frame.delayMillis());
        }
    }

    @Test
    void decodeSourceGif_realDelay_isNotFlooredOrAltered() throws IOException {
        // A genuinely-set 7-centisecond (70ms) delay must come back as exactly 70ms, not clamped up to the
        // zero-delay fallback or down to some arbitrary floor.
        byte[] source = gif(20, 20, 3, "none", false, "7");
        try (AnimatedPhotoCodec.Animation animation = AnimatedPhotoCodec.decodeSourceGif(source)) {
            for (AnimatedPhotoCodec.RawFrame frame : animation.frames())
                assertEquals(70, frame.delayMillis());
        }
    }

    @Test
    void decodeSourceGif_restoreToBackground_disposalDoesNotCrash() throws IOException {
        byte[] source = gif(24, 24, 4, "restoreToBackgroundColor", true);
        try (AnimatedPhotoCodec.Animation animation = AnimatedPhotoCodec.decodeSourceGif(source)) {
            assertEquals(4, animation.frames().size());
        }
    }

    @Test
    void decodeSourceGif_restoreToPrevious_disposalDoesNotCrash() throws IOException {
        byte[] source = gif(24, 24, 4, "restoreToPrevious", false);
        try (AnimatedPhotoCodec.Animation animation = AnimatedPhotoCodec.decodeSourceGif(source)) {
            assertEquals(4, animation.frames().size());
        }
    }

    @Test
    void containerRoundTrip_preservesFrameCountDimensionsAndDelays() throws IOException {
        try (AnimatedPhotoCodec.Animation source = AnimatedPhotoCodec.decodeSourceGif(gif(16, 12, 3, "none", false))) {
            byte[] container = AnimatedPhotoCodec.encodeWithinBudget(source, 512, 30, 5_000_000);
            assertTrue(AnimatedPhotoCodec.isAnimatedContainer(container));
            try (AnimatedPhotoCodec.Animation decoded = AnimatedPhotoCodec.decodeContainer(container)) {
                assertEquals(source.frames().size(), decoded.frames().size());
                assertEquals(16, decoded.width());
                assertEquals(12, decoded.height());
                for (int i = 0; i < decoded.frames().size(); i++)
                    assertEquals(source.frames().get(i).delayMillis(), decoded.frames().get(i).delayMillis());
            }
        }
    }

    @Test
    void encodeWithinBudget_shrinksUntilItFitsTheGivenCeiling() throws IOException {
        try (AnimatedPhotoCodec.Animation source = AnimatedPhotoCodec.decodeSourceGif(gif(200, 200, 24, "none", false))) {
            byte[] tiny = AnimatedPhotoCodec.encodeWithinBudget(source, 200, 24, 20_000);
            // Either it fits under a tiny ceiling (heavily shrunk) or - if even the floor size doesn't fit -
            // null, but never silently returns something OVER budget.
            if (tiny != null)
                assertTrue(tiny.length <= 20_000, "encoded " + tiny.length + " bytes, over the 20000 budget");
        }
    }

    @Test
    void decodeContainer_rejectsPlainPng() {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            AnimatedPhotoCodec.decodeContainer(png.toByteArray());
            throw new AssertionError("decoded a plain PNG as an animated container");
        } catch (IOException expected) {
            // correct - a plain PNG has no CPAG magic
        }
    }
}
