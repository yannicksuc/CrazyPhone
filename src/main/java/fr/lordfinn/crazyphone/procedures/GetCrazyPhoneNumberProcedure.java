package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class GetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack phone) {
		String number = "";
		final String _tagName = "number";
		number = PhoneTagAccess.getTag(phone).getString(_tagName);
		if ((number).isEmpty()) {
			return ResetCrazyPhoneNumberProcedure.execute(phone);
		}
		return number;
	}
}
