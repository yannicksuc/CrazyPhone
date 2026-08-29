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
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Button;
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
		Button.Builder builder = Button.builder(label, onPress)
				.bounds(this.leftPos + xOffset, this.topPos + ACTION_BUTTON_Y, width, ACTION_BUTTON_HEIGHT);
		if (tooltip != null)
			builder.tooltip(tooltip);
		return builder.build();
	}

	/** Where the header icon sits, and where the title text starts just past it - both relative to leftPos. */
	private static final int HEADER_ICON_X = 7;
	private static final int HEADER_TITLE_X = 26;
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
		fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, HEADER_BANNER_IMAGE, this.leftPos + 4, this.topPos + 9, 0, 114, 18);
		guiGraphics./*$ gui_render_item {*/renderItem/*$}*/(icon, this.leftPos + HEADER_ICON_X, this.topPos + 9);
		int availableWidth = Math.max(0, rightBoundX - HEADER_TITLE_RIGHT_GAP - HEADER_TITLE_X);
		ScrollingText.render(guiGraphics, this.font, title, this.leftPos + HEADER_TITLE_X, this.topPos + 14, availableWidth, 0xFF404040);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		return new HashMap<>();
	}

	//? if >=26 {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		this.extractTooltip(guiGraphics, mouseX, mouseY);
	}
	*///? } else {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
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
	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
		this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
	}

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
		if (key == 256) {
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
		if (event.key() == 256) {
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
		//? if >=1.20.5 {
		/*NetworkAccess.sendToServer(
				new CrazyPhoneDefaultScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
		*///? } else {
		PacketDistributor.SERVER.noArg().send(
				new CrazyPhoneDefaultScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
		//?}
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

	@Override
	public void init() {
		super.init();
		HashMap<String, Object> guistate = getWidgets();
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
		imagebutton_crazyphoneback.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_back")));
		guistate.put("button:imagebutton_crazyphoneback", imagebutton_crazyphoneback);
		this.addRenderableWidget(imagebutton_crazyphoneback);

		imagebutton_crazyphonehome = new ImageButton(this.leftPos + 46, this.topPos + 180, 29, 12,
				new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home.png"),
						Crazyphone.parseId("crazyphone:textures/screens/crazyphone-home-hover.png")),
				e -> {
					//? if >=1.20.5 {
					/*NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
					*///? } else {
					PacketDistributor.SERVER.noArg().send(
							new CrazyPhoneDefaultScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
					//?}
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
		imagebutton_crazyphonehome.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_home")));
		guistate.put("button:imagebutton_crazyphonehome", imagebutton_crazyphonehome);
		this.addRenderableWidget(imagebutton_crazyphonehome);

		imagebutton_crazyphonelock = new ImageButton(this.leftPos + 78, this.topPos + 180, 29, 12,
				new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock.png"),
						Crazyphone.parseId("crazyphone:textures/screens/crazyphone-lock-hover.png")),
				e -> {
					//? if >=1.20.5 {
					/*NetworkAccess.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(2, x, y, z, getEditBoxAndCheckBoxValues()));
					*///? } else {
					PacketDistributor.SERVER.noArg().send(
							new CrazyPhoneDefaultScreenButtonMessage(2, x, y, z, getEditBoxAndCheckBoxValues()));
					//?}
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
		imagebutton_crazyphonelock.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_lock")));
		guistate.put("button:imagebutton_crazyphonelock", imagebutton_crazyphonelock);
		this.addRenderableWidget(imagebutton_crazyphonelock);
		imagebutton_crazyphoneback.active = isBackButtonActive;
		imagebutton_crazyphonehome.active = isHomeButtonActive;
		imagebutton_crazyphonelock.active = isLockButtonActive;
	}
}
