package fr.lordfinn.crazyphone.client.picture;

/**
 * Reads a source GIF's real frames (with proper disposal-method compositing - a raw {@code reader.read(i)}
 * only gives you that one frame's own sub-rectangle, not what should actually be on screen at that point)
 * and re-encodes them into this mod's own tiny animated container format ("CPAG") - a plain sequence of
 * PNG frames plus a millisecond delay each, nothing GIF-specific. Storing PNG frames instead of writing a
 * new GIF avoids needing a palette-quantizing GIF encoder entirely (Java's own indexed-color GIF writer
 * produces visibly worse color banding than just keeping each frame true-color) - the trade-off is size,
 * which {@link #encodeWithinBudget} manages by shrinking dimensions and dropping frames instead.
 * <p>
 * Both this class's decode paths (source GIF -> frames, and reading this mod's own container back) run
 * entirely client-side and touch no network-facing type - {@link fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket}
 * already carries a photo's "full" resolution as an opaque {@code byte[]}, so nothing about the wire
 * protocol needed to change for this: a server on ANY 1.7.0+ build stores and relays these bytes exactly
 * like it always has, unaware they're an animation. An older CLIENT that doesn't know this container format
 * simply fails to decode it as PNG (same as any other corrupt/unrecognized image) and falls back to the
 * existing broken-image placeholder - never a crash, never a stuck fetch.
 */
