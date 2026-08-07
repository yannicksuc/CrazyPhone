package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class GetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack phone) {
		String number = "";
		final String _tagName = "number";
		number = phone.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(_tagName);
		if ((number).isEmpty()) {
			return ResetCrazyPhoneNumberProcedure.execute(phone);
		}
		return number;
	}
}
