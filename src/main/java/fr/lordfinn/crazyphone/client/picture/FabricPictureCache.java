package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side texture cache for the native picture pipeline: lazily fetches a photo's bytes (at whichever
 * PhotoResolution is asked for) the first time its texture is needed, decodes and uploads it as a
 * {@link net.minecraft.client.renderer.texture.DynamicTexture}, and caches the resulting entry (texture +
 * real pixel dimensions) so a scrolled-past-and-back message or a re-opened viewer doesn't refetch. Same
 * lazy/on-demand shape as VoiceMessageRecorder's audio fetch, just for images instead of playback.
 */
//? if fabric && >=1.20.5 {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricPictureCache {
    public record CachedTexture(ResourceLocation location, int width, int height) {
    }

    private record Key(UUID photoId, PhotoResolution resolution) {
    }

    private static final Map<Key, CachedTexture> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<Key> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // A failed decode (corrupt/truncated bytes) shouldn't retry every single frame the image is on screen -
    // remembered so the caller can fall back to a placeholder instead of hammering the server.
    private static final Set<Key> FAILED = ConcurrentHashMap.newKeySet();

    private FabricPictureCache() {
    }

    // Returns the cached texture (with real pixel dimensions) for this photo at this resolution, or null
    // while it's still being fetched/decoded (and kicks off that fetch on first call) - callers should draw
    // a placeholder until this returns non-null.
    public static CachedTexture getOrRequest(UUID photoId, PhotoResolution resolution) {
        Key key = new Key(photoId, resolution);
        CachedTexture cached = RESOLVED.get(key);
        if (cached != null)
            return cached;
        if (FAILED.contains(key))
            return null;
        if (IN_FLIGHT.add(key))
            NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(photoId, resolution));
        return null;
    }

    public static void onBytesReceived(UUID photoId, PhotoResolution resolution, byte[] pngBytes) {
        Key key = new Key(photoId, resolution);
        IN_FLIGHT.remove(key);
        if (pngBytes.length == 0) {
            FAILED.add(key);
            return;
        }
        try {
            NativeImage image = NativeImage.read(pngBytes);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register(
                    "crazyphone-picture-" + resolution.name().toLowerCase(java.util.Locale.ROOT) + "-" + photoId, texture);
            RESOLVED.put(key, new CachedTexture(id, image.getWidth(), image.getHeight()));
        } catch (Exception e) {
            FAILED.add(key);
        }
    }
}
*///?}