import com.mojang.blaze3d.platform.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class AnimatedPhotoCodec {
    private static final byte[] MAGIC = {'C', 'P', 'A', 'G'};
    private static final int FORMAT_VERSION = 1;
    // A GIF's delayTime is in centiseconds (hundredths of a second) - delayCentisToMillis below converts.
    // A value of 0 or 1 (0-10ms) is legal but means "as fast as the decoder can go", not a real duration -
    // GIF-authoring tools frequently leave it unset entirely (0) rather than genuinely meaning "no delay".
    // Every mainstream browser and viewer treats this exact case as 100ms (10 centiseconds) instead of
    // honoring the literal near-zero value - live-reported as "way too fast"/"doesn't match the original
    // GIF's timing" before this matched that convention (a real GIF with delayTime=0 played 5x too fast:
    // ~20ms/frame instead of the expected ~100ms/frame).
    private static final int ZERO_DELAY_FALLBACK_MILLIS = 100;

    private AnimatedPhotoCodec() {
    }

    public record RawFrame(NativeImage image, int delayMillis) {
    }

    /** A decoded animation ready to play - every frame the SAME canvas size, first frame first. Caller owns
     * (and must close) every frame's image. */
    public record Animation(List<RawFrame> frames, int width, int height) implements AutoCloseable {
        @Override
        public void close() {
            for (RawFrame frame : frames)
                frame.image().close();
        }
    }

    public static boolean isAnimatedContainer(byte[] bytes) {
        if (bytes == null || bytes.length < MAGIC.length)
            return false;
        for (int i = 0; i < MAGIC.length; i++)
            if (bytes[i] != MAGIC[i])
                return false;
        return true;
    }

    /** Whether {@code bytes} looks like a GIF with more than one frame - a single-frame GIF is just a still
     * image and stays on the existing static-PNG path (see PhotoImporter#decode), only a genuine animation
     * is worth the extra container/playback machinery. */
    public static boolean isMultiFrameGif(byte[] bytes) {
        if (bytes == null || bytes.length < 6)
            return false;
        String header = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
        if (!header.equals("GIF87a") && !header.equals("GIF89a"))
            return false;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = gifReader();
            reader.setInput(in);
            return reader.getNumImages(true) > 1;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static ImageReader gifReader() throws IOException {
        var readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext())
            throw new IOException("No GIF reader available");
        return readers.next();
    }

    /** Decodes every real, composited frame of a source GIF - see this class's own doc comment for why a
     * raw per-index read alone isn't enough. Canvas size is taken from the logical screen descriptor (the
     * GIF's own declared width/height), not just frame 0's own dimensions, so a source whose first frame
     * doesn't cover the full canvas still decodes correctly. */
    public static Animation decodeSourceGif(byte[] gifBytes) throws IOException {
        ImageReader reader = gifReader();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(gifBytes))) {
            reader.setInput(in, false);
            int frameCount = reader.getNumImages(true);
            if (frameCount <= 0)
                throw new IOException("Empty GIF");

            int canvasWidth = readLogicalScreenWidth(reader);
            int canvasHeight = readLogicalScreenHeight(reader);
            if (canvasWidth <= 0 || canvasHeight <= 0) {
                canvasWidth = reader.getWidth(0);
                canvasHeight = reader.getHeight(0);
            }

            BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            List<RawFrame> frames = new ArrayList<>(frameCount);
            try {
                for (int i = 0; i < frameCount; i++) {
                    BufferedImage frameImage = reader.read(i);
                    IIOMetadataNode graphicControl = graphicControlNode(reader.getImageMetadata(i));
                    int delayCentis = intAttribute(graphicControl, "delayTime", 10);
                    String disposal = graphicControl == null ? "none" : graphicControl.getAttribute("disposalMethod");
                    int left = frameLeft(reader, i);
                    int top = frameTop(reader, i);

                    BufferedImage restoreTo = "restoreToPrevious".equals(disposal) ? deepCopy(canvas) : null;

                    // Plain alpha-blend (SrcOver, Graphics2D's own default), NOT a raw overwrite - a
                    // transparent pixel in this frame must let whatever is already on the canvas show
                    // through, which is the entire point of "doNotDispose"/"unspecified" (the vast majority
                    // of real animated GIFs): only the frame's own OPAQUE pixels should ever replace canvas
                    // content.
                    Graphics2D g = canvas.createGraphics();
                    g.drawImage(frameImage, left, top, null);
                    g.dispose();

                    frames.add(new RawFrame(toNativeImage(canvas), delayCentisToMillis(delayCentis)));

                    if ("restoreToBackgroundColor".equals(disposal)) {
                        Graphics2D clear = canvas.createGraphics();
                        clear.setComposite(AlphaComposite.Clear);
                        clear.fillRect(left, top, frameImage.getWidth(), frameImage.getHeight());
                        clear.dispose();
                    } else if (restoreTo != null) {
                        canvas = restoreTo;
                    }
                }
            } catch (RuntimeException | IOException e) {
                for (RawFrame frame : frames)
                    frame.image().close();
                throw e instanceof IOException io ? io : new IOException(e);
            }
            return new Animation(frames, canvasWidth, canvasHeight);
        } finally {
            reader.dispose();
        }
    }

    private static int delayCentisToMillis(int delayCentis) {
        return delayCentis <= 1 ? ZERO_DELAY_FALLBACK_MILLIS : delayCentis * 10;
    }

    private static BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static IIOMetadataNode graphicControlNode(IIOMetadata metadata) {
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
        var children = root.getElementsByTagName("GraphicControlExtension");
        return children.getLength() > 0 ? (IIOMetadataNode) children.item(0) : null;
    }

    private static int frameLeft(ImageReader reader, int index) throws IOException {
        IIOMetadataNode descriptor = imageDescriptorNode(reader.getImageMetadata(index));
        return descriptor == null ? 0 : intAttribute(descriptor, "imageLeftPosition", 0);
    }

    private static int frameTop(ImageReader reader, int index) throws IOException {
        IIOMetadataNode descriptor = imageDescriptorNode(reader.getImageMetadata(index));
        return descriptor == null ? 0 : intAttribute(descriptor, "imageTopPosition", 0);
    }

    private static IIOMetadataNode imageDescriptorNode(IIOMetadata metadata) {
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
        var children = root.getElementsByTagName("ImageDescriptor");
        return children.getLength() > 0 ? (IIOMetadataNode) children.item(0) : null;
    }

    private static int readLogicalScreenWidth(ImageReader reader) throws IOException {
        return logicalScreenDescriptorAttribute(reader, "logicalScreenWidth");
    }

    private static int readLogicalScreenHeight(ImageReader reader) throws IOException {
        return logicalScreenDescriptorAttribute(reader, "logicalScreenHeight");
    }

    private static int logicalScreenDescriptorAttribute(ImageReader reader, String attribute) throws IOException {
        IIOMetadata streamMetadata = reader.getStreamMetadata();
        if (streamMetadata == null)
            return -1;
        IIOMetadataNode root = (IIOMetadataNode) streamMetadata.getAsTree(streamMetadata.getNativeMetadataFormatName());
        var children = root.getElementsByTagName("LogicalScreenDescriptor");
        if (children.getLength() == 0)
            return -1;
        return intAttribute((IIOMetadataNode) children.item(0), attribute, -1);
    }

    private static int intAttribute(IIOMetadataNode node, String attribute, int fallback) {
        if (node == null)
            return fallback;
        String value = node.getAttribute(attribute);
        if (value == null || value.isEmpty())
            return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static NativeImage toNativeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png))
            throw new IOException("Could not encode frame as PNG");
        return NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
    }

    // ---- This mod's own container format --------------------------------------------------------------

    /** Re-encodes {@code source} (every frame at its ORIGINAL size) into the "CPAG" container, shrinking the
     * canvas and/or dropping frames until the result fits under {@code maxBytes} - a picked GIF has no
     * relation to this mod's own upload ceiling otherwise, and the trade-off the user asked for (accept
     * lower quality/fewer frames rather than refuse big GIFs outright) happens here. Does not close
     * {@code source} - the caller still owns it (needed afterward to derive the static thumbnail from frame
     * 0). Returns null if even the smallest allowed size can't fit. */
    public static byte[] encodeWithinBudget(Animation source, int maxDimension, int maxFrames, int maxBytes) {
        int dimension = Math.min(maxDimension, Math.max(source.width(), source.height()));
        int frameCap = Math.min(maxFrames, source.frames().size());
        while (true) {
            try (Animation resized = resample(source, dimension, frameCap)) {
                byte[] encoded = encodeContainer(resized);
                if (encoded.length <= maxBytes)
                    return encoded;
            } catch (IOException e) {
                org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Failed to encode animated photo", e);
                return null;
            }
            if (dimension <= MIN_DIMENSION && frameCap <= MIN_FRAMES)
                return null;
            // Frame count is the cheaper axis to cut first (a GIF with many frames at a small size is still
            // heavy per-frame-PNG-header overhead) - only start shrinking dimensions once frame count is
            // already at its floor.
            if (frameCap > MIN_FRAMES)
                frameCap = Math.max(MIN_FRAMES, frameCap * 3 / 4);
            else
                dimension = Math.max(MIN_DIMENSION, dimension * 3 / 4);
        }
    }

    private static final int MIN_DIMENSION = 96;
    private static final int MIN_FRAMES = 4;

    /** Downscales every frame to fit within {@code maxDimension} on its longer side, and subsamples down to
     * at most {@code maxFrames} frames spread evenly across the ORIGINAL timeline (not just the first N) so
     * a shortened animation still plays the whole loop, just choppier - each surviving frame absorbs the
     * delay of the frames it replaces so total playback duration is preserved. */
    private static Animation resample(Animation source, int maxDimension, int maxFrames) throws IOException {
        List<RawFrame> sourceFrames = source.frames();
        int keep = Math.max(1, Math.min(maxFrames, sourceFrames.size()));
        List<RawFrame> resized = new ArrayList<>(keep);
        int outWidth = -1, outHeight = -1;
        for (int i = 0; i < keep; i++) {
            int startIndex = i * sourceFrames.size() / keep;
            int endIndex = (i + 1) * sourceFrames.size() / keep;
            int mergedDelay = 0;
            for (int j = startIndex; j < endIndex; j++)
                mergedDelay += sourceFrames.get(j).delayMillis();
            NativeImage frameSource = sourceFrames.get(startIndex).image();
            NativeImage scaled = downscale(frameSource, maxDimension);
            outWidth = scaled.getWidth();
            outHeight = scaled.getHeight();
            // Individual delays merged here are already real millisecond durations (delayCentisToMillis
            // resolved the "0 means 100ms" convention per source frame before this ever runs), so a plain
            // sum is correct - no floor to reapply.
            resized.add(new RawFrame(scaled, Math.max(1, mergedDelay)));
        }
        return new Animation(resized, outWidth, outHeight);
    }

    private static NativeImage downscale(NativeImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        if (targetWidth == width && targetHeight == height) {
            NativeImage copy = new NativeImage(width, height, false);
            source.resizeSubRectTo(0, 0, width, height, copy);
            return copy;
        }
        NativeImage target = new NativeImage(targetWidth, targetHeight, false);
        source.resizeSubRectTo(0, 0, width, height, target);
        return target;
    }

    private static byte[] encodeContainer(Animation animation) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.write(MAGIC);
        data.writeByte(FORMAT_VERSION);
        data.writeInt(animation.frames().size());
        data.writeInt(animation.width());
        data.writeInt(animation.height());
        for (RawFrame frame : animation.frames()) {
            data.writeInt(frame.delayMillis());
            byte[] framePng = toPngBytes(frame.image());
            data.writeInt(framePng.length);
            data.write(framePng);
        }
        return out.toByteArray();
    }

    /** Reads this mod's own container back into playable frames - the client-side texture cache calls this
     * for any "full" resolution photo whose bytes start with the CPAG magic (see FabricPictureCache). */
    public static Animation decodeContainer(byte[] bytes) throws IOException {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        byte[] magic = new byte[MAGIC.length];
        data.readFully(magic);
        for (int i = 0; i < MAGIC.length; i++)
            if (magic[i] != MAGIC[i])
                throw new IOException("Not a CrazyPhone animated photo container");
        int version = data.readUnsignedByte();
        if (version != FORMAT_VERSION)
            throw new IOException("Unsupported animated photo container version " + version);
        int frameCount = data.readInt();
        if (frameCount <= 0 || frameCount > 10_000)
            throw new IOException("Implausible frame count " + frameCount);
        int width = data.readInt();
        int height = data.readInt();
        List<RawFrame> frames = new ArrayList<>(frameCount);
        try {
            for (int i = 0; i < frameCount; i++) {
                int delayMillis = data.readInt();
                int pngLength = data.readInt();
                if (pngLength < 0 || pngLength > 100_000_000)
                    throw new IOException("Implausible frame size " + pngLength);
                byte[] framePng = new byte[pngLength];
                data.readFully(framePng);
                frames.add(new RawFrame(NativeImage.read(new ByteArrayInputStream(framePng)), delayMillis));
            }
        } catch (IOException | RuntimeException e) {
            for (RawFrame frame : frames)
                frame.image().close();
            throw e instanceof IOException io ? io : new IOException(e);
        }
        return new Animation(frames, width, height);
    }

    //? if <1.21.10 {
    private static byte[] toPngBytes(NativeImage image) throws IOException {
        return image.asByteArray();
    }
    //? } else {
    /*private static byte[] toPngBytes(NativeImage image) throws IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("crazyphone-anim-", ".png");
        try {
            image.writeToFile(tmp);
            return java.nio.file.Files.readAllBytes(tmp);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
    *///?}
}
