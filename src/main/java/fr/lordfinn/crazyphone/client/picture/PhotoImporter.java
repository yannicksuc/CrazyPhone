package fr.lordfinn.crazyphone.client.picture;

import com.mojang.blaze3d.platform.NativeImage;
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
    // Formats Minecraft's own image loader (stb_image) can decode.
    private static final String[] FILTERS = {"*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.tga"};

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
        try (InputStream in = Files.newInputStream(path); NativeImage image = NativeImage.read(in)) {
            UUID photoId = UUID.randomUUID();
            boolean[] sent = {false};
            FabricPictureCapture.deriveBothResolutions(image, (thumbnailPng, fullPng) -> {
                FabricPictureCache.seedFromLocalCapture(photoId, thumbnailPng, fullPng);
                NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket("", photoId, thumbnailPng, fullPng));
                sent[0] = true;
            });
            return sent[0] ? photoId : null;
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Could not import photo {}", path, e);
            return null;
        }
    }
}
