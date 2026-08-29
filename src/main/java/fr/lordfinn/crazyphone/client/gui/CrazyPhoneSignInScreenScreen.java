package fr.lordfinn.crazyphone.client.gui;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneSignInScreenMenu;
import fr.lordfinn.crazyphone.client.gui.components.PasswordEditBox;
import fr.lordfinn.crazyphone.network.CrazyPhoneSignInScreenButtonMessage;
import java.util.HashMap;

public class CrazyPhoneSignInScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneSignInScreenMenu> {
	private final static HashMap<String, Object> guistate = CrazyPhoneSignInScreenMenu.guistate;
	EditBox password;
	Button button_deverrouiller;

	public CrazyPhoneSignInScreenScreen(CrazyPhoneSignInScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		HashMap<String, String> textstate = new HashMap<>();
		if (Minecraft.getInstance().screen instanceof CrazyPhoneSignInScreenScreen sc) {
			textstate.put("textin:password", sc.password.getValue());

		}
		return textstate;
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	//? if >=26 {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE.get()),
				Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.label_connexion"));
		password.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		this.extractTooltip(guiGraphics, mouseX, mouseY);
	}
	*///? } else {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE.get()),
				Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.label_connexion"));
		password.render(guiGraphics, mouseX, mouseY, partialTicks);
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
	//?}

	//? if >=26 {
	/*@Override
	public void resize(int width, int height) {
		String passwordValue = password.getValue();
		super.resize(width, height);
		password.setValue(passwordValue);
	}
	*///? } else {
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String passwordValue = password.getValue();
		super.resize(minecraft, width, height);
		password.setValue(passwordValue);
	}
	//?}

	//? if >=26 {
	/*@Override
	protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		drawLabel(guiGraphics);
	}
	*///? } else {
	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		drawLabel(guiGraphics);
	}
	//?}

	private void drawLabel(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
		guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.label_mot_de_passe"), 7, 66, -12829636, false);
	}

	@Override
	public void init() {
		super.init();
		setBackButtonActive(false);
		setHomeButtonActive(false);
		setLockButtonActive(false);
		password = new PasswordEditBox(this.font, this.leftPos + 7, this.topPos + 78, 108, 18, Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.password")) {
			@Override
			public void insertText(String text) {
				super.insertText(text);
				if (getValue().isEmpty())
					setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.password").getString());
				else
					setSuggestion(null);
			}

			@Override
			public void moveCursorTo(int pos, boolean flag) {
				super.moveCursorTo(pos, flag);
				if (getValue().isEmpty())
					setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.password").getString());
				else
					setSuggestion(null);
			}
		};
		password.setMaxLength(32767);
		password.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.password").getString());
		guistate.put("text:password", password);
		this.addWidget(this.password);
		button_deverrouiller = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.button_deverrouiller"), e -> {
			//? if >=1.20.5 {
			/*NetworkAccess.sendToServer(new CrazyPhoneSignInScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
			*///? } else {
			PacketDistributor.SERVER.noArg().send(new CrazyPhoneSignInScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
			//?}
			CrazyPhoneSignInScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());
		}).bounds(this.leftPos + 7, this.topPos + 100, 108, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("gui.crazyphone.crazy_phone_sign_in_screen.tooltip_deverrouiller")))
				.build();
		guistate.put("button:button_deverrouiller", button_deverrouiller);
		this.addRenderableWidget(button_deverrouiller);
	}
}
