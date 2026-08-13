package fr.lordfinn.crazyphone.utils;

import com.mojang.authlib.GameProfile;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
//? } else {
/*import net.minecraft.nbt.NbtUtils;
*///?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** Single choke point for reading/writing a phone item's own NBT tag (number, name, isOpen, call
 *  state, etc.), currently backed by the 1.20.5+ Data Components API. Every call site in the mod
 *  goes through this instead of touching DataComponents/CustomData directly, so porting to a
 *  pre-components Minecraft version only means rewriting this one file. */
public final class PhoneTagAccess {
    private PhoneTagAccess() {
    }

    public static CompoundTag getTag(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        //? } else {
        /*return stack.getOrCreateTag().copy();
        *///?}
    }

    public static void updateTag(ItemStack stack, Consumer<CompoundTag> mutator) {
        //? if >=1.20.5 {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, mutator);
        //? } else {
        /*CompoundTag tag = stack.getOrCreateTag();
        mutator.accept(tag);
        stack.setTag(tag);
        *///?}
    }

    /** Sets an ItemStack's display name, e.g. a contact head's "Name • Number" label. */
    public static void setCustomName(ItemStack stack, Component name) {
        //? if >=1.20.5 {
        stack.set(DataComponents.CUSTOM_NAME, name);
        //? } else {
        /*stack.setHoverName(name);
        *///?}
    }

    /** Sets a player-head ItemStack's skin/owner, e.g. a contact head's face texture. */
    public static void setSkullOwner(ItemStack stack, GameProfile profile) {
        //? if >=1.20.5 {
        stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
        //? } else {
        /*CompoundTag tag = stack.getOrCreateTag();
        CompoundTag skullOwnerTag = new CompoundTag();
        NbtUtils.writeGameProfile(skullOwnerTag, profile);
        tag.put("SkullOwner", skullOwnerTag);
        stack.setTag(tag);
        *///?}
    }
}
