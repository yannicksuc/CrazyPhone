package fr.lordfinn.crazyphone.procedures;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class VoteForMayorProcedure {
	public static InteractionResult execute(LevelAccessor world, CommandContext<CommandSourceStack> arguments, Entity entity) {
		if (entity == null || world.isClientSide())
			return InteractionResult.PASS;
		if (entity instanceof ServerPlayer player && !FeatureFlag.MAYOR_VOTING.isEnabledFor(player))
			return InteractionResult.PASS;

		String numberStr = String.valueOf(IntegerArgumentType.getInteger(arguments, "phoneNumber"));
		// Read-only lookup of the number already registered to the held phone - NOT
		// ResetCrazyPhoneNumberFromMainHandProcedure, which fabricates a brand new random number (and
		// writes it onto whatever's held) whenever the item isn't a set-up phone. Using that mutating
		// helper here let anyone holding no phone (or any random item) vote under a fresh fake number every
		// time, bypassing both the "must own a registered phone" requirement and the vote cooldown below
		// (which keys off myNumber, so a new random number each call never collides with a previous one).
		String myNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);

		if (myNumber.isEmpty() || numberStr.isEmpty()) {
			return InteractionResult.PASS;
		}

		long currentTime = world instanceof Level level ? level.getGameTime() : 0;
		CompoundTag voteTimestamps = PhoneRegistrySavedData.get(world).lastMayorVoteTimestamps;

		if (voteTimestamps.contains(myNumber)) {
			long lastVoteTime = fr.lordfinn.crazyphone.utils.NbtCompat.getLong(voteTimestamps, myNumber);
			if (currentTime - lastVoteTime < 600) {
				if (entity instanceof Player _player && !_player.level().isClientSide()) {
					long remainingSeconds = (600 - (currentTime - lastVoteTime)) / 20;
					_player.displayClientMessage(
						Component.translatable("message.crazyphone.vote_cooldown", remainingSeconds)
							.withStyle(ChatFormatting.RED),
						false
					);
				world.playSound(
					_player,
					_player.getOnPos(),
					SoundEvents.VILLAGER_NO,
					SoundSource.PLAYERS,
					1.0F,
					1.0F
				);
				}
				return InteractionResult.FAIL;
			}
		}

		if (PhoneRegistrySavedData.get(world).mayorsCandidates.get(numberStr) == null) {
			if (entity instanceof Player _player && !_player.level().isClientSide()) {
				_player.displayClientMessage(
					Component.translatable("message.crazyphone.candidate_not_found_vote").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					false
				);
				world.playSound(
					_player,
					_player.getOnPos(),
					SoundEvents.VILLAGER_NO,
					SoundSource.PLAYERS,
					1.0F,
					1.0F
				);
			}
			return InteractionResult.PASS;
		} else {
			boolean isNewVote = PhoneRegistrySavedData.get(world).mayorVotes.get(myNumber) == null;

			Contact contact = CrazyPhoneHelper.getContact((Level) world, myNumber);
			String playerName = (contact != null) ? contact.getName() : Component.translatable("message.crazyphone.unknown_contact").getString();
			MutableComponent broadcastMessage;

			if (isNewVote) {
				broadcastMessage = Component.translatable("message.crazyphone.vote_registered",
					Component.literal(playerName).withStyle(ChatFormatting.GOLD));
			} else {
				broadcastMessage = Component.translatable("message.crazyphone.vote_changed",
					Component.literal(playerName).withStyle(ChatFormatting.YELLOW));
			}
			world.getServer().getPlayerList().broadcastSystemMessage(broadcastMessage.withStyle(ChatFormatting.AQUA), false);

			// Enregistrement du vote - mayorVotes/lastMayorVoteTimestamps are never read client-side (only
			// by /crazyphone mayor votes show, server-side), so this only needs a disk-persistence mark, not a
			// broadcast of the whole registry to every online player on every single vote cast.
			PhoneRegistrySavedData.get(world).mayorVotes.put(myNumber, StringTag.valueOf(numberStr));
			voteTimestamps.putLong(myNumber, currentTime);
			PhoneRegistrySavedData.get(world).setDirty();

			if (entity instanceof ServerPlayer _player) {
				_player.displayClientMessage(
					Component.translatable("message.crazyphone.vote_success").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
					false
				);
				world.playSound(
					_player,
					_player.getOnPos(),
					SoundEvents.EXPERIENCE_ORB_PICKUP,
					SoundSource.PLAYERS,
					1.0F,
					1.0F
				);
				_player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
