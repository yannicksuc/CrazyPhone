package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public class CrazyPhoneRemoveMayorCandidateProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null)
			return;
		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));
		if (PhoneRegistrySavedData.get(world).mayorsCandidates.contains(numberStr)) {
			PhoneRegistrySavedData.get(world).mayorsCandidates.remove(numberStr);
			PhoneRegistrySavedData.get(world).syncToAll(world);
			if (entity instanceof Player _player && !_player.level().isClientSide())
				CrazyPhoneHelper.sendClientMessage(_player, Component.translatable("message.crazyphone.candidate_removed"), false);
		} else {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				CrazyPhoneHelper.sendClientMessage(_player, Component.translatable("message.crazyphone.candidate_not_exist"), false);
		}
	}
}
