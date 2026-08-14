package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class LoadPhoneDataIntoItemstackProcedure {
    public static void execute(LevelAccessor world, Entity entity, ItemStack itemstack, String phoneNumber) {
        if (entity == null || itemstack == null || phoneNumber == null || phoneNumber.isEmpty())
            return;

        CompoundTag phoneData = (PhoneRegistrySavedData.get(world).phones.get(phoneNumber) instanceof CompoundTag tag)
                ? tag.copy()
                : null;
        if (phoneData == null)
            return; // no phone data found for that number

        updateItemStackTag(itemstack, "uuid", phoneData.getString("uuid"));
        updateItemStackTag(itemstack, "password", phoneData.getString("password"));
        updateItemStackTag(itemstack, "name", phoneData.getString("name"));
        updateItemStackTag(itemstack, "skin", phoneData.getString("skin"));
        updateItemStackTag(itemstack, "number", phoneNumber);

        // This only reads the registry (into the itemstack's own data), it never mutates it - broadcasting
        // an unchanged registry to every online player here was pure waste.
        PhoneTagAccess.setPhoneDisplayName(itemstack, phoneData.getString("name"));
    }

    private static void updateItemStackTag(ItemStack itemstack, String tagName, String tagValue) {
        PhoneTagAccess.updateTag(itemstack, tag -> tag.putString(tagName, tagValue));
    }
}
