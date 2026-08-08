package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.TextureCache;
import de.maxhenkel.camera.gui.ImageScreen;
import de.maxhenkel.camera.items.AlbumItem;

import static fr.lordfinn.crazyphone.world.inventory.CrazyPhonePicturesScreenMenu.THUMB_SIZE;

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
		renderThumbnails(guiGraphics);
	}

	private static final int SELECTED_BORDER_COLOR = 0xFFFFC107; // amber - more orange than pure yellow
	private static final int SELECTED_INSET = 2; // per side - 4px total, matching the spec's "shrink by 4px, centered"

	/** Instagram-feed style: each slot draws the real photo, center-cropped to fill the square (not the
	 * vanilla 16x16 item icon - renderSlot() below is overridden to suppress that). A selected photo is
	 * inset by SELECTED_INSET on every side (shrinking it 2*SELECTED_INSET total, still centered on the same
	 * spot) with a solid-color square painted first underneath, so exactly a SELECTED_INSET-wide border of
	 * that color remains visible around the shrunk photo - simpler than drawing 4 separate border strips. */
	private void renderThumbnails(GuiGraphics guiGraphics) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		for (Map.Entry<Integer, Slot> entry : this.menu.get().entrySet()) {
			int visibleIndex = entry.getKey();
			Slot slot = entry.getValue();
			ItemStack stack = slot.getItem();
			if (stack.isEmpty())
				continue;
			ImageData imageData = ImageData.fromStack(stack);
			if (imageData == null || imageData.getId() == null)
				continue;

			int x = this.leftPos + slot.x;
			int y = this.topPos + slot.y;
			boolean selected = selectedSlots.contains(menu.absoluteAlbumIndex(visibleIndex));
			if (selected) {
				guiGraphics.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, SELECTED_BORDER_COLOR);
				drawCroppedImage(guiGraphics, x + SELECTED_INSET, y + SELECTED_INSET,
						THUMB_SIZE - SELECTED_INSET * 2, THUMB_SIZE - SELECTED_INSET * 2, imageData.getId());
			} else {
				drawCroppedImage(guiGraphics, x, y, THUMB_SIZE, THUMB_SIZE, imageData.getId());
			}
		}
		RenderSystem.disableBlend();
	}

	/** Vanilla draws each Slot's item as a 16x16 icon by default - this grid shows real cropped photo
	 * thumbnails instead (see renderThumbnails), so the default draw is suppressed entirely. */
	@Override
	protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
	}

	/** Vanilla's own hover-highlight patch is hardcoded to a 16x16 footprint (AbstractContainerScreen's
	 * private isHovering(Slot,...) can't be overridden to fix that for the real 34x34 thumbnails), and
	 * would just show as a small, oddly-placed square in one corner of each photo - skipped entirely since
	 * the selection border already gives clear feedback for what's actually selected. */
	@Override
	protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
	}

	/** Replaces vanilla's tooltip trigger entirely rather than patching around it - it keys off
	 * hoveredSlot, which is only ever set by that same un-overridable 16x16 isHovering check, so tooltips
	 * would otherwise only appear while hovering a small corner of each 34x34 thumbnail instead of the
	 * whole photo. */
	@Override
	protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!this.menu.getCarried().isEmpty())
			return;
		for (Slot slot : this.menu.get().values()) {
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && isHoveringSlot(slot, mouseX, mouseY)) {
				guiGraphics.renderTooltip(this.font, this.getTooltipFromContainerItem(stack), stack.getTooltipImage(), stack, mouseX, mouseY);
				return;
			}
		}
	}

	/** "Cover" crop: unlike CrazyPhoneMayorCandidateScreenScreen's letterboxing drawImage (which shrinks the
	 * quad to fit inside the box, leaving empty space), this always fills the full width x height target -
	 * the UV rectangle sampled from the source is instead shrunk to the target's aspect ratio and centered,
	 * so the extra dimension gets cropped off rather than shown letterboxed. Center-pivoted per the spec:
	 * for a wider-than-tall source the left/right edges are trimmed equally, and vice versa for taller. */
	private static void drawCroppedImage(GuiGraphics guiGraphics, int x, int y, int width, int height, UUID uuid) {
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		ResourceLocation location = TextureCache.instance().getImage(uuid);

		float srcWidth = 1F;
		float srcHeight = 1F;
		if (location == null) {
			RenderSystem.setShaderTexture(0, ImageScreen.DEFAULT_IMAGE);
		} else {
			RenderSystem.setShaderTexture(0, location);
			NativeImage image = TextureCache.instance().getNativeImage(uuid);
			if (image != null) {
				srcWidth = image.getWidth();
				srcHeight = image.getHeight();
			}
		}

		float uSpan = 1f, vSpan = 1f, uOffset = 0f, vOffset = 0f;
		if (srcWidth > srcHeight) {
			uSpan = srcHeight / srcWidth;
			uOffset = (1f - uSpan) / 2f;
		} else if (srcHeight > srcWidth) {
			vSpan = srcWidth / srcHeight;
			vOffset = (1f - vSpan) / 2f;
		}

		Matrix4f matrix = guiGraphics.pose().last().pose();
		BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.addVertex(matrix, 0, 0, 0).setUv(uOffset, vOffset);
		buffer.addVertex(matrix, 0, height, 0).setUv(uOffset, vOffset + vSpan);
		buffer.addVertex(matrix, width, height, 0).setUv(uOffset + uSpan, vOffset + vSpan);
		buffer.addVertex(matrix, width, 0, 0).setUv(uOffset + uSpan, vOffset);
		BufferUploader.drawWithShader(buffer.buildOrThrow());

		guiGraphics.pose().popPose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (Map.Entry<Integer, Slot> entry : this.menu.get().entrySet()) {
			Slot slot = entry.getValue();
			if (isHoveringSlot(slot, mouseX, mouseY) && slot.getItem() instanceof ItemStack stack && !stack.isEmpty()) {
				int index = entry.getKey();
				if (button == 0) { // Left-click -> select
					playToggleSound();
					// Stored as an ABSOLUTE album index, not the visible grid position - both the delete
					// handler below and the server (CrazyPhoneHelper#deleteSelectedAlbumSlotsFromHeldPhone)
					// read selectedSlots straight into the album's own real slot numbering, which only
					// matches the visible position while unscrolled.
					int absoluteIndex = menu.absoluteAlbumIndex(index);
					if (!selectedSlots.add(absoluteIndex)) {
						selectedSlots.remove(absoluteIndex);
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
		int alteredIndex = adjustIndexByIgnoringEmptySlots(menu.absoluteAlbumIndex(index));
		CameraModHelper.openAlbum(entity, albumStack, alteredIndex);
	}

	/** {@code absoluteIndex} must already be the album's real slot (see CrazyPhonePicturesScreenMenu#absoluteAlbumIndex)
	 * - the zoom viewer re-reads the whole album from its own true slot 0 regardless of how the grid here is
	 * currently scrolled, so this has to count empties over that same absolute range, not the shifted grid. */
	private int adjustIndexByIgnoringEmptySlots(int absoluteIndex) {
		int emptySlot = 0;

		for (int i = 0; i < absoluteIndex; i++) {
			if (menu.getAbsoluteAlbumStack(i).isEmpty()) {
				emptySlot++;
			}
		}

		return absoluteIndex - emptySlot;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// One row per notch, same direction convention as vanilla inventory scrolling (up = toward earlier
		// content) - the album's full picture list already lives client-side in one small, fixed-size
		// vanilla container (see CrazyPhonePicturesScreenMenu), so this just shifts which of it the fixed
		// grid slots show, no network round trip involved.
		if (menu.scrollAlbumBy((int) -Math.signum(scrollY)))
			return true;
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private boolean isHoveringSlot(Slot slot, double mouseX, double mouseY) {
		int x = this.leftPos + slot.x;
		int y = this.topPos + slot.y;
		return mouseX >= x && mouseX < x + THUMB_SIZE && mouseY >= y && mouseY < y + THUMB_SIZE;
	}

	@Override
	protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
		List<Component> lines = new java.util.ArrayList<>(super.getTooltipFromContainerItem(stack));
		// "select"/"zoom in" colored to match their actual on-screen feedback - the same amber the
		// selection border uses, and blue for zoom - so the hint visually pairs with what clicking does,
		// not just a plain gray instruction line.
		lines.add(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_left_click")
				.withStyle(net.minecraft.ChatFormatting.GRAY)
				.append(Component.literal(" "))
				.append(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_select")
						// SELECTED_BORDER_COLOR is ARGB (0xAARRGGBB, for guiGraphics.fill) - Style.withColor(int)
						// wants plain RGB, so the alpha byte has to be masked off or it corrupts the color.
						.withStyle(style -> style.withColor(SELECTED_BORDER_COLOR & 0xFFFFFF)))
				.append(Component.literal(" · ").withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_right_click")
						.withStyle(net.minecraft.ChatFormatting.GRAY))
				.append(Component.literal(" "))
				.append(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_zoom_in")
						.withStyle(net.minecraft.ChatFormatting.BLUE)));
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
