package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePicturesScreenMenu;
import fr.lordfinn.crazyphone.network.CrazyPhonePicturesScreenButtonMessage;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.systems.RenderSystem;

import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.gui.AlbumScreen;
import de.maxhenkel.camera.items.AlbumItem;
import de.maxhenkel.camera.items.ImageItem;

public class CrazyPhonePicturesScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhonePicturesScreenMenu> {
	private final static HashMap<String, Object> guistate = CrazyPhonePicturesScreenMenu.guistate;
	Button button_retour;
	Button button_del;
	Button button_take;
	Button button_send;
	boolean isSendMode = false;
	private final Set<Integer> selectedSlots = new HashSet<>();
	public int albumId;

	public CrazyPhonePicturesScreenScreen(CrazyPhonePicturesScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.albumId = container.albumId;
		PlayerPhoneState playerData = this.entity.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);
		List<String> screenHistory = ScreenMenuUtils.getScreenHistory(playerData.crazyPhoneScreenHistory);
		if (screenHistory.size() >= 3) {
			String potentialConversationPage = screenHistory.get(screenHistory.size() - 3);
			if (potentialConversationPage.contains("crazy_phone_conversation"))
				isSendMode = true;
		}
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		Component title = menu.albumStack.isEmpty() ? Component.translatable("gui.crazyphone.crazy_phone_picture_folders_screen.title")
				: menu.albumStack.getHoverName();
		renderHeader(guiGraphics, menu.albumStack, title);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		super.renderBg(guiGraphics, partialTicks, gx, gy);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		for (int index : selectedSlots) {
			Slot slot = menu.get().get(index);
			if (slot != null) {
				int x = this.leftPos + slot.x;
				int y = this.topPos + slot.y;
				guiGraphics.blit(ResourceLocation.parse("crazyphone:textures/screens/slot_selected.png"), x-1, y-1, 0, 0,
						18, 18,18, 18);
			}
		}
		RenderSystem.disableBlend();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Map.Entry<Integer, Slot> entry : this.menu.get().entrySet()) {
			Slot slot = entry.getValue();
			if (isHoveringSlot(slot, mouseX, mouseY) && slot.getItem() instanceof ItemStack stack && !stack.isEmpty()) {
				int index = entry.getKey();
				if (button == 0) { // Left-click -> select
					playToggleSound();
					if (!selectedSlots.add(index)) {
						selectedSlots.remove(index);
					}
					updateActionButtonsState();
					return true;
				} else if (button == 1) { // Right-click -> zoom
					onSlotZoomClick(index);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void onSlotZoomClick(int index) {
		IItemHandlerModifiable handler = CrazyPhoneHelper.getPhoneItemHandler(entity);
        ItemStack albumStack = CrazyPhoneHelper.getAlbumFromPhoneHandler (handler,albumId);
		int alteredIndex = adjustIndexByIgnoringEmptySlots(index);
		CameraModHelper.openAlbum(entity, albumStack, alteredIndex);
	}

	private int adjustIndexByIgnoringEmptySlots(int originalIndex) {
		int emptySlot = 0;

		for (int i = 0; i < originalIndex; i++) {
			if (menu.internal.getStackInSlot(i).isEmpty()) {
				emptySlot++;
			}
		}

		return originalIndex - emptySlot;
	}

	private boolean isHoveringSlot(Slot slot, double mouseX, double mouseY) {
		int x = this.leftPos + slot.x;
		int y = this.topPos + slot.y;
		return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> lines = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
		lines.add(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_image_actions")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		return lines;
	}

	@Override
	public void init() {
		super.init();

		if (isSendMode) {
			button_send = Button
			.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_send"), e -> {
				if (!selectedSlots.isEmpty()) {
					HashMap<String, String> values = new HashMap<>();
					// Pack the selected slots into a comma-separated string
					StringBuilder slotList = new StringBuilder();
					for (int i : selectedSlots) {
						if (slotList.length() > 0) slotList.append(",");
						slotList.append(i);
					}
					values.put("selectedSlots", slotList.toString());
					values.put("albumIndex", String.valueOf(albumId));
					PacketDistributor.sendToServer(new CrazyPhonePicturesScreenButtonMessage(2, x, y, z, values));
					Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
				}
				selectedSlots.clear();
				updateActionButtonsState();
			}).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
			guistate.put("button:button_send", button_send);
			this.addRenderableWidget(button_send);
			updateActionButtonsState();
		} else {
		button_del = Button
		.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_delete"), e -> {
			if (!selectedSlots.isEmpty()) {
				HashMap<String, String> values = new HashMap<>();
				// Pack the selected slots into a comma-separated string
				StringBuilder slotList = new StringBuilder();
				for (int i : selectedSlots) {
					if (slotList.length() > 0) slotList.append(",");
					slotList.append(i);
					if (menu.internal instanceof CrazyPhonePicturesScreenMenu.AlbumInventoryItemHandler handler) {
						handler.albumInventory.setItem(i, ItemStack.EMPTY);
					}
				}
				values.put("selectedSlots", slotList.toString());
				values.put("albumIndex", String.valueOf(albumId));
				PacketDistributor.sendToServer(new CrazyPhonePicturesScreenButtonMessage(0, x, y, z, values));
				CrazyPhoneHelper.deleteSelectedAlbumSlotsFromHeldPhone(entity, world, selectedSlots, albumId);
			}
			selectedSlots.clear(); // Clear selection client-side for UI
			updateActionButtonsState();
			Minecraft.getInstance().player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
		}).bounds(this.leftPos + 62, this.topPos + 158, 52, 14).build();
		guistate.put("button:button_del", button_del);
		this.addRenderableWidget(button_del);

		button_take = Button
		.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_take"), e -> {
			if (!selectedSlots.isEmpty()) {
				HashMap<String, String> values = new HashMap<>();
				// Pack the selected slots into a comma-separated string
				StringBuilder slotList = new StringBuilder();
				for (int i : selectedSlots) {
					if (slotList.length() > 0) slotList.append(",");
					slotList.append(i);
				}
				values.put("selectedSlots", slotList.toString());
				values.put("albumIndex", String.valueOf(albumId));
				PacketDistributor.sendToServer(new CrazyPhonePicturesScreenButtonMessage(1, x, y, z, values));
				Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
			}
			selectedSlots.clear();
			updateActionButtonsState();
		}).bounds(this.leftPos + 8, this.topPos + 158, 52, 14).build();
		guistate.put("button:button_take", button_take);
		this.addRenderableWidget(button_take);
		updateActionButtonsState();
		}
	}

	/** button_take/button_del/button_send are only meaningful once at least one picture is selected (left-click) - grayed out and explained via tooltip otherwise; still keep an explanatory tooltip once active instead of clearing it. */
	private void updateActionButtonsState() {
		boolean hasSelection = !selectedSlots.isEmpty();
		Tooltip selectImageHint = Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_select_image"));
		if (button_del != null) {
			button_del.active = hasSelection;
			button_del.setTooltip(hasSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_delete_selected"))
					: selectImageHint);
		}
		if (button_take != null) {
			button_take.active = hasSelection;
			button_take.setTooltip(hasSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_take_selected"))
					: selectImageHint);
		}
		if (button_send != null) {
			button_send.active = hasSelection;
			button_send.setTooltip(hasSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_send_selected"))
					: selectImageHint);
		}
	}

	private void playToggleSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
