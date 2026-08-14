package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class IsPhoneSetupProcedure {
	public static boolean execute(ItemStack itemstack) {
		return !(fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "name")).isEmpty();
	}
}
