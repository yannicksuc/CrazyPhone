package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

public class CrazyPhoneRightclickedProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null || !(entity instanceof Player playerIn))
			return;

		// Check if the item in the player's main hand is the Crazy Phone
		ItemStack stack = playerIn.getItemInHand(InteractionHand.MAIN_HAND);
		if (stack.getItem() == ModItems.CRAZY_PHONE.get() && entity instanceof ServerPlayer _ent) {
			ScreenMenuUtils.resetToHomeScreen(_ent);
			ScreenMenuUtils.openPhoneCustomMenu(_ent, InteractionHand.MAIN_HAND, CrazyphoneHomeScreenMenu.class);
			if (world instanceof Level _level) {
				if (playerIn instanceof ServerPlayer serverPlayer) {
					SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT,
							Crazyphone.parseId("crazyphone:pokedex"));
					if (sound != null) {
						CrazyPhoneHelper.playNotifySound(serverPlayer, sound, SoundSource.PLAYERS, 0.2f, 1f);
					}
				}
			}
		}
	}
}
