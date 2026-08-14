package fr.lordfinn.crazyphone.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Single choke point for the CompoundTag accessor calls that changed shape in 1.21.10: getString/getInt/
 *  getLong/getBoolean/getCompound/getList used to return a defaulted value directly, and now return an
 *  Optional (getXOr(key, default) is the new defaulted form). contains(key, typeId) and getList(key, typeId)
 *  also lost their type-filtering overload - only the single-arg form remains. Every read of a CompoundTag
 *  built outside PhoneTagAccess (SavedData's own structures, packet payloads, etc.) goes through this
 *  instead of calling the tag directly, so porting to yet another NBT API shape only means rewriting this
 *  one file. */
public final class NbtCompat {
    private NbtCompat() {
    }

    public static String getString(CompoundTag tag, String key, String def) {
        //? if <1.21.10 {
        return tag.contains(key) ? tag.getString(key) : def;
        //? } else {
        /*return tag.getStringOr(key, def);
        *///?}
    }

    public static String getString(CompoundTag tag, String key) {
        return getString(tag, key, "");
    }

    public static int getInt(CompoundTag tag, String key, int def) {
        //? if <1.21.10 {
        return tag.contains(key) ? tag.getInt(key) : def;
        //? } else {
        /*return tag.getIntOr(key, def);
        *///?}
    }

    public static int getInt(CompoundTag tag, String key) {
        return getInt(tag, key, 0);
    }

    public static long getLong(CompoundTag tag, String key, long def) {
        //? if <1.21.10 {
        return tag.contains(key) ? tag.getLong(key) : def;
        //? } else {
        /*return tag.getLongOr(key, def);
        *///?}
    }

    public static long getLong(CompoundTag tag, String key) {
        return getLong(tag, key, 0L);
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean def) {
        //? if <1.21.10 {
        return tag.contains(key) ? tag.getBoolean(key) : def;
        //? } else {
        /*return tag.getBooleanOr(key, def);
        *///?}
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return getBoolean(tag, key, false);
    }

    /** Empty CompoundTag (never null) if {@code key} is missing or isn't a compound. */
    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if <1.21.10 {
        return tag.getCompound(key);
        //? } else {
        /*return tag.getCompoundOrEmpty(key);
        *///?}
    }

    /** {@link ListTag#getCompound(int)} - empty CompoundTag (never null) if {@code index} isn't a compound. */
    public static CompoundTag getCompound(ListTag list, int index) {
        //? if <1.21.10 {
        return list.getCompound(index);
        //? } else {
        /*return list.getCompoundOrEmpty(index);
        *///?}
    }

    /** Empty ListTag (never null) if {@code key} is missing, filtered to elements of {@code typeId} (e.g.
     *  {@code CompoundTag.TAG_COMPOUND}) pre-1.21.10 - the type filter itself is gone in 1.21.10, so callers
     *  there get back whatever's stored regardless of element type (same as every other call site already
     *  has to handle, via an instanceof check on each element). */
    public static ListTag getList(CompoundTag tag, String key, int typeId) {
        //? if <1.21.10 {
        return tag.getList(key, typeId);
        //? } else {
        /*return tag.getListOrEmpty(key);
        *///?}
    }

    /** {@link #getList(CompoundTag, String, int)} defaulting to compound-tag elements, the overwhelmingly
     *  common case in this codebase. */
    public static ListTag getList(CompoundTag tag, String key) {
        return getList(tag, key, CompoundTag.TAG_COMPOUND);
    }

    /** Whether {@code key} is present, regardless of its NBT type - the old type-checking contains(key,
     *  typeId) overload is gone in 1.21.10. */
    public static boolean contains(CompoundTag tag, String key) {
        return tag.contains(key);
    }

    /** Tag#getAsString(), returning a raw String, was replaced by Tag#asString() returning Optional&lt;String&gt;
     *  in 1.21.10 - for any Tag already in hand (a StringTag pulled out of a ListTag, say), not just ones
     *  read by key from a CompoundTag (see {@link #getString(CompoundTag, String)} for that case). */
    public static String asString(Tag tag, String def) {
        //? if <1.21.10 {
        return tag.getAsString();
        //? } else {
        /*return tag.asString().orElse(def);
        *///?}
    }

    public static String asString(Tag tag) {
        return asString(tag, "");
    }

    /** CompoundTag#getAllKeys() was renamed #keySet() in 1.21.10. */
    public static java.util.Set<String> keySet(CompoundTag tag) {
        //? if <1.21.10 {
        return tag.getAllKeys();
        //? } else {
        /*return tag.keySet();
        *///?}
    }
}
