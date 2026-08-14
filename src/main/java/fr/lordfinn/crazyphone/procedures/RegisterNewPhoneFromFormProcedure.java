package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import java.util.HashMap;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class RegisterNewPhoneFromFormProcedure {
    public static void execute(LevelAccessor world, Entity entity, ItemStack itemstack, HashMap textstate) {
        if (entity == null || textstate == null)
            return;

        CompoundTag phone;

        // An empty/never-typed name field falls back to the player's own Minecraft username rather than
        // being stored blank - the client-side EditBox already suggests it as a placeholder (see
        // CrazyPhonePasswordScreenScreen), but this is the actual write path, so it can't just trust the
        // client sent something: a modified client (or simply clicking Validate without touching the field)
        // could still submit an empty string.
        String submittedName = textstate.containsKey("textin:name") ? (String) textstate.get("textin:name") : "";
        String resolvedName = submittedName.isBlank() ? entity.getName().getString() : submittedName;

        // Create a new CompoundTag for the phone data
        phone = new CompoundTag();
        phone.putString("uuid", entity.getStringUUID());
        phone.putString("password", textstate.containsKey("textin:password") ? (String) textstate.get("textin:password") : "");
        phone.putString("name", resolvedName);
        // Get the skin texture UUID from the player entity
        if (entity instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) entity;
            GameProfile gameProfile = CrazyPhoneHelper.applySkinToProfile(player.getGameProfile(), entity.getStringUUID());
            if (gameProfile != null) {
                Property property = fr.lordfinn.crazyphone.utils.GameProfileCompat.properties(gameProfile).get("textures").stream().findFirst().orElse(null);
                if (property != null) {
                    String textureUUID = property.value();
                    phone.putString("skin", textureUUID);
                }
            }
        }

        // Refuse to overwrite an existing registration - CrazyPhoneGetInitialFormValidationMessageProcedure
        // already blocks this from the normal UI flow, but this is the actual write path so it must not
        // trust that gate alone (a modified client could invoke registration directly).
        String targetNumber = textstate.containsKey("textin:number") ? (String) textstate.get("textin:number") : "";
        if (IsPhoneInUseProcedure.execute(world, targetNumber))
            return;

        // Add the phone data to the world's registry
        PhoneRegistrySavedData.get(world).phones.put(targetNumber, phone);

        // Update itemstack with name and number from GUI state again
        updateItemStackTag(itemstack, textstate, "textin:number", "number");
        PhoneTagAccess.updateTag(itemstack, tag -> tag.putString("name", resolvedName));

        // Sync the data with the world
        PhoneRegistrySavedData.get(world).syncToAll(world);
    }

    private static void updateItemStackTag(ItemStack itemstack, HashMap guistate, String guiKey, String tagName) {
        String _tagValue = guistate.containsKey(guiKey) ? ((String) guistate.get(guiKey)) : "";
        PhoneTagAccess.updateTag(itemstack, tag -> tag.putString(tagName, _tagValue));
    }
}
