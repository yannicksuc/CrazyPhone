package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class IsPhoneItemStackInUseProcedure {
	public static boolean execute(LevelAccessor world, ItemStack itemstack) {
		return IsPhoneInUseProcedure.execute(world, itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("number"));
	}
}
