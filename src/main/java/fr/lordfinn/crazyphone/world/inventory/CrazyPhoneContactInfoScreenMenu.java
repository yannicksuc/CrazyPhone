
package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;
import java.util.HashMap;

public class CrazyPhoneContactInfoScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public final static HashMap<String, Object> guistate = new HashMap<>();

	public CrazyPhoneContactInfoScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_CONTACT_INFO_SCREEN.get(), id, inv, extraData);
	}
}
