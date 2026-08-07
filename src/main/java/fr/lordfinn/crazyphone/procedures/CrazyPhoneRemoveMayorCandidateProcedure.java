package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public class CrazyPhoneRemoveMayorCandidateProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null)
			return;
		String numberStr = new java.text.DecimalFormat("###").format(Math.round(DoubleArgumentType.getDouble(arguments, "phoneNumber")));
		if (PhoneRegistrySavedData.get(world).mayorsCandidates.contains(numberStr)) {
			PhoneRegistrySavedData.get(world).mayorsCandidates.remove(numberStr);
			PhoneRegistrySavedData.get(world).syncToAll(world);
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("Candidate removed !"), false);
		} else {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("Candidate doesn't exist"), false);
		}
	}
}
