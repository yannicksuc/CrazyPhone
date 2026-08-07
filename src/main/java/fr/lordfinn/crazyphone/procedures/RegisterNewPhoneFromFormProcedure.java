package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.HashMap;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class RegisterNewPhoneFromFormProcedure {
    public static void execute(LevelAccessor world, Entity entity, ItemStack itemstack, HashMap textstate) {
        if (entity == null || textstate == null)
            return;

        CompoundTag phone;

        // Update itemstack with name and number from GUI state
        updateItemStackTag(itemstack, textstate, "text:name", "name");
        updateItemStackTag(itemstack, textstate, "text:number", "number");

        // Create a new CompoundTag for the phone data
        phone = new CompoundTag();
        phone.putString("uuid", entity.getStringUUID());
        phone.putString("password", textstate.containsKey("textin:password") ? (String) textstate.get("textin:password") : "");
        phone.putString("name", textstate.containsKey("textin:name") ? (String) textstate.get("textin:name") : "");
        // Get the skin texture UUID from the player entity
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            GameProfile gameProfile = CrazyPhoneHelper.applySkinToProfile(player.getGameProfile(), entity.getStringUUID());
            if (gameProfile != null) {
                Property property = gameProfile.getProperties().get("textures").stream().findFirst().orElse(null);
                if (property != null) {
                    String textureUUID = property.value();
                    phone.putString("skin", textureUUID);
                }
            }
        }

        // Add the phone data to the world's registry
        PhoneRegistrySavedData.get(world).phones.put(
            textstate.containsKey("textin:number") ? (String) textstate.get("textin:number") : "", phone);

        // Update itemstack with name and number from GUI state again
        updateItemStackTag(itemstack, textstate, "textin:number", "number");
        updateItemStackTag(itemstack, textstate, "textin:name", "name");

        // Sync the data with the world
        PhoneRegistrySavedData.get(world).syncToAll(world);
    }

    private static void updateItemStackTag(ItemStack itemstack, HashMap guistate, String guiKey, String tagName) {
        String _tagValue = guistate.containsKey(guiKey) ? ((String) guistate.get(guiKey)) : "";
        CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putString(tagName, _tagValue));
    }
}
