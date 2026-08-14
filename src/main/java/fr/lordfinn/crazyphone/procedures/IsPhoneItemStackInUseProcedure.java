package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class IsPhoneItemStackInUseProcedure {
	public static boolean execute(LevelAccessor world, ItemStack itemstack) {
		return IsPhoneInUseProcedure.execute(world, fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "number"));
	}
}
