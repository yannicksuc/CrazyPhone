package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

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
		super(container, inventory, text);
		this.world = container.world;
		this.x = container.x;
		this.y = container.y;
		this.z = container.z;
		this.entity = container.entity;
		this.imageWidth = 122;
		this.imageHeight = 195;
	}

	private static final ResourceLocation HEADER_BANNER_IMAGE = ResourceLocation.parse("crazyphone:textures/screens/crazyphone-header-background.png");
	/** Height in pixels of the header strip drawn by {@link #renderHeader}, measured from the top of the phone background - every screen that shows one must start its own content at this y offset. */
	protected static final int HEADER_HEIGHT = 27;

	public abstract HashMap<String, Object> getWidgets();

	/**
	 * Shared page-title banner: an icon followed by a title, in a strip at the top of the phone screen.
	 * Every screen but the home page shows one - originally introduced (and duplicated) as the
	 * conversation screen's per-contact header, now generalized so every screen can reuse the exact same
	 * texture/layout instead of re-drawing it by hand.
	 */
	protected void renderHeader(GuiGraphics guiGraphics, ItemStack icon, Component title) {
		guiGraphics.blit(HEADER_BANNER_IMAGE, this.leftPos + 4, this.topPos + 9, 0, 0, 0, 114, 18, 114, 18);
		guiGraphics.renderItem(icon, this.leftPos + 8, this.topPos + 9);
		guiGraphics.drawString(this.font, title, this.leftPos + 27, this.topPos + 14, 0x404040, false);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		return new HashMap<>();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

	/** A much lighter dim than vanilla's default (AbstractContainerScreen#renderBackground normally fills a
	 * gradient at roughly 75-82% opacity via renderTransparentBackground - see Screen#renderTransparentBackground)
	 * - the phone is meant to be checked while still keeping an eye on your surroundings, not a full-screen
	 * menu that blacks out the world behind it. AbstractContainerScreen's own renderBackground is ALSO
	 * where {@link #renderBg} normally gets invoked from, so an override that doesn't call it itself
	 * silently drops the phone's own background image - that's the one call that actually matters here,
	 * not just the dim color. */
	@Override
	public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
		this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		guiGraphics.blit(ResourceLocation.parse("crazyphone:textures/screens/phone-background.png"), this.leftPos + 0,
				this.topPos + 0, 0,0, 0, 122, 195, 122, 195);

		RenderSystem.disableBlend();
	}

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

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}

	protected void setBackButtonActive(boolean active) {
		isBackButtonActive = active;
		if (imagebutton_crazyphoneback != null) {
			imagebutton_crazyphoneback.active = active;
		}
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
				new WidgetSprites(ResourceLocation.parse("crazyphone:textures/screens/crazyphone-back.png"),
						ResourceLocation.parse("crazyphone:textures/screens/crazyphone-back-hover.png")),
				e -> {
					PacketDistributor.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 0, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, 0, width, height,
						width, height);
			}
		};
		imagebutton_crazyphoneback.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_back")));
		guistate.put("button:imagebutton_crazyphoneback", imagebutton_crazyphoneback);
		this.addRenderableWidget(imagebutton_crazyphoneback);

		imagebutton_crazyphonehome = new ImageButton(this.leftPos + 46, this.topPos + 180, 29, 12,
				new WidgetSprites(ResourceLocation.parse("crazyphone:textures/screens/crazyphone-home.png"),
						ResourceLocation.parse("crazyphone:textures/screens/crazyphone-home-hover.png")),
				e -> {
					PacketDistributor.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 1, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, 0, width, height,
						width, height);
			}
		};
		imagebutton_crazyphonehome.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_home")));
		guistate.put("button:imagebutton_crazyphonehome", imagebutton_crazyphonehome);
		this.addRenderableWidget(imagebutton_crazyphonehome);

		imagebutton_crazyphonelock = new ImageButton(this.leftPos + 78, this.topPos + 180, 29, 12,
				new WidgetSprites(ResourceLocation.parse("crazyphone:textures/screens/crazyphone-lock.png"),
						ResourceLocation.parse("crazyphone:textures/screens/crazyphone-lock-hover.png")),
				e -> {
					PacketDistributor.sendToServer(
							new CrazyPhoneDefaultScreenButtonMessage(2, x, y, z, getEditBoxAndCheckBoxValues()));
					CrazyPhoneDefaultScreenButtonMessage.handleButtonAction(entity, 2, x, y, z,
							getEditBoxAndCheckBoxValues());
				}) {
			@Override
			public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
				guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, 0, width, height,
						width, height);
			}
		};
		imagebutton_crazyphonelock.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazyphone_home_screen.tooltip_lock")));
		guistate.put("button:imagebutton_crazyphonelock", imagebutton_crazyphonelock);
		this.addRenderableWidget(imagebutton_crazyphonelock);
		imagebutton_crazyphoneback.active = isBackButtonActive;
		imagebutton_crazyphonehome.active = isHomeButtonActive;
		imagebutton_crazyphonelock.active = isLockButtonActive;
	}
}
