package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class IsPhoneInUseProcedure {
	public static boolean execute(LevelAccessor world, String number) {
		if (number == null)
			return false;
		return PhoneRegistrySavedData.get(world).phones.contains(number);
	}
}
