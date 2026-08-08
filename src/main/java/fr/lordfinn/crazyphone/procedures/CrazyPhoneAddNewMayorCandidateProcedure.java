package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

public class CrazyPhoneAddNewMayorCandidateProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null)
			return;
		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));
		if ((PhoneRegistrySavedData.get(world).mayorsCandidates.get(numberStr)) == null) {
			if ((PhoneRegistrySavedData.get(world).phones.get(numberStr)) == null) {
				if (entity instanceof Player _player && !_player.level().isClientSide())
					_player.displayClientMessage(Component.literal("Candidate doesn't exist"), false);
			} else {
				PhoneRegistrySavedData.get(world).mayorsCandidates.put(numberStr, (new CompoundTag()));
				if (entity instanceof Player _player && !_player.level().isClientSide())
					_player.displayClientMessage(Component.literal("New Candidate added !"), false);
				PhoneRegistrySavedData.get(world).syncToAll(world);
			}
		} else {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("Candidate already registered"), false);
		}
	}
}
