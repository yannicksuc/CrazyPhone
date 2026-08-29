package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side texture cache for the native picture pipeline: lazily fetches a photo's bytes (at whichever
 * PhotoResolution is asked for) the first time its texture is needed, decodes and uploads it as a
 * {@link net.minecraft.client.renderer.texture.DynamicTexture}, and caches the resulting entry (texture +
 * real pixel dimensions) so a scrolled-past-and-back message or a re-opened viewer doesn't refetch. Same
 * lazy/on-demand shape as VoiceMessageRecorder's audio fetch, just for images instead of playback.
 */
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricPictureCache {
    // Temporary diagnostic logging for the "viewer shows nothing" investigation - the fetch/decode path has
    // no other visibility (no exceptions get thrown on a clean auth-rejection or an empty response), so
    // there's no way to tell request-never-sent / no-response / decode-failure apart without this. Remove
    // once the viewer bug is confirmed fixed live.
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-picture-debug");
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
        if (IN_FLIGHT.add(key)) {
            LOGGER.info("Requesting {} for photo {}", resolution, photoId);
            NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(photoId, resolution));
        }
        return null;
    }

    // Called on (re)connecting to a server - RESOLVED/IN_FLIGHT/FAILED are static, so they otherwise persist
    // across a disconnect. A request that was IN_FLIGHT at the exact moment the connection dropped never
    // gets its onBytesReceived call (the server never responds to a client that's already gone), so it stays
    // in IN_FLIGHT forever - getOrRequest sees "already in flight" and never re-sends it, permanently
    // blocking that one photo+resolution from ever loading again, even after reconnecting. Clearing all
    // three on every join is simpler and more robust than trying to only clear the stuck entries (RESOLVED's
    // own DynamicTextures may also have been invalidated by the disconnect/world-unload, and FAILED entries
    // deserve a fresh attempt against the new connection regardless).
    public static void reset() {
        RESOLVED.clear();
        IN_FLIGHT.clear();
        FAILED.clear();
    }

    public static void onBytesReceived(UUID photoId, PhotoResolution resolution, byte[] pngBytes) {
        Key key = new Key(photoId, resolution);
        IN_FLIGHT.remove(key);
        LOGGER.info("Received {} bytes for {} photo {}", pngBytes.length, resolution, photoId);
        if (pngBytes.length == 0) {
            FAILED.add(key);
            return;
        }
        //? if <1.21.10 {
        try {
            // NativeImage.read(byte[]) stack-allocates a native buffer sized to the WHOLE input via LWJGL's
            // small per-thread MemoryStack (a few tens of KB by default) - fine for a ~10KB thumbnail, but a
            // full-resolution photo (up to FULL_MAX_BYTES, 2MB) blew right through it ("Out of stack space",
            // confirmed live: thumbnails always decoded fine, full-size photos always crashed the moment the
            // viewer tried to open one). The InputStream overload reads through STBImage's own heap-backed
            // path instead, the same one vanilla itself relies on for arbitrarily large resource pack/skin
            // images - no size ceiling tied to the native stack at all.
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(pngBytes));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register(
                    "crazyphone-picture-" + resolution.name().toLowerCase(java.util.Locale.ROOT) + "-" + photoId, texture);
            RESOLVED.put(key, new CachedTexture(id, image.getWidth(), image.getHeight()));
            LOGGER.info("Decoded {} photo {} as {}x{}", resolution, photoId, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            LOGGER.warn("Failed to decode {} photo {}", resolution, photoId, e);
            FAILED.add(key);
        }
        //? } else {
        /*// TODO: 1.21.10 changed DynamicTexture's constructor (now takes a name Supplier) and
        // TextureManager#register's key type (ResourceLocation, not a plain String) - not backported yet,
        // tracked alongside FabricPictureCapture's own 1.21.10 TODO. Fails closed like a decode error would.
        FAILED.add(key);
        *///?}
    }
}
