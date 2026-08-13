package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionResult;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

import java.util.HashMap;

public class CrazyPhoneTrySignInProcedure {
	public static InteractionResult execute(LevelAccessor world, double x, double y, double z, Entity entity, HashMap guistate) {
		if (entity == null || guistate == null)
			return InteractionResult.PASS;
		CompoundTag phoneData;
		if (IsPhoneSetupProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity))) {
			phoneData = (PhoneRegistrySavedData.get(world).phones.get(GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate))) instanceof CompoundTag _compoundTag ? _compoundTag.copy() : new CompoundTag();
			if (!(phoneData == null) && (guistate.containsKey("textin:password") ? (String) guistate.get("textin:password") : "").equals((phoneData.get("password")) instanceof StringTag _stringTag ? _stringTag.getAsString() : "")) {
				{
					final String _tagName = "isOpen";
					final boolean _tagValue = true;
					PhoneTagAccess.updateTag(CrazyPhoneHelper.getMainHandItemOrEmpty(entity), tag -> tag.putBoolean(_tagName, _tagValue));
				}
				CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
				return InteractionResult.SUCCESS;
			}
		}
		{
			final String _tagName = "isOpen";
			final boolean _tagValue = false;
			PhoneTagAccess.updateTag(CrazyPhoneHelper.getMainHandItemOrEmpty(entity), tag -> tag.putBoolean(_tagName, _tagValue));
		}
		return InteractionResult.FAIL;
	}
}
