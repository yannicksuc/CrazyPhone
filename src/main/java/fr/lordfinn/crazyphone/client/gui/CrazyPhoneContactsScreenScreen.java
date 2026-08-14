package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.network.CrazyPhoneContactsScreenButtonMessage;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Favorites, regular contacts, and groups are laid out as three stacked sections (favorites pinned at the
 * top, then contacts - which also always carries the "add contact" tile - then groups, each preceded by a
 * dashed section title) inside a shared scrollable grid, clipped with a scissor rather than the earlier
 * row-quantized visibility check since title rows and item rows no longer share one row height. None of
 * the entries are real vanilla Slots - Slot#x/y are final, so a Slot can't be repositioned once created,
 * which a scrollable/resortable grid needs to do every frame. Rendering and click hit-testing are done
 * manually instead, the same way group icons always were here.
 */
public class CrazyPhoneContactsScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneContactsScreenMenu> {
	private static final float HEAD_HOVER_GROW_SCALE = 1.15f;
	private final static HashMap<String, Object> guistate = CrazyPhoneContactsScreenMenu.guistate;
	private static final ResourceLocation NOTIFICATION_IMAGE = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-notification.png");
	private static final ResourceLocation IN_CALL_BADGE_IMAGE = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-in-call-badge.png");
	/** Amber variant of the badge above - a call is active for this conversation, but it's not the local
	 * player's own currently-active call (they left it, or were never on it) - see
	 * ClientCallState#hasJoinableCallElsewhere. */
	private static final ResourceLocation REJOIN_CALL_BADGE_IMAGE = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-rejoin-call-badge.png");
	/** Player heads with a resolved skin profile render as a 3D skull model that visually reads larger
	 * than other flat icons next to it - scaled down here (matching the add-contact tile) so they read as
	 * the same size. */
	private static final float ADD_CONTACT_ICON_SCALE = 0.95f;
	/** How long each member's head stays on screen before the group icon cycles to the next one. */
	private static final long GROUP_HEAD_CYCLE_INTERVAL_MS = 1000;
	private static final int GRID_COLUMNS = CrazyPhoneDefaultScreenMenu.GRID_COLUMNS;
	private static final int SLOT_PITCH = CrazyPhoneDefaultScreenMenu.SLOT_PITCH;
	private static final int GRID_START_X = CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_X;
	private static final int GRID_START_Y = CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_Y;
	private static final int GRID_WIDTH = GRID_COLUMNS * SLOT_PITCH;
	/** The grid is scrollable (favorites/contacts/groups together can outgrow the phone frame) - this is
	 * how many pixels fit between the header and the row of action buttons. */
	/** Nudges the whole scrollable grid (titles + items) 1px down from the shared header-content offset,
	 * without touching CrazyPhoneDefaultScreenMenu.HEADER_CONTENT_START_Y itself (shared by every screen). */
	private static final int CONTENT_Y_OFFSET = 1;
	private static final int VIEWPORT_TOP_Y = GRID_START_Y + CONTENT_Y_OFFSET;
	private static final int VIEWPORT_HEIGHT = 126;
	private static final int VIEWPORT_BOTTOM_Y = VIEWPORT_TOP_Y + VIEWPORT_HEIGHT;
	private static final int SCROLL_STEP = SLOT_PITCH;
	/** Section title row height (font line height) and the flat 2px gap used everywhere - before and
	 * after every title, and between one section's last item row and the next section's title. */
	private static final int TITLE_HEIGHT = 9;
	private static final int SECTION_GAP = 2;
	private static final float SECTION_TITLE_SCALE = 0.85f;
	private static final int SECTION_TITLE_COLOR = 0x6C8EBF;
	/** Right edge the section title's separator line reaches - aligned with the action buttons' own right
	 * edge (see the button bounds() calls in init()), not the item grid's own (slightly wider) edge. */
	private static final int TITLE_LINE_END_X = 114;
	private final ItemStack addContactIcon = CrazyPhoneContactsScreenMenu.createAddContactHead();
	private List<String> pendingNotifications;
	private int scrollPosition = 0;
	/** Indices into the combined favorites-then-contacts list (favorites first, matching the section
	 * order), NOT tied to any Slot and NOT counting the "add contact" tile - selection survives
	 * scroll/resort since it's purely by identity index. */
	private final Set<Integer> selectedSlots = new HashSet<>();
	private Button button_creategroup;
	private Button button_remove;
	private Button button_favorite;
	private final String ownerNumber;
	private Layout layout;

