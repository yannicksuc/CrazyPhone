package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.CrazyphoneHomeScreenButtonMessage;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.procedures.IsPhonePasswordSetProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;

import java.util.HashMap;
import java.util.function.Supplier;

public class CrazyphoneHomeScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyphoneHomeScreenMenu> {
    private final HashMap<String, Object> guistate = CrazyphoneHomeScreenMenu.guistate;

    public CrazyphoneHomeScreenScreen(CrazyphoneHomeScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        return new HashMap<>();
    }

    //? if >=26 {
    /*@Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawPhoneNumber(guiGraphics);
        updateAutoLockTooltip();
    }
    *///? } else {
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPhoneNumber(guiGraphics);
        updateAutoLockTooltip();
    }
    //?}

    // Own number, so a player can read/share it without closing the phone to hover the item's tooltip.
    // Called from renderLabels/extractLabels (see above), which AbstractContainerScreen's own extractContents
    // already translates by (leftPos, topPos) before invoking - coordinates here are local to the panel, not
    // absolute screen coordinates (unlike renderHeader, which this screen never calls, since the home screen
    // has no title banner and this text lives in that same otherwise-empty top strip instead).
    private void drawPhoneNumber(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        String number = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, guistate);
        guiGraphics./*$ gui_draw_centered_string {*/drawCenteredString/*$}*/(this.font, number, this.imageWidth / 2, 12, 0xFF0000AA);
    }

    @Override
    public void init() {
        super.init();

        // A phone registered without a password (Config#requirePhonePassword off at registration time) can
        // never actually be locked - see CrazyPhoneLockProcedure/CrazyPhoneOnUseProcedure's own matching
        // guards - so its lock button is disabled here the same way it already is on the screens that have
        // no lock state of their own at all (CrazyPhonePasswordScreenScreen, CrazyPhoneSignInScreenScreen).
        if (!IsPhonePasswordSetProcedure.execute(entity.level(), CrazyPhoneHelper.getMainHandItemOrEmpty(entity)))
            setLockButtonActive(false);

        addAutoLockToggleButton();

        // Default positions
        int photoX = this.leftPos + 12;
        int albumsX = this.leftPos + 61;
        int contactsX = this.leftPos + 34;

        boolean isElection = PhoneRegistrySavedData
            .get(entity.level()).isMayorElectionOn;

        if (isElection) {
            addImageButton("imagebutton_elections", 3, "crazyphone-elections-icon", this.leftPos + 67, this.topPos + 96, 44, 62);
            contactsX -= 26;
        }

        // Photo: purely client-side, opens the same capture overlay the conversation camera icon and
        // punch-to-shoot use - bypasses addImageButton's generic send-packet-then-handleButtonAction
        // machinery entirely since there's nothing server-authoritative about framing a shot.
        net.minecraft.client.gui.components.ImageButton photoButton = new net.minecraft.client.gui.components.ImageButton(
                photoX, this.topPos + 28, 46, 62,
                new net.minecraft.client.gui.components.WidgetSprites(
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-photo-icon.png"),
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-photo-icon-hover.png")),
                e -> fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter("")) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
            //?}
        };
        guistate.put("button:imagebutton_photo", photoButton);
        this.addRenderableWidget(photoButton);

        addImageButton("imagebutton_albums", 1, "crazyphone-album-icon", albumsX, this.topPos + 28, 52, 62);
        addImageButton("imagebutton_contacts", 2, "crazyphone-contacts-icon", contactsX, this.topPos + 92, 53, 66);
    }

    // No tooltips on these - the home screen's 4 icon buttons are meant to be read at a glance, not hovered.
    private void addImageButton(String key, int buttonId, String baseIconName, int x, int y, int width, int height) {
        /*$ res_loc {*/ResourceLocation/*$}*/ normal = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + ".png");
        /*$ res_loc {*/ResourceLocation/*$}*/ hover = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + "-hover.png");

        ImageButton button = new ImageButton(x, y, width, height, new net.minecraft.client.gui.components.WidgetSprites(normal, hover), e -> {
            var values = getEditBoxAndCheckBoxValues();
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyphoneHomeScreenButtonMessage(buttonId, this.x, this.y, this.z, values));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyphoneHomeScreenButtonMessage(buttonId, this.x, this.y, this.z, values));
            //?}
            CrazyphoneHomeScreenButtonMessage.handleButtonAction(this.entity, buttonId, this.x, this.y, this.z, values);
        }) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
            //?}
        };

        guistate.put("button:" + key, button);
        this.addRenderableWidget(button);
    }

    // Per-phone, opt-in (default off) preference: auto-lock THIS specific phone on disconnect if it's still
    // unlocked at that point - see CrazyPhoneHelper#applyAutoLockOnDisconnect for the actual disconnect-time
    // sweep. buttonID 4 on CrazyphoneHomeScreenButtonMessage flips the "autoLock" NBT tag server-side; a
    // phone never opted into it (every phone before this feature) keeps behaving exactly as before (manual
    // lock only).
    private static final int AUTO_LOCK_TOGGLE_BUTTON_ID = 4;
    private static final int AUTO_LOCK_BUTTON_SIZE = 14;
    private Button autoLockButton;

    // Placed beside the phone number (drawn centered at y=12, see drawPhoneNumber) rather than below it or
    // in the icon row starting at topPos+28 - this exact spot (top-right corner of the panel) is clear of
    // every existing icon (photo/albums start at topPos+28, contacts/elections lower still) at every screen
    // width this mod uses.
    private void addAutoLockToggleButton() {
        int x = this.leftPos + this.imageWidth - 8 - AUTO_LOCK_BUTTON_SIZE;
        int y = this.topPos + 9;
        autoLockButton = createSquareIconButton(x, y, () -> isAutoLockEnabled() ? AUTO_LOCK_ICON_ON : AUTO_LOCK_ICON_OFF, b -> {
            var values = getEditBoxAndCheckBoxValues();
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyphoneHomeScreenButtonMessage(AUTO_LOCK_TOGGLE_BUTTON_ID, this.x, this.y, this.z, values));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyphoneHomeScreenButtonMessage(AUTO_LOCK_TOGGLE_BUTTON_ID, this.x, this.y, this.z, values));
            //?}
            CrazyphoneHomeScreenButtonMessage.handleButtonAction(this.entity, AUTO_LOCK_TOGGLE_BUTTON_ID, this.x, this.y, this.z, values);
        });
        guistate.put("button:imagebutton_autolock", autoLockButton);
        this.addRenderableWidget(autoLockButton);
        updateAutoLockTooltip();
    }

    private boolean isAutoLockEnabled() {
        return CrazyPhoneHelper.isPhoneAutoLockEnabled(CrazyPhoneHelper.getMainHandItemOrEmpty(entity));
    }

    // "locked" (U+1F512) while auto-lock is ON, "unlocked" (U+1F513) while it's OFF - same bundled Pixel
    // Twemoji font as CrazyPhonePhotoFrameResizeScreen's own Rotate/Fullbright icons (see createSquareIconButton
    // below, copied from that same screen's own method of the same name/signature).
    private static final Component AUTO_LOCK_ICON_ON = Component.literal("🔒");
    private static final Component AUTO_LOCK_ICON_OFF = Component.literal("🔓");

    // Re-set every frame (called from renderLabels/extractLabels, see above) rather than once in init() -
    // same reasoning as CrazyPhonePhotoFrameResizeScreen's own updateHotbarTooltips: both the icon (handled
    // automatically by createSquareIconButton's own supplier re-invocation) and this tooltip need to reflect
    // the CURRENTLY held phone's own live state, which can change while this screen is open (a click on this
    // very button, or the item's NBT changing from some other cause).
    private void updateAutoLockTooltip() {
        if (autoLockButton == null)
            return;
        boolean enabled = isAutoLockEnabled();
        String titleKey = enabled ? "gui.crazyphone.crazyphone_home_screen.tooltip_auto_lock_on" : "gui.crazyphone.crazyphone_home_screen.tooltip_auto_lock_off";
        String loreKey = enabled ? "gui.crazyphone.crazyphone_home_screen.tooltip_auto_lock_on.lore" : "gui.crazyphone.crazyphone_home_screen.tooltip_auto_lock_off.lore";
        autoLockButton.setTooltip(Tooltip.create(Component.translatable(titleKey)
                .append("\n").append(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY))));
    }

    // A small SQUARE Button showing a single centered icon glyph, using the real vanilla button background
    // (hover/press/disabled all keep working normally) - copied from CrazyPhonePhotoFrameResizeScreen's own
    // method of the same name/signature ("EXACTEMENT le meme pattern visuel que les boutons Rotate/Fullbright"
    // - live request), which itself credits CrazyPhoneContactsScreenScreen's delete/favorite buttons for the
    // same technique. Takes a SUPPLIER, not a fixed Component, so the icon can reflect the currently held
    // phone's own live auto-lock state rather than whatever it was at button-creation time.
    private Button createSquareIconButton(int x, int y, Supplier<Component> iconSupplier, Button.OnPress onPress) {
        return new Button(x, y, AUTO_LOCK_BUTTON_SIZE, AUTO_LOCK_BUTTON_SIZE, iconSupplier.get(), onPress, supplier -> iconSupplier.get().copy()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
                Component message = iconSupplier.get();
                this.extractDefaultSprite(guiGraphics);

                var font = Minecraft.getInstance().font;
                int textWidth = font.width(message);
                int drawX = getX() + (getWidth() - textWidth) / 2;
                int drawY = getY() + (getHeight() - 8) / 2;
                GuiCompat.pushPose(guiGraphics);
                GuiCompat.translate(guiGraphics, 0.5f, 0f);
                guiGraphics./^$ gui_draw_string {^/drawString/^$}^/(font, message, drawX, drawY, 0xFFFFFFFF, true);
                GuiCompat.popPose(guiGraphics);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                Component message = iconSupplier.get();
                setMessage(Component.empty());
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

                var font = Minecraft.getInstance().font;
                int textWidth = font.width(message);
                int drawX = getX() + (getWidth() - textWidth) / 2;
                int drawY = getY() + (getHeight() - 8) / 2;
                GuiCompat.pushPose(guiGraphics);
                GuiCompat.translate(guiGraphics, 0.5f, 0f);
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, message, drawX, drawY, 0xFFFFFFFF, true);
                GuiCompat.popPose(guiGraphics);
            }
            //?}
        };
    }

    @Override
    public HashMap<String, Object> getWidgets() {
        return guistate;
    }
}
