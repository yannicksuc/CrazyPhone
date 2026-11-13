package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import fr.lordfinn.crazyphone.client.gui.components.PhoneButton;
import fr.lordfinn.crazyphone.client.gui.components.DiceButton;
import net.minecraft.client.gui.components.EditBox;
import fr.lordfinn.crazyphone.client.gui.components.PhoneEditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
//? if fabric || neoforge {
import net.minecraft.client.gui.components.WidgetSprites;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import fr.lordfinn.crazyphone.Config;
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
    private DiceButton buttonReset;

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
        if (renderLaunchSplashOnTop(guiGraphics))
            return;
        extractTooltip(guiGraphics, mouseX, mouseY);
    }
    *///? } else {
    //? if neoforge || fabric {
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
        if (renderLaunchSplashOnTop(guiGraphics))
            return;
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
    //?}
    // Real 1.20.1 vanilla has no renderBackground(GuiGraphics, int, int, float) override point (see
    // CrazyPhoneDefaultScreenScreen's own doc comment) - the dim is drawn directly here instead.
    //? if legacyforge {
    /*@Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x50000000);
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
        if (renderLaunchSplashOnTop(guiGraphics))
            return;
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
    *///?}
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

    /** Top and bottom of the room the admin warning may use: under the header, above the password label (y 96). */
    private static final int WARNING_TOP = 30, WARNING_BOTTOM = 92;
    private static final int WARNING_FILL = 0xFFFDE8E6, WARNING_FRAME = 0xFFE74C3C, WARNING_TEXT = 0xFFB83227;

    /**
     * The "admins can see this password" warning, in an alert box like the rest of the phone (pale red fill,
     * red 1 px frame with cut corners, dark red text). The text is drawn a bit smaller and never bold, and the
     * box sizes itself to the wrapped text: in a long language it shrinks the text further rather than run
     * over the password label below it.
     */
    private void drawAdminWarning(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        Component warning = Component.translatable("gui.crazyphone.crazy_phone_password_screen.warning_admin_visible");
        int x0 = 6, x1 = 116, pad = 3;
        int room = WARNING_BOTTOM - WARNING_TOP - 2 * pad;
        float scale = 0.75f;
        int lines;
        while (true) {
            int wrapWidth = Math.round((x1 - x0 - 2 * pad) / scale);
            lines = font.split(warning, wrapWidth).size();
            if (lines * 9 * scale <= room || scale <= 0.5f)
                break;
            scale -= 0.05f;
        }
        int textHeight = (int) Math.ceil(lines * 9 * scale);
        // Centered in the free room between the header and the password label.
        int boxHeight = textHeight + 2 * pad;
        int y0 = WARNING_TOP + Math.max(0, (WARNING_BOTTOM - WARNING_TOP - boxHeight) / 2), y1 = y0 + boxHeight;
        guiGraphics.fill(x0 + 1, y0, x1 - 1, y1, WARNING_FRAME);
        guiGraphics.fill(x0, y0 + 1, x1, y1 - 1, WARNING_FRAME);
        guiGraphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, WARNING_FILL);
        fr.lordfinn.crazyphone.utils.GuiCompat.pushPose(guiGraphics);
        fr.lordfinn.crazyphone.utils.GuiCompat.translate(guiGraphics, x0 + pad, y0 + pad);
        fr.lordfinn.crazyphone.utils.GuiCompat.scale(guiGraphics, scale, scale);
        guiGraphics./*$ gui_draw_word_wrap {*/drawWordWrap/*$}*/(font, warning, 0, 0, Math.round((x1 - x0 - 2 * pad) / scale), WARNING_TEXT);
        fr.lordfinn.crazyphone.utils.GuiCompat.popPose(guiGraphics);
    }

    private void drawLabels(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (step == STEP_IDENTITY) {
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_numero_associe_au_telephone"), 8, 29, -12829636, false);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate), -153, -111, -12829636, false);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable("gui.crazyphone.crazy_phone_password_screen.label_nom"), 8, 61, -12829636, false);
            if (!identityStepMessage.isEmpty())
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, identityStepMessage, 8, 128, -12829636, false);
        } else {
            drawAdminWarning(guiGraphics);
            String passwordLabelKey = Config.requirePhonePassword
                    ? "gui.crazyphone.crazy_phone_password_screen.label_mot_de_passe"
                    : "gui.crazyphone.crazy_phone_password_screen.label_mot_de_passe_optionnel";
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, Component.translatable(passwordLabelKey), 8, 96, -12829636, false);
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
        number = new PhoneEditBox(font, leftPos + 8, topPos + 40, 89, 14, Component.translatable("gui.crazyphone.crazy_phone_password_screen.number"));
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
        name = new PhoneEditBox(font, leftPos + 8, topPos + 72, 106, 14, Component.translatable("gui.crazyphone.crazy_phone_password_screen.name")) {
            @Override
            public void insertText(String text) {
                super.insertText(text);
                updateSuggestion();
            }

            // Real 1.20.1 vanilla predates EditBox#moveCursorTo's boolean "select" parameter (added by 1.20.4).
            //? if >=1.20.4 {
            @Override
            public void moveCursorTo(int pos, boolean flag) {
                super.moveCursorTo(pos, flag);
                updateSuggestion();
            }
            //?}
            //? if <1.20.4 {
            /*@Override
            public void moveCursorTo(int pos) {
                super.moveCursorTo(pos);
                updateSuggestion();
            }
            *///?}

            private void updateSuggestion() {
                setSuggestion(getValue().isEmpty() ? defaultNameSuggestion() : null);
            }
        };
        name.setMaxLength(32767);
        name.setSuggestion(defaultNameSuggestion());
        guistate.put("text:name", name);
        addWidget(name);
    }

    // "(optionnel)" ghosted placeholder when Config#requirePhonePassword is off, so a player isn't left
    // guessing whether leaving this field blank is actually allowed - the field's own validation (see
    // CrazyPhoneGetInitialFormValidationMessageProcedure) already accepts empty in that case, this is purely
    // a UI hint on top of that. Reads Config directly the same way other client-side screens in this mod
    // already do (e.g. CrazyPhoneConversationScreen/FabricPictureCapture) - on an integrated/singleplayer
    // server this is always the real value; on a dedicated server a remote client's own local copy could be
    // briefly stale if only Config's default was ever read, but this only affects the ghosted hint text
    // itself, never the actual (server-authoritative) validation outcome.
    private String passwordSuggestion() {
        return Component.translatable(Config.requirePhonePassword
                ? "gui.crazyphone.crazy_phone_password_screen.password"
                : "gui.crazyphone.crazy_phone_password_screen.password_optional").getString();
    }

    private void initPasswordField() {
        password = new PasswordEditBox(font, leftPos + 8, topPos + 107, 106, 14, Component.translatable("gui.crazyphone.crazy_phone_password_screen.password")) {
            @Override
            public void insertText(String text) {
                super.insertText(text);
                updateSuggestion();
            }

            // Real 1.20.1 vanilla predates EditBox#moveCursorTo's boolean "select" parameter (added by 1.20.4).
            //? if >=1.20.4 {
            @Override
            public void moveCursorTo(int pos, boolean flag) {
                super.moveCursorTo(pos, flag);
                updateSuggestion();
            }
            //?}
            //? if <1.20.4 {
            /*@Override
            public void moveCursorTo(int pos) {
                super.moveCursorTo(pos);
                updateSuggestion();
            }
            *///?}

            private void updateSuggestion() {
                setSuggestion(getValue().isEmpty() ? passwordSuggestion() : null);
            }
        };
        password.setMaxLength(32767);
        password.setSuggestion(passwordSuggestion());
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
        buttonAction = actionButton(PhoneButton.Style.CONFIRM, Component.translatable("gui.crazyphone.crazy_phone_password_screen.button_valider"),
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
                    NetworkAccess.sendToServer(new CrazyPhonePasswordScreenButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
                });
        guistate.put("button:button_valider", buttonAction);
        addRenderableWidget(buttonAction);
    }

    private void initResetButton() {
        // A rolling die: picks a new random number (same message as before).
        buttonReset = new DiceButton(leftPos + 100, topPos + 40, 14, e -> {
            NetworkAccess.sendToServer(new CrazyPhonePasswordScreenButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
            CrazyPhonePasswordScreenButtonMessage.handleButtonAction(entity, 1, x, y, z, getEditBoxAndCheckBoxValues());
        });
        buttonReset.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_password_screen.tooltip_reset")));
        guistate.put("button:imagebutton_reset", buttonReset);
        addRenderableWidget(buttonReset);
    }

	@Override
	protected boolean isLaunchScreen() {
		return true;
	}

	/** Only a phone that is not registered yet lands here: every new phone plays the splash again. */
	@Override
	protected boolean isNewPhoneScreen() {
		return true;
	}
}
