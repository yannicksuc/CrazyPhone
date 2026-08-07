package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.component.DataComponents;

import java.util.HashMap;

public class CrazyPhoneValidateInitialFormProcedure {
	public static void execute(LevelAccessor world, Entity entity, HashMap guistate) {
		if (entity == null || guistate == null)
			return;
		ListTag phoneData;
		String number = "";
		if (!IsPhoneItemStackInUseProcedure.execute(world, entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY)) {
			RegisterNewPhoneFromFormProcedure.execute(world, entity, entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY, guistate);
			(entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).set(DataComponents.CUSTOM_NAME,
					Component.literal(("CrazyPhone de " + (guistate.containsKey("textin:name") ? (String) guistate.get("textin:name") : ""))));
		}
	}
}
