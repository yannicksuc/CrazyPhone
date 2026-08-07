package fr.lordfinn.crazyphone.client.gui;

import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.slf4j.LoggerFactory;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
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
		if (Minecraft.getInstance().screen instanceof CrazyPhoneContactInfoScreenScreen sc) {
			textstate.put("textin:number", sc.number.getValue());
		}
		return textstate;
	}

	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
		super.render(guiGraphics, mouseX, mouseY, partialTicks);
		renderHeader(guiGraphics, new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD),
				Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.title"));
		number.render(guiGraphics, mouseX, mouseY, partialTicks);
		if (fakePlayer instanceof LivingEntity livingEntity) {
			// Scale/position nudged slightly down and smaller than the original 137/50 so the model clears
			// the page header added above it instead of poking through/behind it.
			this.renderEntityInInventoryFollowsAngle(guiGraphics, this.leftPos + 61, this.topPos + 141, 46,
				(float) Math.atan((this.leftPos + 85 - mouseX) / 40.0),
				(float) Math.atan((this.topPos + 35 - mouseY) / 40.0), livingEntity);
		}
		this.renderTooltip(guiGraphics, mouseX, mouseY);
	}

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
      	  	CompletableFuture<PlayerSkin> skinFuture = skinManager.getOrLoad(profile);

       		skinFuture.thenAccept(playerSkin -> {
			PlayerInfo playerInfo = new PlayerInfo(profile, false);
            playerInfo.getSkin();
			this.fakePlayer = new RemotePlayer(level, profile);
			this.fakePlayer.refreshDisplayName();
			level.addFreshEntity(this.fakePlayer);
		});
		} catch (Exception e) {
			createGuiDefaultFakePlayer();
		}
	}

	@Override
	public void resize(Minecraft minecraft, int width, int height) {
		String numberValue = number.getValue();
		super.resize(minecraft, width, height);
		number.setValue(numberValue);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.label_numero"), 10, 144, -12829636, false);
	}

	@Override
	public void init() {
		super.init();
		number = new EditBox(this.font, this.leftPos + 10, this.topPos + 157, 53, 18, Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.number")) {
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
				PacketDistributor.sendToServer(new CrazyPhoneContactInfoScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
				CrazyPhoneContactInfoScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, getEditBoxAndCheckBoxValues());
			}
		});
		guistate.put("text:number", number);
		this.addWidget(this.number);

		button_ajouter = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.button_ajouter"), e -> {
			PacketDistributor.sendToServer(new CrazyPhoneContactInfoScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
			CrazyPhoneContactInfoScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());
		}).bounds(this.leftPos + 67, this.topPos + 156, 46, 20)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("gui.crazyphone.crazy_phone_contact_info_screen.tooltip_ajouter")))
				.build();
		guistate.put("button:button_ajouter", button_ajouter);
		this.addRenderableWidget(button_ajouter);
	}

	private void renderEntityInInventoryFollowsAngle(GuiGraphics guiGraphics, int x, int y, int scale, float angleXComponent, float angleYComponent, LivingEntity entity) {
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
		InventoryScreen.renderEntityInInventory(guiGraphics, x, y, scale, new Vector3f(0, 0, 0), pose, cameraOrientation, entity);
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
