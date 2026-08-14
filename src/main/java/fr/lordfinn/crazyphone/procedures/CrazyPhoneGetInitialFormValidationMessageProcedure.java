package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.network.chat.Component;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;

public class CrazyPhoneGetInitialFormValidationMessageProcedure {
	/** Not a translated message - a plain sentinel this procedure's own callers compare against via
	 * {@code .equals()} to detect "the form is valid", never itself shown to the player. */
	public static final String OK = "Ok!";

	public static String execute(LevelAccessor world, Entity entity, HashMap guistate) {
		return execute(world, entity, guistate, false);
	}

	/** @param skipPasswordCheck true when validating just the number/name step of registration, before the
	 *                            separate password step has anything to check yet. */
	public static String execute(LevelAccessor world, Entity entity, HashMap guistate, boolean skipPasswordCheck) {
		if (entity == null || guistate == null || guistate.isEmpty())
			return "";
		if (IsPhoneItemStackInUseProcedure.execute(world, CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			if (IsPhoneSetupProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
				return Component.translatable("message.crazyphone.form_already_configured").getString();
			}
			return Component.translatable("message.crazyphone.form_number_already_used").getString();
		// An empty name is fine, not an error - RegisterNewPhoneFromFormProcedure falls back to the
		// player's own Minecraft username (already shown as the field's ghosted placeholder).
		} else if ((guistate.containsKey("textin:name") ? (String) guistate.get("textin:name") : "").length() > 25) {
			return Component.translatable("message.crazyphone.form_name_too_long").getString();
		} else if (!skipPasswordCheck && (guistate.containsKey("textin:password") ? (String) guistate.get("textin:password") : "").isEmpty()) {
			return Component.translatable("message.crazyphone.form_password_required").getString();
		} else if (IsPhoneInUseProcedure.execute(world, guistate.containsKey("textin:number") ? (String) guistate.get("textin:number") : "")) {
			// The item's OWN number tag is still empty at this point (checked above), so this is a
			// different, already-registered phone's number being typed into the form - without this
			// check the registration below would silently overwrite that phone's registry entry
			// (contacts, password, uuid) with this player's, a number hijack.
			return Component.translatable("message.crazyphone.form_number_taken").getString();
		}
		return OK;
	}
}
