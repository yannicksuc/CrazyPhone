package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

public class CrazyPhoneOpenAlbumScreenProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof ServerPlayer _ent) {
			ScreenMenuUtils.openPhoneAlbumMenu(_ent, InteractionHand.MAIN_HAND, 0);
		}
	}
}
