package fr.lordfinn.crazyphone.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the actual data-loss-prevention logic: a pre-{@link PhotoSavedData} conversation photo (bytes
 * embedded directly in {@link ConversationSavedData#imageBytes}, the mod's own original native camera
 * pipeline) must reappear under {@link PhotoSavedData} after {@link LegacyPhotoMigration#migrate} runs,
 * exactly as if it had always been sent through the current pipeline.
 * <p>
 * Only the native-format recovery path is covered here - the Camera-mod-file-on-disk fallback needs a real
 * world save directory to read from and is exercised by the dev-launch smoke test instead (see this
 * project's own session notes), not a fast unit test. The mocked {@link MinecraftServer#getWorldPath} below
 * still needs to return SOME real, existing (but photo-less) directory rather than null/a bogus path -
 * {@link LegacyPhotoMigration}'s own file-lookup does a plain {@code Files.exists} check that would throw
 * on a null Path, not gracefully return "not found".
 */
class LegacyPhotoMigrationTest {

    private static final String CONVO = "111.222";

    private static CompoundTag imageMessage(UUID imageId, String owner, int timecode) {
        CompoundTag imageTag = new CompoundTag();
        imageTag.putLong("image_id_most", imageId.getMostSignificantBits());
        imageTag.putLong("image_id_least", imageId.getLeastSignificantBits());
        imageTag.putString("owner", owner);
        CompoundTag message = new CompoundTag();
        message.putString("sender", owner);
        message.putString("value", "");
        message.putInt("timecode", timecode);
        message.put("image", imageTag);
        return message;
    }

    /** A message from before even the "owner"-carrying image tag existed - just an id pointer, no
     * recoverable bytes anywhere. Must be skipped, not crash. */
    private static CompoundTag imageMessageMissingIdField(String owner, int timecode) {
        CompoundTag message = new CompoundTag();
        message.putString("sender", owner);
        message.putString("value", "");
        message.putInt("timecode", timecode);
        message.put("image", new CompoundTag());
        return message;
    }

    private MinecraftServer mockServerWithEmptyWorldFolder(Path tempDir) {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel overworld = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(overworld);
        when(server.getWorldPath(any(LevelResource.class))).thenReturn(tempDir);
        return server;
    }

    @Test
    void migrate_recoversNativeImageBytesIntoPhotoSavedData(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        MinecraftServer server = mockServerWithEmptyWorldFolder(tempDir);
        // CALLS_REAL_METHODS as the default answer: mockStatic intercepts EVERY static call to the class
        // from here on, not just the one(s) explicitly stubbed below - without this, PhotoSavedData's own
        // internal static helper (sha256Hex, called from the very real, unmocked storePhoto instance method
        // migrate() ends up calling) would silently return null instead of actually hashing anything.
        try (var mocked = mockStatic(ConversationSavedData.class, CALLS_REAL_METHODS);
             var mockedPhotos = mockStatic(PhotoSavedData.class, CALLS_REAL_METHODS)) {

            ConversationSavedData conversations = new ConversationSavedData();
            UUID imageId = UUID.randomUUID();
            byte[] pngBytes = {1, 2, 3, 4, 5};
            conversations.storeImageBytes(imageId, CONVO, pngBytes);
            conversations.appendMessage(CONVO, imageMessage(imageId, "111", 42));

            PhotoSavedData photos = new PhotoSavedData();
            mocked.when(() -> ConversationSavedData.get(any())).thenReturn(conversations);
            mockedPhotos.when(() -> PhotoSavedData.get(any())).thenReturn(photos);

            LegacyPhotoMigration.migrate(server);

            PhotoSavedData.PhotoEntry entry = photos.getPhoto(imageId);
            assertNotNull(entry, "the native photo's bytes must have been backfilled under its message's own id");
            assertArrayEquals(pngBytes, entry.full());
            assertEquals("111", entry.owner());
            assertEquals(CONVO, entry.conversationId());
            assertEquals(42, entry.createdMinutes());
            assertTrue(photos.getPhotoIdsForOwner("111").contains(imageId),
                    "the sender's own My Photos gallery must list the recovered photo too");
            assertEquals(LegacyPhotoMigration.CURRENT_VERSION, photos.legacyPhotosMigrationVersion, "must record that this world has been scanned");
        }
    }

    @Test
    void migrate_secondCallIsANoOp_doesNotRescanOrDuplicate(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        MinecraftServer server = mockServerWithEmptyWorldFolder(tempDir);
        // CALLS_REAL_METHODS as the default answer: mockStatic intercepts EVERY static call to the class
        // from here on, not just the one(s) explicitly stubbed below - without this, PhotoSavedData's own
        // internal static helper (sha256Hex, called from the very real, unmocked storePhoto instance method
        // migrate() ends up calling) would silently return null instead of actually hashing anything.
        try (var mocked = mockStatic(ConversationSavedData.class, CALLS_REAL_METHODS);
             var mockedPhotos = mockStatic(PhotoSavedData.class, CALLS_REAL_METHODS)) {

            ConversationSavedData conversations = new ConversationSavedData();
            UUID imageId = UUID.randomUUID();
            conversations.storeImageBytes(imageId, CONVO, new byte[]{9});
            conversations.appendMessage(CONVO, imageMessage(imageId, "111", 0));

            PhotoSavedData photos = new PhotoSavedData();
            mocked.when(() -> ConversationSavedData.get(any())).thenReturn(conversations);
            mockedPhotos.when(() -> PhotoSavedData.get(any())).thenReturn(photos);

            LegacyPhotoMigration.migrate(server);
            assertEquals(LegacyPhotoMigration.CURRENT_VERSION, photos.legacyPhotosMigrationVersion);

            // Simulate a fresh boot re-running the same hook - already-migrated data must be left alone
            // (deleting the recovered entry here would make a second call's no-op behavior visibly wrong).
            photos.deletePhotos("111", java.util.Set.of(imageId));
            LegacyPhotoMigration.migrate(server);

            assertNull(photos.getPhoto(imageId), "a second migrate() call must not re-scan and re-recover what was already handled once");
        }
    }

    @Test
    void migrate_messageWithNoRecoverableBytesAnywhere_isSkippedNotThrown(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        MinecraftServer server = mockServerWithEmptyWorldFolder(tempDir);
        // CALLS_REAL_METHODS as the default answer: mockStatic intercepts EVERY static call to the class
        // from here on, not just the one(s) explicitly stubbed below - without this, PhotoSavedData's own
        // internal static helper (sha256Hex, called from the very real, unmocked storePhoto instance method
        // migrate() ends up calling) would silently return null instead of actually hashing anything.
        try (var mocked = mockStatic(ConversationSavedData.class, CALLS_REAL_METHODS);
             var mockedPhotos = mockStatic(PhotoSavedData.class, CALLS_REAL_METHODS)) {

            ConversationSavedData conversations = new ConversationSavedData();
            // No storeImageBytes call at all, and no matching file in tempDir either - genuinely
            // unrecoverable, same as an old Camera-mod-shared image whose mod is no longer installed.
            conversations.appendMessage(CONVO, imageMessage(UUID.randomUUID(), "111", 0));

            PhotoSavedData photos = new PhotoSavedData();
            mocked.when(() -> ConversationSavedData.get(any())).thenReturn(conversations);
            mockedPhotos.when(() -> PhotoSavedData.get(any())).thenReturn(photos);

            assertDoesNotThrow(() -> LegacyPhotoMigration.migrate(server));
            assertEquals(LegacyPhotoMigration.CURRENT_VERSION, photos.legacyPhotosMigrationVersion);
        }
    }

    @Test
    void migrate_messageMissingImageIdField_isSkippedNotThrown(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        MinecraftServer server = mockServerWithEmptyWorldFolder(tempDir);
        // CALLS_REAL_METHODS as the default answer: mockStatic intercepts EVERY static call to the class
        // from here on, not just the one(s) explicitly stubbed below - without this, PhotoSavedData's own
        // internal static helper (sha256Hex, called from the very real, unmocked storePhoto instance method
        // migrate() ends up calling) would silently return null instead of actually hashing anything.
        try (var mocked = mockStatic(ConversationSavedData.class, CALLS_REAL_METHODS);
             var mockedPhotos = mockStatic(PhotoSavedData.class, CALLS_REAL_METHODS)) {

            ConversationSavedData conversations = new ConversationSavedData();
            conversations.appendMessage(CONVO, imageMessageMissingIdField("111", 0));

            PhotoSavedData photos = new PhotoSavedData();
            mocked.when(() -> ConversationSavedData.get(any())).thenReturn(conversations);
            mockedPhotos.when(() -> PhotoSavedData.get(any())).thenReturn(photos);

            assertDoesNotThrow(() -> LegacyPhotoMigration.migrate(server));
        }
    }
}
