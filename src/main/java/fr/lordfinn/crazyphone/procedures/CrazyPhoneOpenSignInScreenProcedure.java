package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneSignInScreenMenu;

public class CrazyPhoneOpenSignInScreenProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof ServerPlayer _ent) {
			ScreenMenuUtils.openPhoneCustomMenu(_ent, InteractionHand.MAIN_HAND, CrazyPhoneSignInScreenMenu.class);
		}
	}
}
