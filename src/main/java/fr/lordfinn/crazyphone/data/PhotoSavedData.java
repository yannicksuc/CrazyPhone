package fr.lordfinn.crazyphone.data;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
//? if <1.20.5 {
import net.minecraft.util.datafix.DataFixTypes;
//?}
//? if >=1.21.10 {
/*import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?}
//? if >=26 {
/*import fr.lordfinn.crazyphone.Crazyphone;
*///?}
//? if fabric && >=1.20.5 <1.21.10 {
/*import net.minecraft.util.datafix.DataFixTypes;
*///?}

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.utils.NbtCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Global photo store, shared by every conversation and every physical Photo item - deliberately NOT scoped
 * to a single conversation the way {@link ConversationSavedData#imageBytes} used to be, since a photo can
 * outlive the message it was first sent in (a Photo item copy can sit in an inventory long after the chat
 * history around it has trimmed away). Capped per OWNING phone number instead: same "oldest disappears once
 * you have enough" trade-off {@link ConversationSavedData} already accepts for message history, just scoped
 * to the actor instead of the conversation, since vanilla gives no reliable way to reference-count a
 * duplicated/dropped/shulker-boxed ItemStack.
 */
public class PhotoSavedData extends SavedData {
    public static final String DATA_NAME = "crazyphone_photos";

    /** photoId (string) -> {owner, conversationId, created, thumbnail: PNG bytes, full: PNG bytes} */
    public CompoundTag photos = new CompoundTag();
    /** owner (phone number) -> ListTag of photoId strings, oldest first - lets eviction find/drop the
     * oldest entry for that owner in O(1) instead of scanning every photo's "created" timestamp. */
    public CompoundTag photosByOwner = new CompoundTag();

    //? if >=1.20.5 <1.21.10 {
    /*public static PhotoSavedData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider lookupProvider) {
    *///? } else {
    public static PhotoSavedData load(CompoundTag tag) {
    //?}
        PhotoSavedData data = new PhotoSavedData();
        data.photos = tag.get("photos") instanceof CompoundTag t ? t : new CompoundTag();
        data.photosByOwner = tag.get("photosByOwner") instanceof CompoundTag t ? t : new CompoundTag();
        return data;
    }

    //? if <1.20.5 {
    @Override
    public CompoundTag save(CompoundTag nbt) {
        return writeNbt(nbt);
    }
    //?}
    //? if >=1.20.5 <1.21.10 {
    /*@Override
    public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}
    //? if >=1.21.10 {
    /*public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}

    private CompoundTag writeNbt(CompoundTag nbt) {
        nbt.put("photos", this.photos);
        nbt.put("photosByOwner", this.photosByOwner);
        return nbt;
    }

    //? if >=1.21.10 {
    /*public static final Codec<PhotoSavedData> CODEC = CompoundTag.CODEC.xmap(PhotoSavedData::load,
            data -> data.writeNbt(new CompoundTag()));

    public static final SavedDataType<PhotoSavedData> TYPE =
            new SavedDataType<>(/^$ saved_data_id {^/DATA_NAME/^$}^/, PhotoSavedData::new, CODEC, DataFixTypes.LEVEL);
    *///?}

    /** Stores a freshly-captured photo under {@code clientPhotoId} (or a fresh random id if null),
     * evicting the owner's oldest photo first if this pushes them over {@link Config#maxPhotosStoredPerOwner}.
     * When the caller had no separate low-quality preview to offer (photoThumbnailPixelHeight disabled, or
     * the source was already shorter than the configured target - see
     * FabricPictureCapture#captureBothResolutions), thumbnailPng and fullPng are the exact same bytes -
     * stored once, not twice (see the "thumbnail" tag omission below and getPhoto's matching fallback).
     *
     * Deduplicates by content hash first: if this owner already has a stored photo with the exact same full-
     * image bytes under the exact same conversationId, its existing id is reused instead of minting (and
     * storing the bytes of) a new one - a real, if uncommon, case whenever the same screenshot gets sent
     * more than once into the same conversation (e.g. a Send-from-gallery re-share of a photo already
     * posted there). Deliberately scoped to same-owner AND same-conversationId, not a global content index -
     * {@link fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket}'s own authorization check trusts a
     * photo's stored conversationId to decide who may fetch it, so reusing one id across two different
     * owners or conversations would silently let a photo taken/shared in one conversation become readable
     * from another it was never actually sent to. */
    public UUID storePhoto(String owner, String conversationId, UUID clientPhotoId, byte[] thumbnailPng, byte[] fullPng, int createdMinutes) {
        String hash = sha256Hex(fullPng);
        UUID duplicate = findDuplicate(owner, conversationId, hash);
        if (duplicate != null)
            return duplicate;

        // clientPhotoId lets the sender optimistically seed its own cache and message list with the SAME id
        // this ends up stored under (see CrazyPhoneUploadPicturePacket's own doc comment) - only trusted when
        // no duplicate was found above, so re-sending a byte-identical photo still correctly reuses whatever
        // id it was already stored under instead of minting a second entry for the same bytes.
        UUID photoId = clientPhotoId != null ? clientPhotoId : UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putString("owner", owner);
        entry.putString("conversationId", conversationId);
        entry.putInt("created", createdMinutes);
        entry.putString("hash", hash);
        if (!Arrays.equals(thumbnailPng, fullPng))
            entry.put("thumbnail", new ByteArrayTag(thumbnailPng));
        entry.put("full", new ByteArrayTag(fullPng));
        photos.put(photoId.toString(), entry);

        ListTag ownerList = photosByOwner.get(owner) instanceof ListTag t ? t : new ListTag();
        ownerList.add(StringTag.valueOf(photoId.toString()));
        if (ownerList.size() > Config.maxPhotosStoredPerOwner) {
            String evictedId = NbtCompat.asString(ownerList.get(0));
            ownerList.remove(0);
            photosByOwner.put(owner, ownerList);
            eraseIfOrphaned(evictedId);
        } else {
            photosByOwner.put(owner, ownerList);
        }
        setDirty();
        return photoId;
    }

    /** Marks a photo as having a real, physical Photo item somewhere in the world - see this class's own
     * doc comment on why that then blocks {@link #eraseIfOrphaned} forever, not just for the owner who took
     * it out. Called once, the moment a physical copy is actually created (see
     * {@link fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket}'s own "Save to Inventory"/TAKE
     * handling) - never unset, since a printed copy can outlive every phone/message that ever pointed at it
     * and this class has no way to track where it ends up (dropped, traded, shulker-boxed, item-framed...). */
    public void markPhysical(UUID photoId) {
        if (photos.get(photoId.toString()) instanceof CompoundTag entry && !NbtCompat.getBoolean(entry, "physical")) {
            entry.putBoolean("physical", true);
            setDirty();
        }
    }

    /** Whether any owner's gallery list still references this id - checked before actually erasing a shared
     * entry's bytes, since {@link #linkPhotoToOwner} lets several owners' lists point at the same one (a
     * photo you deleted from your own gallery may still be sitting in a group chat partner's). */
    private boolean isReferencedByAnyOwner(String idString) {
        for (String owner : NbtCompat.keySet(photosByOwner)) {
            if (photosByOwner.get(owner) instanceof ListTag ownerList) {
                for (Tag t : ownerList)
                    if (idString.equals(NbtCompat.asString(t)))
                        return true;
            }
        }
        return false;
    }

    /** Erases a photo's actual bytes only if nothing still needs them: no owner's gallery list references
     * it anymore (see {@link #isReferencedByAnyOwner}) AND no physical Photo item was ever taken out for it
     * (see {@link #markPhysical}) - a real item already out in the world would otherwise silently turn into
     * an unimportable, unviewable ghost the moment the last gallery reference to it disappeared (a photo
     * printed once, then deleted from every phone that ever listed it, is still a real physical object
     * sitting in someone's inventory or nailed to a wall as a frame). Safe to call on an id that's already
     * gone, or still referenced/physical - just does nothing in that case. Callers are still responsible for
     * removing the id from whichever owner list(s) they're actually acting on - this only ever touches the
     * shared {@link #photos} entry. */
    private void eraseIfOrphaned(String idString) {
        if (isReferencedByAnyOwner(idString))
            return;
        if (photos.get(idString) instanceof CompoundTag entry && NbtCompat.getBoolean(entry, "physical"))
            return;
        photos.remove(idString);
    }

    private @Nullable UUID findDuplicate(String owner, String conversationId, String hash) {
        if (!(photosByOwner.get(owner) instanceof ListTag ownerList))
            return null;
        for (Tag t : ownerList) {
            String idString = NbtCompat.asString(t);
            if (photos.get(idString) instanceof CompoundTag entry
                    && hash.equals(NbtCompat.getString(entry, "hash"))
                    && conversationId.equals(NbtCompat.getString(entry, "conversationId")))
                return UUID.fromString(idString);
        }
        return null;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest)
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JCA algorithm on every real JVM - this is unreachable outside a broken
            // installation, and there's no sane per-call fallback that preserves dedup correctness.
            throw new IllegalStateException(e);
        }
    }

    /** Null if the id doesn't exist (evicted, or never existed) - callers must not trust a client-supplied
     * conversationId, only the one stored here at upload time. */
    public @Nullable PhotoEntry getPhoto(UUID photoId) {
        if (!(photos.get(photoId.toString()) instanceof CompoundTag entry))
            return null;
        byte[] full = entry.get("full") instanceof ByteArrayTag tag ? tag.getAsByteArray() : new byte[0];
        // No separate "thumbnail" tag means it was never stored distinct from "full" - see storePhoto.
        byte[] thumbnail = entry.get("thumbnail") instanceof ByteArrayTag tag ? tag.getAsByteArray() : full;
        return new PhotoEntry(NbtCompat.getString(entry, "owner"), NbtCompat.getString(entry, "conversationId"),
                NbtCompat.getInt(entry, "created"), thumbnail, full);
    }

    public record PhotoEntry(String owner, String conversationId, int createdMinutes, byte[] thumbnail, byte[] full) {
    }

    /** Every photo id owned by this phone number, newest first (for the "My Photos" gallery - reverse of
     * {@link #photosByOwner}'s own oldest-first eviction order). */
    public List<UUID> getPhotoIdsForOwner(String owner) {
        List<UUID> result = new ArrayList<>();
        if (photosByOwner.get(owner) instanceof ListTag ownerList) {
            for (Tag t : ownerList)
                result.add(UUID.fromString(NbtCompat.asString(t)));
        }
        Collections.reverse(result);
        return result;
    }

    /** Removes the given photos from this owner's own gallery list - "delete" only ever means "make this
     * phone forget it ever had this photo", never "erase it everywhere" (live request: "supprimer une photo
     * d'un tel supprime son existence du tel c'est tout"). The underlying bytes only actually go away once
     * {@link #eraseIfOrphaned} confirms nothing else still needs them - no other owner's list references the
     * id, and no physical Photo item was ever taken out for it (see {@link #markPhysical}) - otherwise a
     * photo someone else still has in their gallery, or that exists as a real item/photo frame somewhere in
     * the world, would turn into an unviewable, unimportable ghost the moment this one owner deleted their
     * own copy. Ids not owned by {@code owner} (or already gone) are silently skipped - never trust a
     * client-supplied id set as authoritative without this owner check. */
    public void deletePhotos(String owner, Set<UUID> photoIds) {
        if (!(photosByOwner.get(owner) instanceof ListTag ownerList))
            return;
        ListTag updated = new ListTag();
        boolean changed = false;
        for (Tag t : ownerList) {
            String idString = NbtCompat.asString(t);
            if (photoIds.contains(UUID.fromString(idString))) {
                changed = true;
            } else {
                updated.add(t);
            }
        }
        if (changed) {
            photosByOwner.put(owner, updated);
            for (UUID photoId : photoIds)
                eraseIfOrphaned(photoId.toString());
            setDirty();
        }
    }

    /** Links an already-stored photo (its bytes/entry already exist under {@link #photos}) into another
     * owner's own gallery list - e.g. clicking a physical Photo item onto/with a phone to import it, when
     * the item may have started life belonging to a different owner (given, traded, dropped and picked back
     * up). A no-op if this owner's list already has it. Deliberately does NOT touch {@link #photos} itself
     * or the entry's own "owner" field - multiple owners' lists can end up pointing at the same shared
     * entry this way, same trade-off {@link #storePhoto}'s own dedup already accepts for a single owner
     * (see this class's own doc comment); eviction here only ever drops the id from THIS owner's list, never
     * the shared entry, so it can't delete bytes another owner's list still references. */
    public void linkPhotoToOwner(String owner, UUID photoId) {
        ListTag ownerList = photosByOwner.get(owner) instanceof ListTag t ? t : new ListTag();
        String idString = photoId.toString();
        for (Tag t : ownerList)
            if (idString.equals(NbtCompat.asString(t)))
                return;
        ownerList.add(StringTag.valueOf(idString));
        if (ownerList.size() > Config.maxPhotosStoredPerOwner)
            ownerList.remove(0);
        photosByOwner.put(owner, ownerList);
        setDirty();
    }

    public static PhotoSavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    //? if neoforge && <1.20.5 {
                    .computeIfAbsent(new SavedData.Factory<>(PhotoSavedData::new, PhotoSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    //?}
                    //? if neoforge && >=1.20.5 <1.21.10 {
                    /*.computeIfAbsent(new SavedData.Factory<>(PhotoSavedData::new, PhotoSavedData::load), DATA_NAME);
                    *///?}
                    // >=1.21.10's computeIfAbsent(SavedDataType) overload and TYPE itself are both plain
                    // vanilla (no loader-specific types involved) - shared across both loaders instead of
                    // duplicating a fabric-only copy of the same call.
                    //? if >=1.21.10 {
                    /*.computeIfAbsent(TYPE);
                    *///?}
                    //? if (fabric || legacyforge) && <1.20.5 {
                    /*.computeIfAbsent(PhotoSavedData::load, PhotoSavedData::new, DATA_NAME);
                    *///?}
                    //? if fabric && >=1.20.5 <1.21.10 {
                    /*.computeIfAbsent(new SavedData.Factory<>(PhotoSavedData::new, PhotoSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    *///?}
        }
        throw new IllegalStateException("PhotoSavedData is server-only");
    }
}
