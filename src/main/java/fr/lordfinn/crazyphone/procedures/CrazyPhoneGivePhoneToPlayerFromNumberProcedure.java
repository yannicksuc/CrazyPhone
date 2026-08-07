package fr.lordfinn.crazyphone.procedures;

import net.neoforged.neoforge.items.ItemHandlerHelper;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.init.ModItems;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;

public class CrazyPhoneGivePhoneToPlayerFromNumberProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null)
			return;
		String number = "";
		ItemStack phone = ItemStack.EMPTY;
		number = StringArgumentType.getString(arguments, "number");
		phone = new ItemStack(ModItems.CRAZY_PHONE.get());
		if (entity instanceof Player _player) {
			phone.setCount(1);
			ItemHandlerHelper.giveItemToPlayer(_player, phone);
		}
		LoadPhoneDataIntoItemstackProcedure.execute(world, entity, phone, number);
	}
}
