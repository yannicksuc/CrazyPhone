package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class IsPhoneSetupProcedure {
	public static boolean execute(ItemStack itemstack) {
		return !(PhoneTagAccess.getTag(itemstack).getString("name")).isEmpty();
	}
}
