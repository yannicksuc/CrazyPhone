package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.world.entity.Entity;

public class CrazyPhoneLockProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		{
			final String _tagName = "isOpen";
			final boolean _tagValue = false;
			PhoneTagAccess.updateTag(CrazyPhoneHelper.getMainHandItemOrEmpty(entity), tag -> tag.putBoolean(_tagName, _tagValue));
		}
		CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
	}
}
