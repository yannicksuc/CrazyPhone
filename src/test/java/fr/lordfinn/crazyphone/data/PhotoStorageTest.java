package fr.lordfinn.crazyphone.data;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.PhotoResolution;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Photo bytes live in their own files, the {@link PhotoSavedData} index stays small, and worlds saved with
 * the bytes inline are migrated without losing a photo.
 */
class PhotoStorageTest {

    @BeforeEach
    void setUp() {
        Config.maxPhotosStoredPerOwner = 5000;
    }

    private static PhotoSavedData load(CompoundTag tag) {
        //? if >=1.20.5 <1.21.10 {
        /*return PhotoSavedData.load(tag, null);
        *///? } else {
        return PhotoSavedData.load(tag);
        //?}
    }

    /** Random bytes behind a real PNG signature + IHDR size, like a captured photo. */
    private static byte[] fakePng(Random random, int size, int width, int height) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(header, 0, bytes, 0, header.length);
        for (int i = 0; i < 4; i++) {
            bytes[16 + i] = (byte) (width >>> (24 - 8 * i));
            bytes[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        return bytes;
    }

    /** Times a vanilla-style compressed write of the tag (what a world save does on the server thread). */
    private static long[] timeCompressedWrite(CompoundTag tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long start = System.nanoTime();
        NbtIo.writeCompressed(tag, out);
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new long[]{millis, out.size()};
    }

    @Test
    void manyPhotos_indexStaysSmall_saveIsFast_andPhotosSurviveARestart(@TempDir Path world) throws IOException {
        Random random = new Random(1);
        PhotoSavedData photos = new PhotoSavedData();
        photos.bindStorage(world);

        int count = 300;
        List<UUID> ids = new ArrayList<>();
        List<byte[]> fulls = new ArrayList<>();
        List<byte[]> thumbs = new ArrayList<>();
        CompoundTag oldFormat = new CompoundTag();
        for (int i = 0; i < count; i++) {
            byte[] full = fakePng(random, 160_000, 854, 480);
            byte[] thumb = fakePng(random, 12_000, 128, 72);
            UUID id = photos.storePhoto("1000" + (i % 12), "", null, thumb, full, 1000 + i);
            ids.add(id);
            fulls.add(full);
            thumbs.add(thumb);
            CompoundTag legacy = new CompoundTag();
            legacy.put("full", new ByteArrayTag(full));
            legacy.put("thumbnail", new ByteArrayTag(thumb));
            oldFormat.put(id.toString(), legacy);
        }
        photos.flushStorage();

        // Before: the same photos with the bytes inline, as crazyphone_photos.dat used to hold them.
        CompoundTag before = new CompoundTag();
        before.put("photos", oldFormat);
        long[] beforeWrite = timeCompressedWrite(before);
        long[] afterWrite = timeCompressedWrite(photos.writeNbt(new CompoundTag()));
        System.out.printf("[PhotoStorageTest] %d photos - save with bytes inline: %d ms, %d KB; index only: %d ms, %d KB%n",
                count, beforeWrite[0], beforeWrite[1] / 1024, afterWrite[0], afterWrite[1] / 1024);
        assertTrue(afterWrite[1] < 64 * 1024, "the index of " + count + " photos must stay tiny, was " + afterWrite[1] + " bytes");
        assertTrue(afterWrite[0] < 250, "saving the index must be fast, took " + afterWrite[0] + " ms");

        Path dir = world.resolve("data").resolve("crazyphone").resolve("photos");
        assertTrue(Files.exists(dir.resolve(ids.get(0) + ".png")));
        assertTrue(Files.exists(dir.resolve(ids.get(0) + ".thumb.png")));
        try (var listing = Files.list(dir)) {
            assertEquals(0, listing.filter(p -> p.toString().endsWith(".tmp")).count(), "no temp file may be left behind");
        }

        // "Restart": a fresh index loaded from the saved NBT, bound to the same world folder.
        PhotoSavedData reloaded = load(photos.writeNbt(new CompoundTag()));
        reloaded.bindStorage(world);
        assertFalse(reloaded.isDirty(), "loading an already-migrated index must not mark it dirty");
        for (int i = 0; i < count; i += 37) {
            UUID id = ids.get(i);
            assertArrayEquals(fulls.get(i), reloaded.readBytes(id, PhotoResolution.FULL));
            assertArrayEquals(thumbs.get(i), reloaded.readBytesAsync(id, PhotoResolution.THUMBNAIL).join());
            assertEquals(1000 + i, reloaded.getPhoto(id).createdMinutes());
        }
        CompoundTag entry = NbtCompat.getCompound(reloaded.photos, ids.get(0).toString());
        assertEquals(854, NbtCompat.getInt(entry, "width"));
        assertEquals(480, NbtCompat.getInt(entry, "height"));
        reloaded.flushStorage();
    }

    @Test
    void sameBytesForBothResolutions_storedOnce(@TempDir Path world) {
        PhotoSavedData photos = new PhotoSavedData();
        photos.bindStorage(world);
        byte[] bytes = fakePng(new Random(2), 5000, 64, 64);
        UUID id = photos.storePhoto("111", "", null, bytes, bytes, 1);
        photos.flushStorage();
        Path dir = world.resolve("data").resolve("crazyphone").resolve("photos");
        assertTrue(Files.exists(dir.resolve(id + ".png")));
        assertFalse(Files.exists(dir.resolve(id + ".thumb.png")));
        assertArrayEquals(bytes, photos.readBytes(id, PhotoResolution.THUMBNAIL));
    }

    @Test
    void deletingTheLastReference_removesTheFiles(@TempDir Path world) {
        PhotoSavedData photos = new PhotoSavedData();
        photos.bindStorage(world);
        Random random = new Random(3);
        UUID id = photos.storePhoto("111", "", null, fakePng(random, 500, 8, 8), fakePng(random, 5000, 64, 64), 1);
        photos.flushStorage();
        photos.deletePhotos("111", Set.of(id));
        photos.flushStorage();
        Path dir = world.resolve("data").resolve("crazyphone").resolve("photos");
        assertFalse(Files.exists(dir.resolve(id + ".png")));
        assertFalse(Files.exists(dir.resolve(id + ".thumb.png")));
        assertNull(photos.getPhoto(id));
        assertNull(photos.readBytes(id, PhotoResolution.FULL));
    }

    @Test
    void indexIsOnlyDirtiedByRealChanges(@TempDir Path world) {
        PhotoSavedData photos = new PhotoSavedData();
        photos.bindStorage(world);
        byte[] bytes = fakePng(new Random(4), 5000, 64, 64);
        UUID id = photos.storePhoto("111", "", null, bytes, bytes, 1);
        photos.setDirty(false);

        photos.linkPhotoToOwner("111", id); // already linked
        photos.markPhysical(id);
        assertTrue(photos.isDirty());
        photos.setDirty(false);
        photos.markPhysical(id); // already physical
        photos.storePhoto("111", "", null, bytes, bytes, 2); // byte-identical duplicate
        photos.deletePhotos("222", Set.of(id)); // not this owner's
        photos.readBytes(id, PhotoResolution.FULL);
        photos.getPhotoIdsForOwner("111");
        assertFalse(photos.isDirty(), "reads, duplicates and no-op calls must never dirty the index");
        photos.flushStorage();
    }

    /** Builds a world whose crazyphone_photos.dat still holds every photo's bytes inline. */
    private static CompoundTag writeOldFormatWorld(Path world, List<UUID> ids, List<byte[]> fulls, List<byte[]> thumbs) throws IOException {
        Random random = new Random(5);
        CompoundTag photosTag = new CompoundTag();
        ListTag ownerList = new ListTag();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            byte[] full = fakePng(random, 40_000 + i, 320, 180);
            // Photo 0 is a bug-report-style photo stored with a single resolution (no "thumbnail" tag).
            byte[] thumb = i == 0 ? null : fakePng(random, 3_000 + i, 64, 36);
            CompoundTag entry = new CompoundTag();
            entry.putString("owner", "5551234");
            entry.putString("conversationId", i % 2 == 0 ? "" : "5551234.5559999");
            entry.putInt("created", 500 + i);
            entry.putString("hash", "h" + i);
            if (i == 3)
                entry.putBoolean("physical", true);
            if (thumb != null)
                entry.put("thumbnail", new ByteArrayTag(thumb));
            entry.put("full", new ByteArrayTag(full));
            photosTag.put(id.toString(), entry);
            ownerList.add(StringTag.valueOf(id.toString()));
            ids.add(id);
            fulls.add(full);
            thumbs.add(thumb);
        }
        CompoundTag photosByOwner = new CompoundTag();
        photosByOwner.put("5551234", ownerList);
        CompoundTag root = new CompoundTag();
        root.put("photos", photosTag);
        root.put("photosByOwner", photosByOwner);
        root.putInt("legacyPhotosMigrationVersion", 2);

        CompoundTag file = new CompoundTag();
        file.put("data", root);
        Path dataDir = Files.createDirectories(world.resolve("data"));
        try (OutputStream out = Files.newOutputStream(dataDir.resolve("crazyphone_photos.dat"))) {
            NbtIo.writeCompressed(file, out);
        }
        return root;
    }

