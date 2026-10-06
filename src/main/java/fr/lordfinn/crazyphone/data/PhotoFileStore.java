package fr.lordfinn.crazyphone.data;

import fr.lordfinn.crazyphone.utils.PhotoResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * The pixel side of {@link PhotoSavedData}: one file per photo resolution under
 * {@code <world>/data/crazyphone/photos/}, so a world save only ever rewrites the small index, never the
 * images themselves. Before this, every photo lived inside {@code crazyphone_photos.dat} and each autosave
 * re-deflated the whole file on the server thread (tens of MB, multi-second freezes).
 * <p>
 * Files: {@code <id>.png} (full resolution) and {@code <id>.thumb.png} (only when a distinct thumbnail was
 * uploaded). The bytes are stored exactly as received - almost always PNG, but a photo recovered from the
 * Camera mod may be a JPEG under the same name. Writes go through a temp file then a move, so a crash never
 * leaves a half-written image behind.
 * <p>
 * Threading: one single background thread does every write, delete and disk read, in submission order, so
 * a delete queued after a write always wins. Bytes not yet on disk stay in {@link #pending} (readable at
 * once), and recently used bytes sit in a small LRU cache. A store built with no directory keeps everything
 * in memory (unit tests without a world folder).
 */
public final class PhotoFileStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    /** Every store with a live IO thread - {@link #closeAll()} flushes them when the server stops. */
    private static final Set<PhotoFileStore> OPEN = ConcurrentHashMap.newKeySet();
    private static final long DEFAULT_CACHE_BYTES = 24L * 1024 * 1024;

    private final @Nullable Path dir;
    private final @Nullable ExecutorService io;
    /** file name -> bytes queued for writing (or whose write failed and will be retried by flush). */
    private final Map<String, byte[]> pending = new ConcurrentHashMap<>();
    private final ByteLru cache;

    private PhotoFileStore(@Nullable Path dir, long cacheBytes) {
        this.dir = dir;
        this.cache = new ByteLru(cacheBytes);
        if (dir == null) {
            this.io = null;
        } else {
            this.io = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "CrazyPhone photo IO");
                t.setDaemon(true);
                return t;
            });
            OPEN.add(this);
        }
    }

    /** A disk-backed store rooted at {@code dir} (created on first write). */
    public static PhotoFileStore onDisk(Path dir) {
        return new PhotoFileStore(dir, DEFAULT_CACHE_BYTES);
    }

    /** A memory-only store - nothing ever touches the disk. */
    public static PhotoFileStore inMemory() {
        return new PhotoFileStore(null, Long.MAX_VALUE);
    }

    public boolean isOnDisk() {
        return dir != null;
    }

    public @Nullable Path directory() {
        return dir;
    }

    static String fileName(UUID id, PhotoResolution resolution) {
        return resolution == PhotoResolution.THUMBNAIL ? id + ".thumb.png" : id + ".png";
    }

    /** Queues both resolutions for writing; {@code thumbnail} null means "same bytes as full", stored once.
     * Returns immediately - the bytes are readable at once from memory until the write lands. */
    public void write(UUID id, @Nullable byte[] thumbnail, byte[] full) {
        enqueueWrite(fileName(id, PhotoResolution.FULL), full);
        if (thumbnail != null)
            enqueueWrite(fileName(id, PhotoResolution.THUMBNAIL), thumbnail);
    }

    private void enqueueWrite(String name, byte[] bytes) {
        pending.put(name, bytes);
        cache.put(name, bytes);
        if (io == null)
            return;
        io.execute(() -> {
            if (pending.get(name) != bytes)
                return; // superseded or deleted since this task was queued
            try {
                writeAtomically(name, bytes);
                pending.remove(name, bytes);
            } catch (IOException e) {
                LOGGER.error("Could not write photo file {} - kept in memory, retried when the server stops", name, e);
            }
        });
    }

    /** Synchronous write on the calling thread, for the one-time migration: returns only once the file is
     * on disk and its size checked, so the caller may then drop its own copy of the bytes. */
    public void writeNow(UUID id, @Nullable byte[] thumbnail, byte[] full) throws IOException {
        if (dir == null) {
            write(id, thumbnail, full);
            return;
        }
        writeAtomically(fileName(id, PhotoResolution.FULL), full);
        if (thumbnail != null)
            writeAtomically(fileName(id, PhotoResolution.THUMBNAIL), thumbnail);
    }

    private void writeAtomically(String name, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        Path tmp = dir.resolve(name + ".tmp");
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.size(target) != bytes.length)
            throw new IOException("Size mismatch after writing " + target);
    }

    /** Forgets a photo's files (both resolutions). Queued behind any pending write of the same photo. */
    public void delete(UUID id) {
        for (PhotoResolution resolution : PhotoResolution.values()) {
            String name = fileName(id, resolution);
            pending.remove(name);
            cache.remove(name);
            if (io != null) {
                io.execute(() -> {
                    try {
                        Files.deleteIfExists(dir.resolve(name));
                    } catch (IOException e) {
                        LOGGER.warn("Could not delete photo file {}", name, e);
                    }
                });
            }
        }
    }

    /** Bytes already in memory (pending write or cached), or null if a disk read would be needed. */
    private @Nullable byte[] inMemory(String name) {
        byte[] bytes = pending.get(name);
        return bytes != null ? bytes : cache.get(name);
    }

    /** Blocking read on the calling thread. {@code hasThumbnail} false reads the full file for a THUMBNAIL
     * request. Null if the file doesn't exist. */
    public @Nullable byte[] read(UUID id, PhotoResolution resolution, boolean hasThumbnail) {
        String name = fileName(id, hasThumbnail ? resolution : PhotoResolution.FULL);
        byte[] bytes = inMemory(name);
        if (bytes != null || dir == null)
            return bytes;
        return readFromDisk(name);
    }

    /** Same as {@link #read} but the disk access, if any, happens on the IO thread. */
    public CompletableFuture<byte[]> readAsync(UUID id, PhotoResolution resolution, boolean hasThumbnail) {
        String name = fileName(id, hasThumbnail ? resolution : PhotoResolution.FULL);
        byte[] bytes = inMemory(name);
        if (bytes != null || io == null)
            return CompletableFuture.completedFuture(bytes);
        return CompletableFuture.supplyAsync(() -> {
            byte[] again = inMemory(name);
            return again != null ? again : readFromDisk(name);
        }, io);
    }

    private @Nullable byte[] readFromDisk(String name) {
        try {
            byte[] bytes = Files.readAllBytes(dir.resolve(name));
            cache.put(name, bytes);
            return bytes;
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            LOGGER.warn("Could not read photo file {}", name, e);
            return null;
        }
    }

    /** Waits for every queued task, then retries any write that failed. Safe to call from any thread
     * except the IO thread itself. */
    public void flush() {
        if (io == null)
            return;
        try {
            io.submit(() -> { }).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Timed out waiting for photo writes to finish", e);
        }
        for (Map.Entry<String, byte[]> entry : pending.entrySet()) {
            try {
                writeAtomically(entry.getKey(), entry.getValue());
                pending.remove(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                LOGGER.error("Photo file {} could not be written and is lost on shutdown", entry.getKey(), e);
            }
        }
    }

    public void close() {
        flush();
        if (io != null)
            io.shutdown();
        OPEN.remove(this);
    }

    /** Flushes and stops every disk-backed store - called when the server stops. */
    public static void closeAll() {
        for (PhotoFileStore store : OPEN)
            store.close();
    }

    /** Access-ordered LRU bounded by total byte size. */
    private static final class ByteLru {
        private final long maxBytes;
        private final LinkedHashMap<String, byte[]> map = new LinkedHashMap<>(64, 0.75f, true);
        private long size;

        ByteLru(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        synchronized @Nullable byte[] get(String key) {
            return map.get(key);
        }

        synchronized void put(String key, byte[] value) {
            if (value.length > maxBytes)
                return;
            byte[] old = map.put(key, value);
            if (old != null)
                size -= old.length;
            size += value.length;
            Iterator<byte[]> it = map.values().iterator();
            while (size > maxBytes && it.hasNext()) {
                size -= it.next().length;
                it.remove();
            }
        }

        synchronized void remove(String key) {
            byte[] old = map.remove(key);
            if (old != null)
                size -= old.length;
        }
    }
}
