package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//? if fabric || neoforge {
import net.minecraft.client.gui.components.WidgetSprites;
//?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Button;
import fr.lordfinn.crazyphone.client.gui.components.PhoneButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.client.gui.components.ScrollingText;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneDefaultScreenMenu;
import fr.lordfinn.crazyphone.network.CrazyPhoneDefaultScreenButtonMessage;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Base class for every CrazyPhone screen. Implements {@link PhoneScreen} so shared code can recognize any
 * phone screen without an instanceof chain per concrete class.
 *
 * Deviation from the old file: the old class implemented a {@code WidgetScreen} interface nested inside
 * CrazythingsModScreens (a generated registration class) purely to expose {@code getWidgets()} for the
 * text-box sync handler. That interface isn't part of this port's scope, so {@code getWidgets()} is
 * declared directly as an abstract method on this class instead - every concrete screen subclass still
 * implements it with the same {@code public HashMap<String, Object> getWidgets()} signature as before, so
 * this is a source-compatible change for subclasses.
 */
public abstract class CrazyPhoneDefaultScreenScreen<T extends CrazyPhoneDefaultScreenMenu>
		extends AbstractContainerScreen<T> implements PhoneScreen {
	protected final Level world;
	protected final int x, y, z;
	protected final Player entity;
	protected ImageButton imagebutton_crazyphoneback;
	protected ImageButton imagebutton_crazyphonehome;
	protected ImageButton imagebutton_crazyphonelock;
	protected boolean isBackButtonActive = true;
	protected boolean isHomeButtonActive = true;
	protected boolean isLockButtonActive = true;

	public CrazyPhoneDefaultScreenScreen(T container, Inventory inventory, Component text) {
		this(container, inventory, text, 122, 195);
	}

	// 26.x made imageWidth/imageHeight constructor-only final fields (were freely settable after the fact
	// pre-26 - see AbstractContainerScreen's own doc comment on drawScreenBackground's sibling methods for
	// the broader pattern of "what used to be a late-bound hook is now fixed earlier"). The 3-arg super
	// constructor (defaulting to AbstractContainerScreen's own DEFAULT_IMAGE_WIDTH/HEIGHT) still exists on
	// every version, but every screen in this mod needs its own size (either the standard 122x195 phone,
	// via the constructor above, or a custom one - e.g. CrazyPhoneGroupSettingsScreenScreen's wider
	// layout) - this protected overload is this project's OWN stable constructor shape for that, going
	// through AbstractContainerScreen's 5-arg overload directly on >=26 instead of assigning the fields
	// afterward (a subclass can no longer do that itself post-construction either, once this constructor
	// has already finally-assigned them).
	//? if >=26 {
	/*protected CrazyPhoneDefaultScreenScreen(T container, Inventory inventory, Component text, int imageWidth, int imageHeight) {
		super(container, inventory, text, imageWidth, imageHeight);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
	}
	*///? } else {
	protected CrazyPhoneDefaultScreenScreen(T container, Inventory inventory, Component text, int imageWidth, int imageHeight) {
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = imageWidth;
		this.imageHeight = imageHeight;
	}
	//?}

	private static final /*$ res_loc {*/ResourceLocation/*$}*/ HEADER_BANNER_IMAGE = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-header-background.png");
	/** Height in pixels of the header strip drawn by {@link #renderHeader}, measured from the top of the phone background - every screen that shows one must start its own content at this y offset. */
	protected static final int HEADER_HEIGHT = 27;

	public abstract HashMap<String, Object> getWidgets();

	/** Shared bottom action-button row geometry, matching what every phone screen (contacts, group
	 * settings, calls, album...) already draws its own confirm/cancel buttons at - factored
	 * out here so a screen reaches for these instead of inventing its own button size/position. */
	protected static final int ACTION_BUTTON_Y = 158;
	protected static final int ACTION_BUTTON_HEIGHT = 14;
	protected static final int ACTION_BUTTON_X = 8;
	/** Width of a single button spanning the whole row alone (e.g. one "Send"/"Validate" action). */
	protected static final int ACTION_BUTTON_FULL_WIDTH = 106;

	/** Builds one bottom-row action button at the shared size/position above. Pass the button's own
	 * xOffset/width when it shares the row with another (see e.g. CrazyPhoneIncomingCallScreenScreen's
	 * accept/decline pair); use {@link #ACTION_BUTTON_X}/{@link #ACTION_BUTTON_FULL_WIDTH} for a lone
	 * button spanning the row by itself. */
	protected Button actionButton(Component label, int xOffset, int width, Tooltip tooltip, Button.OnPress onPress) {
		return actionButton(PhoneButton.Style.PRIMARY, label, xOffset, width, tooltip, onPress);
	}

	/** {@link #actionButton(Component, int, int, Tooltip, Button.OnPress)} with its role (colour). */
	protected Button actionButton(PhoneButton.Style style, Component label, int xOffset, int width, Tooltip tooltip, Button.OnPress onPress) {
		PhoneButton.Builder builder = PhoneButton.builder(style, label, onPress)
				.bounds(this.leftPos + xOffset, this.topPos + ACTION_BUTTON_Y, width, ACTION_BUTTON_HEIGHT);
		if (tooltip != null)
			builder.tooltip(tooltip);
		return builder.build();
	}

	/** Where the header icon sits, and where the title text starts just past it - both relative to leftPos.
	 * The icon is the item drawn at 75 % (12 px), 4 px in from the banner's left edge (x 4). */
	private static final int HEADER_ICON_X = 7;
	private static final int HEADER_TITLE_X = 23;

	// ---- Header icons: one rule for every screen ------------------------------------------------------
	// Every icon of the header banner is vertically centered on the title's line (glyph rows 14-21 relative
	// to topPos; the banner itself is y 9-27). The right-hand icons are laid out from the right: the last one
	// ends 3 px in from the banner's right edge, 3 px apart from each other.
	/** Exclusive right edge of the rightmost header icon, relative to leftPos (last pixel: 113): the same
	 * right edge as the phone's content (x 8 to 114 exclusive), mirroring the left icon at x 8. */
	protected static final int HEADER_ICONS_RIGHT = 114;
	protected static final int HEADER_ICON_GAP = 3;
	/** Top of an 8 px emoji glyph, relative to topPos: same line as the title. */
	protected static final int HEADER_GLYPH_Y = 14;
	protected static final int HEADER_GLYPH_SIZE = 8;
	/**
	 * Header buttons: each one is a 12 px square slot, rows 12-23 relative to topPos (centered on the banner, y 9
	 * to 27), laid out from the right edge of the content (x 114) leftwards, 2 px apart: slot 0 is x 102-113,
	 * slot 1 x 88-99, slot 2 x 74-85... The hover highlight fills exactly the slot (corners cut), so slots never
	 * overlap, and the icon is centered in its slot on its own drawn pixels (emojis are measured in the font
	 * image, see EmojiGlyphs#bounds), half pixels allowed.
	 */
	protected static final int HEADER_SLOT = 12;
	protected static final int HEADER_SLOT_GAP = 2;
	protected static final int HEADER_SLOT_Y = 12;

	/** Left edge (absolute) of header slot n counted from the right (0 = rightmost). */
	protected int headerSlotX(int n) {
		return this.leftPos + HEADER_ICONS_RIGHT - HEADER_SLOT - n * (HEADER_SLOT + HEADER_SLOT_GAP);
	}

	/** Whether the mouse is in the header slot starting at slotX (absolute). */
	protected boolean isInHeaderSlot(double mouseX, double mouseY, int slotX) {
		int y = this.topPos + HEADER_SLOT_Y;
		return mouseX >= slotX && mouseX < slotX + HEADER_SLOT && mouseY >= y && mouseY < y + HEADER_SLOT;
	}

	private void drawHeaderSlotHover(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int slotX) {
		int y = this.topPos + HEADER_SLOT_Y;
		guiGraphics.fill(slotX + 1, y, slotX + HEADER_SLOT - 1, y + HEADER_SLOT, 0x80FFFFFF);
		guiGraphics.fill(slotX, y + 1, slotX + 1, y + HEADER_SLOT - 1, 0x80FFFFFF);
		guiGraphics.fill(slotX + HEADER_SLOT - 1, y + 1, slotX + HEADER_SLOT, y + HEADER_SLOT - 1, 0x80FFFFFF);
	}

	/** Items in the header are drawn at 75 %: 12 px, rows 12-23 relative to topPos (centered on the title). */
	protected static final float HEADER_ITEM_SCALE = 0.75f;
	protected static final int HEADER_ITEM_SIZE = 12;
	protected static final int HEADER_ITEM_Y = 12;

	/** An emoji icon in the header slot starting at slotX (absolute), centered on its own pixels. */
	protected void drawHeaderGlyph(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, Component glyph, int slotX, int color, boolean hovered) {
		int slotY = this.topPos + HEADER_SLOT_Y;
		if (hovered)
			drawHeaderSlotHover(guiGraphics, slotX);
		String text = glyph.getString();
		float[] b = text.isEmpty() ? null : fr.lordfinn.crazyphone.client.EmojiGlyphs.bounds(text.codePointAt(0));
		float x, y;
		if (b != null) {
			// The emoji cell's top row is drawn at the text y (measured in game).
			x = slotX + (HEADER_SLOT - (b[2] - b[0])) / 2f - b[0];
			y = slotY + (HEADER_SLOT - (b[3] - b[1])) / 2f - b[1];
		} else {
			x = slotX + (HEADER_SLOT - (this.font.width(glyph) - 1)) / 2f;
			y = slotY + (HEADER_SLOT - 7) / 2f;
		}
		// Drawn with its shadow (keeps a light icon readable on the yellow banner): the shadow adds one pixel
		// down and right, so the icon and its shadow together are what gets centered.
		fr.lordfinn.crazyphone.utils.GuiCompat.pushPose(guiGraphics);
		fr.lordfinn.crazyphone.utils.GuiCompat.translate(guiGraphics, x - 0.5f, y - 0.5f);
		guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, glyph, 0, 0, color, true);
		fr.lordfinn.crazyphone.utils.GuiCompat.popPose(guiGraphics);
	}

	/** An item icon of the header at 75 % (12 px), filling the header slot starting at x (absolute). */
	protected void drawHeaderItem(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, ItemStack stack, int x, boolean hovered) {
		int y = this.topPos + HEADER_ITEM_Y;
		if (hovered)
			drawHeaderSlotHover(guiGraphics, x);
		// Heads sit low in their 16 px item box (vanilla's head model is drawn in its lower part): measured
		// 1.25 px low at this size, so they go up by that much to be centered in the slot like other items.
		float headLift = stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
				&& blockItem.getBlock() instanceof net.minecraft.world.level.block.AbstractSkullBlock ? 1.25f : 0f;
		fr.lordfinn.crazyphone.utils.GuiCompat.pushPose(guiGraphics);
		fr.lordfinn.crazyphone.utils.GuiCompat.translate(guiGraphics, x, y - headLift);
		fr.lordfinn.crazyphone.utils.GuiCompat.scale(guiGraphics, HEADER_ITEM_SCALE, HEADER_ITEM_SCALE);
		guiGraphics./*$ gui_render_item {*/renderItem/*$}*/(stack, 0, 0);
		fr.lordfinn.crazyphone.utils.GuiCompat.popPose(guiGraphics);
	}

	/** Hit box of a header icon whose drawn box starts at (x, y) and is size px wide and tall (2 px of slack). */
	protected static boolean isInHeaderIcon(double mouseX, double mouseY, int x, int y, int size) {
		return mouseX >= x - 2 && mouseX < x + size + 2 && mouseY >= y - 2 && mouseY < y + size + 2;
	}
	/** Right edge of the header banner itself, relative to leftPos (banner starts at +4, is 114px wide). */
	private static final int HEADER_BANNER_RIGHT_X = 118;
	/** Small breathing room between the scrolling title and whatever sits immediately to its right. */
	private static final int HEADER_TITLE_RIGHT_GAP = 2;

	/**
	 * Shared page-title banner: an icon followed by a title, in a strip at the top of the phone screen.
	 * Every screen but the home page shows one - originally introduced (and duplicated) as the
	 * conversation screen's per-contact header, now generalized so every screen can reuse the exact same
	 * texture/layout instead of re-drawing it by hand. The title scrolls (see ScrollingText) instead of
	 * overflowing/overlapping whatever's to its right when it's too long to fit - e.g. a group's name
	 * auto-built from every member's name easily exceeds the available width.
	 */
	protected void renderHeader(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, ItemStack icon, Component title) {
		renderHeader(guiGraphics, icon, title, HEADER_BANNER_RIGHT_X);
	}

	/** @param rightBoundX where the title's available width stops, relative to leftPos - pass the exact x
	 *                     of whichever right-side icon sits closest to the title (e.g. the conversation
	 *                     screen's call icon, which itself shifts left of the group-settings cog when both
	 *                     are shown) so the title scrolls under it, not behind or past it. Defaults to the
	 *                     header banner's own right edge when a screen has no such icon. */
	protected void renderHeader(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, ItemStack icon, Component title, int rightBoundX) {
		renderHeader(guiGraphics, icon, title, rightBoundX, true);
	}

	/** @param showIcon false skips the item icon entirely and starts the title where the icon would have
	 *                  sat instead (HEADER_ICON_X), reclaiming that space - e.g. My Photos, whose own header
	 *                  icon was redundant next to the photo count already shown on the same row. */
	protected void renderHeader(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, ItemStack icon, Component title, int rightBoundX, boolean showIcon) {
		fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, HEADER_BANNER_IMAGE, this.leftPos + 4, this.topPos + 9, 0, 114, 18);
		int titleX = HEADER_TITLE_X;
		if (showIcon)
			drawHeaderItem(guiGraphics, icon, this.leftPos + HEADER_ICON_X + 1, false);
		else
			titleX = HEADER_ICON_X;
		int availableWidth = Math.max(0, rightBoundX - HEADER_TITLE_RIGHT_GAP - titleX);
		// Half a pixel up: the 7 px capitals are then centered on the banner like the icons (measured in game).
		fr.lordfinn.crazyphone.utils.GuiCompat.pushPose(guiGraphics);
		fr.lordfinn.crazyphone.utils.GuiCompat.translate(guiGraphics, 0f, -0.5f);
		ScrollingText.render(guiGraphics, this.font, title, this.leftPos + titleX, this.topPos + 14, availableWidth, 0xFF404040);
		fr.lordfinn.crazyphone.utils.GuiCompat.popPose(guiGraphics);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		return new HashMap<>();
	}

	//? if >=26 {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		if (LaunchSplash.isActive()) {
			LaunchSplash.render(guiGraphics, this.leftPos, this.topPos);
			return;
		}
		this.extractTooltip(guiGraphics, mouseX, mouseY);
	}
	*///? } else {
	//? if neoforge || fabric {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		if (LaunchSplash.isActive()) {
			LaunchSplash.render(guiGraphics, this.leftPos, this.topPos);
			return;
		}
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
	//?}
	// Real 1.20.1 vanilla's AbstractContainerScreen has no renderBackground(GuiGraphics, int, int, float)
	// override point at all (only the no-args Screen#renderBackground(GuiGraphics), and render() itself
	// already invokes renderBg internally) - the dim this mod wants drawn before the phone's own texture is
	// just done directly in render() here instead, ahead of the super.render() call that triggers renderBg
	// below.
	//? if legacyforge {
	/*@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		if (LaunchSplash.isActive()) {
			LaunchSplash.render(guiGraphics, this.leftPos, this.topPos);
			return;
		}
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
	*///?}
	//?}

	/** A much lighter dim than vanilla's default (AbstractContainerScreen#renderBackground normally fills a
	 * gradient at roughly 75-82% opacity via renderTransparentBackground - see Screen#renderTransparentBackground)
	 * - the phone is meant to be checked while still keeping an eye on your surroundings, not a full-screen
	 * menu that blacks out the world behind it. Pre-26 this lived in a two-step renderBackground()+renderBg()
	 * pair (AbstractContainerScreen's own renderBackground is where renderBg normally gets invoked from, so
	 * an override that doesn't call it itself silently drops the phone's own background image); 26.x merged
	 * both into this one Screen#extractBackground hook directly (confirmed against vanilla's own
	 * InventoryScreen#extractBackground, which does the same dim-then-texture sequence in one method now) -
	 * see drawScreenBackground's own doc comment for how subclasses still layer onto this either way. */
	//? if >=26 {
	/*@Override
	public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
		this.drawScreenBackground(guiGraphics);
	}
	*///? } else {
	//? if neoforge || fabric {
	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
		this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
	}
	//?}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		this.drawScreenBackground(guiGraphics);
	}
	//?}

	// Own, version-stable extension point for the phone's own background texture. A real vanilla hook
	// (renderBg) existed here pre-26 for exactly this, but 26.x folded it directly into
	// Screen#extractBackground with no separate per-container-screen slot left for subclasses to layer
	// onto (see this class's own extractBackground/renderBg above for how each version's real entry point
	// reaches here). The 4 subclasses that used to override renderBg to draw more on top of the phone
	// background (selection highlights, etc.) now override this instead, regardless of version - keeps
	// their own logic from needing two versions of itself just because vanilla's hook moved again.
	protected void drawScreenBackground(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
		//? if <1.21.10 {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		//?}

		fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics,
				Crazyphone.parseId("crazyphone:textures/screens/phone-background.png"),
				this.leftPos + 0, this.topPos + 0, 0, 122, 195);

		//? if <1.21.10 {
		RenderSystem.disableBlend();
		//?}
	}

	//? if <1.21.10 {
	@Override
	public boolean keyPressed(int key, int b, int c) {
		if (LaunchSplash.isActive()) {
			LaunchSplash.skip();
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			this.minecraft.player.closeContainer();
			return true;
		}
		Map<String, Object> copy = new HashMap<>(this.getWidgets());
		for (Map.Entry<String, Object> entry : copy.entrySet()) {
			Object widget = entry.getValue();
			if (widget instanceof AbstractWidget widgetObject && widgetObject.isHoveredOrFocused()) {
				widgetObject.keyPressed(key, b, c);
				return true;
			}
		}
		return false;
	}
	//?}
	//? if >=1.21.10 {
	/*@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (LaunchSplash.isActive()) {
			LaunchSplash.skip();
			return true;
		}
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
			this.minecraft.player.closeContainer();
			return true;
		}
		Map<String, Object> copy = new HashMap<>(this.getWidgets());
		for (Map.Entry<String, Object> entry : copy.entrySet()) {
			Object widget = entry.getValue();
			if (widget instanceof AbstractWidget widgetObject && widgetObject.isHoveredOrFocused()) {
				widgetObject.keyPressed(event);
				return true;
			}
		}
		return false;
	}
	*///?}

	// While the launch splash covers the display (see LaunchSplash), the phone underneath gets no input: a
	// click or a key only skips the splash. Screens that override these themselves check
	// LaunchSplash.isActive() first too (see CrazyPhoneSignInScreenScreen).
	//? if <1.21.10 {
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (LaunchSplash.isActive()) {
			LaunchSplash.skip();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (LaunchSplash.isActive())
			return true;
		return super.charTyped(codePoint, modifiers);
	}
	//?}
	//? if >=1.21.10 {
	/*@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (LaunchSplash.isActive()) {
			LaunchSplash.skip();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (LaunchSplash.isActive())
			return true;
		return super.charTyped(event);
	}
	*///?}

	/** The screens a phone launch lands on (sign in, password, home): they start the first-launch splash.
	 * Every other screen (an incoming call, a photo...) never does. */
	protected boolean isLaunchScreen() {
		return false;
	}

	//? if >=26 {
	/*@Override
	protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
	}
	*///? } else {
	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}
	//?}

	protected void setBackButtonActive(boolean active) {
		isBackButtonActive = active;
		if (imagebutton_crazyphoneback != null) {
			imagebutton_crazyphoneback.active = active;
		}
	}

	/** Default back-button behaviour: pop one entry off the server-tracked screen history. Subclasses with
	 * their own local, non-history navigation (e.g. a multi-step form's "previous step") should override
	 * this instead of touching the button itself - AbstractWidget.OnPress has no way to be reassigned once
	 * a Button is constructed, so this indirection is what makes that overridable at all. */
	protected void onBackButtonPressed() {
		NetworkAccess.sendToServer(
				new CrazyPhoneDefaultScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
		CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 0, x, y, z,
				getEditBoxAndCheckBoxValues());
	}

	protected void setHomeButtonActive(boolean active) {
		isHomeButtonActive = active;
		if (imagebutton_crazyphonehome != null) {
			imagebutton_crazyphonehome.active = active;
		}
	}

	protected void setLockButtonActive(boolean active) {
		isLockButtonActive = active;
		if (imagebutton_crazyphonelock != null) {
			imagebutton_crazyphonelock.active = active;
		}
	}

	/** See {@link PhoneScale}: recomputed on every init() (first open, window resize, GUI scale change,
	 * fullscreen toggle all re-run it), so the phone always matches the current window. */
	protected double phoneScale = 1.0;

	@Override
	public double phoneGuiScale() {
		return phoneScale;
	}

	@Override
	public void init() {
		// Must run before super.init(): AbstractContainerScreen centers leftPos/topPos from width/height.
		phoneScale = PhoneScale.computeScale(this.imageWidth, Math.max(this.imageHeight, PhoneScale.PHONE_HEIGHT), 0, 0);
		PhoneScale.applyVirtualSize(this, phoneScale);
		super.init();
		if (isLaunchScreen())
			LaunchSplash.startIfFirstLaunch();
		HashMap<String, Object> guistate = getWidgets();
		//? if legacyforge {
		/*ResourceLocation backNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-back.png");
		ResourceLocation backHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-back-hover.png");
		imagebutton_crazyphoneback = new ImageButton(this.leftPos + 14, this.topPos + 180, 29, 12, 0, 0, 12, backNormal,
				e -> onBackButtonPressed()) {
			private final WidgetSprites sprites = new WidgetSprites(backNormal, backHover);

			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
		};
		*///? } else {
		imagebutton_crazyphoneback = new ImageButton(this.leftPos + 14, this.topPos + 180, 29, 12,
				new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-back.png"),
						Crazyphone.parseId("crazyphone:textures/screens/crazyphone-back-hover.png")),
				e -> onBackButtonPressed()) {
			//? if >=26 {
			/*@Override
			public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			*///? } else {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			//?}
		};
		//?}
		imagebutton_crazyphoneback.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_back")));
		guistate.put("button:imagebutton_crazyphoneback", imagebutton_crazyphoneback);
		this.addRenderableWidget(imagebutton_crazyphoneback);

		//? if legacyforge {
		/*ResourceLocation homeNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home.png");
		ResourceLocation homeHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home-hover.png");
		imagebutton_crazyphonehome = new ImageButton(this.leftPos + 46, this.topPos + 180, 29, 12, 0, 0, 12, homeNormal,
				e -> {
					NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 1, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			private final WidgetSprites sprites = new WidgetSprites(homeNormal, homeHover);

			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
		};
		*///? } else {
		imagebutton_crazyphonehome = new ImageButton(this.leftPos + 46, this.topPos + 180, 29, 12,
				new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home.png"),
						Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home-hover.png")),
				e -> {
					NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 1, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			//? if >=26 {
			/*@Override
			public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			*///? } else {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			//?}
		};
		//?}
		imagebutton_crazyphonehome.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_home")));
		guistate.put("button:imagebutton_crazyphonehome", imagebutton_crazyphonehome);
		this.addRenderableWidget(imagebutton_crazyphonehome);

		//? if legacyforge {
		/*ResourceLocation lockNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock.png");
		ResourceLocation lockHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock-hover.png");
		imagebutton_crazyphonelock = new ImageButton(this.leftPos + 78, this.topPos + 180, 29, 12, 0, 0, 12, lockNormal,
				e -> {
					NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(2, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 2, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			private final WidgetSprites sprites = new WidgetSprites(lockNormal, lockHover);

			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
		};
		*///? } else {
		imagebutton_crazyphonelock = new ImageButton(this.leftPos + 78, this.topPos + 180, 29, 12,
				new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock.png"),
						Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock-hover.png")),
				e -> {
					NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(2, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 2, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			//? if >=26 {
			/*@Override
			public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			*///? } else {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
			}
			//?}
		};
		//?}
		imagebutton_crazyphonelock.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_lock")));
		guistate.put("button:imagebutton_crazyphonelock", imagebutton_crazyphonelock);
		this.addRenderableWidget(imagebutton_crazyphonelock);
		imagebutton_crazyphoneback.active = isBackButtonActive;
		imagebutton_crazyphonehome.active = isHomeButtonActive;
		imagebutton_crazyphonelock.active = isLockButtonActive;
	}
}
