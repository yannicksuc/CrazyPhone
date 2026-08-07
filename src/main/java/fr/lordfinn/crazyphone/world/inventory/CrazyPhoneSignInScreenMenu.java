
package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;

import java.util.HashMap;

public class CrazyPhoneSignInScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public final static HashMap<String, Object> guistate = new HashMap<>();

	public CrazyPhoneSignInScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_SIGN_IN_SCREEN.get(), id, inv, extraData);
	}
}
