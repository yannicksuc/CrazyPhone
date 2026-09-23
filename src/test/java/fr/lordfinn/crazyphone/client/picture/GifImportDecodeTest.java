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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The photo importer feeds any picked file or downloaded link straight into NativeImage.read - this checks
 * that path actually decodes GIFs (animated ones included) instead of assuming it does. */
class GifImportDecodeTest {

    private static byte[] animatedGif(int width, int height, int frames, boolean transparentBackground) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames; i++) {
                BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = frame.createGraphics();
                if (!transparentBackground) {
                    g.setColor(new Color(20 + i * 60, 120, 200));
                    g.fillRect(0, 0, width, height);
                }
                g.setColor(Color.RED);
                g.fillOval(width / 4, height / 4, width / 2, height / 2);
                g.dispose();
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), param);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                control.setAttribute("disposalMethod", "restoreToBackgroundColor");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", transparentBackground ? "TRUE" : "FALSE");
                control.setAttribute("delayTime", "10");
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
    void animatedGif_decodesToItsFirstFrameAtFullSize() throws IOException {
        byte[] gif = animatedGif(64, 48, 4, false);
        try (NativeImage image = PhotoImporter.decode(gif)) {
            assertEquals(64, image.getWidth());
            assertEquals(48, image.getHeight());
        }
    }

    @Test
    void transparentGif_decodes() throws IOException {
        byte[] gif = animatedGif(64, 48, 2, true);
        try (NativeImage image = PhotoImporter.decode(gif)) {
            assertEquals(64, image.getWidth());
            assertEquals(48, image.getHeight());
            // The transparent corner must come back with alpha < 255 so the frame renderer treats it as such.
            int corner = image.getPixelRGBA(0, 0) >>> 24 & 0xFF;
            assertTrue(corner < 255, "corner alpha was " + corner);
        }
    }

    @Test
    void jpeg_decodes() throws IOException {
        BufferedImage source = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(source, "jpg", jpeg);
        try (NativeImage image = PhotoImporter.decode(jpeg.toByteArray())) {
            assertEquals(40, image.getWidth());
            assertEquals(30, image.getHeight());
        }
    }

    @Test
    void png_decodesDirectly() throws IOException {
        BufferedImage source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(source, "png", png);
        try (NativeImage image = PhotoImporter.decode(png.toByteArray())) {
            assertEquals(20, image.getWidth());
            assertEquals(10, image.getHeight());
        }
    }

    @Test
    void notAnImage_throwsInsteadOfCrashing() {
        try (NativeImage ignored = PhotoImporter.decode("<html>not an image</html>".getBytes())) {
            throw new AssertionError("decoded a non-image");
        } catch (IOException | RuntimeException expected) {
            // importStream catches exactly these and reports the import as failed
        }
    }
}
