package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;

public class CrazyPhoneGetInitialFormValidationMessageProcedure {
	public static String execute(LevelAccessor world, Entity entity, HashMap guistate) {
		if (entity == null || guistate == null || guistate.isEmpty())
			return "";
		if (IsPhoneItemStackInUseProcedure.execute(world, entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
			if (IsPhoneSetupProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
				return "Téléphone configuré!";
			}
			return "Numero déja utilisé";
		} else if ((guistate.containsKey("textin:name") ? (String) guistate.get("textin:name") : "").isEmpty()) {
			return "Nom requis";
		} else if ((guistate.containsKey("textin:name") ? (String) guistate.get("textin:name") : "").length() > 25) {
			return "Nom trop long";
		} else if ((guistate.containsKey("textin:password") ? (String) guistate.get("textin:password") : "").isEmpty()) {
			return "Mot de passe requis";
		}
		return "Ok!";
	}
}
