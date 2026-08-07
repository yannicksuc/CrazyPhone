
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

import fr.lordfinn.crazyphone.procedures.CrazyPhoneListAndPrintPhonesProcedure;

import com.mojang.brigadier.arguments.StringArgumentType;

@EventBusSubscriber
public class CrazyPhoneListPhonesCommand {
	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("phoneList").requires(s -> s.hasPermission(4)).then(Commands.argument("search", StringArgumentType.word()).executes(arguments -> {
			Level world = arguments.getSource().getUnsidedLevel();
			Entity entity = arguments.getSource().getEntity();
			if (entity == null && world instanceof ServerLevel _servLevel)
				entity = FakePlayerFactory.getMinecraft(_servLevel);
            if (entity != null)
                entity.getDirection();

			CrazyPhoneListAndPrintPhonesProcedure.execute(world, arguments, entity);
			return 0;
		})).executes(arguments -> {
			Level world = arguments.getSource().getUnsidedLevel();
			Entity entity = arguments.getSource().getEntity();
			if (entity == null && world instanceof ServerLevel _servLevel)
				entity = FakePlayerFactory.getMinecraft(_servLevel);
			CrazyPhoneListAndPrintPhonesProcedure.execute(world, arguments, entity);
			return 0;
		}));
	}
}
