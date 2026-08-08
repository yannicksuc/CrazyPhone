package fr.lordfinn.crazyphone.procedures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class RemoveVoteByNumberProcedure {
	public static void execute(Level world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (!(world instanceof ServerLevel)) return;

		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));

		CompoundTag votes = PhoneRegistrySavedData.get(world).mayorVotes;

		if (votes.contains(numberStr)) {
			votes.remove(numberStr);

			// mayorVotes is never read client-side (only by /crazyphone mayor votes show, server-side), so this only
			// needs a disk-persistence mark, not a broadcast of the whole registry to every online player.
			PhoneRegistrySavedData.get(world).mayorVotes = votes;
			PhoneRegistrySavedData.get(world).setDirty();

			if (entity instanceof Player player) {
				player.displayClientMessage(
					Component.literal("✅ Le vote du numéro " + numberStr + " a été supprimé.")
						.withStyle(ChatFormatting.GREEN),
					false
				);
			}
		} else {
			if (entity instanceof Player player) {
				player.displayClientMessage(
					Component.literal("ℹ️ Le numéro " + numberStr + " n'a pas voté.")
						.withStyle(ChatFormatting.GRAY),
					false
				);
			}
		}
	}
}
