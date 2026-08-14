package fr.lordfinn.crazyphone.procedures;

import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import java.util.List;

@EventBusSubscriber(value = {Dist.CLIENT})
public class CrazyPhoneItemInInventoryTickProcedure {
	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		execute(event.getItemStack(), event.getToolTip());
	}

	public static void execute(ItemStack itemstack, List<Component> tooltip) {
		if (tooltip == null)
			return;
		if (itemstack.getItem() == ModItems.CRAZY_PHONE.get()) {
			if (!(PhoneTagAccess.getTag(itemstack).getString("number")).isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_number", PhoneTagAccess.getTag(itemstack).getString("number")));
			}
			if (!(PhoneTagAccess.getTag(itemstack).getString("name")).isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_owner", PhoneTagAccess.getTag(itemstack).getString("name")));
			}
		}
	}
}
