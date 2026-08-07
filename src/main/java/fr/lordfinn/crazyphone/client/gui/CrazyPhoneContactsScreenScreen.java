package fr.lordfinn.crazyphone.client.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.CrazyPhoneContactsScreenButtonMessage;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;

import java.util.HashMap;
import java.util.List;

public class CrazyPhoneContactsScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneContactsScreenMenu> {
	private static final float HEAD_HOVER_GROW_SCALE = 1.15f;
	private final static HashMap<String, Object> guistate = CrazyPhoneContactsScreenMenu.guistate;
	private static final ResourceLocation NOTIFICATION_IMAGE = ResourceLocation.parse("crazyphone:textures/screens/crazyphone-notification.png");
	/** Top-right corner of the header banner (which spans leftPos+4..leftPos+118), with a small margin. */
	private static final int ADD_CONTACT_ICON_X = 100;
	private static final int ADD_CONTACT_ICON_Y = 10;
	private final ItemStack addContactIcon = CrazyPhoneContactsScreenMenu.createAddContactHead();
	private List<String> pendingNotifications;
	private int lastMouseX;
	private int lastMouseY;

	public CrazyPhoneContactsScreenScreen(CrazyPhoneContactsScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		// Load once (example retrieval logic — adjust as needed)
		String playerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
		var tag = PhoneRegistrySavedData.get(entity.level()).phones.get(playerNumber);
		if (tag instanceof CompoundTag compound) {
			ListTag notifList = compound.getList("notifications", ListTag.TAG_STRING);
			this.pendingNotifications = notifList.stream()
				.filter(t -> t instanceof StringTag)
				.map(t -> ((StringTag)t).getAsString())
				.toList();
		}
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(Items.PLAYER_HEAD),
				Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.title"));
		renderAddContactIcon(guiGraphics, mouseX, mouseY);

		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty()) {
				String number = getContactNumber(stack);
				if (number != null && !number.isEmpty() && pendingNotifications.stream().anyMatch(id -> id.contains(number))) {
					int iconX = this.leftPos + slot.x; // Adjust X offset to position the icon
					int iconY = this.topPos + slot.y;   // Adjust Y offset

					guiGraphics.blit(NOTIFICATION_IMAGE, iconX, iconY, 0, 0, 18, 18, 18, 18); // assuming icon is 8x8
				}
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
		if (isHoveringAddContactIcon(mouseX, mouseY)) {
			PacketDistributor.sendToServer(new CrazyPhoneContactsScreenButtonMessage(0, x, y, z, textstate));
			CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, textstate);
			return true;
		}
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (isHovering(slot, mouseX, mouseY)) {
				ItemStack clickedStack = slot.getItem();
				textstate.put("contactNumber", getContactNumber(clickedStack));
				PacketDistributor.sendToServer(new CrazyPhoneContactsScreenButtonMessage(1, x, y, z, textstate));
				CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, textstate);
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void renderAddContactIcon(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int iconX = this.leftPos + ADD_CONTACT_ICON_X;
		int iconY = this.topPos + ADD_CONTACT_ICON_Y;
		if (isHoveringAddContactIcon(mouseX, mouseY)) {
			CursorEffects.requestPointerCursor();
			guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
		}
		guiGraphics.renderItem(addContactIcon, iconX, iconY);
		if (isHoveringAddContactIcon(mouseX, mouseY)) {
			guiGraphics.renderTooltip(this.font, addContactIcon, mouseX, mouseY);
		}
	}

	private boolean isHoveringAddContactIcon(double mouseX, double mouseY) {
		int iconX = this.leftPos + ADD_CONTACT_ICON_X;
		int iconY = this.topPos + ADD_CONTACT_ICON_Y;
		return mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
	}

	public static String getContactNumber(ItemStack head) {
        return (head.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("number"));
    }

	private boolean isHovering(Slot slot, double mouseX, double mouseY) {
		int slotX = slot.x + leftPos;
		int slotY = slot.y + topPos;
		return mouseX >= slotX && mouseX < slotX + 16 &&
			   mouseY >= slotY && mouseY < slotY + 16;
	}

	@Override
	protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
		if (slot.getItem().isEmpty() || !isHovering(slot, lastMouseX, lastMouseY)) {
			super.renderSlot(guiGraphics, slot);
			return;
		}

		CursorEffects.requestPointerCursor();

		// AbstractContainerScreen.render() already translates the pose stack by (leftPos, topPos) before
		// calling renderSlot(), so the pivot here must be relative to THAT already-shifted origin (just
		// slot.x/slot.y) - adding leftPos/topPos again offset the pivot, which visibly shifted the grown
		// icon to the left/up instead of growing it in place.
		int centerX = slot.x + 8;
		int centerY = slot.y + 8;

		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(centerX, centerY, 0);
		guiGraphics.pose().scale(HEAD_HOVER_GROW_SCALE, HEAD_HOVER_GROW_SCALE, 1.0f);
		guiGraphics.pose().translate(-centerX, -centerY, 0);
		super.renderSlot(guiGraphics, slot);
		guiGraphics.pose().popPose();
	}
}
