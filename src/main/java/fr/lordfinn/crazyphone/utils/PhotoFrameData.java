package fr.lordfinn.crazyphone.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;

import javax.annotation.Nullable;

/**
 * A photo item's own memory of the frame size it was placed at, in {@link CrazyPhonePhotoFrameEntity#UNITS_PER_BLOCK}
 * units. Only ever present on a stack that came from breaking a placed frame WITH Silk Touch (see
 * {@code CrazyPhonePhotoFrameEntity#hurt}) or from duplicating such a stack (see
 * {@code CrazyPhoneDuplicatePhotoRecipe}) - a plain photo (never placed, or placed and broken without Silk
 * Touch) carries none of this, and re-placing it starts back at the default 1x1 size. "Origin" from the live
 * feature request maps to this being the SIZE the frame is recreated at when re-placed - the actual anchor
 * point is always wherever the player clicks next, deliberately not preserved (a portable item has no
 * meaningful memory of a specific world position to return to).
 */
public record PhotoFrameData(int widthUnits, int heightUnits) {
    private static final String KEY_WIDTH = "frame_width_units";
    private static final String KEY_HEIGHT = "frame_height_units";

    public static @Nullable PhotoFrameData fromStack(ItemStack stack) {
        CompoundTag tag = PhoneTagAccess.getTag(stack);
        if (!NbtCompat.contains(tag, KEY_WIDTH))
            return null;
        return new PhotoFrameData(NbtCompat.getInt(tag, KEY_WIDTH), NbtCompat.getInt(tag, KEY_HEIGHT));
    }

    public void writeTo(ItemStack stack) {
        PhoneTagAccess.updateTag(stack, tag -> {
            tag.putInt(KEY_WIDTH, widthUnits);
            tag.putInt(KEY_HEIGHT, heightUnits);
        });
    }
}
