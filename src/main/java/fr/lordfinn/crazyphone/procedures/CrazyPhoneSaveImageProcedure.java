package fr.lordfinn.crazyphone.procedures;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.capabilities.Capabilities;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;

public class CrazyPhoneSaveImageProcedure {
	public static void execute(Entity entity) {
		if (entity == null)
			return;
		if ((CrazyPhoneHelper.getMainHandItemOrEmpty(entity)).getCapability(Capabilities.ItemHandler.ITEM, null) instanceof IItemHandlerModifiable _modHandlerItemSetSlot) {
			ItemStack _setstack = new ItemStack(Blocks.GOLD_BLOCK).copy();
			_setstack.setCount(1);
			_modHandlerItemSetSlot.setStackInSlot(0, _setstack);
		}
	}
}
