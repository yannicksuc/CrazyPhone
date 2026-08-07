package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.gui.components.EditBox;

import fr.lordfinn.crazyphone.init.ModItems;

import java.util.HashMap;

public class GetCrazyPhoneNumberFromMainHandProcedure {
	public static String execute(Entity entity, HashMap guistate) {
		if (entity == null)
			return "";
		String number = "";
		if ((CrazyPhoneHelper.getMainHandItemOrEmpty(entity)).getItem() == ModItems.CRAZY_PHONE.get()) {
			number = GetCrazyPhoneNumberProcedure.execute(CrazyPhoneHelper.getMainHandItemOrEmpty(entity));
			if (guistate != null && guistate.get("text:number") instanceof EditBox _tf)
				_tf.setValue(number);
			return number;
		}
		return "";
	}
}
