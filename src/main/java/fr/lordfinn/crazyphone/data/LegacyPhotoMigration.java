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
 * Runs once per world (see {@link PhotoSavedData#legacyPhotosMigrationVersion}), right after
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

    /** Bump this whenever a fix to the migration logic itself needs to re-run against a world that already
     * completed an earlier version - a plain boolean can't express that; a version number can. History:
     * 1 - initial recovery (native imageBytes + Camera-mod file fallback). 2 - fixed photosByOwner's key:
     * an image message's OWN "sender" field is always a real phone number, but the image sub-tag's own
     * "owner" field isn't - it's a bare Minecraft username for a photo that came from Camera mod's own
     * ImageData, so trusting it silently filed a recovered photo under a key "My Photos" (queried by phone
     * number) could never match, even though the conversation message itself displayed the photo correctly
     * either way (it only ever looks a photo up by id, never by owner). Confirmed live: photos reappeared
     * in conversations but not in My Photos until this fix. */
    public static final int CURRENT_VERSION = 2;

    public static void migrate(MinecraftServer server) {
        PhotoSavedData photos = PhotoSavedData.get(server.overworld());
        if (photos.legacyPhotosMigrationVersion >= CURRENT_VERSION)
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
        photos.legacyPhotosMigrationVersion = CURRENT_VERSION;
        photos.setDirty();
    }

    private static void migrateMessage(MinecraftServer server, PhotoSavedData photos, ConversationSavedData conversations, String conversationId, CompoundTag message) {
        if (!(message.get("image") instanceof CompoundTag imageTag))
            return;
        if (!NbtCompat.contains(imageTag, "image_id_most"))
            return;
        UUID imageId = new UUID(NbtCompat.getLong(imageTag, "image_id_most"), NbtCompat.getLong(imageTag, "image_id_least"));
        // The message's OWN "sender" field, never the image sub-tag's "owner" one - see CURRENT_VERSION's
        // own doc comment on why the latter is unreliable (a bare Minecraft username for a Camera-mod-
        // shared photo, not a phone number).
        String owner = NbtCompat.getString(message, "sender");

        if (photos.getPhoto(imageId) == null) {
            // Never recovered before - the expensive path (file I/O for the Camera-mod fallback).
            byte[] bytes = recoverBytes(server, conversations, imageId);
            if (bytes == null)
                return;
            int createdMinutes = NbtCompat.getInt(message, "timecode");
            // Same bytes for both resolutions - the old formats never had a separate low-res thumbnail
            // either, same fallback storePhoto's own newer callers already use when there's nothing
            // smaller to offer.
            photos.storePhoto(owner, conversationId, imageId, bytes, bytes, createdMinutes);
        } else {
            // Already recovered - by this exact pass moments ago (the same photo shared into more than one
            // conversation message), or by an earlier, lower-CURRENT_VERSION pass that got the owner wrong
            // (see CURRENT_VERSION's own history). linkPhotoToOwner is a no-op if already linked, so this
            // is always safe to call, not just on a genuine repair.
            photos.linkPhotoToOwner(owner, imageId);
        }
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

    // 1.1.0-beta.1's own "albums in my phone" feature (CrazyPhonePicturesScreenMenu): the phone item's own
    // attached IItemHandler (see CrazyPhoneHelper#getPhoneItemHandler) could directly hold a Camera-mod
    // Album (or loose Image) item, entirely separate from ever sending it in a conversation - a photo taken
    // but never shared has no message anywhere for migrate()'s own conversation scan to find at all.
    // Confirmed live: a player's actual playerdata .dat file still has real camera:album/camera:image item
    // ids in it after upgrading, sitting inside their crazy_phone item's own capability data, which survives
    // a mod update untouched since it's just NBT no different from any other item's.
    //
    // NeoForge-only, matching ForeignPhotoMods' own scope. Runs once per login (see PhoneAttachmentTypes'
    // own onPlayerLoggedIn) rather than once per world boot like migrate() above, since it needs one
    // specific player's own inventory, which doesn't exist yet at server start. Not flag-guarded anywhere:
    // every step here is already idempotent on its own (storePhoto dedups by content hash, linkPhotoToOwner
    // is a no-op once already linked), so a cheap re-scan on every future login only costs however many
    // phones/albums THIS ONE player happens to carry, never the whole server's history the way migrate()
    // has to guard against.
    //? if neoforge {
    public static void importPhoneAlbumsOnLogin(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof fr.lordfinn.crazyphone.item.CrazyPhoneItem))
                continue;
            net.neoforged.neoforge.items.IItemHandlerModifiable handler = fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.getPhoneItemHandler(stack);
            if (handler == null)
                continue;
            String owner = fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberProcedure.execute(stack, player.level());
            if (!owner.isEmpty())
                importPhoneSlots(player.getServer(), handler, owner);
        }
    }

    private static void importPhoneSlots(MinecraftServer server, net.neoforged.neoforge.items.IItemHandlerModifiable handler, String owner) {
        PhotoSavedData photos = null;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack sub = handler.getStackInSlot(slot);
            if (sub.isEmpty())
                continue;

            java.util.List<fr.lordfinn.crazyphone.utils.ForeignPhotoMods.ForeignPhoto> found;
            if (fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignAlbumItem(sub)) {
                found = fr.lordfinn.crazyphone.utils.ForeignPhotoMods.readAlbum(server, sub);
            } else if (fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignPhotoItem(sub)) {
                fr.lordfinn.crazyphone.utils.ForeignPhotoMods.ForeignPhoto single = fr.lordfinn.crazyphone.utils.ForeignPhotoMods.readSingle(server, sub);
                found = single == null ? java.util.List.of() : java.util.List.of(single);
            } else {
                continue;
            }
            if (found.isEmpty())
                continue;

            // Fetched lazily (only once something worth storing was actually found) - most phones carry no
            // legacy album at all, and PhotoSavedData.get() isn't free.
            if (photos == null)
                photos = PhotoSavedData.get(server.overworld());
            for (fr.lordfinn.crazyphone.utils.ForeignPhotoMods.ForeignPhoto photo : found) {
                if (photos.getPhoto(photo.id()) == null)
                    photos.storePhoto(owner, owner, photo.id(), photo.bytes(), photo.bytes(), photo.createdMinutes());
                else
                    photos.linkPhotoToOwner(owner, photo.id());
            }
        }
    }
    //?}
}
