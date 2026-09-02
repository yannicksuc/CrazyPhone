package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side texture cache for the native picture pipeline: lazily fetches a photo's bytes (at whichever
 * PhotoResolution is asked for) the first time its texture is needed, decodes and uploads it as a
 * {@link net.minecraft.client.renderer.texture.DynamicTexture}, and caches the resulting entry (texture +
 * real pixel dimensions) so a scrolled-past-and-back message or a re-opened viewer doesn't refetch. Same
 * lazy/on-demand shape as VoiceMessageRecorder's audio fetch, just for images instead of playback.
 *
 * Backed by a disk cache under the game directory (crazyphone/photocache/) so a photo already viewed once
 * survives a reconnect or a full client restart without hitting the server again - RESOLVED/IN_FLIGHT/FAILED
 * below are still purely in-memory (and still cleared on every reconnect, see reset()'s own doc comment),
 * but a disk hit skips the network round trip entirely. Safe to cache indefinitely: a photoId always refers
 * to the exact same immutable bytes once captured (CrazyPhone never edits a photo in place), so there's no
 * staleness/invalidation concern the way there would be for mutable server data.
 */
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class FabricPictureCache {
    // Temporary diagnostic logging for the "viewer shows nothing" investigation - the fetch/decode path has
    // no other visibility (no exceptions get thrown on a clean auth-rejection or an empty response), so
    // there's no way to tell request-never-sent / no-response / decode-failure apart without this. Remove
    // once the viewer bug is confirmed fixed live.
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone-picture-debug");
    public record CachedTexture(/*$ res_loc {*/ResourceLocation/*$}*/ location, int width, int height) {
    }

    private record Key(UUID photoId, PhotoResolution resolution) {
    }

    private static final Map<Key, CachedTexture> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<Key> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // A failed decode (corrupt/truncated bytes) shouldn't retry every single frame the image is on screen -
    // remembered so the caller can fall back to a placeholder instead of hammering the server.
    private static final Set<Key> FAILED = ConcurrentHashMap.newKeySet();

    // Single background thread for all disk I/O below - photo cache reads/writes are small, infrequent, and
    // never latency-critical (the caller always already has a placeholder to show meanwhile), so there's no
    // need for a pooled/parallel executor here.
    private static final Executor DISK_IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crazyphone-photo-disk-cache");
        t.setDaemon(true);
        return t;
    });

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
            Path diskFile = cacheFile(key);
            if (Files.isRegularFile(diskFile)) {
                LOGGER.info("Loading {} for photo {} from disk cache", resolution, photoId);
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return Files.readAllBytes(diskFile);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to read disk-cached {} photo {} - falling back to server", resolution, photoId, e);
                        return null;
                    }
                }, DISK_IO).thenAcceptAsync(bytes -> {
                    if (bytes == null || bytes.length == 0) {
                        // Corrupt/unreadable cache file - clear the IN_FLIGHT marker so the normal network
                        // fetch path below still runs (onBytesReceived itself is never called from here in
                        // this case, so nothing else would ever un-stick it).
                        IN_FLIGHT.remove(key);
                        NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(photoId, resolution));
                        return;
                    }
                    decodeAndRegister(key, bytes, false);
                }, Minecraft.getInstance());
                return null;
            }
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
    //
    // The disk cache is deliberately NOT touched here - it's keyed by photoId, which is globally unique and
    // immutable regardless of which server/world it was first seen on, so a photo cached from one server is
    // perfectly valid to reuse when reconnecting to (or joining a different) server that references the same
    // photoId. Only the in-memory bookkeeping above needs resetting.
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
        decodeAndRegister(key, pngBytes, true);
    }

    // Shared by both the network-fetch path (onBytesReceived) and the disk-cache-hit path (getOrRequest) -
    // decoding/texture upload must run on the render thread either way (DynamicTexture registration touches
    // the GL context), so this itself makes no thread assumption; callers are responsible for getting here
    // on the right thread (onBytesReceived already runs there via the packet handler, the disk path hops
    // back via Minecraft.getInstance() as an Executor above).
    private static void decodeAndRegister(Key key, byte[] pngBytes, boolean persistToDisk) {
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
            /*$ res_loc {*/ResourceLocation/*$}*/ id = Minecraft.getInstance().getTextureManager().register(
                    "crazyphone-picture-" + key.resolution().name().toLowerCase(java.util.Locale.ROOT) + "-" + key.photoId(), texture);
            RESOLVED.put(key, new CachedTexture(id, image.getWidth(), image.getHeight()));
            LOGGER.info("Decoded {} photo {} as {}x{}", key.resolution(), key.photoId(), image.getWidth(), image.getHeight());
        } catch (Exception e) {
            LOGGER.warn("Failed to decode {} photo {}", key.resolution(), key.photoId(), e);
            FAILED.add(key);
            return;
        }
        //? } else {
        /*try {
            // 1.21.10 changed DynamicTexture's constructor (now takes a name Supplier and uploads itself) and
            // TextureManager#register's key type (Identifier, not a plain String) - implemented against the
            // real API instead of the plain-String id the <1.21.10 branch uses, since Identifier paths need
            // the same lowercase [a-z0-9/._-] validation as everywhere else (a UUID's hex+hyphens already
            // satisfies this, so the id shape itself is unchanged, just wrapped through Crazyphone.resource()).
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(pngBytes));
            net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ id = fr.lordfinn.crazyphone.Crazyphone.resource(
                    "crazyphone-picture-" + key.resolution().name().toLowerCase(java.util.Locale.ROOT) + "-" + key.photoId());
            DynamicTexture texture = new DynamicTexture(id::toString, image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            RESOLVED.put(key, new CachedTexture(id, image.getWidth(), image.getHeight()));
            LOGGER.info("Decoded {} photo {} as {}x{}", key.resolution(), key.photoId(), image.getWidth(), image.getHeight());
        } catch (Exception e) {
            LOGGER.warn("Failed to decode {} photo {}", key.resolution(), key.photoId(), e);
            FAILED.add(key);
            return;
        }
        *///?}
        if (persistToDisk)
            writeToDiskAsync(key, pngBytes);
    }

    // Fire-and-forget - a failed disk write just means this photo re-fetches from the server next session,
    // same as it always has, not worth surfacing to the player or retrying.
    private static void writeToDiskAsync(Key key, byte[] pngBytes) {
        CompletableFuture.runAsync(() -> {
            try {
                Path file = cacheFile(key);
                Files.createDirectories(file.getParent());
                Files.write(file, pngBytes);
            } catch (IOException e) {
                LOGGER.warn("Failed to write {} photo {} to disk cache", key.resolution(), key.photoId(), e);
            }
        }, DISK_IO);
    }

    private static Path cacheFile(Key key) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("crazyphone").resolve("photocache")
                .resolve(key.resolution().name().toLowerCase(java.util.Locale.ROOT) + "-" + key.photoId() + ".png");
    }
}
