package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import com.mojang.brigadier.context.CommandContext;

import com.mojang.brigadier.arguments.IntegerArgumentType;

public class CrazyPhoneAddMayorProgramFromMainHandProcedure {
	public static void execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null || !(entity instanceof ServerPlayer player))
			return;
		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));
		if ((PhoneRegistrySavedData.get(world).mayorsCandidates.get(numberStr)) == null) {
			if (entity instanceof Player _player && !_player.level().isClientSide())
				_player.displayClientMessage(Component.translatable("message.crazyphone.candidate_not_found"), false);
			return;
		}
		PhotoItemData data = PhotoItemData.fromStack(player.getMainHandItem());
		if (data == null) {
			player.displayClientMessage(Component.translatable("message.crazyphone.candidate_poster_needs_photo"), false);
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putLong("photo_id_most", data.photoId().getMostSignificantBits());
		tag.putLong("photo_id_least", data.photoId().getLeastSignificantBits());
		PhoneRegistrySavedData.get(world).mayorsCandidates.put(numberStr, tag);
		player.displayClientMessage(Component.translatable("message.crazyphone.candidate_poster_added"), false);
		PhoneRegistrySavedData.get(world).syncToAll(world);
	}
}
