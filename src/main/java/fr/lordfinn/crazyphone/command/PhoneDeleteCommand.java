package fr.lordfinn.crazyphone.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;

import fr.lordfinn.crazyphone.procedures.CrazyPhoneDeletePhoneByNumberProcedure;

import com.mojang.brigadier.arguments.StringArgumentType;

@EventBusSubscriber
public class PhoneDeleteCommand {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("phoneDelete").requires(s -> s.hasPermission(4))
                .then(Commands.argument("number", StringArgumentType.word()).executes(arguments -> {
                    Level world = arguments.getSource().getUnsidedLevel();
                    String number = StringArgumentType.getString(arguments, "number");

                    boolean deleted = CrazyPhoneDeletePhoneByNumberProcedure.execute(world, number);

                    if (deleted) {
                        arguments.getSource().sendSuccess(() -> Component.literal("Phone " + number + " deleted.")
                                .withStyle(ChatFormatting.GREEN), true);
                    } else {
                        arguments.getSource().sendFailure(Component.literal("No phone registered with number " + number + ".")
                                .withStyle(ChatFormatting.RED));
                    }

                    return deleted ? 1 : 0;
                })));
    }
}
