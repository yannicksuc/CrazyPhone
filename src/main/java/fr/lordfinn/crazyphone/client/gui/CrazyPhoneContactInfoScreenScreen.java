package fr.lordfinn.crazyphone.client.gui;

import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.slf4j.LoggerFactory;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
//? if <1.21.10 {
import net.minecraft.client.resources.PlayerSkin;
//? } else {
/*import net.minecraft.world.entity.player.PlayerSkin;
*///?}
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactInfoScreenMenu;
import fr.lordfinn.crazyphone.network.CrazyPhoneContactInfoScreenButtonMessage;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.mojang.authlib.GameProfile;

public class CrazyPhoneContactInfoScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneContactInfoScreenMenu> {
	private final static HashMap<String, Object> guistate = CrazyPhoneContactInfoScreenMenu.guistate;
	/** Same 8px side margin and 106px-wide full-width button used elsewhere (e.g. the pictures screen's
	 * send-mode button_send) - NOT the item grid's own width (108px, one pixel per side wider). */
	private static final int CONTENT_X = 8;
	private static final int CONTENT_WIDTH = 106;
	private static final int LABEL_Y = 130;
	private static final int INPUT_Y = 140;
	/** Same button row y as the contacts screen's action buttons / group settings' Validate button. */
	private static final int BUTTON_Y = 158;
	EditBox number;
	Button button_ajouter;

    private static final String[] defaultNames = {"Steve", "Alex"};
    private static final UUID[] defaultUUIDs = {
        UUID.fromString("792d387e-73d9-4906-9e6f-b8a84c887043"), // Steve
        UUID.fromString("2d5111f6-77fb-4a25-ba72-b6c6648ec801")  // Alex
    };
	private Player fakePlayer;
	private String currentName;
	private UUID currentUUID;
	private GameProfile profile;

	public CrazyPhoneContactInfoScreenScreen(CrazyPhoneContactInfoScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
		createGuiDefaultFakePlayer();
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		HashMap<String, String> textstate = new HashMap<>();
		if (Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhoneContactInfoScreenScreen sc) {
			textstate.put("textin:number", sc.number.getValue());
		}
		return textstate;
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	/** The header icon mirrors whichever skin is currently shown by the 3D preview below it, instead of a
	 * generic Steve head - same ResolvableProfile mechanism the 3D preview's own skin lookup already uses,
	 * so it resolves to the same texture once loaded. */
	private ItemStack resolveHeaderIcon() {
		ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
		if (profile != null) {
			fr.lordfinn.crazyphone.utils.PhoneTagAccess.setSkullOwner(head, profile);
		}
		return head;
	}

	//? if >=26 {
	/*@Override
	public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, resolveHeaderIcon(),
				Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.title"));
		number.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
		if (fakePlayer != null) {
			// Centered in the space between the header and the number field/button, matching every other
			// screen's 8px side margins and giving the preview its own clear band instead of overlapping
			// the label/input below it.
			this.renderEntityInInventoryFollowsAngle(guiGraphics, this.leftPos + 61, this.topPos + 118, 40,
				(float) Math.atan((this.leftPos + 85 - mouseX) / 40.0),
				(float) Math.atan((this.topPos + 35 - mouseY) / 40.0), fakePlayer);
		}
		this.extractTooltip(guiGraphics, mouseX, mouseY);
	}
	*///? } else {
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, resolveHeaderIcon(),
				Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.title"));
		number.render(guiGraphics, mouseX, mouseY, partialTicks);
		if (fakePlayer != null) {
			// Centered in the space between the header and the number field/button, matching every other
			// screen's 8px side margins and giving the preview its own clear band instead of overlapping
			// the label/input below it.
			this.renderEntityInInventoryFollowsAngle(guiGraphics, this.leftPos + 61, this.topPos + 118, 40,
				(float) Math.atan((this.leftPos + 85 - mouseX) / 40.0),
				(float) Math.atan((this.topPos + 35 - mouseY) / 40.0), fakePlayer);
		}
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}
	//?}

	private void createGuiDefaultFakePlayer() {
		int randomIndex = new Random().nextInt(defaultNames.length);
        currentName = defaultNames[randomIndex];
        currentUUID = defaultUUIDs[randomIndex];
		createGuiFakePlayer(currentName, currentUUID, false);
	}

	public void createGuiFakePlayer(String name, UUID uuid, boolean isConnected) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;

		// Check if the level is null, and if it is, return a default fake player
		if (level == null) {
			createGuiDefaultFakePlayer();
			return;
		}

