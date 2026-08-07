package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.gui.components.EditBox;

import fr.lordfinn.crazyphone.init.ModItems;

import java.util.HashMap;

public class GetCrazyPhoneNumberFromMainHandProcedure {
	public static String execute(Entity entity, HashMap guistate) {
		if (entity == null)
			return "";
		String number = "";
		if ((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getItem() == ModItems.CRAZY_PHONE.get()) {
			number = GetCrazyPhoneNumberProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY);
			if (guistate != null && guistate.get("text:number") instanceof EditBox _tf)
				_tf.setValue(number);
			return number;
		}
		return "";
	}
}
