
package fr.lordfinn.crazyphone.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.commands.Commands;

import fr.lordfinn.crazyphone.procedures.ShowMayorVotesProcedure;

@EventBusSubscriber
public class PhoneShowVotesCommand {
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("phoneshowvotes").requires(s -> s.hasPermission(2)).executes(PhoneShowVotesCommand::run));
	}

	private static int run(CommandContext<CommandSourceStack> arguments) {
		Level world = arguments.getSource().getUnsidedLevel();
		double x = arguments.getSource().getPosition().x();
		double y = arguments.getSource().getPosition().y();
		double z = arguments.getSource().getPosition().z();
		Entity entity = arguments.getSource().getEntity();
		if (entity == null && world instanceof ServerLevel _servLevel)
			entity = FakePlayerFactory.getMinecraft(_servLevel);
		Direction direction = Direction.DOWN;
		if (entity != null)
			direction = entity.getDirection();

		ShowMayorVotesProcedure.execute(world, entity);
		return 0;
	}
}
