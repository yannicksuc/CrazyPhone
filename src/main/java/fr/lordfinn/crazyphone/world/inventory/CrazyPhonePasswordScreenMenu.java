
package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;

import java.util.HashMap;

public class CrazyPhonePasswordScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public final static HashMap<String, Object> guistate = new HashMap<>();

	public CrazyPhonePasswordScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_PASSWORD_SCREEN.get(), id, inv, extraData);
	}
}
