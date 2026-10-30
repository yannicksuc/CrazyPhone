package fr.lordfinn.crazyphone.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The two extra, privacy-safe bits of state the live screen on a held phone needs to show OTHER players,
 * stored on the phone stack itself next to "screenOpen"/"callState" (see CrazyPhoneHelper#setPhoneScreenOpen
 * for why state lives on the stack: vanilla's own equipment sync then carries it to every tracking player,
 * with no extra packet and no per-viewer bookkeeping).
 * <ul>
 *     <li>{@code screenApp}: a {@link PhoneScreenApp} id, written server-side only (PhoneLiveScreenServer),
 *     from the holder's own report. Only ever a category, never content.</li>
 *     <li>{@code callSince}: the server game time at which this phone's call became ACTIVE, so bystanders
 *     can show a running call timer without any extra sync.</li>
 * </ul>
 * Readers never trust these blindly: an unknown app id reads as NONE, a negative or future timestamp reads
 * as "unknown".
 */
public final class PhoneLiveScreenData {
    public static final String TAG_APP = "screenApp";
    public static final String TAG_CALL_SINCE = "callSince";

    private PhoneLiveScreenData() {
    }

    public static PhoneScreenApp readApp(CompoundTag tag) {
        if (tag == null || !NbtCompat.contains(tag, TAG_APP))
            return PhoneScreenApp.NONE;
        return PhoneScreenApp.byIdOrNone(NbtCompat.getInt(tag, TAG_APP, 0));
    }

    /** @return whether the stack actually changed (callers only resync the inventory when it did). */
    public static boolean writeApp(ItemStack phone, PhoneScreenApp app) {
        if (readApp(PhoneTagAccess.getTag(phone)) == app)
            return false;
        PhoneTagAccess.updateTag(phone, tag -> {
            if (app == PhoneScreenApp.NONE)
                tag.remove(TAG_APP);
            else
                tag.putInt(TAG_APP, app.id);
        });
        return true;
    }

    /** -1 when absent or invalid. */
    public static long readCallSince(CompoundTag tag) {
        if (tag == null || !NbtCompat.contains(tag, TAG_CALL_SINCE))
            return -1;
        long value = NbtCompat.getLong(tag, TAG_CALL_SINCE, -1);
        return value < 0 ? -1 : value;
    }

    /** @param gameTime the server game time, or -1 to remove. @return whether the stack changed. */
    public static boolean writeCallSince(ItemStack phone, long gameTime) {
        CompoundTag current = PhoneTagAccess.getTag(phone);
        if (gameTime < 0) {
            if (!NbtCompat.contains(current, TAG_CALL_SINCE))
                return false;
            PhoneTagAccess.updateTag(phone, tag -> tag.remove(TAG_CALL_SINCE));
            return true;
        }
        if (readCallSince(current) == gameTime)
            return false;
        PhoneTagAccess.updateTag(phone, tag -> tag.putLong(TAG_CALL_SINCE, gameTime));
        return true;
    }
}
