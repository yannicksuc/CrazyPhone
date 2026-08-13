package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class ResetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack itemstack) {
		String number = "";
		number = "" + Mth.nextInt(RandomSource.create(), 100, 999);
		{
			final String _tagName = "number";
			final String _tagValue = number;
			PhoneTagAccess.updateTag(itemstack, tag -> tag.putString(_tagName, _tagValue));
		}
		return number;
	}
}
