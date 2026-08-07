package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import de.maxhenkel.camera.items.ImageItem;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CrazyPhoneOnUseProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof Player _player)
			_player.closeContainer();
		if (!IsPhoneSetupProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
			CrazyPhoneOpenPasswordScreenProcedure.execute(world, x, y, z, entity);
		} else if (IsPhoneOpenProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
			if (entity instanceof ServerPlayer player) {
				ItemStack offhandStack = player.getOffhandItem();

				if (offhandStack != null && !offhandStack.isEmpty() && offhandStack.getItem() instanceof ImageItem) {
					boolean result = CameraModHelper.tryInsertImageIntoCrazyPhone(player, offhandStack);
					if (result) {
						player.displayClientMessage(
							Component.translatable("message.crazyphone.image_upload_success")
								.withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)),
							true
						);
						player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
					} else {
						player.displayClientMessage(
							Component.translatable("message.crazyphone.image_upload_failed")
								.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(true)),
							true
						);
						player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
					}
				} else {
					CrazyPhoneRightclickedProcedure.execute(world, x, y, z, entity);
				}
			}
		} else {
			CrazyPhoneOpenSignInScreenProcedure.execute(world, x, y, z, entity);
		}
	}
}
