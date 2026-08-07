
package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;

public class CrazyphoneHomeScreenMenu extends CrazyPhoneDefaultScreenMenu {

	public CrazyphoneHomeScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZYPHONE_HOME_SCREEN.get(), id, inv, extraData);
	}
}
