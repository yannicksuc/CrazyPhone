package fr.lordfinn.crazyphone.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/** Reads/writes a physical Photo item's own pointer to its server-side {@link fr.lordfinn.crazyphone.data.PhotoSavedData}
 * entry, via the existing loader/version-safe {@link PhoneTagAccess} - no new NBT/data-component compat
 * layer needed, this is just three primitive fields. */
public record PhotoItemData(UUID photoId, String owner, int createdMinutes) {
    public static @Nullable PhotoItemData fromStack(ItemStack stack) {
        CompoundTag tag = PhoneTagAccess.getTag(stack);
        if (!NbtCompat.contains(tag, "photo_id_most"))
            return null;
        UUID photoId = new UUID(NbtCompat.getLong(tag, "photo_id_most"), NbtCompat.getLong(tag, "photo_id_least"));
        return new PhotoItemData(photoId, NbtCompat.getString(tag, "owner"), NbtCompat.getInt(tag, "created"));
    }

    public void writeTo(ItemStack stack) {
        PhoneTagAccess.updateTag(stack, tag -> {
            tag.putLong("photo_id_most", photoId.getMostSignificantBits());
            tag.putLong("photo_id_least", photoId.getLeastSignificantBits());
            tag.putString("owner", owner);
            tag.putInt("created", createdMinutes);
        });
    }
}
