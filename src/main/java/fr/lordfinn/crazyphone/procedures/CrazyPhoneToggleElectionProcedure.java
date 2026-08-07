package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;

public class CrazyPhoneToggleElectionProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments) {
		PhoneRegistrySavedData.get(world).isMayorElectionOn = BoolArgumentType.getBool(arguments, "isOn");
		PhoneRegistrySavedData.get(world).syncToAll(world);
	}
}
