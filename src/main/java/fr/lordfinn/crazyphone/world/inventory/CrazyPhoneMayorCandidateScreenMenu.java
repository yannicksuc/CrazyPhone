package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;

import java.util.HashMap;

public class CrazyPhoneMayorCandidateScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public final static HashMap<String, Object> guistate = new HashMap<>();
	public final String mayorNumber;

	public CrazyPhoneMayorCandidateScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN.get(), id, inv, extraData);
		this.mayorNumber = extraData.readUtf();
	}
}