	public CrazyPhoneContactsScreenScreen(CrazyPhoneContactsScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		this.ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
		var tag = PhoneRegistrySavedData.get(entity.level()).phones.get(ownerNumber);
		if (tag instanceof CompoundTag compound) {
			ListTag notifList = fr.lordfinn.crazyphone.utils.NbtCompat.getList(compound, "notifications", ListTag.TAG_STRING);
			this.pendingNotifications = notifList.stream()
				.filter(t -> t instanceof StringTag)
				.map(t -> fr.lordfinn.crazyphone.utils.NbtCompat.asString(t))
				.toList();
		}
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		super.renderBg(guiGraphics, partialTicks, gx, gy);
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		guiGraphics.enableScissor(scissorX0(), scissorY0(), scissorX1(), scissorY1());
		for (int index : selectedSlots) {
			int[] pos = personPosByFlatIndex(index);
			if (pos != null) {
				guiGraphics.blit(Crazyphone.parseId("crazyphone:textures/screens/slot_selected.png"), pos[0] - 1, pos[1] - 1, 0, 0,
						18, 18, 18, 18);
			}
		}
		guiGraphics.disableScissor();
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
	}

	@Override
	public void init() {
		super.init();

		button_creategroup = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.button_create_group"), e -> {
			if (selectedSlots.size() >= 2) {
				HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
				textstate.put("selectedNumbers", joinSelectedNumbers());
				//? if >=1.20.5 {
				/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(2, x, y, z, textstate));
				*///? } else {
				PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(2, x, y, z, textstate));
				//?}
				CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 2, x, y, z, textstate);
			}
		}).bounds(this.leftPos + 8, this.topPos + 158, 74, 14).build();
		guistate.put("button:button_creategroup", button_creategroup);
		this.addRenderableWidget(button_creategroup);

		button_remove = createSquareIconButton(this.leftPos + 84, this.topPos + 158,
				Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.button_remove").withStyle(ChatFormatting.RED), e -> {
			if (!selectedSlots.isEmpty()) {
				HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
				textstate.put("selectedNumbers", joinSelectedNumbers());
				//? if >=1.20.5 {
				/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(3, x, y, z, textstate));
				*///? } else {
				PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(3, x, y, z, textstate));
				//?}
				CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 3, x, y, z, textstate);
			}
			selectedSlots.clear();
			updateActionButtonsState();
		});
		guistate.put("button:button_remove", button_remove);
		this.addRenderableWidget(button_remove);

		button_favorite = createSquareIconButton(this.leftPos + 100, this.topPos + 158,
				Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.button_favorite").withStyle(ChatFormatting.GOLD), e -> {
			if (!selectedSlots.isEmpty()) {
				HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
				textstate.put("selectedNumbers", joinSelectedNumbers());
				//? if >=1.20.5 {
				/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(5, x, y, z, textstate));
				*///? } else {
				PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(5, x, y, z, textstate));
				//?}
				CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 5, x, y, z, textstate);
			}
			selectedSlots.clear();
			updateActionButtonsState();
		});
		guistate.put("button:button_favorite", button_favorite);
		this.addRenderableWidget(button_favorite);

		updateActionButtonsState();
	}

	/**
	 * A 14x14 square Button showing a single centered icon glyph. Vanilla's own text centering truncates
	 * (buttonWidth - textWidth)/2 to an int, which for an odd leftover visibly biases the glyph a pixel
	 * off-center (e.g. 2px clearance on one side, 3px on the other) - drawing the icon ourselves with a
	 * 0.5px sub-pixel pose translate lands it exactly in the middle instead. The real button background
	 * still comes from vanilla (via super.renderWidget with a blanked-out message), so hover/press/disabled
	 * states keep working normally.
	 */
	private Button createSquareIconButton(int x, int y, Component icon, Button.OnPress onPress) {
		return new Button(x, y, 14, 14, icon, onPress, supplier -> icon.copy()) {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
				Component message = getMessage();
				setMessage(Component.empty());
				super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
				setMessage(message);

				var font = Minecraft.getInstance().font;
				int textWidth = font.width(message);
				int drawX = getX() + (getWidth() - textWidth) / 2;
				int drawY = getY() + (getHeight() - 8) / 2;
				GuiCompat.pushPose(guiGraphics);
				GuiCompat.translate(guiGraphics, 0.5f, 0f);
				guiGraphics.drawString(font, message, drawX, drawY, 0xFFFFFF, true);
				GuiCompat.popPose(guiGraphics);
			}
		};
	}

	private String joinSelectedNumbers() {
		StringBuilder csv = new StringBuilder();
		for (int index : selectedSlots) {
			Contact person = personAt(index);
			if (person == null)
				continue;
			if (csv.length() > 0)
				csv.append(",");
			csv.append(person.getNumber());
		}
		return csv.toString();
	}

	/** button_creategroup needs 2+ selected contacts (a "group" of one other person is just the regular
	 * conversation); button_remove and button_favorite work from 1+. All three stay greyed with a hint
	 * until then, like the picture screen's action buttons. */
	private void updateActionButtonsState() {
		boolean hasSelection = !selectedSlots.isEmpty();
		boolean hasGroupSelection = selectedSlots.size() >= 2;
		Tooltip selectHint = Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_select_contact"));
		if (button_creategroup != null) {
			button_creategroup.active = hasGroupSelection;
			button_creategroup.setTooltip(hasGroupSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_create_group"))
					: selectHint);
		}
		if (button_remove != null) {
			button_remove.active = hasSelection;
			button_remove.setTooltip(hasSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_remove"))
					: selectHint);
		}
		if (button_favorite != null) {
			button_favorite.active = hasSelection;
			button_favorite.setTooltip(hasSelection
					? Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_favorite"))
					: selectHint);
		}
	}

	private void playToggleSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	/** Pixel Y offsets (relative to the grid's own top, i.e. before adding GRID_START_Y/topPos/scroll) for
	 * every section's title and item-grid start, computed fresh each frame from the current
	 * favorites/contacts/groups counts. The "add contact" tile always occupies the first cell of the
	 * contacts section, so that section is never actually empty. */
	private static final class Layout {
		int favTitleY, favItemsY;
		int contactsTitleY, contactsItemsY;
		int groupsTitleY, groupsItemsY;
		int totalHeight;
	}

	/** Favorites and Groups always render their title (even with nobody in them yet), so the section
	 * headers stay in a stable, predictable place instead of popping in and out as membership changes. */
	private Layout computeLayout() {
		Layout l = new Layout();
		int cursor = 0;
		List<Contact> favorites = menu.getFavorites();
		List<Contact> contacts = menu.getContacts();

		l.favTitleY = cursor;
		cursor += TITLE_HEIGHT + SECTION_GAP;
		l.favItemsY = cursor;
		cursor += ceilDiv(favorites.size()) * SLOT_PITCH;
		cursor += SECTION_GAP;

		l.contactsTitleY = cursor;
		cursor += TITLE_HEIGHT + SECTION_GAP;
		l.contactsItemsY = cursor;
		cursor += ceilDiv(contacts.size() + 1) * SLOT_PITCH; // +1 reserves the leading "add contact" tile
		cursor += SECTION_GAP;

		l.groupsTitleY = cursor;
		cursor += TITLE_HEIGHT + SECTION_GAP;
		l.groupsItemsY = cursor;
		cursor += ceilDiv(menu.getGroups().size()) * SLOT_PITCH;

		l.totalHeight = cursor;
		return l;
	}

	private static int ceilDiv(int count) {
		return (count + GRID_COLUMNS - 1) / GRID_COLUMNS;
	}

	private int maxScroll() {
		return Math.max(0, layout.totalHeight - VIEWPORT_HEIGHT);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.layout = computeLayout();
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(Items.WRITABLE_BOOK),
				Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.title"));

		guiGraphics.enableScissor(scissorX0(), scissorY0(), scissorX1(), scissorY1());

		List<Component> hoveredTooltip = null;
		List<Component> t;

		drawSectionTitle(guiGraphics, layout.favTitleY, sectionFavoritesTitle());
		t = renderPersonSection(guiGraphics, mouseX, mouseY, menu.getFavorites(), layout.favItemsY, sectionFavoritesTitle());
		if (t != null)
			hoveredTooltip = t;

		drawSectionTitle(guiGraphics, layout.contactsTitleY, sectionContactsTitle());
		if (renderAddContactTile(guiGraphics, mouseX, mouseY))
			hoveredTooltip = List.of(addContactIcon.getHoverName());
		t = renderContactsSection(guiGraphics, mouseX, mouseY);
		if (t != null)
			hoveredTooltip = t;

		drawSectionTitle(guiGraphics, layout.groupsTitleY, sectionGroupsTitle());
		t = renderGroupIcons(guiGraphics, mouseX, mouseY);
		if (t != null)
			hoveredTooltip = t;

		guiGraphics.disableScissor();

		if (hoveredTooltip != null)
			guiGraphics.renderTooltip(this.font, hoveredTooltip.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
	}

	private int scissorX0() {
		return this.leftPos + GRID_START_X;
	}

	private int scissorX1() {
		return this.leftPos + GRID_START_X + GRID_WIDTH;
	}

	private int scissorY0() {
		return this.topPos + VIEWPORT_TOP_Y;
	}

	private int scissorY1() {
		return this.topPos + VIEWPORT_BOTTOM_Y;
	}

	private Component sectionFavoritesTitle() {
		return Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.section_favorites");
	}

	private Component sectionContactsTitle() {
		return Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.section_contacts");
	}

	private Component sectionGroupsTitle() {
		return Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.section_groups");
	}

	/** Draws a section title flush to the grid's left edge, followed by enough dashes to fill the rest of
	 * the row - the dash count is computed from the title's own pixel width so it always reaches the same
	 * right edge regardless of word length. */
	/** Draws a section title flush to the grid's left edge, followed by a real 1px-tall separator line
	 * (not dashed text) reaching exactly TITLE_LINE_END_X regardless of word length. */
	private void drawSectionTitle(GuiGraphics guiGraphics, int relY, Component word) {
		String text = word.getString();
		int drawX = this.leftPos + GRID_START_X;
		int drawY = this.topPos + GRID_START_Y + CONTENT_Y_OFFSET + relY - scrollPosition;
		GuiCompat.pushPose(guiGraphics);
		GuiCompat.translate(guiGraphics, drawX, drawY);
		GuiCompat.scale(guiGraphics, SECTION_TITLE_SCALE, SECTION_TITLE_SCALE);
		guiGraphics.drawString(this.font, text, 0, 0, SECTION_TITLE_COLOR, false);
		GuiCompat.popPose(guiGraphics);

		int textWidthPx = Math.round(this.font.width(text) * SECTION_TITLE_SCALE);
		int lineStartX = drawX + textWidthPx + 2;
		int lineEndX = this.leftPos + TITLE_LINE_END_X;
		int lineY = drawY + Math.round(TITLE_HEIGHT * SECTION_TITLE_SCALE / 2f);
		if (lineEndX > lineStartX) {
			GuiCompat.pushPose(guiGraphics);
			GuiCompat.translate(guiGraphics, 0f, -0.5f);
			// fill() needs an explicit alpha channel, unlike drawString() - the color constant has none.
			guiGraphics.fill(lineStartX, lineY, lineEndX, lineY + 1, 0xFF000000 | SECTION_TITLE_COLOR);
			GuiCompat.popPose(guiGraphics);
		}
	}

	/** Absolute on-screen position (already including leftPos/topPos and scroll) of the
	 * indexWithinSection-th cell of a section whose item grid starts at relItemsY. */
	private int[] posAt(int relItemsY, int indexWithinSection) {
		int row = indexWithinSection / GRID_COLUMNS;
		int col = indexWithinSection % GRID_COLUMNS;
		int x = this.leftPos + GRID_START_X + col * SLOT_PITCH;
		int y = this.topPos + GRID_START_Y + CONTENT_Y_OFFSET + relItemsY + row * SLOT_PITCH - scrollPosition;
		return new int[]{x, y};
	}

	/** Resolves a flat index (favorites first, then regular contacts - see {@link #selectedSlots}) to its
	 * on-screen position, or null if that index no longer exists (e.g. the contact list changed). Contacts
	 * are offset by 1 within their section to leave room for the leading "add contact" tile. */
	private int[] personPosByFlatIndex(int flatIndex) {
		int favCount = menu.getFavorites().size();
		if (flatIndex >= 0 && flatIndex < favCount)
			return posAt(layout.favItemsY, flatIndex);
		int idx = flatIndex - favCount;
		if (idx >= 0 && idx < menu.getContacts().size())
			return posAt(layout.contactsItemsY, idx + 1);
		return null;
	}

	private Contact personAt(int flatIndex) {
		List<Contact> favorites = menu.getFavorites();
		if (flatIndex >= 0 && flatIndex < favorites.size())
			return favorites.get(flatIndex);
		List<Contact> contacts = menu.getContacts();
		int idx = flatIndex - favorites.size();
		if (idx >= 0 && idx < contacts.size())
			return contacts.get(idx);
		return null;
	}

	/** Renders the favorites section (favorites always start at index 0 of their own section, no leading
	 * tile). Returns the hovered person's tooltip lines, or null. */
	private List<Component> renderPersonSection(GuiGraphics guiGraphics, int mouseX, int mouseY, List<Contact> people, int relItemsY, Component sectionLabel) {
		List<Component> hoveredTooltip = null;
		for (int i = 0; i < people.size(); i++) {
			int[] pos = posAt(relItemsY, i);
			Contact person = people.get(i);
			if (drawPersonTile(guiGraphics, mouseX, mouseY, pos, person))
				hoveredTooltip = List.of(
						CrazyPhoneHelper.formatContactDisplayName(person.getName(), person.getNumber()),
						sectionLabel.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		return hoveredTooltip;
	}

	/** Renders the contacts section - real contacts only, starting at index 1 (index 0 is the "add
	 * contact" tile, drawn separately by {@link #renderAddContactTile}). Returns the hovered contact's
	 * tooltip lines, or null. */
	private List<Component> renderContactsSection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		List<Contact> contacts = menu.getContacts();
		List<Component> hoveredTooltip = null;
		for (int i = 0; i < contacts.size(); i++) {
			int[] pos = posAt(layout.contactsItemsY, i + 1);
			Contact person = contacts.get(i);
			if (drawPersonTile(guiGraphics, mouseX, mouseY, pos, person))
				hoveredTooltip = List.of(
						CrazyPhoneHelper.formatContactDisplayName(person.getName(), person.getNumber()),
						sectionContactsTitle().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		return hoveredTooltip;
	}

	/** Head icon + hover-grow + notification badge for one person tile - shared by the favorites and
	 * contacts sections. Returns whether this tile is currently hovered. */
	private boolean drawPersonTile(GuiGraphics guiGraphics, int mouseX, int mouseY, int[] pos, Contact person) {
		int iconX = pos[0];
		int iconY = pos[1];
		boolean hovered = mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
		if (hovered)
			CursorEffects.requestPointerCursor();

		ItemStack head = CrazyPhoneHelper.createContactHead(person);
		renderIconScaled(guiGraphics, iconX, iconY, head, hovered ? HEAD_HOVER_GROW_SCALE : 1.0f);

		// Exact match against this contact's own 1:1 conversation id - a plain .contains(number) check
		// previously lit this badge up for group conversations too, since a group id like
		// "111.222.333" contains "111" as a substring even though the unread message isn't in the 1:1
		// conversation with that contact at all.
		String oneOnOneId = CrazyPhoneHelper.getConversationNumber(person.getNumber(), ownerNumber);
		if (pendingNotifications != null && pendingNotifications.contains(oneOnOneId)) {
			guiGraphics.blit(NOTIFICATION_IMAGE, iconX, iconY, 0, 0, 18, 18, 18, 18);
		}

		// Bottom-left corner, distinct from the top-left unread-message badge above - this contact's own
		// number has to actually be on the local player's currently ACTIVE call, not just "some call
		// somewhere", the same per-number gating ClientCallState.numberHasState was built for.
		if (ClientCallState.numberHasState(person.getNumber(), State.ACTIVE)) {
			guiGraphics.blit(IN_CALL_BADGE_IMAGE, iconX, iconY + 2, 0, 0, 14, 14, 14, 14);
		} else if (ClientCallState.hasJoinableCallElsewhere(oneOnOneId)) {
			guiGraphics.blit(REJOIN_CALL_BADGE_IMAGE, iconX, iconY + 2, 0, 0, 14, 14, 14, 14);
		}

		return hovered;
	}

	/** Draws a single 16x16 icon scaled around its own center - scale 1.0f is a plain unscaled draw, used
	 * for both the "shrink to match other icons" correction (add-contact tile) and the "grow while
	 * hovered" effect (contact/favorite heads, the add-contact tile, group icons), which stack by just
	 * multiplying the two scale factors together. */
	private void renderIconScaled(GuiGraphics guiGraphics, int iconX, int iconY, ItemStack stack, float scale) {
		int centerX = iconX + 8;
		int centerY = iconY + 8;
		GuiCompat.pushPose(guiGraphics);
		GuiCompat.translate(guiGraphics, centerX, centerY);
		GuiCompat.scale(guiGraphics, scale, scale);
		GuiCompat.translate(guiGraphics, -centerX, -centerY);
		guiGraphics.renderItem(stack, iconX, iconY);
		GuiCompat.popPose(guiGraphics);
	}

	/** The "add contact" tile - always the first cell of the contacts section. Returns whether hovered. */
	private boolean renderAddContactTile(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		int[] pos = posAt(layout.contactsItemsY, 0);
		int iconX = pos[0];
		int iconY = pos[1];
		boolean hovered = mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
		if (hovered) {
			CursorEffects.requestPointerCursor();
			guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
		}

		float scale = ADD_CONTACT_ICON_SCALE * (hovered ? HEAD_HOVER_GROW_SCALE : 1.0f);
		renderIconScaled(guiGraphics, iconX, iconY, addContactIcon, scale);

		return hovered;
	}

	private boolean isHoveringAddContactTile(double mouseX, double mouseY) {
		int[] pos = posAt(layout.contactsItemsY, 0);
		return mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16;
	}

	/** Groups aren't real Slots (a Slot can only show one static ItemStack), so they're rendered manually,
	 * in their own section below favorites/contacts, cycling through each member's head over time so a
	 * group with no custom icon still reads as "these people". Returns the hovered group's tooltip lines,
	 * or null. */
	private List<Component> renderGroupIcons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		List<CrazyPhoneContactsScreenMenu.GroupInfo> groups = menu.getGroups();
		long time = System.currentTimeMillis();
		List<Component> hoveredTooltip = null;
		for (int i = 0; i < groups.size(); i++) {
			CrazyPhoneContactsScreenMenu.GroupInfo group = groups.get(i);
			List<Contact> members = group.members();
			if (members.isEmpty())
				continue;

			int[] pos = groupIconPos(i);
			int iconX = pos[0];
			int iconY = pos[1];
			boolean hovered = mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
			if (hovered) {
				CursorEffects.requestPointerCursor();
			}

			ItemStack icon = resolveGroupIcon(group, members, time);
			renderIconScaled(guiGraphics, iconX, iconY, icon, hovered ? HEAD_HOVER_GROW_SCALE : 1.0f);

			if (pendingNotifications != null && pendingNotifications.contains(group.conversationId())) {
				guiGraphics.blit(NOTIFICATION_IMAGE, iconX, iconY, 0, 0, 18, 18, 18, 18);
			}

			// Same rejoin badge as a 1:1 contact - a group call has no equivalent "am I on MY OWN active
			// call here" green state to prefer, since ClientCallState only ever tracks one call's numbers
			// (a 1:1 conversation's own contact number), not a group conversation id - hasJoinableCallElsewhere
			// already excludes the local player's own active call by conversation id, so this alone is correct.
			if (ClientCallState.hasJoinableCallElsewhere(group.conversationId())) {
				guiGraphics.blit(REJOIN_CALL_BADGE_IMAGE, iconX, iconY + 2, 0, 0, 14, 14, 14, 14);
			}

			if (hovered) {
				hoveredTooltip = List.of(
						CrazyPhoneHelper.formatGroupDisplayName(group.name(), members),
						sectionGroupsTitle().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
		return hoveredTooltip;
	}

	/** The group's custom icon (set via the group settings screen) if one is set, otherwise a member head
	 * that cycles over time so a group with no custom icon still reads as "these people". */
	private ItemStack resolveGroupIcon(CrazyPhoneContactsScreenMenu.GroupInfo group, List<Contact> members, long time) {
		if (!group.icon().isEmpty())
			return group.icon();
		int memberIndex = (int) ((time / GROUP_HEAD_CYCLE_INTERVAL_MS) % members.size());
		return CrazyPhoneHelper.createContactHead(members.get(memberIndex));
	}

	/** Grid position of the i-th group entry, in the groups section (below favorites/contacts). */
	private int[] groupIconPos(int i) {
		return posAt(layout.groupsItemsY, i);
	}

	private int hoveredGroupIndex(double mouseX, double mouseY) {
		List<CrazyPhoneContactsScreenMenu.GroupInfo> groups = menu.getGroups();
		for (int i = 0; i < groups.size(); i++) {
			int[] pos = groupIconPos(i);
			if (mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16) {
				return i;
			}
		}
		return -1;
	}

	private int hoveredPersonIndex(double mouseX, double mouseY) {
		int favCount = menu.getFavorites().size();
		int total = favCount + menu.getContacts().size();
		for (int i = 0; i < total; i++) {
			int[] pos = personPosByFlatIndex(i);
			if (pos == null)
				continue;
			if (mouseX >= pos[0] && mouseX < pos[0] + 16 && mouseY >= pos[1] && mouseY < pos[1] + 16) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.layout == null)
			this.layout = computeLayout();
		scrollPosition -= (int) (scrollY * SCROLL_STEP);
		int max = maxScroll();
		if (scrollPosition < 0)
			scrollPosition = 0;
		else if (scrollPosition > max)
			scrollPosition = max;
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (this.layout == null)
			this.layout = computeLayout();
		HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
		if (button == 0 && isHoveringAddContactTile(mouseX, mouseY)) {
			//? if >=1.20.5 {
			/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(0, x, y, z, textstate));
			*///? } else {
			PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(0, x, y, z, textstate));
			//?}
			CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, textstate);
			return true;
		}
		if (button == 0) { // Left-click on a group entry -> open that group's conversation directly
			int groupIndex = hoveredGroupIndex(mouseX, mouseY);
			if (groupIndex >= 0) {
				textstate.put("conversationId", menu.getGroups().get(groupIndex).conversationId());
				//? if >=1.20.5 {
				/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(4, x, y, z, textstate));
				*///? } else {
				PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(4, x, y, z, textstate));
				//?}
				CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 4, x, y, z, textstate);
				return true;
			}
		}
		int personIndex = hoveredPersonIndex(mouseX, mouseY);
		if (personIndex >= 0) {
			if (button == 1) { // Right-click -> toggle selection (for group creation / bulk removal / favoriting)
				playToggleSound();
				if (!selectedSlots.add(personIndex)) {
					selectedSlots.remove(personIndex);
				}
				updateActionButtonsState();
				return true;
			} else if (button == 0) { // Left-click -> open this contact's conversation directly
				Contact person = personAt(personIndex);
				if (person != null) {
					textstate.put("contactNumber", person.getNumber());
					//? if >=1.20.5 {
					/*NetworkAccess.sendToServer(new CrazyPhoneContactsScreenButtonMessage(1, x, y, z, textstate));
					*///? } else {
					PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactsScreenButtonMessage(1, x, y, z, textstate));
					//?}
					CrazyPhoneContactsScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, textstate);
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
}
