package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
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
        if (Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhonePasswordScreenScreen sc) {
            // sc.number/sc.name are only non-null on STEP_IDENTITY - init() rebuilds the screen for
            // STEP_PASSWORD without them (only the password field exists there), so by the time "Valider"
            // is clicked and this runs, both are null and the auto-generated number and typed name never
            // made it into that final packet at all - the server never learned either value, no matter what
            // was typed on step one. guistate (unlike sc.number/sc.name) is never rebuilt by init() and
            // still holds STEP_IDENTITY's own EditBox widget references (put there by initNumberField/
            // initNameField, keyed "text:number"/"text:name") with their last-typed values intact - falling
            // back to those here is what actually gets step one's data into the submission.
            if (sc.number != null)
                textstate.put("textin:number", sc.number.getValue());
            else if (guistate.get("text:number") instanceof EditBox stepOneNumber)
                textstate.put("textin:number", stepOneNumber.getValue());
            if (sc.name != null)
                textstate.put("textin:name", sc.name.getValue());
            else if (guistate.get("text:name") instanceof EditBox stepOneName)
                textstate.put("textin:name", stepOneName.getValue());
            if (sc.password != null)
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
        extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE.get()),
                Component.translatable(step == STEP_PASSWORD
                        ? "gui.crazyphone.crazy_phone_password_screen.title_password_step"
                        : "gui.crazyphone.crazy_phone_password_screen.title"));
        if (step == STEP_IDENTITY) {
            number.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            name.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            password.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        }
        extractTooltip(guiGraphics, mouseX, mouseY);
    }
    *///? } else {
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
    //?}

    //? if >=26 {
    /*@Override
    public void resize(int width, int height) {
        // Only one of (number, name) vs password is ever non-null at a time - init() only builds the
        // fields for the current step (see STEP_IDENTITY/STEP_PASSWORD branches above).
        String numberVal = number != null ? number.getValue() : null;
        String nameVal = name != null ? name.getValue() : null;
        String passwordVal = password != null ? password.getValue() : null;
        super.resize(width, height);
        if (number != null && numberVal != null)
            number.setValue(numberVal);
        if (name != null && nameVal != null)
            name.setValue(nameVal);
        if (password != null && passwordVal != null)
            password.setValue(passwordVal);
    }
    *///? } else {
    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        // Only one of (number, name) vs password is ever non-null at a time - init() only builds the
        // fields for the current step (see STEP_IDENTITY/STEP_PASSWORD branches above).
        String numberVal = number != null ? number.getValue() : null;
        String nameVal = name != null ? name.getValue() : null;
        String passwordVal = password != null ? password.getValue() : null;
        super.resize(minecraft, width, height);
        if (number != null && numberVal != null)
            number.setValue(numberVal);
        if (name != null && nameVal != null)
            name.setValue(nameVal);
        if (password != null && passwordVal != null)
            password.setValue(passwordVal);
    }
    //?}

    //? if >=26 {
    /*@Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawLabels(guiGraphics);
    }
    *///? } else {
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawLabels(guiGraphics);
    }
    //?}

    private void drawLabels(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (step == STEP_IDENTITY) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_numero_associe_au_telephone"), 8, 29, -12829636, false);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate), -153, -111, -12829636, false);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_nom"), 8, 61, -12829636, false);
            if (!identityStepMessage.isEmpty())
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, identityStepMessage, 8, 128, -12829636, false);
        } else {
            Component warning = Component.translatable("gui.crazyphone.crazy_phone_password_screen.warning_admin_visible")
                    .copy().withStyle(ChatFormatting.BOLD, ChatFormatting.RED);
            guiGraphics./*$ gui_draw_word_wrap {*/drawWordWrap/*$}*/(font, warning, 8, 32, 106, 0xFFFF5555);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_mot_de_passe"), 8, 96, -12829636, false);
            // guistate (CrazyPhonePasswordScreenMenu's static field) only gets "textin:*" entries written by
            // an actual button click - reusing it here would validate against whatever number/name/password
            // were submitted by the LAST click (possibly from an earlier, already-successful registration
            // attempt still lingering in that static field), not what's currently typed. Build a fresh
            // snapshot of the live EditBox values instead, same as the click handlers do.
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, getEditBoxAndCheckBoxValues()), 8, 128, -12829636, false);
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
            //? if >=26 {
            /*this.init(this.width, this.height);
            *///? } else {
            this.init(this.minecraft, this.width, this.height);
            //?}
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
                        //? if >=26 {
                        /*this.init(this.width, this.height);
                        *///? } else {
                        this.init(this.minecraft, this.width, this.height);
                        //?}
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
                    // NOT also calling handleButtonAction() directly here, unlike this screen's own reset
                    // button and most other MCreator-generated buttons in this codebase - those extra local
                    // calls are a harmless "instant feedback" duplicate for idempotent/read-only actions, but
                    // registration WRITES to the shared world-wide PhoneRegistrySavedData. Live-debug logging
                    // showed the two independent executions (this local one racing against the real server-
                    // side packet handler) each pass their own validation, but whichever runs first claims
                    // the number in the registry and writes number/name onto ITS OWN ItemStack reference -
                    // the second (usually the authoritative server one, since it's a genuine network hop even
                    // on an integrated server) then sees the number already taken and silently no-ops,
                    // leaving the REAL, persisted item stack's own tags blank. Both still play the success
                    // sound (validation alone already passed for both), so it looked like it worked -
                    // explains why the very first phone in a session sometimes actually registered (pure
                    // ordering luck) and every one after kept bouncing back to the registration screen.
                    //? if >=1.20.5 {
                    /*NetworkAccess.sendToServer(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
                    *///? } else {
                    PacketDistributor.SERVER.noArg().send(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
                    //?}
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
        guistate.put("button:imagebutton_reset", buttonReset);
        addRenderableWidget(buttonReset);
    }
}