    @Test
    void migration_movesInlineBytesToFiles_keepsABackup_andShrinksTheIndex(@TempDir Path world) throws IOException {
        List<UUID> ids = new ArrayList<>();
        List<byte[]> fulls = new ArrayList<>();
        List<byte[]> thumbs = new ArrayList<>();
        CompoundTag oldTag = writeOldFormatWorld(world, ids, fulls, thumbs);
        Path dat = world.resolve("data").resolve("crazyphone_photos.dat");
        Path backup = world.resolve("data").resolve("crazyphone_photos.dat" + PhotoSavedData.MIGRATION_BACKUP_SUFFIX);
        byte[] originalDat = Files.readAllBytes(dat);
        long[] oldWrite = timeCompressedWrite(oldTag.copy());

        PhotoSavedData photos = load(oldTag);
        photos.bindStorage(world);

        assertTrue(Files.exists(backup), "the old file must be backed up before anything is changed");
        assertArrayEquals(originalDat, Files.readAllBytes(backup));
        assertTrue(photos.isDirty(), "the shrunk index must be written at the next save");
        Path dir = world.resolve("data").resolve("crazyphone").resolve("photos");
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            CompoundTag entry = NbtCompat.getCompound(photos.photos, id.toString());
            assertFalse(NbtCompat.contains(entry, "full"), "no pixel data may stay in the index");
            assertFalse(NbtCompat.contains(entry, "thumbnail"));
            assertArrayEquals(fulls.get(i), Files.readAllBytes(dir.resolve(id + ".png")));
            assertArrayEquals(fulls.get(i), photos.readBytes(id, PhotoResolution.FULL));
            assertArrayEquals(thumbs.get(i) != null ? thumbs.get(i) : fulls.get(i), photos.readBytes(id, PhotoResolution.THUMBNAIL));
            assertEquals(500 + i, photos.getPhoto(id).createdMinutes());
            assertEquals("5551234", photos.getPhoto(id).owner());
        }
        assertFalse(Files.exists(dir.resolve(ids.get(0) + ".thumb.png")));
        assertTrue(NbtCompat.getBoolean(NbtCompat.getCompound(photos.photos, ids.get(3).toString()), "physical"), "other index fields must survive");
        assertEquals(ids.size(), photos.getPhotoIdsForOwner("5551234").size());
        long[] newWrite = timeCompressedWrite(photos.writeNbt(new CompoundTag()));
        System.out.printf("[PhotoStorageTest] migration of %d photos - save before: %d KB, after: %d KB%n",
                ids.size(), oldWrite[1] / 1024, newWrite[1] / 1024);
        assertTrue(newWrite[1] * 20 < oldWrite[1], "the index must be much smaller than the old file");

