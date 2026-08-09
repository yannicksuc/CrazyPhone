package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;

public class CrazyPhoneGetInitialFormValidationMessageProcedure {
	public static String execute(LevelAccessor world, Entity entity, HashMap guistate) {
		if (entity == null || guistate == null || guistate.isEmpty())
			return "";
		if (IsPhoneItemStackInUseProcedure.execute(world, CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			if (IsPhoneSetupProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
				return "Téléphone configuré!";
			}
			return "Numero déja utilisé";
		// An empty name is fine, not an error - RegisterNewPhoneFromFormProcedure falls back to the
		// player's own Minecraft username (already shown as the field's ghosted placeholder).
		} else if ((guistate.containsKey("textin:name") ? (String) guistate.get("textin:name") : "").length() > 25) {
			return "Nom trop long";
		} else if ((guistate.containsKey("textin:password") ? (String) guistate.get("textin:password") : "").isEmpty()) {
			return "Mot de passe requis";
		} else if (IsPhoneInUseProcedure.execute(world, guistate.containsKey("textin:number") ? (String) guistate.get("textin:number") : "")) {
			// The item's OWN number tag is still empty at this point (checked above), so this is a
			// different, already-registered phone's number being typed into the form - without this
			// check the registration below would silently overwrite that phone's registry entry
			// (contacts, password, uuid) with this player's, a number hijack.
			return "Numéro déjà pris";
		}
		return "Ok!";
	}
}
