package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;

public class ResetCrazyPhoneNumberFromMainHandProcedure {
	public static String execute(Entity entity) {
		if (entity == null)
			return "";
		if (IsPhoneSetupProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			return GetCrazyPhoneNumberProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity));
		}
		return ResetCrazyPhoneNumberProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity));
	}
}
