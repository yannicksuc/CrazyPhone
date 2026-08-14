package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.ChatFormatting;
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
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import fr.lordfinn.crazyphone.client.gui.components.PasswordEditBox;
import fr.lordfinn.crazyphone.network.CrazyPhonePasswordScreenButtonMessage;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetInitialFormValidationMessageProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;

import java.util.HashMap;

public class CrazyPhonePasswordScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhonePasswordScreenMenu> {
    private static final HashMap<String, Object> guistate = CrazyPhonePasswordScreenMenu.guistate;
    private static final int STEP_IDENTITY = 0;
    private static final int STEP_PASSWORD = 1;

    private EditBox number;
    private EditBox name;
    private EditBox password;
    private Button buttonAction;
    private ImageButton buttonReset;

    /** Which half of registration is showing - number/name first, then password on its own page (kept out
     * of the first page so the admin-visibility warning below can't be missed by someone rushing through).
     * Back/home/lock stay disabled on both steps (see init()) - registration has no valid "previous
     * screen" to return to, and once it completes the player lands fresh on the home screen, never through
     * back-button-reachable history. */
    private int step = STEP_IDENTITY;
    /** Result of the last "Suivant" attempt on the identity step - the password step reuses the existing
     * guistate-driven message instead, see renderLabels(). */
    private String identityStepMessage = "";

