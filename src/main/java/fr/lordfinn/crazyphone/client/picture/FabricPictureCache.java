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
 *
 * RESOLVED is a bounded LRU (see MAX_RESOLVED_TEXTURES) rather than an unbounded map - a long session that
 * scrolls through many conversations/galleries would otherwise accumulate GPU textures forever. Eviction and
 * reset() both release the evicted/cleared DynamicTexture's GPU resources through TextureManager, not just
 * drop the map entry - textures don't free themselves.
 */
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.network.CrazyPhoneClearPictureCachePacket;
import fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class FabricPictureCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

    /** A photo ready to draw - either a single fixed texture, or (see {@link #animated}) a short loop of
     * frame textures this class itself picks between based on wall-clock time, so every call site that
     * already just calls {@code texture.location()} once per render keeps working unchanged: it always gets
     * back whichever frame should be showing right now, static photos included (where that's simply the
     * only frame there is). Two viewers' clocks aren't synchronized, so they see roughly, not frame-exactly,
     * the same point in the loop - acceptable for a decorative photo animation. */
    public static final class CachedTexture {
        private final /*$ res_loc {*/ResourceLocation/*$}*/[] frameLocations;
        // Cumulative delay in millis through frame i (inclusive) - null for a static (single-frame) texture,
        // where location() never needs to consult the clock at all.
        private final int[] cumulativeDelaysMillis;
        private final int totalDurationMillis;
        private final int width;
        private final int height;
        private final boolean hasTransparency;

        private CachedTexture(/*$ res_loc {*/ResourceLocation/*$}*/[] frameLocations, int[] delaysMillis, int width, int height, boolean hasTransparency) {
            this.frameLocations = frameLocations;
            this.width = width;
            this.height = height;
            this.hasTransparency = hasTransparency;
            if (frameLocations.length <= 1) {
                this.cumulativeDelaysMillis = null;
                this.totalDurationMillis = 0;
            } else {
                this.cumulativeDelaysMillis = new int[delaysMillis.length];
                int sum = 0;
                for (int i = 0; i < delaysMillis.length; i++) {
                    sum += delaysMillis[i];
                    this.cumulativeDelaysMillis[i] = sum;
                }
                this.totalDurationMillis = Math.max(1, sum);
            }
        }

        static CachedTexture of(/*$ res_loc {*/ResourceLocation/*$}*/ location, int width, int height, boolean hasTransparency) {
            return new CachedTexture(new /*$ res_loc {*/ResourceLocation/*$}*/[]{location}, new int[]{0}, width, height, hasTransparency);
        }

        static CachedTexture animated(/*$ res_loc {*/ResourceLocation/*$}*/[] frameLocations, int[] delaysMillis, int width, int height, boolean hasTransparency) {
            return new CachedTexture(frameLocations, delaysMillis, width, height, hasTransparency);
        }

        public /*$ res_loc {*/ResourceLocation/*$}*/ location() {
            if (cumulativeDelaysMillis == null)
                return frameLocations[0];
            long elapsed = System.currentTimeMillis() % totalDurationMillis;
            for (int i = 0; i < cumulativeDelaysMillis.length; i++)
                if (elapsed < cumulativeDelaysMillis[i])
                    return frameLocations[i];
            return frameLocations[frameLocations.length - 1];
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public boolean hasTransparency() {
            return hasTransparency;
        }

        // Releases EVERY frame's GPU texture, not just whichever one location() would currently return -
        // eviction/reset must free the whole animation, not one arbitrarily-picked frame of it.
        void releaseAll(net.minecraft.client.renderer.texture.TextureManager textureManager) {
            for (/*$ res_loc {*/ResourceLocation/*$}*/ frameLocation : frameLocations)
                textureManager.release(frameLocation);
        }
    }

    private record Key(UUID photoId, PhotoResolution resolution) {
    }

    // Capped, access-ordered (true below) so the least-recently-used entry is evicted first once the cap is
    // hit - a gallery/conversation-heavy session naturally keeps recently-viewed photos resident and lets old
    // ones' GPU textures go. Wrapped in synchronizedMap since removeEldestEntry (and every other mutation)
    // can run from either the network-response path or the disk-cache-hit path, both of which hop back onto
    // the client thread via different routes (see decodeAndRegister's own doc comment) - LinkedHashMap itself
    // isn't thread-safe even for same-thread-eventually callers racing each other.
    private static final int MAX_RESOLVED_TEXTURES = 400;
    private static final Map<Key, CachedTexture> RESOLVED = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, CachedTexture> eldest) {
                    if (size() <= MAX_RESOLVED_TEXTURES)
                        return false;
                    eldest.getValue().releaseAll(Minecraft.getInstance().getTextureManager());
                    return true;
                }
            });
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
        if (IN_FLIGHT.add(key) && !tryLoadFromDisk(key))
            NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(
                    List.of(new CrazyPhonePictureRequestPacket.Entry(photoId, resolution))));
        return null;
    }

    // Warms the cache for a whole batch of photos at once (a gallery page, the visible range of a
    // conversation's message list) - skips anything already resolved/failed/in flight, then issues at most
    // ONE network request covering every remaining disk-cache miss, instead of letting each photo's first
    // render trigger its own separate getOrRequest fetch as it scrolls into view. Callers don't need the
    // result here; they still poll getOrRequest (or read RESOLVED indirectly through it) once rendering the
    // actual widget, by which point prefetch has usually already resolved or at least started the fetch.
    public static void prefetch(Collection<UUID> photoIds, PhotoResolution resolution) {
        List<CrazyPhonePictureRequestPacket.Entry> misses = new ArrayList<>();
        for (UUID photoId : photoIds) {
            Key key = new Key(photoId, resolution);
            if (RESOLVED.containsKey(key) || FAILED.contains(key) || !IN_FLIGHT.add(key))
                continue;
            if (!tryLoadFromDisk(key))
                misses.add(new CrazyPhonePictureRequestPacket.Entry(photoId, resolution));
        }
        if (!misses.isEmpty())
            NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(misses));
    }

    // Shared by getOrRequest and prefetch - key must already be in IN_FLIGHT. Returns true if a disk file
    // exists (an async read+decode has been kicked off, still resolving the same as before), false if the
    // caller still needs to go fetch this key from the network.
    private static boolean tryLoadFromDisk(Key key) {
        Path diskFile = cacheFile(key);
        if (!Files.isRegularFile(diskFile))
            return false;
        CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readAllBytes(diskFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to read disk-cached {} photo {} - falling back to server", key.resolution(), key.photoId(), e);
                return null;
            }
        }, DISK_IO).thenAcceptAsync(bytes -> {
            if (bytes == null || bytes.length == 0) {
                // Corrupt/unreadable cache file - clear the IN_FLIGHT marker so a normal network fetch still
                // runs (onBytesReceived is never called from here in this case, so nothing else would ever
                // un-stick it).
                IN_FLIGHT.remove(key);
                NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(
                        List.of(new CrazyPhonePictureRequestPacket.Entry(key.photoId(), key.resolution()))));
                return;
            }
            decodeAndRegister(key, bytes, false);
        }, Minecraft.getInstance());
        return true;
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
    // Every RESOLVED texture is explicitly released through TextureManager first - clearing the map alone
    // drops CrazyPhone's own reference but leaves the texture registered and resident on the GPU forever
    // (TextureManager has no other owner to eventually reclaim it), a real per-reconnect leak on any session
    // that reconnects more than once.
    //
    // The disk cache is deliberately NOT touched here - it's keyed by photoId, which is globally unique and
    // immutable regardless of which server/world it was first seen on, so a photo cached from one server is
    // perfectly valid to reuse when reconnecting to (or joining a different) server that references the same
    // photoId. Only the in-memory bookkeeping above needs resetting.
    public static void reset() {
        synchronized (RESOLVED) {
            net.minecraft.client.renderer.texture.TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            for (CachedTexture texture : RESOLVED.values())
                texture.releaseAll(textureManager);
            RESOLVED.clear();
        }
        IN_FLIGHT.clear();
        FAILED.clear();
    }

    /** Full wipe, on top of what reset() already does - additionally deletes every file under the disk
     * cache directory, which reset() deliberately leaves alone (see its own doc comment: a photo cached
     * from one server stays valid on another). This is the explicit "actually purge everything" entry
     * point, driven by {@link CrazyPhoneClearPictureCachePacket} from the admin-only
     * {@code /crazyphone cache clear} command - for a stale/corrupted local cache that reset()'s own
     * per-reconnect clearing wouldn't touch (a bad disk file keeps getting reloaded from disk on every
     * getOrRequest, reconnect or not). */
    public static void clearAll() {
        reset();
        CompletableFuture.runAsync(() -> {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("crazyphone").resolve("photocache");
            if (!Files.isDirectory(dir))
                return;
            try (var files = Files.list(dir)) {
                files.forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to delete disk-cached file {}", file, e);
                    }
                });
            } catch (IOException e) {
                LOGGER.warn("Failed to list photo disk cache directory {}", dir, e);
            }
        }, DISK_IO);
    }

    public static void onBatchReceived(List<fr.lordfinn.crazyphone.network.CrazyPhonePictureResponsePacket.Entry> entries) {
        for (fr.lordfinn.crazyphone.network.CrazyPhonePictureResponsePacket.Entry entry : entries)
            onBytesReceived(entry.photoId(), entry.resolution(), entry.pngBytes());
    }

    /** Seeds this client's own cache directly from PNG bytes it already produced locally - the moment a
     * screenshot is taken, the capturing client already holds both resolutions' exact final bytes, so there's
     * no reason for it to wait on CrazyPhoneUploadPicturePacket's own round trip and then a normal
     * CrazyPhonePictureRequestPacket fetch just to see its own just-taken photo render (see
     * CrazyPhoneCaptureMode#triggerCapture, which calls this right before sending that upload packet). Must
     * be called from the render thread, same as {@link #onBytesReceived} - texture registration touches the
     * GL context. */
    public static void seedFromLocalCapture(UUID photoId, byte[] thumbnailPng, byte[] fullPng) {
        onBytesReceived(photoId, PhotoResolution.THUMBNAIL, thumbnailPng);
        onBytesReceived(photoId, PhotoResolution.FULL, fullPng);
    }

    private static void onBytesReceived(UUID photoId, PhotoResolution resolution, byte[] pngBytes) {
        Key key = new Key(photoId, resolution);
        IN_FLIGHT.remove(key);
        if (pngBytes.length == 0) {
            FAILED.add(key);
            return;
        }
        decodeAndRegister(key, pngBytes, true);
    }

    // Shared by both the network-fetch path (onBytesReceived) and the disk-cache-hit path (getOrRequest/
    // prefetch) - decoding/texture upload must run on the render thread either way (DynamicTexture
    // registration touches the GL context), so this itself makes no thread assumption; callers are
    // responsible for getting here on the right thread (onBytesReceived already runs there via the packet
    // handler, the disk path hops back via Minecraft.getInstance() as an Executor above).
    private static void decodeAndRegister(Key key, byte[] pngBytes, boolean persistToDisk) {
        // Only ever produced for FULL (see PhotoImporter#importAnimated) - a THUMBNAIL is always a plain
        // static PNG of the animation's first frame, so this check would simply never match for one anyway;
        // spelled out regardless so this branch reads as deliberate, not accidental.
        if (key.resolution() == PhotoResolution.FULL && AnimatedPhotoCodec.isAnimatedContainer(pngBytes)) {
            registerAnimated(key, pngBytes, persistToDisk);
            return;
        }
        NativeImage image;
        CachedTexture texture;
        try {
            // NativeImage.read(byte[]) stack-allocates a native buffer sized to the WHOLE input via LWJGL's
            // small per-thread MemoryStack (a few tens of KB by default) - fine for a ~10KB thumbnail, but a
            // full-resolution photo (up to FULL_MAX_BYTES, 2MB) blew right through it ("Out of stack space",
            // confirmed live: thumbnails always decoded fine, full-size photos always crashed the moment the
            // viewer tried to open one). The InputStream overload reads through STBImage's own heap-backed
            // path instead, the same one vanilla itself relies on for arbitrarily large resource pack/skin
            // images - no size ceiling tied to the native stack at all.
            image = NativeImage.read(new java.io.ByteArrayInputStream(pngBytes));
            texture = registerTexture(key, image, "");
        } catch (Exception e) {
            LOGGER.warn("Failed to decode {} photo {}", key.resolution(), key.photoId(), e);
            FAILED.add(key);
            return;
        }
        RESOLVED.put(key, texture);
        if (persistToDisk)
            writeToDiskAsync(key, pngBytes);
        if (key.resolution() == PhotoResolution.FULL)
            deriveThumbnailFromFull(key.photoId(), image);
    }

    // Mirrors decodeAndRegister's own static path, just decoding every frame of this mod's own "CPAG"
    // container (see AnimatedPhotoCodec) instead of one PNG, and registering one DynamicTexture per frame.
    private static void registerAnimated(Key key, byte[] containerBytes, boolean persistToDisk) {
        AnimatedPhotoCodec.Animation animation;
        try {
            animation = AnimatedPhotoCodec.decodeContainer(containerBytes);
        } catch (Exception e) {
            LOGGER.warn("Failed to decode animated photo {}", key.photoId(), e);
            FAILED.add(key);
            return;
        }
        CachedTexture texture;
        try {
            texture = registerAnimatedTexture(key, animation);
        } catch (Exception e) {
            LOGGER.warn("Failed to register animated photo {}", key.photoId(), e);
            // Registration failed part way through - every frame's NativeImage is still ours to close (a
            // successfully-registered frame's own DynamicTexture would otherwise never get released either,
            // but registerAnimatedTexture itself is the only thing that hands frames off one at a time, and
            // it never leaves this method having partially succeeded - see its own doc comment).
            animation.close();
            FAILED.add(key);
            return;
        }
        RESOLVED.put(key, texture);
        if (persistToDisk)
            writeToDiskAsync(key, containerBytes);
        // Same "derive the other resolution from pixels already in hand" trick decodeAndRegister's own
        // static path uses - reads frame 0's pixels, which stay valid until whichever DynamicTexture now
        // owns them is eventually released (registerAnimatedTexture never closes what it registers).
        deriveThumbnailFromFull(key.photoId(), animation.frames().get(0).image());
    }

    // Skips a real network/disk round trip for the THUMBNAIL resolution entirely when a FULL decode of the
    // same photo just happened to run first (e.g. opening the full-size viewer, or a photo frame/held item
    // rendering FULL directly) - downscaling an already-decoded NativeImage is essentially free next to a
    // request-response round trip. Only fires when THUMBNAIL isn't already resolved/in flight/failed, so it
    // never fights a fetch that's already in progress or duplicates a texture that already exists. RAM-only
    // (not written to the disk cache) - the FULL bytes are already on disk if persistToDisk was true, and
    // re-deriving the thumbnail from that FULL file on a later session costs the same few milliseconds this
    // does here, not worth the extra disk write/space to persist a second copy.
    private static void deriveThumbnailFromFull(UUID photoId, NativeImage fullImage) {
        Key thumbKey = new Key(photoId, PhotoResolution.THUMBNAIL);
        if (RESOLVED.containsKey(thumbKey) || IN_FLIGHT.contains(thumbKey) || FAILED.contains(thumbKey))
            return;
        int targetHeight = Config.photoThumbnailPixelHeight;
        // Mirrors FabricPictureCapture's own capture-time skip: 0 means "no separate preview configured",
        // and a target at/above the full image's own height would only ever upscale it - in both cases the
        // server-side storePhoto already reused the same bytes for both resolutions (see PhotoSavedData's
        // own doc), so a real THUMBNAIL fetch/decode will just get identical pixels; deriving one here would
        // be pure waste.
        if (targetHeight <= 0 || targetHeight >= fullImage.getHeight())
            return;
        try (NativeImage thumbnail = downscaleForThumbnail(fullImage, targetHeight)) {
            RESOLVED.put(thumbKey, registerTexture(thumbKey, thumbnail, "-derived"));
        } catch (Exception e) {
            // Not marked FAILED - a genuine fetch (network or disk) for THUMBNAIL is still perfectly able to
            // succeed later; this was purely a local optimization attempt.
            LOGGER.warn("Failed to derive thumbnail for photo {} from its full image", photoId, e);
        }
    }

    // Downscale to an exact target height, mirroring FabricPictureCapture#downscaleToHeight (capture-time
    // derivation of the same two resolutions from one screenshot) - both now delegate to the same
    // PixelArtDownscaler, which is safe to share despite the two call sites' different NativeImage lifetimes
    // (capture's source is closed by its own try-with-resources right after; this one must NOT close
    // fullImage, which the caller (decodeAndRegister) still owns via its DynamicTexture) since
    // PixelArtDownscaler only ever reads from source, never closes it, and returns a fresh NativeImage this
    // method's own caller owns exactly as before.
    private static NativeImage downscaleForThumbnail(NativeImage source, int targetHeight) {
        return PixelArtDownscaler.downscaleToHeight(source, targetHeight);
    }

    // Wraps image in a DynamicTexture and registers it with TextureManager under a name unique to this key -
    // nameSuffix only exists so the locally-derived thumbnail (see deriveThumbnailFromFull) can't collide
    // with a texture name a real network fetch of the same key would also use, in the (currently impossible,
    // but cheap to guard against) case both paths ever raced to register the same key.
    //
    // <1.21.10's setFilter(false, false) explicitly disables mipmapping - a freshly uploaded GL texture
    // defaults to a mip-sampling MIN_FILTER (GL_NEAREST_MIPMAP_LINEAR) even though only the base level ever
    // gets generated here, an incomplete-texture state vanilla's own batched renderer happens to tolerate
    // but a heavier third-party render pipeline might not. AbstractTexture has no equivalent method on
    // >=1.21.10 (needs its own real API investigation, not guessed) - left as a known gap there for now.
    private static CachedTexture registerTexture(Key key, NativeImage image, String nameSuffix) {
        String name = "crazyphone-picture-" + key.resolution().name().toLowerCase(Locale.ROOT) + "-" + key.photoId() + nameSuffix;
        /*$ res_loc {*/ResourceLocation/*$}*/ id = registerDynamicTexture(name, image);
        return CachedTexture.of(id, image.getWidth(), image.getHeight(), hasTransparency(image));
    }

    // One DynamicTexture per frame, named so each is unique within the animation (and across animations/
    // resolutions/photoIds, same "-derived"-suffix trick registerTexture's own nameSuffix already relies on).
    // Transparency is checked on frame 0 only, not every frame - representative enough for what's ultimately
    // just a cosmetic frame/backing-border choice (see CrazyPhonePhotoFrameRenderer), not worth an O(frames *
    // pixels) scan for.
    private static CachedTexture registerAnimatedTexture(Key key, AnimatedPhotoCodec.Animation animation) {
        List<AnimatedPhotoCodec.RawFrame> frames = animation.frames();
        /*$ res_loc {*/ResourceLocation/*$}*/[] locations = new /*$ res_loc {*/ResourceLocation/*$}*/[frames.size()];
        int[] delaysMillis = new int[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            AnimatedPhotoCodec.RawFrame frame = frames.get(i);
            String name = "crazyphone-picture-" + key.resolution().name().toLowerCase(Locale.ROOT) + "-" + key.photoId() + "-f" + i;
            locations[i] = registerDynamicTexture(name, frame.image());
            delaysMillis[i] = frame.delayMillis();
        }
        boolean transparent = hasTransparency(frames.get(0).image());
        return CachedTexture.animated(locations, delaysMillis, animation.width(), animation.height(), transparent);
    }

    // Shared wrap-and-register step both registerTexture (one static photo) and registerAnimatedTexture (one
    // frame of many) delegate to - does NOT close image; DynamicTexture keeps it readable (see this class's
    // own deriveThumbnailFromFull, which already relied on that before animation existed) until whichever
    // GPU texture now owns it is eventually released.
    //
    // <1.21.10's setFilter(false, false) explicitly disables mipmapping - a freshly uploaded GL texture
    // defaults to a mip-sampling MIN_FILTER (GL_NEAREST_MIPMAP_LINEAR) even though only the base level ever
    // gets generated here, an incomplete-texture state vanilla's own batched renderer happens to tolerate
    // but a heavier third-party render pipeline might not. AbstractTexture has no equivalent method on
    // >=1.21.10 (needs its own real API investigation, not guessed) - left as a known gap there for now.
    private static /*$ res_loc {*/ResourceLocation/*$}*/ registerDynamicTexture(String name, NativeImage image) {
        //? if <1.21.10 {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        return Minecraft.getInstance().getTextureManager().register(name, texture);
        //? } else {
        /*// 1.21.10 changed DynamicTexture's constructor (now takes a name Supplier and uploads itself) and
        // TextureManager#register's key type (ResourceLocation, not a plain String) - Identifier paths need
        // the same lowercase [a-z0-9/._-] validation as everywhere else (a UUID's hex+hyphens already
        // satisfies this, so the id shape itself is unchanged, just wrapped through Crazyphone.resource()).
        net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ id = fr.lordfinn.crazyphone.Crazyphone.resource(name);
        DynamicTexture texture = new DynamicTexture(id::toString, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
        *///?}
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

    // Scanned once at decode time (not per-frame) so the photo frame renderer can cheaply branch on it every
    // draw call - a fully-opaque photo gets the solid brown border/backing treatment, one with any
    // transparent pixel skips it and floats just off the surface instead (see
    // CrazyPhonePhotoFrameRenderer's own doc comment for why).
    // Deliberately tolerant, not "any single pixel below 255" - a real in-game capture is confirmed always
    // fully opaque (Screenshot.takeScreenshot reads straight from the render target's color texture, and
    // vanilla's own blend funcs keep that alpha pinned at 255 for ordinary gameplay - investigated live, not
    // assumed), but the thumbnail/full downscale step (NativeImage#resizeSubRectTo, a real filtered resize
    // via STBImageResize, not the nearest-neighbor its own comment claims) can leave a handful of EDGE pixels
    // a shade under 255 from interpolation rounding alone - an earlier version treated ANY such pixel as
    // "this photo has transparency" and silently lost the floor/ceiling border on ordinary opaque photos.
    // Both a lower alpha bound and a minimum affected-pixel count are required before treating a photo as
    // genuinely transparent, so a few stray rounding pixels can't flip the whole render mode.
    private static final int TRANSPARENCY_ALPHA_THRESHOLD = 240;

    private static boolean hasTransparency(NativeImage image) {
        // NativeImage.read(InputStream) (both decode branches above) always forces Format.RGBA - confirmed
        // against the real decompiled NativeImage.java - so reading a packed pixel is always safe here, no
        // format check needed first. >=26 renamed getPixelRGBA(x,y) to getPixel(x,y) (still alpha in the top
        // byte of the packed int, via ARGB.fromABGR internally) - confirmed against the real decompiled
        // 26.1.2 NativeImage.java, not guessed.
        int totalPixels = image.getWidth() * image.getHeight();
        int minAffectedPixels = Math.max(16, totalPixels / 100);
        int affected = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                //? if <26 {
                int alpha = image.getPixelRGBA(x, y) >>> 24 & 0xFF;
                //? } else {
                /*int alpha = image.getPixel(x, y) >>> 24 & 0xFF;
                *///?}
                if (alpha < TRANSPARENCY_ALPHA_THRESHOLD && ++affected >= minAffectedPixels)
                    return true;
            }
        }
        return false;
    }

    private static Path cacheFile(Key key) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("crazyphone").resolve("photocache")
                .resolve(key.resolution().name().toLowerCase(Locale.ROOT) + "-" + key.photoId() + ".png");
    }

    /** Reads a photo's own raw disk-cached bytes synchronously - for the one case (the photo editor) that
     * needs real pixels to mutate, not just a GPU texture handle {@link #getOrRequest} hands back. Only
     * meaningful once the CORRESPONDING getOrRequest(photoId, resolution) call has already resolved (the
     * disk cache is only ever written right after a successful decode - see decodeAndRegister/registerAnimated),
     * which every caller of this is expected to have already checked; returns null otherwise rather than
     * blocking on a fetch of its own. A synchronous read on the render thread is an acceptable one-off cost
     * here (a local, already-on-disk file of at most a few MB, read once when the editor screen opens) -
     * unlike every other path through this class, which is careful to never block the render thread on I/O. */
    public static byte[] readCachedBytesBlocking(UUID photoId, PhotoResolution resolution) {
        Path file = cacheFile(new Key(photoId, resolution));
        if (!Files.isRegularFile(file))
            return null;
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            LOGGER.warn("Failed to read disk-cached {} photo {}", resolution, photoId, e);
            return null;
        }
    }
}
