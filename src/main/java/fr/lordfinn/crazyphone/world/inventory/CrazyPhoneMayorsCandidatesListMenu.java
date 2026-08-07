package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.init.ModMenus;

import java.util.HashMap;
import java.util.Map;

public class CrazyPhoneMayorsCandidatesListMenu extends CrazyPhoneDefaultScreenMenu {
	private final Map<Integer, Slot> customSlots = new HashMap<>();

	public CrazyPhoneMayorsCandidatesListMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
    	super(ModMenus.CRAZY_PHONE_MAYORS_CANDIDATES_LIST.get(), id, inv, extraData);
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public Map<Integer, Slot> get() {
		return customSlots;
	}
}
