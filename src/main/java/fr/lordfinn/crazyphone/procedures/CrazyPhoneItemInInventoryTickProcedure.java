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
	//? if <1.21.10 {
	@OnlyIn(Dist.CLIENT)
	//?}
	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		execute(event.getItemStack(), event.getToolTip());
	}

	public static void execute(ItemStack itemstack, List<Component> tooltip) {
		if (tooltip == null)
			return;
		if (itemstack.getItem() == ModItems.CRAZY_PHONE.get()) {
			String number = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "number");
			String name = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "name");
			if (!number.isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_number", number));
			}
			if (!name.isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_owner", name));
			}
		}
	}
}
