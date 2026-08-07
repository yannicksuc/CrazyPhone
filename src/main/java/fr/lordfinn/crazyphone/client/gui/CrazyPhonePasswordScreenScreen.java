package fr.lordfinn.crazyphone.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.client.gui.components.PasswordEditBox;
import fr.lordfinn.crazyphone.network.CrazyPhonePasswordScreenButtonMessage;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetInitialFormValidationMessageProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;

import java.util.HashMap;

public class CrazyPhonePasswordScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhonePasswordScreenMenu> {
    private static final HashMap<String, Object> guistate = CrazyPhonePasswordScreenMenu.guistate;

    private EditBox number;
    private EditBox name;
    private EditBox password;
    private Button buttonValider;
    private ImageButton buttonReset;

    public CrazyPhonePasswordScreenScreen(CrazyPhonePasswordScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        HashMap<String, String> textstate = new HashMap<>();
        if (Minecraft.getInstance().screen instanceof CrazyPhonePasswordScreenScreen sc) {
            textstate.put("textin:number", sc.number.getValue());
            textstate.put("textin:name", sc.name.getValue());
            textstate.put("textin:password", sc.password.getValue());
        }
        return textstate;
    }

    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_password_screen.title"));
        number.render(guiGraphics, mouseX, mouseY, partialTicks);
        name.render(guiGraphics, mouseX, mouseY, partialTicks);
        password.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String numberVal = number.getValue();
        String nameVal = name.getValue();
        String passwordVal = password.getValue();
        super.resize(minecraft, width, height);
        number.setValue(numberVal);
        name.setValue(nameVal);
        password.setValue(passwordVal);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_numero_associe_au_telephone"), 8, 29, -12829636, false);
        guiGraphics.drawString(font, GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate), -153, -111, -12829636, false);
        guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_nom"), 8, 61, -12829636, false);
        guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_mot_de_passe"), 8, 93, -12829636, false);
        guiGraphics.drawString(font, CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, guistate), 8, 128, -12829636, false);
    }

    @Override
    public void init() {
        super.init();
		setBackButtonActive(false);
		setHomeButtonActive(false);
		setLockButtonActive(false);
        initNumberField();
        initNameField();
        initPasswordField();
        initValidateButton();
        initResetButton();
    }

    private void initNumberField() {
        number = new EditBox(font, leftPos + 8, topPos + 40, 83, 18, Component.translatable("gui.crazyphone.crazy_phone_password_screen.number"));
        number.setMaxLength(32767);
        guistate.put("text:number", number);
        addWidget(number);
    }

    private void initNameField() {
        name = new EditBox(font, leftPos + 8, topPos + 72, 106, 18, Component.translatable("gui.crazyphone.crazy_phone_password_screen.name")) {
            @Override
            public void insertText(String text) {
                super.insertText(text);
                updateSuggestion();
            }

            @Override
            public void moveCursorTo(int pos, boolean flag) {
                super.moveCursorTo(pos, flag);
                updateSuggestion();
            }

            private void updateSuggestion() {
                setSuggestion(getValue().isEmpty() ? Component.translatable("gui.crazyphone.crazy_phone_password_screen.name").getString() : null);
            }
        };
        name.setMaxLength(32767);
        name.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_password_screen.name").getString());
        guistate.put("text:name", name);
        addWidget(name);
    }

    private void initPasswordField() {
        password = new PasswordEditBox(font, leftPos + 8, topPos + 104, 106, 18, Component.translatable("gui.crazyphone.crazy_phone_password_screen.password")) {
            @Override
            public void insertText(String text) {
                super.insertText(text);
                updateSuggestion();
            }

            @Override
            public void moveCursorTo(int pos, boolean flag) {
                super.moveCursorTo(pos, flag);
                updateSuggestion();
            }

            private void updateSuggestion() {
                setSuggestion(getValue().isEmpty() ? Component.translatable("gui.crazyphone.crazy_phone_password_screen.password").getString() : null);
            }
        };
        password.setMaxLength(32767);
        password.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_password_screen.password").getString());
        guistate.put("text:password", password);
        addWidget(password);
    }

    private void initValidateButton() {
        buttonValider = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_password_screen.button_valider"), e -> {
            PacketDistributor.sendToServer(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
            CrazyPhonePasswordScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());
        }).bounds(leftPos + 31, topPos + 150, 61, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_password_screen.tooltip_valider")))
                .build();
        guistate.put("button:button_valider", buttonValider);
        addRenderableWidget(buttonValider);
    }

    private void initResetButton() {
        buttonReset = new ImageButton(leftPos + 96, topPos + 40, 18, 18,
            new WidgetSprites(ResourceLocation.parse("crazyphone:textures/screens/reset.png"),
                              ResourceLocation.parse("crazyphone:textures/screens/reset.png")),
            e -> {
                PacketDistributor.sendToServer(new CrazyPhonePasswordScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
                CrazyPhonePasswordScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, getEditBoxAndCheckBoxValues());
            }) {
            {
                setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_password_screen.tooltip_reset")));
            }
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, 0, width, height, width, height);
            }
        };
        guistate.put("button:imagebutton_reset", buttonReset);
        addRenderableWidget(buttonReset);
    }
}