        // Crash before the next save: the old .dat is still there (pretend it changed since), the
        // migration runs again on the next boot - files rewritten, original backup left untouched.
        Files.write(dat, new byte[]{1, 2, 3});
        PhotoSavedData again = load(writeOldFormatWorldTagCopy(oldTag, fulls, thumbs, ids));
        again.bindStorage(world);
        assertArrayEquals(originalDat, Files.readAllBytes(backup), "an existing backup must never be overwritten");
        for (int i = 0; i < ids.size(); i++)
            assertArrayEquals(fulls.get(i), again.readBytes(ids.get(i), PhotoResolution.FULL));
        photos.flushStorage();
        again.flushStorage();
    }

    /** The old tag was mutated in place by the first migration - rebuild an inline copy of it. */
    private static CompoundTag writeOldFormatWorldTagCopy(CompoundTag migrated, List<byte[]> fulls, List<byte[]> thumbs, List<UUID> ids) {
        CompoundTag copy = migrated.copy();
        CompoundTag photosTag = NbtCompat.getCompound(copy, "photos");
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag entry = NbtCompat.getCompound(photosTag, ids.get(i).toString());
            entry.put("full", new ByteArrayTag(fulls.get(i)));
            if (thumbs.get(i) != null)
                entry.put("thumbnail", new ByteArrayTag(thumbs.get(i)));
        }
        return copy;
    }

    @Test
    void migration_withoutAnOldFileToBackUp_leavesTheBytesInline(@TempDir Path world) {
        CompoundTag entry = new CompoundTag();
        entry.putString("owner", "111");
        entry.putString("conversationId", "");
        byte[] full = {9, 8, 7};
        entry.put("full", new ByteArrayTag(full));
        CompoundTag photosTag = new CompoundTag();
        UUID id = UUID.randomUUID();
        photosTag.put(id.toString(), entry);
        CompoundTag root = new CompoundTag();
        root.put("photos", photosTag);

        PhotoSavedData photos = load(root);
        photos.bindStorage(world);

        assertFalse(photos.isDirty());
        assertTrue(NbtCompat.contains(NbtCompat.getCompound(photos.photos, id.toString()), "full"), "no backup, no migration");
        assertArrayEquals(full, photos.readBytes(id, PhotoResolution.FULL), "inline photos must still be served");
        assertArrayEquals(full, photos.readBytesAsync(id, PhotoResolution.THUMBNAIL).join());
        photos.flushStorage();
    }
}
