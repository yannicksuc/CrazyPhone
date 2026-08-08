package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import com.mojang.brigadier.context.CommandContext;

import de.maxhenkel.camera.ImageData;

import com.mojang.brigadier.arguments.IntegerArgumentType;

public class CrazyPhoneAddMayorProgramFromMainHandProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null || !(entity instanceof ServerPlayer player))
			return;
		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));
		if ((PhoneRegistrySavedData.get(world).mayorsCandidates.get(numberStr)) == null) {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("Candidate not found"), false);
		} else {
			ImageData data = ImageData.fromStack(player.getMainHandItem());
			CompoundTag tag = CrazyPhoneHelper.imageDataToCompoundTag(data);
			PhoneRegistrySavedData.get(world).mayorsCandidates.put(numberStr, tag);
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.literal("Poster successfully added to candidate !"), false);
			PhoneRegistrySavedData.get(world).syncToAll(world);
		}
	}
}
