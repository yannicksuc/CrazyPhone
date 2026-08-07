package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

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

        PhoneRegistrySavedData.get(world).syncToAll(world);
        itemstack.set(DataComponents.CUSTOM_NAME,
                Component.literal(("CrazyPhone de " + phoneData.getString("name"))));
    }

    private static void updateItemStackTag(ItemStack itemstack, String tagName, String tagValue) {
        CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putString(tagName, tagValue));
    }
}
