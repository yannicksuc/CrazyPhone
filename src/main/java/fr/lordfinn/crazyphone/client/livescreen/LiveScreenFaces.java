package fr.lordfinn.crazyphone.client.livescreen;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Faces for the call tiles of the held phone's live screen: the game's own default skins (Steve, Alex, Kai,
 * Zuri...), read from the game's resources (so a resource pack that changes them is followed), never the
 * real skin of the people in the call. Participant 1 is always Steve, 2 Alex, and so on: the tiles only
 * tell how many people are in the call, nothing about who they are.
 *
 * Each face is 8 x 8 (the head's front, with the hat layer on top) plus one row for the shirt (the top row
 * of the body's front), packed ARGB. Loaded once, on first use; if anything fails (missing texture, no
 * game around as in a test) {@link #face} returns null and the painter keeps its generic face.
 */
public final class LiveScreenFaces {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    /** Vanilla's default skins (textures/entity/player/wide/), in the order the tiles use them. */
    private static final String[] SKINS = {"steve", "alex", "kai", "zuri", "noor", "sunny", "ari", "efe", "makena"};
    public static final int SIZE = 8;

    private static int[][] faces;
    private static boolean loaded;

    private LiveScreenFaces() {
    }

    /** Face of the call tile at {@code index} (0-based): 9 rows of 8 ARGB pixels, or null. */
    public static synchronized int[] face(int index) {
        if (!loaded) {
            loaded = true;
            faces = load();
        }
        if (faces == null || faces.length == 0)
            return null;
        return faces[Math.floorMod(index, faces.length)];
    }

    /** For tools and tests without a running game: use these faces instead of reading the skins. */
    public static synchronized void useFaces(int[][] given) {
        faces = given;
        loaded = true;
    }

    private static int[][] load() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null)
                return null;
            java.util.List<int[]> out = new java.util.ArrayList<>();
            for (String name : SKINS) {
                /*$ res_loc {*/ResourceLocation/*$}*/ id = fr.lordfinn.crazyphone.Crazyphone.parseId("minecraft:textures/entity/player/wide/" + name + ".png");
                var resource = mc.getResourceManager().getResource(id);
                if (resource.isEmpty())
                    continue;
                try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                    if (image.getWidth() < 64 || image.getHeight() < 32)
                        continue;
                    out.add(extract(image));
                }
            }
            return out.isEmpty() ? null : out.toArray(new int[0][]);
        } catch (Throwable t) {
            LOGGER.warn("CrazyPhone: could not read the default skins for the live call screen, using the generic face", t);
            return null;
        }
    }

    private static int[] extract(NativeImage image) {
        int[] face = new int[SIZE * (SIZE + 1)];
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int base = readPixelARGB(image, 8 + x, 8 + y) | 0xFF000000;
                int hat = readPixelARGB(image, 40 + x, 8 + y);
                face[y * SIZE + x] = over(hat, base);
            }
        // Shirt: the top row of the body's front (x 20-27, y 20).
        for (int x = 0; x < SIZE; x++)
            face[SIZE * SIZE + x] = readPixelARGB(image, 20 + x, 20) | 0xFF000000;
        return face;
    }

    /** {@code top} drawn over the opaque {@code bottom}, by top's alpha. */
    private static int over(int top, int bottom) {
        int a = (top >>> 24) & 0xFF;
        if (a == 0)
            return bottom;
        if (a == 255)
            return top;
        int r = mix((top >> 16) & 0xFF, (bottom >> 16) & 0xFF, a);
        int g = mix((top >> 8) & 0xFF, (bottom >> 8) & 0xFF, a);
        int b = mix(top & 0xFF, bottom & 0xFF, a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int top, int bottom, int alpha) {
        return (top * alpha + bottom * (255 - alpha)) / 255;
    }

    // Same version-gated pixel layout as PhotoImageOps#readPixelARGB.
    private static int readPixelARGB(NativeImage image, int x, int y) {
        //? if <1.21.10 {
        int p = image.getPixelRGBA(x, y);
        int a = (p >> 24) & 0xFF, r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
        //? } else {
        /*int p = image.getPixel(x, y);
        int a = (p >>> 24) & 0xFF, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        *///?}
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
