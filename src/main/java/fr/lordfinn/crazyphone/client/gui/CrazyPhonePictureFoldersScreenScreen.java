package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePictureFoldersScreenMenu;
import fr.lordfinn.crazyphone.network.PictureFoldersSlotClickMessage;

import java.util.HashMap;

import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.items.AlbumItem;

public class CrazyPhonePictureFoldersScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhonePictureFoldersScreenMenu> {
	final static private HashMap<String, Object> guistate= CrazyPhonePictureFoldersScreenMenu.guistate;

	public CrazyPhonePictureFoldersScreenScreen(CrazyPhonePictureFoldersScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		HashMap<String, String> textstate = new HashMap<>();
		if (Minecraft.getInstance().screen instanceof CrazyPhonePictureFoldersScreenScreen sc) {

		}
		return textstate;
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(Main.ALBUM.get()),
				Component.translatable("gui.crazyphone.crazy_phone_picture_folders_screen.title"));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (isHovering(slot, mouseX, mouseY)) {
				ItemStack clickedStack = slot.getItem();
				if (clickedStack.getItem() instanceof AlbumItem) {
					PacketDistributor.sendToServer(new PictureFoldersSlotClickMessage(i));
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean isHovering(Slot slot, double mouseX, double mouseY) {
		int slotX = slot.x + leftPos;
		int slotY = slot.y + topPos;
		return mouseX >= slotX && mouseX < slotX + 16 &&
			   mouseY >= slotY && mouseY < slotY + 16;
	}

	@Override
	protected java.util.List<Component> getTooltipFromContainerItem(ItemStack stack) {
		if (!(stack.getItem() instanceof AlbumItem))
			return super.getTooltipFromContainerItem(stack);
		java.util.List<Component> lines = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
		lines.add(Component.translatable("gui.crazyphone.crazy_phone_picture_folders_screen.tooltip_open_album")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		return lines;
	}

	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (key == 256) {
			this.minecraft.player.closeContainer();
			return true;
		}
		return super.keyPressed(key, b, c);
	}
}
