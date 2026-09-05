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
		ItemStack phone = CrazyPhoneHelper.getMainHandItemOrEmpty(entity);
		// A phone registered without a password (Config#requirePhonePassword off at registration time) can
		// never be locked at all - there would be no password left to unlock it with again. The home
		// screen's own lock button is already visually disabled for such a phone (setLockButtonActive), but
		// this is the actual server-side write path, so it must not trust that alone - silent no-op backstop
		// against any other caller (a modified client included).
		if (!IsPhonePasswordSetProcedure.execute(world, phone))
			return;
		{
			final String _tagName = "isOpen";
			final boolean _tagValue = false;
			PhoneTagAccess.updateTag(phone, tag -> tag.putBoolean(_tagName, _tagValue));
		}
		CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
	}
}
