package fr.lordfinn.crazyphone.client.picture;

import com.mojang.blaze3d.platform.NativeImage;
import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "Import from PC": lets the player pick image files with the OS file dialog and stores each one in their
 * own photo list (a standalone upload - no conversation), through the exact same upload packet, downscaling
 * and thumbnail pipeline a live capture uses. */
public final class PhotoImporter {
    // NativeImage itself only reads PNG, so everything else goes through Java's ImageIO (see decode).
    private static final String[] FILTERS = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"};

    private PhotoImporter() {
    }

    /** Opens the file dialog (blocks until closed) and uploads every picked image. Returns the ids of the
     * photos actually sent, in order - empty if the dialog was cancelled or nothing could be decoded. */
    public static List<UUID> importFromDisk() {
        List<UUID> imported = new ArrayList<>();
        for (Path path : pickFiles()) {
            UUID id = importOne(path);
            if (id != null)
                imported.add(id);
        }
        return imported;
    }

    private static List<Path> pickFiles() {
        String result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(FILTERS.length);
            for (String filter : FILTERS)
                filters.put(stack.UTF8(filter));
            filters.flip();
            result = TinyFileDialogs.tinyfd_openFileDialog("Import photos", "", filters, "Images", true);
        }
        List<Path> paths = new ArrayList<>();
        if (result != null)
            for (String part : result.split(java.util.regex.Pattern.quote("|")))
                if (!part.isBlank())
                    paths.add(Path.of(part));
        return paths;
    }

    private static UUID importOne(Path path) {
        try {
            if (Files.size(path) > MAX_DOWNLOAD_BYTES)
                return null;
            return importBytes(Files.readAllBytes(path), path.toString());
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Could not import photo {}", path, e);
            return null;
        }
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }

    /** NativeImage#read only accepts PNG (it validates the PNG signature and throws "Bad PNG Signature" for
     * anything else), so JPEG, GIF and BMP are decoded with Java's own ImageIO and re-encoded as PNG first. An
     * animated GIF yields its first frame. Throws IOException for anything that isn't a readable image. */
    static NativeImage decode(byte[] bytes) throws IOException {
        if (isPng(bytes))
            return NativeImage.read(new java.io.ByteArrayInputStream(bytes));
        java.awt.image.BufferedImage buffered = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (buffered == null)
            throw new IOException("Unsupported image format");
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(buffered, "png", png))
            throw new IOException("Could not convert image to PNG");
        return NativeImage.read(new java.io.ByteArrayInputStream(png.toByteArray()));
    }

    /** Decodes an image and uploads it - shared by the file dialog and the link import. Must run on the client
     * thread (it seeds the picture cache and sends the upload packet). Routes a genuinely animated GIF
     * (more than one real frame) through {@link #importAnimated}; every other picked/downloaded format
     * (single-frame GIF included) stays on this, the existing static path. */
    private static UUID importBytes(byte[] bytes, String source) {
        if (AnimatedPhotoCodec.isMultiFrameGif(bytes))
            return importAnimated(bytes, source);
        try (NativeImage image = decode(bytes)) {
            UUID photoId = UUID.randomUUID();
            byte[][] result = new byte[2][];
            // A picked file is not a screenshot: a noisy photo can encode to a PNG several times heavier than
            // a capture of the same size, and an upload over the network payload limit does not get refused
            // politely - the server drops the connection (reported live as a time out). Shrink until it fits.
            int maxDimension = Config.photoFullMaxDimension;
            int uploadLimit = Math.min(MAX_UPLOAD_PAYLOAD_BYTES, Config.photoFullMaxUploadBytes);
            while (true) {
                FabricPictureCapture.deriveBothResolutions(image, maxDimension, (thumbnailPng, fullPng) -> {
                    result[0] = thumbnailPng;
                    result[1] = fullPng;
                });
                if (result[1].length <= uploadLimit || maxDimension <= MIN_DIMENSION)
                    break;
                maxDimension = Math.max(MIN_DIMENSION, maxDimension * 3 / 4);
            }
            if (result[1].length > uploadLimit)
                return null;
            FabricPictureCache.seedFromLocalCapture(photoId, result[0], result[1]);
            NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket("", photoId, result[0], result[1], false));
            return photoId;
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Could not import photo {}", source, e);
            return null;
        }
    }

    // A held/dropped/framed animation needs to stay small enough to decode+upload+re-render every frame
    // without becoming its own lag source - deliberately smaller than a normal photo's own
    // Config.photoFullMaxDimension/thumbnail pipeline. This is exactly the "trade-off in quality" the user
    // explicitly accepted in exchange for GIFs actually playing rather than freezing on frame 1.
    private static final int ANIMATED_MAX_DIMENSION = 480;
    private static final int ANIMATED_MAX_FRAMES = 24;

    /** Re-encodes a genuinely animated GIF into this mod's own "CPAG" container (see AnimatedPhotoCodec) for
     * the FULL resolution, and a plain static PNG of its first frame - downscaled the exact same way a live
     * capture's own preview is - for the THUMBNAIL, so the gallery grid/message bubbles/mayor poster etc.
     * never need to know animation exists at all; only a full-resolution render (held item, dropped item,
     * photo frame, the full-screen viewer) ever asks FabricPictureCache for FULL and gets an animated
     * texture back. */
    private static UUID importAnimated(byte[] gifBytes, String source) {
        try (AnimatedPhotoCodec.Animation source0 = AnimatedPhotoCodec.decodeSourceGif(gifBytes)) {
            byte[] fullBytes = AnimatedPhotoCodec.encodeWithinBudget(source0, ANIMATED_MAX_DIMENSION, ANIMATED_MAX_FRAMES,
                    Math.min(MAX_UPLOAD_PAYLOAD_BYTES, Config.photoFullMaxUploadBytes));
            if (fullBytes == null)
                return null;
            int thumbnailHeight = Config.photoThumbnailPixelHeight;
            NativeImage firstFrame = source0.frames().get(0).image();
            byte[] thumbnailBytes;
            if (thumbnailHeight <= 0 || thumbnailHeight >= firstFrame.getHeight()) {
                thumbnailBytes = staticPng(firstFrame);
            } else {
                // Caught in a cleanliness pass - the downscaled copy here used to never get closed (leaked
                // one native image per animated import, same "close what you just allocated" pattern every
                // other caller of downscaleToHeight in this codebase already follows).
                try (NativeImage thumbnail = PixelArtDownscaler.downscaleToHeight(firstFrame, thumbnailHeight)) {
                    thumbnailBytes = staticPng(thumbnail);
                }
            }
            UUID photoId = UUID.randomUUID();
            FabricPictureCache.seedFromLocalCapture(photoId, thumbnailBytes, fullBytes);
            NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket("", photoId, thumbnailBytes, fullBytes, false));
            return photoId;
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Could not import animated photo {}", source, e);
            return null;
        }
    }

    //? if <1.21.10 {
    private static byte[] staticPng(NativeImage image) throws IOException {
        return image.asByteArray();
    }
    //? } else {
    /*private static byte[] staticPng(NativeImage image) throws IOException {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("crazyphone-import-", ".png");
        try {
            image.writeToFile(tmp);
            return java.nio.file.Files.readAllBytes(tmp);
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
    *///?}

    // Under the 1 MiB ceiling NeoForge puts on a serverbound custom payload, with room for the thumbnail and
    // the packet's own fields.
    private static final int MAX_UPLOAD_PAYLOAD_BYTES = 900_000;
    private static final int MIN_DIMENSION = 128;

    // A photo is downscaled to Config#photoFullMaxDimension anyway - nothing legitimate needs more than this.
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;

    /** Whether {@code text} looks like an http(s) link worth trying (checked before any request is made). */
    public static boolean isHttpLink(String text) {
        if (text == null)
            return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2000 || trimmed.contains(" "))
            return false;
        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Downloads the image at {@code link} in the background (never blocks the render thread), then decodes and
     * uploads it on the client thread. {@code onDone} receives the new photo id on the client thread, or null if
     * the download or decode failed (not a link, not an image, too big, unreachable...). */
    public static void importFromLink(String link, java.util.function.Consumer<UUID> onDone) {
        String url = link == null ? "" : link.trim();
        if (!isHttpLink(url)) {
            onDone.accept(null);
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> download(url)).thenAccept(bytes -> mc.execute(() -> {
            UUID id = null;
            if (bytes != null)
                id = importBytes(bytes, url);
            onDone.accept(id);
        }));
    }

    private static byte[] download(String url) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("User-Agent", "CrazyPhone photo import")
                    .GET().build();
            java.net.http.HttpResponse<InputStream> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2)
                return null;
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declared > MAX_DOWNLOAD_BYTES)
                return null;
            try (InputStream in = response.body()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[16 * 1024];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    out.write(chunk, 0, read);
                    if (out.size() > MAX_DOWNLOAD_BYTES)
                        return null;
                }
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Could not download {}", url, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
