package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
//? if neoforge {
import fr.lordfinn.crazyphone.voicechat.CallRegistry;
//?}
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

public class CrazyPhoneOnUseProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		//? if neoforge {
		if (entity instanceof Player _player)
			_player.closeContainer();
		//?}
		// closeContainer() is protected on vanilla Player - NeoForge access-transforms it public, but plain
		// Fabric Loom doesn't widen it without our own access widener. Not needed on Fabric anyway: every
		// screen opened below goes through Player#openMenu, which already closes whatever is currently open.
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
		//? if neoforge {
		if (entity instanceof ServerPlayer serverPlayer && isHeldPhoneInThisCall(serverPlayer, world)) {
			ScreenMenuUtils.openCallScreenForPlayer(serverPlayer);
			return;
		}
		//?}
		// TODO: calls aren't routed on Fabric yet (voicechat.CallRegistry not ported this pass), so there's
		// no in-call state to redirect to here.
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
		// A phone with no password on file (Config#requirePhonePassword was off at registration time) can
		// never actually be locked (see CrazyPhoneLockProcedure's own matching guard) - route straight past
		// the sign-in screen the same way an already-unlocked phone would, regardless of its own isOpen
		// tag. Covers both the normal case (such a phone's isOpen never actually goes false) and a
		// pre-existing/edge-case isOpen=false on one (see CrazyPhoneSignInScreenScreen's own defensive
		// redirect for the same edge case via any OTHER path that might reopen that screen directly).
		} else if (IsPhoneOpenProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))
				|| !IsPhonePasswordSetProcedure.execute(world, CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			CrazyPhoneRightclickedProcedure.execute(world, x, y, z, entity);
		} else {
			CrazyPhoneOpenSignInScreenProcedure.execute(world, x, y, z, entity);
		}
	}

	/** Whether the phone currently in the player's hand is actually one of the numbers on their active call
	 * - not just whether the player (as a Minecraft account) happens to have a call going somewhere. A call
	 * is one Simple Voice Chat connection per player, but a phone's number lives in that specific item's own
	 * NBT, and a player can hold several registered phones at once; without this check, using ANY of them
	 * while any one of them was mid-call redirected to that call's screen. */
	//? if neoforge {
	private static boolean isHeldPhoneInThisCall(ServerPlayer player, LevelAccessor world) {
		CallRegistry.CallSession session = CallRegistry.getSessionFor(player.getUUID()).orElse(null);
		if (session == null)
			return false;
		String heldNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
		return !heldNumber.isEmpty() && CrazyPhoneHelper.getGroupMembers(world, session.conversationId).contains(heldNumber);
	}
	//?}
}