package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class ResetCrazyPhoneNumberProcedure {
	public static String execute(ItemStack itemstack, LevelAccessor world) {
		RandomSource random = RandomSource.create();
		String number = "" + Mth.nextInt(random, 100, 999);
		// Only 900 possible 3-digit codes exist, so a fresh/refreshed suggestion can otherwise land on one
		// someone else already registered - RegisterNewPhoneFromFormProcedure still re-checks at submit
		// time (a modified client could skip straight there), but re-rolling here means a normal player
		// never even sees a doomed-to-fail suggestion. Bounded to every possible code so this can't spin
		// forever once the pool is exhausted.
		for (int attempts = 0; attempts < 900 && IsPhoneInUseProcedure.execute(world, number); attempts++)
			number = "" + Mth.nextInt(random, 100, 999);
		{
			final String _tagName = "number";
			final String _tagValue = number;
			PhoneTagAccess.updateTag(itemstack, tag -> tag.putString(_tagName, _tagValue));
		}
		return number;
	}
}
