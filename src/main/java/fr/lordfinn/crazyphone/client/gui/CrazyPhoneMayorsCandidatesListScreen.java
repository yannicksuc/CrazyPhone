package fr.lordfinn.crazyphone.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorsCandidatesListMenu;
import fr.lordfinn.crazyphone.network.CrazyPhoneMayorsCandidatesButtonMessage;
import fr.lordfinn.crazyphone.utils.Contact;

import java.util.*;

public class CrazyPhoneMayorsCandidatesListScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneMayorsCandidatesListMenu> {
	private final static HashMap<String, Object> guistate = CrazyPhoneMayorsCandidatesListMenu.guistate;
	private static final int SLOT_WIDTH = fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.SLOT_PITCH;
	private static final int SLOT_HEIGHT = fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.SLOT_PITCH;

	private final List<RenderSlot> renderSlots = new ArrayList<>();

	public CrazyPhoneMayorsCandidatesListScreen(CrazyPhoneMayorsCandidatesListMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		buildRenderList();
	}

	private void buildRenderList() {
		renderSlots.clear();
		var registry = PhoneRegistrySavedData.get(entity.level());
		var candidates = registry.mayorsCandidates;
		var phones = registry.phones;

		List<String> keys = new ArrayList<>(candidates.getAllKeys());

		int startX = fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_X;
		int startY = fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_Y;

		for (int i = 0; i < keys.size(); i++) {
			String number = keys.get(i);
			CompoundTag phoneData = phones.getCompound(number);

			String name = phoneData.getString("name");
			String skin = phoneData.getString("skin");
			String uuid = phoneData.getString("uuid");

			Contact contact = new Contact(number, name);
			if (!uuid.isEmpty()) contact.setUuid(uuid);
			if (!skin.isEmpty()) contact.setSkin(skin);

			ItemStack head = CrazyPhoneHelper.createContactHead(contact);

			int x = startX + (i % fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.GRID_COLUMNS) * SLOT_WIDTH;
			int y = startY + (i / fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu.GRID_COLUMNS) * SLOT_HEIGHT;
			renderSlots.add(new RenderSlot(x, y, head));
		}
	}

	@Override
	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
				Component.translatable("gui.crazyphone.crazy_phone_mayors_candidates_list.title"));

		for (RenderSlot rs : renderSlots) {
			int iconX = this.leftPos + rs.x;
			int iconY = this.topPos + rs.y;
			guiGraphics.renderItem(rs.stack, iconX, iconY);
			if (isHovering(rs, mouseX, mouseY)) {
				guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
				guiGraphics.renderTooltip(this.font, rs.stack, mouseX, mouseY);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
		for (RenderSlot rs : renderSlots) {
			if (isHovering(rs, mouseX, mouseY)) {
				String candidateNumber = PhoneTagAccess.getTag(rs.stack).getString("number");
				textstate.put(	"candidateNumber", candidateNumber);
                //? if >=1.20.5 {
                /*NetworkAccess.sendToServer(new CrazyPhoneMayorsCandidatesButtonMessage(0, x, y, z, textstate));
                *///? } else {
                PacketDistributor.SERVER.noArg().send(new CrazyPhoneMayorsCandidatesButtonMessage(0, x, y, z, textstate));
                //?}
                CrazyPhoneMayorsCandidatesButtonMessage.handleButtonAction(entity, 0, x, y, z, textstate);
				break;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean isHovering(RenderSlot rs, double mouseX, double mouseY) {
		int slotX = this.leftPos + rs.x;
		int slotY = this.topPos + rs.y;
		return mouseX >= slotX && mouseX < slotX + 16 &&
			   mouseY >= slotY && mouseY < slotY + 16;
	}

	private static class RenderSlot {
		final int x, y;
		final ItemStack stack;

		RenderSlot(int x, int y, ItemStack stack) {
			this.x = x;
			this.y = y;
			this.stack = stack;
		}
	}
}
