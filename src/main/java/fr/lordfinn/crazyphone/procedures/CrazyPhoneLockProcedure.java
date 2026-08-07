package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.component.DataComponents;

public class CrazyPhoneLockProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		{
			final String _tagName = "isOpen";
			final boolean _tagValue = false;
			CustomData.update(DataComponents.CUSTOM_DATA, (CrazyPhoneHelper.getMainHandItemOrEmpty(entity)), tag -> tag.putBoolean(_tagName, _tagValue));
		}
		CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
	}
}
