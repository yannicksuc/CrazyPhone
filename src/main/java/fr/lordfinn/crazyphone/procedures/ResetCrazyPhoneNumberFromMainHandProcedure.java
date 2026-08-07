package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

public class ResetCrazyPhoneNumberFromMainHandProcedure {
	public static String execute(Entity entity) {
		if (entity == null)
			return "";
		if (IsPhoneSetupProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
			return GetCrazyPhoneNumberProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY);
		}
		return ResetCrazyPhoneNumberProcedure.execute(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY);
	}
}
