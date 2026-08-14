package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class GetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack phone, LevelAccessor world) {
		String number = "";
		final String _tagName = "number";
		number = PhoneTagAccess.getTag(phone).getString(_tagName);
		if ((number).isEmpty()) {
			return ResetCrazyPhoneNumberProcedure.execute(phone, world);
		}
		return number;
	}
}