		try {
			// Try to create a RemotePlayer with the given name and UUID
			this.profile = new GameProfile(uuid, name);
        	SkinManager skinManager = Minecraft.getInstance().getSkinManager();
        	//? if <1.21.10 {
      	  	CompletableFuture<PlayerSkin> skinFuture = skinManager.getOrLoad(profile);
        	//? } else {
        	/*CompletableFuture<java.util.Optional<PlayerSkin>> skinFuture = skinManager.get(profile);
        	*///?}

       		skinFuture.thenAccept(playerSkin -> {
			PlayerInfo playerInfo = new PlayerInfo(profile, false);
            playerInfo.getSkin();
			this.fakePlayer = new RemotePlayer(level, profile);
			//? if neoforge {
			this.fakePlayer.refreshDisplayName();
			//?}
			fr.lordfinn.crazyphone.client.FakePlayerPreview.showAllSkinLayers(this.fakePlayer);
			level.addFreshEntity(this.fakePlayer);
		});
		} catch (Exception e) {
			createGuiDefaultFakePlayer();
		}
	}

	//? if >=26 {
	/*@Override
	public void resize(int width, int height) {
		String numberValue = number.getValue();
		super.resize(width, height);
		number.setValue(numberValue);
	}
	*///? } else {
	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String numberValue = number.getValue();
		super.resize(minecraft, width, height);
		number.setValue(numberValue);
	}
	//?}

	//? if >=26 {
	/*@Override
	protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
		guiGraphics./^$ gui_draw_string {^/drawString/^$}^/(this.font, Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.label_numero"), CONTENT_X, LABEL_Y, 0xFF3C3C3C, false);
	}
	*///? } else {
	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.label_numero"), CONTENT_X, LABEL_Y, 0xFF3C3C3C, false);
	}
	//?}

	@Override
	public void init() {
		super.init();
		number = new EditBox(this.font, this.leftPos + CONTENT_X, this.topPos + INPUT_Y, CONTENT_WIDTH, 14, Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.number")) {
			@Override
			public void insertText(String text) {
				super.insertText(text);
				setSuggestion(getValue().isEmpty() ? Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.number").getString() : null);
			}

			@Override
			public void moveCursorTo(int pos, boolean flag) {
				super.moveCursorTo(pos, flag);
				setSuggestion(getValue().isEmpty() ? Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.number").getString() : null);
			}
		};
		number.setMaxLength(32767);
		number.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.number").getString());
		number.setResponder(text -> {
			if (entity != null) {
				//? if >=1.20.5 {
				/*NetworkAccess.sendToServer(new CrazyPhoneContactInfoScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
				*///? } else {
				PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactInfoScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
				//?}
				CrazyPhoneContactInfoScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, getEditBoxAndCheckBoxValues());
			}
		});
		guistate.put("text:number", number);
		this.addWidget(this.number);

		button_ajouter = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.button_ajouter"), e -> {
			//? if >=1.20.5 {
			/*NetworkAccess.sendToServer(new CrazyPhoneContactInfoScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
			*///? } else {
			PacketDistributor.SERVER.noArg().send(new CrazyPhoneContactInfoScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
			//?}
			CrazyPhoneContactInfoScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());
		}).bounds(this.leftPos + CONTENT_X, this.topPos + BUTTON_Y, CONTENT_WIDTH, 14)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.tooltip_ajouter")))
				.build();
		guistate.put("button:button_ajouter", button_ajouter);
		this.addRenderableWidget(button_ajouter);
	}

	private void renderEntityInInventoryFollowsAngle(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int x, int y, int scale, float angleXComponent, float angleYComponent, LivingEntity entity) {
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf cameraOrientation = new Quaternionf().rotateX(angleYComponent * 20 * ((float) Math.PI / 180F));
		pose.mul(cameraOrientation);
		float f2 = entity.yBodyRot;
		float f3 = entity.getYRot();
		float f4 = entity.getXRot();
		float f5 = entity.yHeadRotO;
		float f6 = entity.yHeadRot;
		entity.yBodyRot = 180.0F + angleXComponent * 20.0F;
		entity.setYRot(180.0F + angleXComponent * 40.0F);
		entity.setXRot(-angleYComponent * 20.0F);
		entity.yHeadRot = entity.getYRot();
		entity.yHeadRotO = entity.getYRot();
		fr.lordfinn.crazyphone.utils.GuiCompat.renderEntityInInventory(guiGraphics, x, y, scale, new Vector3f(0, 0, 0), pose, cameraOrientation, entity);
		entity.yBodyRot = f2;
		entity.setYRot(f3);
		entity.setXRot(f4);
		entity.yHeadRotO = f5;
		entity.yHeadRot = f6;
	}

	/** Called when the server sends the updated contact info **/
	public void updateContactInfo(String name, String uuidStr, String number2) {
		try {
			this.currentName = name;
			this.currentUUID = UUID.fromString(uuidStr);
			createGuiFakePlayer(currentName, currentUUID, true);
		} catch (IllegalArgumentException e) {
			LoggerFactory.getLogger("crazyphone").error("Invalid UUID string: " + uuidStr);
			createGuiDefaultFakePlayer();
		}
	}
}
