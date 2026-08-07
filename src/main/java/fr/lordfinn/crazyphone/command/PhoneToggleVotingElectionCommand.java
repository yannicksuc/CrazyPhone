
package fr.lordfinn.crazyphone.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.commands.Commands;

import fr.lordfinn.crazyphone.procedures.CrazyPhoneToggleMayorVotingProcedure;

import com.mojang.brigadier.arguments.BoolArgumentType;

@EventBusSubscriber
public class PhoneToggleVotingElectionCommand {
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("phoneToggleVotingElection").requires(s -> s.hasPermission(4)).then(Commands.argument("isOn", BoolArgumentType.bool()).executes(arguments -> {
			Level world = arguments.getSource().getUnsidedLevel();
			CrazyPhoneToggleMayorVotingProcedure.execute(world, arguments);
			return 0;
		})));
	}
}
