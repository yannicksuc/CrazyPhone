package fr.lordfinn.crazyphone.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import fr.lordfinn.crazyphone.utils.NbtCompat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Runs once per world (see {@link PhotoSavedData#legacyPhotosMigrated}), right after
 * {@link OrphanedCallCleanup}: recovers every conversation photo that predates {@link PhotoSavedData}
 * itself (pre-1.2.0-ish saves, back when a photo message's bytes either lived inline in
 * {@link ConversationSavedData#imageBytes} - the mod's own original native camera, both loaders - or not in
 * this mod's data at all, when the photo was shared out of the "Camera" mod's (de.maxhenkel.camera) own
 * Album/Image items instead).
 * <p>
 * A message's own "image" tag (image_id_most/image_id_least/owner) never changed shape across any of this -
 * see {@link fr.lordfinn.crazyphone.utils.CrazyPhoneHelper}'s own addImageMessage/getMessageFromTag - so the
 * conversation history itself needs no rewriting at all, only {@link PhotoSavedData} needs the actual bytes
 * backfilled under the same id the message already points at.
 * Once that's done, the message displays exactly like it always did, with no visible difference to the
 * player: same conversation, same "My Photos" gallery entry for whoever sent it.
 * <p>
 * Camera mod recovery needs no compile-time dependency on it at all: its own {@code ImageTools.saveImage}
 * always wrote to {@code <world>/camera_images/<uuid>.jpg} (falling back to a legacy {@code .png} for saves
 * from even before that), both plain vanilla {@link LevelResource}-resolved paths - reading the raw file
 * bytes needs nothing from the mod's own jar, whether or not it's even still installed right now. If it
 * isn't (or the file's simply gone), that one photo stays exactly as unrecoverable as the current code
 * already treats it - see getMessageFromTag's own doc comment on the imageless-text-bubble fallback.
 */
public final class LegacyPhotoMigration {
    private LegacyPhotoMigration() {
    }

    public static void migrate(MinecraftServer server) {
        PhotoSavedData photos = PhotoSavedData.get(server.overworld());
        if (photos.legacyPhotosMigrated)
            return;

        ConversationSavedData conversations = ConversationSavedData.get(server.overworld());
        for (String conversationId : NbtCompat.keySet(conversations.conversations)) {
            if (!(conversations.conversations.get(conversationId) instanceof ListTag messages))
                continue;
            for (int i = 0; i < messages.size(); i++) {
                CompoundTag message = NbtCompat.getCompound(messages, i);
                migrateMessage(server, photos, conversations, conversationId, message);
            }
        }

        // Set unconditionally, even when nothing needed recovering - a world with nothing left to migrate
        // (or that never had anything to begin with) should never pay this scan's cost again either.
        photos.legacyPhotosMigrated = true;
        photos.setDirty();
    }

    private static void migrateMessage(MinecraftServer server, PhotoSavedData photos, ConversationSavedData conversations, String conversationId, CompoundTag message) {
        if (!(message.get("image") instanceof CompoundTag imageTag))
            return;
        if (!NbtCompat.contains(imageTag, "image_id_most"))
            return;
        UUID imageId = new UUID(NbtCompat.getLong(imageTag, "image_id_most"), NbtCompat.getLong(imageTag, "image_id_least"));
        // Already backfilled by an earlier boot, or genuinely sent through the current PhotoSavedData-based
        // pipeline to begin with - nothing to recover either way.
        if (photos.getPhoto(imageId) != null)
            return;

        byte[] bytes = recoverBytes(server, conversations, imageId);
        if (bytes == null)
            return;

        String owner = NbtCompat.getString(imageTag, "owner", NbtCompat.getString(message, "sender"));
        int createdMinutes = NbtCompat.getInt(message, "timecode");
        // Same bytes for both resolutions - the old formats never had a separate low-res thumbnail either,
        // same fallback storePhoto's own newer callers already use when there's nothing smaller to offer.
        photos.storePhoto(owner, conversationId, imageId, bytes, bytes, createdMinutes);
    }

    private static byte[] recoverBytes(MinecraftServer server, ConversationSavedData conversations, UUID imageId) {
        ConversationSavedData.ImageBytesEntry nativeEntry = conversations.getImageBytes(imageId);
        if (nativeEntry != null)
            return nativeEntry.bytes();
        return readCameraModFile(server, imageId);
    }

    private static byte[] readCameraModFile(MinecraftServer server, UUID imageId) {
        // LevelResource's own (String) constructor is private on vanilla/Fabric mappings - only NeoForge/
        // Forge access-transform it open - so this resolves the exact same path Camera mod's own
        // ImageTools.CAMERA_IMAGES constant would (a root-relative "camera_images" directory) starting from
        // the one guaranteed-public constant every mapping set exposes instead of minting a custom resource.
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("camera_images");
        for (String extension : new String[]{".jpg", ".png"}) {
            Path file = folder.resolve(imageId + extension);
            if (!Files.exists(file))
                continue;
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }
}
