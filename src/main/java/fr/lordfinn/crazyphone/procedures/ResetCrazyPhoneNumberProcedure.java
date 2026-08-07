package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;

public class ResetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack itemstack) {
		String number = "";
		number = "" + Mth.nextInt(RandomSource.create(), 100, 999);
		{
			final String _tagName = "number";
			final String _tagValue = number;
			CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putString(_tagName, _tagValue));
		}
		return number;
	}
}
