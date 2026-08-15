package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
//? if neoforge {
import de.maxhenkel.camera.items.ImageItem;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
//?}
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import fr.lordfinn.crazyphone.voicechat.CallRegistry;

public class CrazyPhoneOnUseProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof Player _player)
			_player.closeContainer();
		// Using the phone while ringing opens the Incoming Call screen (Accept/Decline - answering is no
		// longer automatic just from touching the phone); using it while already ringing or in a call
		// reopens the matching call screen directly - even past a password lock, and even if the phone isn't
		// set up yet, since being in a call takes priority over every other phone state. Runs before any of
		// the normal setup/lock/home branching below, which is what makes both cases work.
		//
		// CallRegistry tracks calls per PLAYER (a call is one Simple Voice Chat connection, which is
		// inherently per-player, not per-item), but a phone's NUMBER lives in the item's own NBT - a player
		// can physically hold several registered phones. Without the isHeldPhoneInThisCall check below,
		// using ANY of them while the player has an active call anywhere redirected to that call's screen,
		// even for a completely unrelated number that was never part of it.
		if (entity instanceof ServerPlayer serverPlayer && isHeldPhoneInThisCall(serverPlayer, world)) {
			ScreenMenuUtils.openCallScreenForPlayer(serverPlayer);
			return;
		}
		// IsPhoneSetupProcedure only trusts the item's OWN cached "name" tag, which can desync from the
		// registry (the actual source of truth) if an earlier write to the item was ever interrupted -
		// resyncing here whenever the registry disagrees means the item self-heals on its very next open
		// instead of being stuck showing the registration screen forever despite already being registered.
		ItemStack heldPhone = CrazyPhoneHelper.getMainHandItemOrEmpty(entity);
		if (IsPhoneItemStackInUseProcedure.execute(world, heldPhone) && !IsPhoneSetupProcedure.execute(heldPhone)) {
			String heldNumber = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(heldPhone), "number");
			LoadPhoneDataIntoItemstackProcedure.execute(world, entity, heldPhone, heldNumber);
		}
		if (!IsPhoneSetupProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			CrazyPhoneOpenPasswordScreenProcedure.execute(world, x, y, z, entity);
		} else if (IsPhoneOpenProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			if (entity instanceof ServerPlayer player) {
				ItemStack offhandStack = player.getOffhandItem();

				//? if neoforge {
				if (offhandStack != null && !offhandStack.isEmpty() && offhandStack.getItem() instanceof ImageItem) {
					if (!FeatureFlag.CAMERA.isEnabledFor(player)) {
						player.displayClientMessage(
							Component.translatable("message.crazyphone.camera_feature_disabled")
								.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(true)),
							true
						);
						player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
					} else {
						boolean result = CameraModHelper.tryInsertImageIntoCrazyPhone(player, offhandStack);
						if (result) {
							player.displayClientMessage(
								Component.translatable("message.crazyphone.image_upload_success")
									.withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)),
								true
							);
							player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
						} else {
							player.displayClientMessage(
								Component.translatable("message.crazyphone.image_upload_failed")
									.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(true)),
								true
							);
							player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
						}
					}
				} else {
					CrazyPhoneRightclickedProcedure.execute(world, x, y, z, entity);
				}
				//? } else {
				/*// TODO(#165): Fabric equivalent of the offhand-image-into-phone upload, once the
				// Camerapture integration (replacing Camera mod's ImageItem) is written - falls through to
				// the normal rightclick flow unconditionally until then.
				CrazyPhoneRightclickedProcedure.execute(world, x, y, z, entity);
				*///?}
			}
		} else {
			CrazyPhoneOpenSignInScreenProcedure.execute(world, x, y, z, entity);
		}
	}

	/** Whether the phone currently in the player's hand is actually one of the numbers on their active call
	 * - not just whether the player (as a Minecraft account) happens to have a call going somewhere. A call
	 * is one Simple Voice Chat connection per player, but a phone's number lives in that specific item's own
	 * NBT, and a player can hold several registered phones at once; without this check, using ANY of them
	 * while any one of them was mid-call redirected to that call's screen. */
	private static boolean isHeldPhoneInThisCall(ServerPlayer player, LevelAccessor world) {
		CallRegistry.CallSession session = CallRegistry.getSessionFor(player.getUUID()).orElse(null);
		if (session == null)
			return false;
		String heldNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
		return !heldNumber.isEmpty() && CrazyPhoneHelper.getGroupMembers(world, session.conversationId).contains(heldNumber);
	}
}