    public CrazyPhonePasswordScreenScreen(CrazyPhonePasswordScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        HashMap<String, String> textstate = new HashMap<>();
        if (Minecraft.getInstance().screen instanceof CrazyPhonePasswordScreenScreen sc) {
            if (sc.number != null)
                textstate.put("textin:number", sc.number.getValue());
            if (sc.name != null)
                textstate.put("textin:name", sc.name.getValue());
            if (sc.password != null)
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
                Component.translatable(step == STEP_PASSWORD
                        ? "gui.crazyphone.crazy_phone_password_screen.title_password_step"
                        : "gui.crazyphone.crazy_phone_password_screen.title"));
        if (step == STEP_IDENTITY) {
            number.render(guiGraphics, mouseX, mouseY, partialTicks);
            name.render(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            password.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
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
        if (step == STEP_IDENTITY) {
            guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_numero_associe_au_telephone"), 8, 29, -12829636, false);
            guiGraphics.drawString(font, GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate), -153, -111, -12829636, false);
            guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_nom"), 8, 61, -12829636, false);
            if (!identityStepMessage.isEmpty())
                guiGraphics.drawString(font, identityStepMessage, 8, 128, -12829636, false);
        } else {
            Component warning = Component.translatable("gui.crazyphone.crazy_phone_password_screen.warning_admin_visible")
                    .copy().withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
            guiGraphics.drawWordWrap(font, warning, 8, 32, 106, 0xFF5555);
            guiGraphics.drawString(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_mot_de_passe"), 8, 96, -12829636, false);
            // guistate (CrazyPhonePasswordScreenMenu's static field) only gets "textin:*" entries written by
            // an actual button click - reusing it here would validate against whatever number/name/password
            // were submitted by the LAST click (possibly from an earlier, already-successful registration
            // attempt still lingering in that static field), not what's currently typed. Build a fresh
            // snapshot of the live EditBox values instead, same as the click handlers do.
            guiGraphics.drawString(font, CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, getEditBoxAndCheckBoxValues()), 8, 128, -12829636, false);
        }
    }

    @Override
    public void init() {
        super.init();
        // Nothing to return to from the identity step (registration has no valid "previous screen"), but the
        // password step can always go back to the identity step it came from - purely a local step flip, see
        // onBackButtonPressed().
        setBackButtonActive(step == STEP_PASSWORD);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        if (step == STEP_IDENTITY) {
            initNumberField();
            initNameField();
            initResetButton();
            initNextButton();
        } else {
            initPasswordField();
            initValidateButton();
        }
    }

    /** Overrides the default server-history pop - this wizard's steps aren't separate screens in that
     * history (see the class doc on {@link #step}), so going back just flips the local step and re-renders,
     * the same way {@link #initNextButton} advances forward. */
    @Override
    protected void onBackButtonPressed() {
        if (step == STEP_PASSWORD) {
            step = STEP_IDENTITY;
            this.init(this.minecraft, this.width, this.height);
        }
    }

    private void initNumberField() {
        number = new EditBox(font, leftPos + 8, topPos + 40, 83, 18, Component.translatable("gui.crazyphone.crazy_phone_password_screen.number"));
        number.setMaxLength(32767);
        guistate.put("text:number", number);
        addWidget(number);
    }

    /** Ghosted placeholder shown while the field is empty - the player's own Minecraft username, not a
     * generic example name, since RegisterNewPhoneFromFormProcedure falls back to exactly that if the field
     * is ever actually submitted empty. */
    private String defaultNameSuggestion() {
        return this.minecraft != null && this.minecraft.player != null
                ? fr.lordfinn.crazyphone.utils.GameProfileCompat.name(this.minecraft.player.getGameProfile())
                : Component.translatable("gui.crazyphone.crazy_phone_password_screen.name").getString();
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
                setSuggestion(getValue().isEmpty() ? defaultNameSuggestion() : null);
            }
        };
        name.setMaxLength(32767);
        name.setSuggestion(defaultNameSuggestion());
        guistate.put("text:name", name);
        addWidget(name);
    }

    private void initPasswordField() {
        password = new PasswordEditBox(font, leftPos + 8, topPos + 107, 106, 18, Component.translatable("gui.crazyphone.crazy_phone_password_screen.password")) {
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

    /** Advances to the password step once the number/name are locally valid - checked client-side against
     * the already-synced phone registry, no server round trip needed just to flip pages. */
    private void initNextButton() {
        buttonAction = actionButton(Component.translatable("gui.crazyphone.crazy_phone_password_screen.button_suivant"),
                ACTION_BUTTON_X, ACTION_BUTTON_FULL_WIDTH,
                Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_password_screen.tooltip_suivant")),
                e -> {
                    HashMap<String, String> localState = new HashMap<>();
                    localState.put("textin:name", name.getValue());
                    localState.put("textin:number", number.getValue());
                    String validation = CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, localState, true);
                    if (validation.equals(CrazyPhoneGetInitialFormValidationMessageProcedure.OK)) {
                        identityStepMessage = "";
                        step = STEP_PASSWORD;
                        this.init(this.minecraft, this.width, this.height);
                    } else {
                        identityStepMessage = validation;
                    }
                });
        guistate.put("button:button_suivant", buttonAction);
        addRenderableWidget(buttonAction);
    }

    private void initValidateButton() {
        buttonAction = actionButton(Component.translatable("gui.crazyphone.crazy_phone_password_screen.button_valider"),
                ACTION_BUTTON_X, ACTION_BUTTON_FULL_WIDTH,
                Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_password_screen.tooltip_valider")),
                e -> {
                    //? if >=1.20.5 {
                    /*NetworkAccess.sendToServer(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
                    *///? } else {
                    PacketDistributor.SERVER.noArg().send(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
                    //?}
                    CrazyPhonePasswordScreenButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());
                });
        guistate.put("button:button_valider", buttonAction);
        addRenderableWidget(buttonAction);
    }

    private void initResetButton() {
        buttonReset = new ImageButton(leftPos + 96, topPos + 40, 18, 18,
            new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/reset.png"),
                              Crazyphone.parseId("crazyphone:textures/screens/reset.png")),
            e -> {
                //? if >=1.20.5 {
                /*NetworkAccess.sendToServer(new CrazyPhonePasswordScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
                *///? } else {
                PacketDistributor.SERVER.noArg().send(new CrazyPhonePasswordScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
                //?}
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
