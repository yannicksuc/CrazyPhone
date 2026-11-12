package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.client.gui.components.PhoneButton;
import fr.lordfinn.crazyphone.client.gui.components.PhoneIconButton;
import fr.lordfinn.crazyphone.utils.GuiCompat;

import fr.lordfinn.crazyphone.Crazyphone;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.ClientMessageDraft;
import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.client.ClientFeatureFlagState;
import fr.lordfinn.crazyphone.client.ConversationClientCache;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.VoiceMessageUploadPacket;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;
import fr.lordfinn.crazyphone.voicechat.VoiceMessageRecorder;
import fr.lordfinn.crazyphone.client.ConversationClientCache.ConversationPage;
import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.client.gui.components.MessageDisplayManager;
import fr.lordfinn.crazyphone.client.gui.components.MessageDisplayManager.MessageEntry;
import fr.lordfinn.crazyphone.client.gui.components.SmallTextEditBox;
import fr.lordfinn.crazyphone.client.gui.components.EmojiDrawer;
import fr.lordfinn.crazyphone.client.ClientPrefs;
import fr.lordfinn.crazyphone.client.EmojiCatalog;
import fr.lordfinn.crazyphone.network.ConversationRequestPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneConversationButtonMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneMuteConversationMessage;
//? if fabric && >=1.20.5 {
/*import fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket;
*///?}
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
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
//? if fabric || neoforge {
import net.minecraft.client.gui.components.WidgetSprites;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

/**
 * Deviation from the old file: the old menu already held the conversation's entire message history (read
 * from a full-world sync blob that grew forever and eventually crashed the server on player join - see
 * PORTING_CONTRACT.md). Here the menu no longer carries any message history at all. Instead this screen
 * registers itself with {@link ConversationClientCache} and asks the server for the first page of messages
 * via {@link ConversationRequestPacket} right after the widgets are built, and the response comes back later
 * (asynchronously) through {@link fr.lordfinn.crazyphone.network.ConversationResponsePacket} ->
 * {@link ConversationClientCache#onPageReceived}. Received messages are buffered in {@link #receivedMessages}
 * so a screen resize (which rebuilds {@link #messageManager} from scratch, like the old code did every
 * init()) can replay them without a second network round trip.
 *
 * Pagination ("load older messages on scroll up") is intentionally NOT wired up: doing it correctly would
 * require {@link MessageDisplayManager} to support prepending entries above the current oldest message, but
 * that shared component (ported as-is, out of this file's scope) always inserts new entries at the
 * newest/bottom position. Per the porting contract this is an acceptable simplification - the first page
 * (most recent messages) loads on open, and live incoming messages still arrive instantly via
 * {@link #addMessage(String, MessageData)}, called by the new-message notification packet exactly like the
 * old code did.
 */
public class CrazyPhoneConversationScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneConversationMenu> {
    private final static HashMap<String, Object> guistate = CrazyPhoneConversationMenu.guistate;
    /** Second-from-right slot of the header banner's icon row, same position/size convention as the
     * contacts screen's add-contact icon - only shown for group conversations. Shifted one 16px slot left
     * of its old position (99) to make room for the always-shown mute icon at {@link #MUTE_ICON_X}. */
    // Header icons from the right (header icon rule in CrazyPhoneDefaultScreenScreen): mute, then group
    // settings (groups only), then call, 3 px apart.
    private final ItemStack groupSettingsIcon = CrazyPhoneConversationMenu.createGroupSettingsIcon();
    private final Component groupSettingsTooltip = Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_group_settings")
            .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    /** Sits immediately left of the group-settings icon when both are shown (group conversations, flush
     * against it - no gap between the two buttons), or immediately left of the mute icon when there's no
     * group icon to share a slot with (1:1 conversations). */
    private int callIconX() {
        return headerSlotX(menu.isGroup() ? 2 : 1);
    }

    private int groupSettingsIconX() {
        return headerSlotX(1);
    }

    private int muteIconX() {
        return headerSlotX(0);
    }
    /** Rightmost slot of the header banner's icon row - unlike the group-settings/call icons above, this
     * one is ALWAYS shown regardless of conversation type, since muting applies to any conversation. */
    private fr.lordfinn.crazyphone.client.gui.components.MessageTextBox message;
    private Button button_envoyer;
    /** Same cap the message field has always had (setMaxLength below); the emoji drawer checks it before
     * inserting, so an emoji is either inserted whole or not at all. */
    private static final int MAX_MESSAGE_LENGTH = 32767;
    /** Message field width without / with the emoji button squeezed in between it and the send button. */
    // Message row: field, (emoji button), send button at 100, 3 px apart, within the content width (8-114).
    private static final int MESSAGE_FIELD_WIDTH = 89;
    private static final int MESSAGE_FIELD_WIDTH_WITH_EMOJI = 72;
    private static final int EMOJI_BUTTON_X = 83;
    /** GLFW's shift modifier bit (GLFW_MOD_SHIFT), spelled out: LWJGL isn't on every node's compile classpath. */
    private static final int GLFW_MOD_SHIFT = 0x0001;
    /** Opens the emoji drawer; null when the drawer is turned off (client prefs) or has nothing to offer. */
    private ImageButton imagebutton_emoji;
    /** Kept across init() (a resize keeps it open on the same tab), re-anchored on every init. */
    private final EmojiDrawer emojiDrawer = new EmojiDrawer(this::insertEmoji);
    /** "Take and send image" - opens the capture overlay, then uploads and posts the shot into this
     * conversation directly (also lands in the phone's own My Photos list, same as any other capture). */
    private ImageButton imagebutton_crazyphoneaddimage;
    /** "Send image" - browses the My Photos gallery in send mode instead of taking a fresh shot, one or
     * several at once. Same hover-reveal column as imagebutton_crazyphoneaddimage/imagebutton_crazyphonevoicemessage
     * (see updateButtonVisibility) - closest to the send button, matching how often it's reached for. */
    private ImageButton imagebutton_attachphoto;
    // The send buttons stacked above the send button (y 158): 14 px each, 3 px apart.
    private static final int SEND_GALLERY_IMAGE_ICON_Y = 141;
    private static final int TAKE_AND_SEND_IMAGE_ICON_Y = 124;

    /** Voice message recording, replaces the text input row while active. NONE = normal text input;
     * RECORDING = mic capture in progress, [trash][waveform][pause] shown; REVIEWING = paused, waiting for
     * send or delete - no other interaction is possible in that state. */
    private enum VoiceRecordingState { NONE, RECORDING, REVIEWING }
    private VoiceRecordingState voiceRecordingState = VoiceRecordingState.NONE;
    private byte[] recordedAudio = new byte[0];
    /** Topmost of the three hover-reveal icons (furthest from the send button) - only constructed/shown
     * when SVC is available (see VoicechatIntegration), guarded with a null check everywhere it's touched. */
    private ImageButton imagebutton_crazyphonevoicemessage;
    private static final int SEND_VOICE_ICON_Y = 107;
    private static final int RECORDING_ROW_Y = 158;
    private static final int TRASH_X = 8;
    // Recording row: trash 8-21, waveform 25-96, pause / send 100-113: 3 px apart.
    private static final int WAVEFORM_X = 25;
    private static final int WAVEFORM_WIDTH = 72;
    private static final int PAUSE_SEND_X = 100;
    /** Real vanilla Button widgets (see createSquareIconButton) - trash/pause always exist once the screen
     * is built, only shown/hidden per voiceRecordingState; send reuses the same glyph as button_envoyer. */
    private Button button_voicetrash;
    private Button button_voicepause;
    private Button button_voicesend;

    private int scrollPosition = 0;
    private static final int SCROLL_STEP = 10;
    private MessageDisplayManager messageManager;

    /** Messages received so far for this conversation (oldest first), replayed into {@link #messageManager} on every init() (e.g. after a resize) so they aren't lost. */
    private final List<MessageData> receivedMessages = new ArrayList<>();
    /** Stored so the exact same reference can be passed to both setListener and clearListener. */
    private final BiConsumer<String, ConversationPage> conversationListener = this::onConversationPageReceived;
    private boolean firstPageRequested = false;
    /** Progressive "load older messages on scroll to top" - mirrors the image/voice-message lazy-fetch
     * philosophy: only the newest page is ever requested up front, older ones are fetched one at a time as
     * the player actually scrolls up to them, never the whole history in one shot. Unknown (assumed false)
     * until the first page's response tells us for sure. */
    private boolean hasMoreOlderMessages = false;
    /** Guards against firing a second load-older request while one is already in flight - scrolling
     * repeatedly at the very top would otherwise spam duplicate requests every single scroll tick. */
    private boolean loadingOlderMessages = false;

    public CrazyPhoneConversationScreen(CrazyPhoneConversationMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        HashMap<String, String> textstate = new HashMap<>();
        if (Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhoneConversationScreen sc) {
            textstate.put("textin:message", sc.message.getValue());
            textstate.put("conversationId", sc.menu.getConversationId());
        }
        return textstate;
    }

    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        //? if fabric && >=1.20.5 {
        /^// Skipped for the handful of frames a Fabric-native photo capture is pending, so the screenshot
        // shows the world instead of this screen itself - see FabricPictureCapture's own doc comment.
        if (fr.lordfinn.crazyphone.client.picture.FabricPictureCapture.suppressPhoneRendering)
            return;
        ^///?}
        message.visible = voiceRecordingState == VoiceRecordingState.NONE;
        if (imagebutton_emoji != null)
            imagebutton_emoji.visible = false;
        // The drawer belongs to the text row: gone as soon as a voice recording replaces that row.
        if (voiceRecordingState != VoiceRecordingState.NONE && emojiDrawer.isVisible())
            emojiDrawer.closeNow();
        // button_envoyer sits at the exact same coordinates/size as button_voicepause/button_voicesend
        // (leftPos+100, topPos+158, 14x14) - without this it stayed clickable (its own .visible was never
        // touched, only its render() call was skipped) and, being registered first, silently swallowed
        // every click on that spot via sendCurrentMessage()'s empty-text no-op before pause/send ever saw it.
        button_envoyer.visible = voiceRecordingState == VoiceRecordingState.NONE;
        if (button_voicetrash != null) {
            button_voicetrash.visible = voiceRecordingState != VoiceRecordingState.NONE;
            button_voicepause.visible = voiceRecordingState == VoiceRecordingState.RECORDING;
            button_voicesend.visible = voiceRecordingState == VoiceRecordingState.REVIEWING;
        }
        // maxVoiceMessageRecordingSeconds - without this a recording could run indefinitely; reuses the
        // same stop-and-move-to-REVIEWING branch the pause button itself triggers, so hitting the cap
        // behaves exactly like the player pausing manually right at that moment.
        if (voiceRecordingState == VoiceRecordingState.RECORDING
                && VoiceMessageRecorder.getElapsedSeconds() >= Config.maxVoiceMessageRecordingSeconds)
            onPauseSendClicked();
        updateButtonVisibility(mouseX, mouseY);
        layoutMessageField();
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderBanner(guiGraphics);
        if (menu.isGroup())
            renderGroupSettingsIcon(guiGraphics, mouseX, mouseY);
        if (VoicechatIntegration.isAvailable())
            renderCallIcon(guiGraphics, mouseX, mouseY);
        renderMuteIcon(guiGraphics, mouseX, mouseY);
        renderMessageWidget(guiGraphics, mouseX, mouseY, partialTicks);
        // Tooltip only, deferred until after the message feed: the feed's own (opaque) content renders
        // right after the icon in normal flow and, since the tooltip pops up below the cursor, overlapped
        // and painted over it - the box's thin border could survive at the edges while the text underneath
        // got covered, which is exactly the "wide box, no text" look this was producing.
        // >=26 always implies >=1.21.10, so these two hardcode the setComponentTooltipForNextFrame path -
        // no nested <1.21.10 split needed (and one couldn't be written as a further comment pair nested
        // inside this branch's own outer comment anyway).
        if (menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(groupSettingsTooltip)), mouseX, mouseY);
        }
        if (VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(callIconTooltip())), mouseX, mouseY);
        }
        if (isHoveringMuteIcon(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(muteIconTooltip())), mouseX, mouseY);
        }
        // Drawn again here, after the message feed: button_envoyer/imagebutton_crazyphoneaddimage sit at
        // the bottom-right corner of the message crop zone (y 144-173, crop ends at 158) as a deliberate
        // floating overlay, but the standard renderable pass (inside super.render() above) draws them
        // BEFORE the message feed - so a right-aligned message bubble reaching that corner painted over
        // them, most visibly hiding the add-image icon right as hovering the send button revealed it.
        // They're registered via addWidget (not addRenderableWidget) so this is their only render call.
        if (voiceRecordingState == VoiceRecordingState.NONE) {
            renderSendBar(guiGraphics, mouseX, mouseY);
            button_envoyer.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_crazyphoneaddimage.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_attachphoto.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            if (imagebutton_crazyphonevoicemessage != null)
                imagebutton_crazyphonevoicemessage.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            renderVoiceRecordingRow(guiGraphics, mouseX, mouseY, partialTicks);
        }
        // Last thing drawn on the phone (above the feed and the send column), before the tooltips.
        emojiDrawer.render(guiGraphics, mouseX, mouseY);
        renderHoveredHeadTooltip(guiGraphics, mouseX, mouseY);
        renderHoveredTimestampTooltip(guiGraphics, mouseX, mouseY);
        List<Component> barTooltip = sendBarTooltip(mouseX, mouseY);
        if (barTooltip != null)
            guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, barTooltip), mouseX, mouseY);
        this.extractTooltip(guiGraphics, mouseX, mouseY);
        // CursorEffects.endFrame() is NOT called here - PhoneClickableCursorHandler does it once, in a
        // ScreenEvent.Render.Post listener that fires after this whole method returns, so it can also
        // pick up the standard-button hover requests it makes itself without a premature reset.
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        //? if fabric && >=1.20.5 {
        /*// Skipped for the handful of frames a Fabric-native photo capture is pending, so the screenshot
        // shows the world instead of this screen itself - see FabricPictureCapture's own doc comment.
        if (fr.lordfinn.crazyphone.client.picture.FabricPictureCapture.suppressPhoneRendering)
            return;
        *///?}
        message.visible = voiceRecordingState == VoiceRecordingState.NONE;
        if (imagebutton_emoji != null)
            imagebutton_emoji.visible = false;
        // The drawer belongs to the text row: gone as soon as a voice recording replaces that row.
        if (voiceRecordingState != VoiceRecordingState.NONE && emojiDrawer.isVisible())
            emojiDrawer.closeNow();
        // button_envoyer sits at the exact same coordinates/size as button_voicepause/button_voicesend
        // (leftPos+100, topPos+158, 14x14) - without this it stayed clickable (its own .visible was never
        // touched, only its render() call was skipped) and, being registered first, silently swallowed
        // every click on that spot via sendCurrentMessage()'s empty-text no-op before pause/send ever saw it.
        button_envoyer.visible = voiceRecordingState == VoiceRecordingState.NONE;
        if (button_voicetrash != null) {
            button_voicetrash.visible = voiceRecordingState != VoiceRecordingState.NONE;
            button_voicepause.visible = voiceRecordingState == VoiceRecordingState.RECORDING;
            button_voicesend.visible = voiceRecordingState == VoiceRecordingState.REVIEWING;
        }
        // maxVoiceMessageRecordingSeconds - without this a recording could run indefinitely; reuses the
        // same stop-and-move-to-REVIEWING branch the pause button itself triggers, so hitting the cap
        // behaves exactly like the player pausing manually right at that moment.
        if (voiceRecordingState == VoiceRecordingState.RECORDING
                && VoiceMessageRecorder.getElapsedSeconds() >= Config.maxVoiceMessageRecordingSeconds)
            onPauseSendClicked();
        updateButtonVisibility(mouseX, mouseY);
        layoutMessageField();
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderBanner(guiGraphics);
        if (menu.isGroup())
            renderGroupSettingsIcon(guiGraphics, mouseX, mouseY);
        if (VoicechatIntegration.isAvailable())
            renderCallIcon(guiGraphics, mouseX, mouseY);
        renderMuteIcon(guiGraphics, mouseX, mouseY);
        renderMessageWidget(guiGraphics, mouseX, mouseY, partialTicks);
        // Tooltip only, deferred until after the message feed: the feed's own (opaque) content renders
        // right after the icon in normal flow and, since the tooltip pops up below the cursor, overlapped
        // and painted over it - the box's thin border could survive at the edges while the text underneath
        // got covered, which is exactly the "wide box, no text" look this was producing.
        if (menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(groupSettingsTooltip)), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(groupSettingsTooltip)), mouseX, mouseY);
            *///?}
        }
        if (VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(callIconTooltip())), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(callIconTooltip())), mouseX, mouseY);
            *///?}
        }
        if (isHoveringMuteIcon(mouseX, mouseY)) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(muteIconTooltip())), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, List.of(muteIconTooltip())), mouseX, mouseY);
            *///?}
        }
        // Drawn again here, after the message feed: button_envoyer/imagebutton_crazyphoneaddimage sit at
        // the bottom-right corner of the message crop zone (y 144-173, crop ends at 158) as a deliberate
        // floating overlay, but the standard renderable pass (inside super.render() above) draws them
        // BEFORE the message feed - so a right-aligned message bubble reaching that corner painted over
        // them, most visibly hiding the add-image icon right as hovering the send button revealed it.
        // They're registered via addWidget (not addRenderableWidget) so this is their only render call.
        if (voiceRecordingState == VoiceRecordingState.NONE) {
            renderSendBar(guiGraphics, mouseX, mouseY);
            button_envoyer.render(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_crazyphoneaddimage.render(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_attachphoto.render(guiGraphics, mouseX, mouseY, partialTicks);
            if (imagebutton_crazyphonevoicemessage != null)
                imagebutton_crazyphonevoicemessage.render(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            renderVoiceRecordingRow(guiGraphics, mouseX, mouseY, partialTicks);
        }
        // Last thing drawn on the phone (above the feed and the send column), before the tooltips.
        emojiDrawer.render(guiGraphics, mouseX, mouseY);
        renderHoveredHeadTooltip(guiGraphics, mouseX, mouseY);
        renderHoveredTimestampTooltip(guiGraphics, mouseX, mouseY);
        List<Component> barTooltip = sendBarTooltip(mouseX, mouseY);
        if (barTooltip != null) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, barTooltip), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, barTooltip), mouseX, mouseY);
            *///?}
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        // CursorEffects.endFrame() is NOT called here - PhoneClickableCursorHandler does it once, in a
        // ScreenEvent.Render.Post listener that fires after this whole method returns, so it can also
        // pick up the standard-button hover requests it makes itself without a premature reset.
    }
    //?}

    /**
     * The message feed is scissor-cropped to this rect (see renderMessageWidget) - hover tooltips must
     * only trigger when the cursor is actually within it, not just within a widget's raw (possibly
     * scrolled-off-screen) bounds. Also excludes the send/add-image buttons, which sit right at the
     * bottom-right corner of the crop zone and have their own tooltips (set via setTooltip, rendered by
     * the normal this.renderTooltip() call) - without this, a message/head happening to render behind
     * one of those buttons would show its own tooltip instead of "Send an image" / "Send the message".
     */
    private boolean isWithinMessageCropZone(int mouseX, int mouseY) {
        // Whatever the drawer covers is not hoverable/clickable feed.
        if (emojiDrawer.contains(mouseX, mouseY))
            return false;
        if (button_envoyer.isMouseOver(mouseX, mouseY) || imagebutton_crazyphoneaddimage.isMouseOver(mouseX, mouseY)
                || imagebutton_attachphoto.isMouseOver(mouseX, mouseY)
                || (imagebutton_crazyphonevoicemessage != null && imagebutton_crazyphonevoicemessage.isMouseOver(mouseX, mouseY)))
            return false;
        return mouseX >= this.leftPos && mouseX < this.leftPos + 200
                && mouseY >= this.topPos + 27 && mouseY < this.topPos + 158 - messageFieldExtra;
    }

    private void renderHoveredHeadTooltip(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        if (!isWithinMessageCropZone(mouseX, mouseY))
            return;
        for (MessageEntry entry : messageManager.getMessages()) {
            if (entry.widget().isHeadHovered(mouseX, mouseY)) {
                // Same "Name • number" format as the contact heads in the contacts menu.
                List<Component> headTooltip = List.of(
                        CrazyPhoneHelper.formatContactDisplayName(entry.widget().getContactName(), entry.widget().getContactNumber())
                );
                //? if <1.21.10 {
                guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, headTooltip), mouseX, mouseY);
                //? } else {
                /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, headTooltip), mouseX, mouseY);
                *///?}
                return;
            }
        }
    }

    private void renderHoveredTimestampTooltip(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        if (!isWithinMessageCropZone(mouseX, mouseY))
            return;
        for (MessageEntry entry : messageManager.getMessages()) {
            if (entry.widget().isBubbleHovered(mouseX, mouseY)) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(formatMessageTimestamp(entry.data().getTimecode())));
                if (entry.widget().isImageHovered(mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.crazyphone.crazy_phone_conversation.click_to_zoom")
                            .withStyle(ChatFormatting.GRAY));
                }
                //? if <1.21.10 {
                guiGraphics.renderComponentTooltip(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, lines), mouseX, mouseY);
                //? } else {
                /*guiGraphics.setComponentTooltipForNextFrame(this.font, fr.lordfinn.crazyphone.client.gui.PhoneTooltips.wrap(this.font, lines), mouseX, mouseY);
                *///?}
                return;
            }
        }
    }

    /** dd/MM HH:mm, or just HH:mm if the message was sent today - timecode is minutes since epoch. */
    private static String formatMessageTimestamp(int timecodeMinutes) {
        Instant instant = Instant.ofEpochSecond(timecodeMinutes * 60L);
        ZonedDateTime sentAt = instant.atZone(ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now();
        boolean sameDay = sentAt.toLocalDate().equals(now.toLocalDate());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(sameDay ? "HH:mm" : "dd/MM HH:mm");
        return sentAt.format(formatter);
    }

    private void renderBanner(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);

        // Filter out self from contacts
        List<Contact> otherContacts = menu.getContacts().stream()
                .filter(contact -> !contact.getNumber().equals(ownerNumber))
                .toList();

        ItemStack contactHead = resolveHeaderIcon(otherContacts);
        String names = otherContacts.stream().map(Contact::getName).collect(java.util.stream.Collectors.joining(", "));
        String title = (menu.isGroup() && !menu.getGroupName().isEmpty()) ? menu.getGroupName() : names;
        // The title's scroll boundary must stop at whichever right-side header button sits closest to it -
        // the call icon, which itself shifts left to sit flush against the group-settings cog when both are
        // shown (see callIconX()) - not a fixed constant, or the title would scroll behind/under a button.
        renderHeader(guiGraphics, contactHead, Component.literal(title), callIconX() - this.leftPos - 2);
    }

    private ItemStack resolveHeaderIcon(List<Contact> otherContacts) {
        if (menu.isGroup() && !menu.getGroupIcon().isEmpty())
            return menu.getGroupIcon();
        return otherContacts.isEmpty() ? ItemStack.EMPTY : CrazyPhoneHelper.createContactHead(otherContacts.get(0));
    }

    private void renderGroupSettingsIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        boolean hovered = isHoveringGroupSettingsIcon(mouseX, mouseY);
        if (hovered)
            CursorEffects.requestPointerCursor();
        drawHeaderItem(guiGraphics, groupSettingsIcon, groupSettingsIconX(), hovered);
    }

    private boolean isHoveringGroupSettingsIcon(double mouseX, double mouseY) {
        return isInHeaderSlot(mouseX, mouseY, groupSettingsIconX());
    }

    /** True while a call tied to THIS conversation is active/ringing/calling for the local player - a
     * player is only ever in one call at a time (see CallRegistry), so their own {@link ClientCallState}
     * fully answers "is there a call I'm part of for this conversation right now". */
    private boolean hasMyActiveCallHere() {
        return ClientCallState.isInCall() && menu.getConversationId().equals(ClientCallState.getConversationId());
    }

    /** Reopening an already-active call is always allowed regardless of the CALLS toggle - only STARTING a
     * new one is gated, same as the server-side check in CrazyPhoneCallActionMessage. */
    private boolean isCallStartDisabled() {
        return !hasMyActiveCallHere() && !ClientFeatureFlagState.isEnabled(FeatureFlag.CALLS);
    }

    private void renderCallIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        boolean hovered = isHoveringCallIcon(mouseX, mouseY) && !isCallStartDisabled();
        if (hovered)
            CursorEffects.requestPointerCursor();
        int color;
        if (isCallStartDisabled())
            color = 0x80FFFFFF;
        else if (hasMyActiveCallHere())
            color = 0xFF44FF66;
        else if (ClientCallState.hasJoinableCallElsewhere(menu.getConversationId()))
            color = CrazyPhoneColors.ACCENT_YELLOW; // same "you could join/rejoin this" color as the contacts-list badge
        else
            color = 0xFFFFFFFF;
        drawHeaderGlyph(guiGraphics, Component.literal("📞"), callIconX(), color, hovered);
    }

    private boolean isHoveringCallIcon(double mouseX, double mouseY) {
        return isInHeaderSlot(mouseX, mouseY, callIconX());
    }

    private Component callIconTooltip() {
        if (isCallStartDisabled())
            return Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_call_disabled")
                    .withStyle(style -> style.withColor(ChatFormatting.RED));
        if (hasMyActiveCallHere())
            return Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_reopen_call")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true));
        if (ClientCallState.hasJoinableCallElsewhere(menu.getConversationId()))
            return Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_rejoin_call")
                    .withStyle(style -> style.withColor(CrazyPhoneColors.ACCENT_YELLOW & 0xFFFFFF).withBold(true));
        return Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_call")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true));
    }

    private void onCallIconClicked() {
        if (isCallStartDisabled())
            return;
        int action = hasMyActiveCallHere() ? CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN : CrazyPhoneCallActionMessage.START_CALL;
        NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(action, menu.getConversationId()));
    }

    /** Always shown (unlike the group-settings/call icons above), regardless of conversation type - mutes
     * just THIS player's own sound/toast for THIS specific conversation (see
     * CrazyPhoneHelper#toggleMutedConversation); the unread-notification badge is a separate mechanism and
     * keeps appearing normally either way. Bell/no-bell glyph mirrors CrazyPhonePhotoFrameResizeScreen's
     * Fullbright hotbar button - same "icon + tooltip both re-read current state every frame" technique. */
    private void renderMuteIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        boolean hovered = isHoveringMuteIcon(mouseX, mouseY);
        if (hovered)
            CursorEffects.requestPointerCursor();
        String glyph = isConversationMuted() ? "🔕" : "🔔";
        drawHeaderGlyph(guiGraphics, Component.literal(glyph), muteIconX(), 0xFFFFFFFF, hovered);
    }

    private boolean isHoveringMuteIcon(double mouseX, double mouseY) {
        return isInHeaderSlot(mouseX, mouseY, muteIconX());
    }

    /** The current player's own phone is guaranteed to be in their main hand while this screen is open
     * (same assumption every other ownerNumber lookup in this class already makes - see renderBanner,
     * sendCurrentMessage, addMessage...), so the fast main-hand lookup is fine here too. */
    private boolean isConversationMuted() {
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);
        if (ownerNumber.isEmpty())
            return false;
        return CrazyPhoneHelper.isConversationMuted(this.menu.entity.level(), ownerNumber, this.menu.getConversationId());
    }

    private Component muteIconTooltip() {
        return Component.translatable(isConversationMuted()
                ? "gui.crazyphone.crazy_phone_conversation.tooltip_unmute"
                : "gui.crazyphone.crazy_phone_conversation.tooltip_mute")
            .withStyle(style -> style.withColor(ChatFormatting.GRAY).withBold(true));
    }

    private void onMuteIconClicked() {
        NetworkAccess.sendToServer(new CrazyPhoneMuteConversationMessage(menu.getConversationId()));
    }

    private void onMicIconClicked() {
        if (!ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES))
            return;
        voiceRecordingState = VoiceRecordingState.RECORDING;
        VoiceMessageRecorder.startRecording();
    }

    private void onAttachExistingPhotoClicked() {
        HashMap<String, String> values = getEditBoxAndCheckBoxValues();
        NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(1, x, y, z, values));
        CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 1, x, y, z, values);
    }

    /** [trash][waveform][pause-or-send] - replaces the normal text input row while recording/reviewing a
     * voice message. Only clickable region while REVIEWING is trash/send, matching the spec's "no other
     * interaction possible until sent or deleted". Trash/pause/send are real vanilla Button widgets with a
     * glyph label (see createSquareIconButton) - same style as the contacts screen's remove/favorite
     * buttons - not image-textured icon buttons like button_envoyer/imagebutton_crazyphoneaddimage.
     * Rendered via their own .render() calls here (they're addWidget-registered, not addRenderableWidget,
     * matching how this screen already draws its other floating buttons after the message feed). */
    //? if >=26 {
    /*private void renderVoiceRecordingRow(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int rowY = this.topPos + RECORDING_ROW_Y;
        int waveformX = this.leftPos + WAVEFORM_X;

        button_voicetrash.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);

        // Light-blue rectangle, deliberately distinct from the phone's own background texture. Even height
        // (12) so the white histogram bars inside it (see renderWaveformBars) center exactly, with no
        // rounding remainder from an odd height/2.
        guiGraphics.fill(waveformX, rowY, waveformX + WAVEFORM_WIDTH, rowY + 14, 0xFF3FA9F5);
        renderWaveformBars(guiGraphics, waveformX, rowY);

        if (voiceRecordingState == VoiceRecordingState.RECORDING)
            button_voicepause.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        else
            button_voicesend.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
    }
    *///? } else {
    private void renderVoiceRecordingRow(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        int rowY = this.topPos + RECORDING_ROW_Y;
        int waveformX = this.leftPos + WAVEFORM_X;

        button_voicetrash.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Light-blue rectangle, deliberately distinct from the phone's own background texture. Even height
        // (12) so the white histogram bars inside it (see renderWaveformBars) center exactly, with no
        // rounding remainder from an odd height/2.
        guiGraphics.fill(waveformX, rowY, waveformX + WAVEFORM_WIDTH, rowY + 14, 0xFF3FA9F5);
        renderWaveformBars(guiGraphics, waveformX, rowY);

        if (voiceRecordingState == VoiceRecordingState.RECORDING)
            button_voicepause.render(guiGraphics, mouseX, mouseY, partialTicks);
        else
            button_voicesend.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    //?}

    private void renderWaveformBars(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int waveformX, int rowY) {
        float[] levels = VoiceMessageRecorder.getRecentLevels();
        int barCount = levels.length;
        // 1px inset on both sides (matching the sent-message bubble's waveform), not just the left -
        // the old "+2" here left a 2px gap on the left but only 1px on the right.
        int startX = waveformX + 1;
        int totalWidth = (waveformX + WAVEFORM_WIDTH - 1) - startX;
        int barWidth = Math.max(1, totalWidth / barCount);
        int centerY = rowY + 7;

        // Same pushPose/scale(1, 0.5, 1) half-pixel trick as MessageWidget#renderVoiceWaveform: with an
        // even-height (14) rectangle, integer math can only center a bar exactly when its own height is
        // also even, otherwise the leftover pixel gets dumped entirely on one side. Doubling Y precision
        // here lets a bar's edges land on a half-pixel boundary and render genuinely centered regardless.
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.scale(guiGraphics, 1f, 0.5f);
        int centerY2x = centerY * 2;
        for (int i = 0; i < barCount; i++) {
            int barHeight = Math.max(1, Math.round(levels[i] * 10));
            int barX = startX + i * barWidth;
            guiGraphics.fill(barX, centerY2x - barHeight, barX + Math.max(1, barWidth - 1), centerY2x + barHeight, 0xFFFFFFFF);
        }
        GuiCompat.popPose(guiGraphics);
    }

    private void onTrashClicked() {
        VoiceMessageRecorder.discard();
        recordedAudio = new byte[0];
        voiceRecordingState = VoiceRecordingState.NONE;
    }

    /** Matches VoiceMessageRecorder's own capture format (48kHz mono 16-bit PCM) - used to turn the
     * recorded byte count into a display duration. */
    private static final int SVC_SAMPLE_RATE = 48000;

    private void onPauseSendClicked() {
        if (voiceRecordingState == VoiceRecordingState.RECORDING) {
            recordedAudio = VoiceMessageRecorder.stopRecording();
            voiceRecordingState = VoiceRecordingState.REVIEWING;
        } else if (voiceRecordingState == VoiceRecordingState.REVIEWING) {
            if (recordedAudio.length > 0) {
                int sampleCount = recordedAudio.length / 2;
                int durationTicks = Math.max(1, sampleCount * 20 / SVC_SAMPLE_RATE);
                byte[] envelope = VoiceMessageRecorder.computeEnvelope(recordedAudio, 24);
                // Client-generated, not server-assigned: unlike sendCurrentMessage() (whose optimistic
                // append needs no id at all), a voice bubble needs a real, playable voiceId immediately -
                // waiting on a server round-trip would mean the sender briefly sees a message they can't
                // click play on. A random UUID is collision-safe for this (128 bits, nothing brute-forceable).
                UUID voiceId = UUID.randomUUID();
                NetworkAccess.sendToServer(new VoiceMessageUploadPacket(menu.getConversationId(), voiceId, recordedAudio, durationTicks, envelope));

                String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);
                int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
                MessageData optimistic = MessageData.voice(timestampInMinutes, ownerNumber, voiceId, durationTicks, envelope);
                receivedMessages.add(optimistic);
                messageManager.addMessage(optimistic);
            }
            VoiceMessageRecorder.discard();
            recordedAudio = new byte[0];
            voiceRecordingState = VoiceRecordingState.NONE;
        }
    }

    /** The gallery-send, take-and-send, and voice-message icons share one "column": all three stay hidden
     * until the send button (or the column itself) is hovered, and all three stay visible together as long
     * as the cursor is anywhere in that column - hovering one doesn't hide the others. Checked against one
     * contiguous rectangle spanning the whole column (send button's bottom edge up to the topmost icon's
     * top edge) rather than each button's own small isMouseOver individually - the small gaps a cursor
     * drifts through moving from one 14px-tall icon to the next (normal mouse movement is rarely perfectly
     * vertical) otherwise flickered the column closed before it ever reached the topmost icon. */
    /** Whether the send button's icon column is shown (see updateButtonVisibility). */
    private boolean columnOpen;

    private void updateButtonVisibility(int mouseX, int mouseY) {
        int columnLeft = this.leftPos + 100;
        int columnRight = columnLeft + 14;
        int columnTop = this.topPos + SEND_VOICE_ICON_Y;
        int columnBottom = button_envoyer.getY() + button_envoyer.getHeight();
        boolean inColumn = mouseX >= columnLeft && mouseX < columnRight
                && mouseY >= columnTop && mouseY < columnBottom;
        boolean onSendButton = mouseX >= button_envoyer.getX() && mouseX < button_envoyer.getX() + button_envoyer.getWidth()
                && mouseY >= button_envoyer.getY() && mouseY < button_envoyer.getY() + button_envoyer.getHeight();
        // Opens only from the send button (the hidden icons' spot over the feed must not open it), then stays
        // open while the cursor is anywhere in the column.
        columnOpen = (onSendButton || (columnOpen && inColumn))
                // The column would pop up behind the emoji drawer, which covers that spot.
                && !emojiDrawer.isVisible();
        // Replaced by the send bar (renderSendBar): the old column never shows any more.
        boolean hoveringColumn = false;
        imagebutton_attachphoto.visible = hoveringColumn;
        imagebutton_crazyphoneaddimage.visible = hoveringColumn;
        if (imagebutton_crazyphonevoicemessage != null)
            imagebutton_crazyphonevoicemessage.visible = hoveringColumn;

        boolean imagesEnabled = ClientFeatureFlagState.isEnabled(FeatureFlag.IMAGES);
        imagebutton_attachphoto.active = imagesEnabled;
        imagebutton_attachphoto.setTooltip(Tooltip.create(Component.translatable(imagesEnabled
                ? "gui.crazyphone.crazy_phone_conversation.tooltip_send_image"
                : "gui.crazyphone.crazy_phone_conversation.tooltip_send_image_disabled")));
        imagebutton_crazyphoneaddimage.active = imagesEnabled;
        imagebutton_crazyphoneaddimage.setTooltip(Tooltip.create(Component.translatable(imagesEnabled
                ? "gui.crazyphone.crazy_phone_conversation.tooltip_take_and_send_image"
                : "gui.crazyphone.crazy_phone_conversation.tooltip_take_and_send_image_disabled")));

        if (imagebutton_crazyphonevoicemessage != null) {
            boolean voiceEnabled = ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES);
            imagebutton_crazyphonevoicemessage.active = voiceEnabled;
            imagebutton_crazyphonevoicemessage.setTooltip(Tooltip.create(Component.translatable(voiceEnabled
                    ? "gui.crazyphone.crazy_phone_conversation.tooltip_send_voice_message"
                    : "gui.crazyphone.crazy_phone_conversation.tooltip_send_voice_message_disabled")));
        }
    }

    /** How much taller than one line the message field is right now (it grows upward with its text). */
    private int messageFieldExtra;

    // ---- Send bar -----------------------------------------------------------------------------------------
    // Resting (empty field, not clicked): a short field with the emoji button in it, and the attach buttons
    // (album, photo, voice message) right of it. Writing (field clicked or text in it): the field takes the
    // whole row with a + on its left and the emoji button on its right, and the button on the right is the
    // voice message while the field is empty, the send arrow once something is written. The + opens a row of
    // the attach buttons under the bar, which moves up one row for it. Every icon is an emoji without a frame
    // (hover: a tinted square, pressed: one pixel down, disabled: faded).
    private static final int BAR_PLUS = 0, BAR_SMILEY = 1, BAR_ALBUM = 2, BAR_PHOTO = 3, BAR_VOICE = 4;
    private static final String GLYPH_PLUS = "\u2795", GLYPH_CLOSE = "\u2716", GLYPH_SMILEY = "\uD83D\uDE42",
            GLYPH_ALBUM = "\uD83D\uDDBC", GLYPH_PHOTO = "\uD83D\uDCF7", GLYPH_VOICE = "\uD83C\uDFA4";
    private static final int BAR_BOTTOM = 172, BAR_ROW = 17, RAIL_SIZE = 14, FIELD_ICON_SIZE = 12;
    private static final int BAR_HOVER = 0x33007EA2, BAR_PRESSED = 0x55007EA2;
    /** The + menu's tray: right under the raised field (its bottom edge at y 155), down to the bar's old
     * bottom, in the emoji drawer's colours (teal frame, cream panel) so it reads as part of the bar. */
    private static final int TRAY_TOP = 155, TRAY_BOTTOM = 172;
    private static final int TRAY_FRAME = 0xFF007EA2, TRAY_PANEL = 0xFFFFF7EE;

    private record BarIcon(int id, String glyph, int x, int y, int size, boolean enabled) {
    }

    /** The attach buttons keep their framed button images (14 px, the bar's height); only the + and the
     * emoji button inside the field are bare emojis. */
    private static String barTexture(int id) {
        return switch (id) {
            case BAR_ALBUM -> "crazyphone-send-gallery";
            case BAR_PHOTO -> "crazyphone-add-image";
            case BAR_VOICE -> "crazyphone-send-voice";
            default -> null;
        };
    }

    private static String barHoverTexture(int id) {
        return switch (id) {
            case BAR_ALBUM -> "crazyphone-send-gallery-hover";
            case BAR_PHOTO -> "crazyphone-add-hover";
            case BAR_VOICE -> "crazyphone-send-voice-hover";
            default -> null;
        };
    }

    private final List<BarIcon> barIcons = new ArrayList<>();
    /** The + menu (attach buttons under the bar) is open. */
    private boolean plusMenuOpen;

    private boolean voiceAvailable() {
        return imagebutton_crazyphonevoicemessage != null;
    }

    private boolean resting() {
        return message.getValue().isEmpty() && !message.isFocused() && !plusMenuOpen && !emojiDrawer.isVisible();
    }

    /** The message field takes the height its text wants (bottom edge fixed); the feed and the emoji drawer move
     * up by the same amount, and by one more row while the + menu is open. Lays out the send bar's icons too. */
    private void layoutMessageField() {
        boolean recording = voiceRecordingState != VoiceRecordingState.NONE;
        if (recording)
            plusMenuOpen = false;
        boolean rest = resting();
        boolean imagesEnabled = ClientFeatureFlagState.isEnabled(FeatureFlag.IMAGES);
        boolean voiceEnabled = voiceAvailable() && ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES);
        int railCount = voiceAvailable() ? 3 : 2;
        int shift = plusMenuOpen ? BAR_ROW : 0;
        int fieldWidth = rest ? 106 - railCount * BAR_ROW : 89;
        message.setRequestedWidth(fieldWidth);
        boolean smiley = imagebutton_emoji != null;
        message.setInsets(rest ? 0 : FIELD_ICON_SIZE, smiley ? FIELD_ICON_SIZE : 0);

        int height = message.wantedHeight();
        int fieldBottom = this.topPos + BAR_BOTTOM - shift;
        if (message.getY() != fieldBottom - height || height != messageFieldExtra + fr.lordfinn.crazyphone.client.gui.components.MessageTextBox.ONE_LINE_HEIGHT) {
            message.setRequestedHeight(height);
            message.setY(fieldBottom - height);
        }
        messageFieldExtra = height - fr.lordfinn.crazyphone.client.gui.components.MessageTextBox.ONE_LINE_HEIGHT + shift;
        messageManager.setBottom(this.topPos + 157 - messageFieldExtra);
        emojiDrawer.setBottomShift(messageFieldExtra);

        barIcons.clear();
        button_envoyer.setY(this.topPos + 158 - shift);
        button_envoyer.visible = !recording && !rest && (!message.getValue().isEmpty() || !voiceAvailable());
        if (recording)
            return;
        // Icons inside the field: a 12 px square on its bottom row, 1 px in from its frame.
        int iconY = fieldBottom - 1 - FIELD_ICON_SIZE;
        if (!rest)
            barIcons.add(new BarIcon(BAR_PLUS, plusMenuOpen ? GLYPH_CLOSE : GLYPH_PLUS, message.getX() + 1, iconY, FIELD_ICON_SIZE, true));
        if (smiley)
            barIcons.add(new BarIcon(BAR_SMILEY, GLYPH_SMILEY, message.getX() + fieldWidth - 1 - FIELD_ICON_SIZE, iconY, FIELD_ICON_SIZE, true));
        // The attach buttons: right of the field while resting, in a row under the bar while the + menu is open.
        if (rest || plusMenuOpen) {
            // In the + menu they sit in a tray under the field (see renderSendBar), 1 px in from its frame.
            int[] railX = rest ? new int[]{114 - railCount * BAR_ROW + 3, 114 - (railCount - 1) * BAR_ROW + 3, 100}
                    : new int[]{10, 10 + BAR_ROW, 10 + 2 * BAR_ROW};
            int railY = this.topPos + (rest ? 158 : TRAY_TOP + 1);
            barIcons.add(new BarIcon(BAR_ALBUM, GLYPH_ALBUM, this.leftPos + railX[0], railY, RAIL_SIZE, imagesEnabled));
            barIcons.add(new BarIcon(BAR_PHOTO, GLYPH_PHOTO, this.leftPos + railX[1], railY, RAIL_SIZE, imagesEnabled));
            // In the tray the voice message only shows when the right button is the send arrow (else it is there).
            if (voiceAvailable() && (rest || !message.getValue().isEmpty()))
                barIcons.add(new BarIcon(BAR_VOICE, GLYPH_VOICE, this.leftPos + railX[2], railY, RAIL_SIZE, voiceEnabled));
        }
        // Writing with an empty field: the right button is the voice message.
        if (!rest && message.getValue().isEmpty() && voiceAvailable())
            barIcons.add(new BarIcon(BAR_VOICE, GLYPH_VOICE, this.leftPos + 100, this.topPos + 158 - shift, RAIL_SIZE, voiceEnabled));
    }

    private BarIcon barIconAt(double mouseX, double mouseY) {
        for (BarIcon icon : barIcons)
            if (mouseX >= icon.x() && mouseX < icon.x() + icon.size() && mouseY >= icon.y() && mouseY < icon.y() + icon.size())
                return icon;
        return null;
    }

    private void onBarIcon(int id) {
        switch (id) {
            case BAR_PLUS -> plusMenuOpen = !plusMenuOpen;
            case BAR_SMILEY -> toggleEmojiDrawer();
            case BAR_ALBUM -> {
                plusMenuOpen = false;
                onAttachExistingPhotoClicked();
            }
            case BAR_PHOTO -> {
                plusMenuOpen = false;
                fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter(this.menu.getConversationId());
            }
            case BAR_VOICE -> {
                plusMenuOpen = false;
                onMicIconClicked();
            }
            default -> {
            }
        }
    }

    private void renderSendBar(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        boolean leftDown = Minecraft.getInstance().mouseHandler.isLeftPressed();
        if (plusMenuOpen && voiceRecordingState == VoiceRecordingState.NONE) {
            // The tray: same width as the field, its top edge shared with the field's bottom edge, corners cut.
            int x0 = message.getX(), x1 = x0 + message.getRequestedWidth();
            int y0 = this.topPos + TRAY_TOP, y1 = this.topPos + TRAY_BOTTOM;
            guiGraphics.fill(x0, y0, x1, y1 - 1, TRAY_FRAME);
            guiGraphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, TRAY_FRAME);
            guiGraphics.fill(x0 + 1, y0, x1 - 1, y1 - 1, TRAY_PANEL);
        }
        for (BarIcon icon : barIcons) {
            boolean hovered = mouseX >= icon.x() && mouseX < icon.x() + icon.size() && mouseY >= icon.y() && mouseY < icon.y() + icon.size();
            boolean pressed = hovered && icon.enabled() && leftDown;
            boolean toggled = (icon.id() == BAR_PLUS && plusMenuOpen) || (icon.id() == BAR_SMILEY && emojiDrawer.isOpen());
            String texture = barTexture(icon.id());
            if (texture != null) {
                if (hovered && icon.enabled())
                    CursorEffects.requestPointerCursor();
                String name = hovered && icon.enabled() ? barHoverTexture(icon.id()) : texture;
                // The images are 14 x 15 with an empty last row.
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, Crazyphone.parseId("crazyphone:textures/screens/" + name + ".png"),
                        icon.x(), icon.y() + (pressed ? 1 : 0), 0, 14, 15, icon.enabled() ? 1f : 0.45f);
                continue;
            }
            if (icon.id() == BAR_PLUS) {
                drawPlusButton(guiGraphics, icon, hovered, pressed);
                continue;
            }
            if (icon.enabled() && (hovered || toggled)) {
                CursorEffects.requestPointerCursor();
                int c = pressed || toggled ? BAR_PRESSED : BAR_HOVER;
                int x0 = icon.x(), y0 = icon.y(), x1 = x0 + icon.size(), y1 = y0 + icon.size();
                guiGraphics.fill(x0 + 1, y0, x1 - 1, y1, c);
                guiGraphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, c);
                guiGraphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, c);
            }
            drawBarGlyph(guiGraphics, icon, pressed ? 1 : 0);
        }
    }

    /** The + of the field, drawn in pixels (2 px strokes, 8 px across, centered in its 12 px square): dark on the
     * field while the menu is closed (tinted square on hover), teal on a tinted square while it is open, the
     * tray's colour. */
    private void drawPlusButton(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, BarIcon icon, boolean hovered, boolean pressed) {
        int x0 = icon.x(), y0 = icon.y(), x1 = x0 + icon.size(), y1 = y0 + icon.size();
        if (plusMenuOpen || hovered) {
            if (hovered)
                CursorEffects.requestPointerCursor();
            int c = plusMenuOpen || pressed ? BAR_PRESSED : BAR_HOVER;
            guiGraphics.fill(x0 + 1, y0, x1 - 1, y1, c);
            guiGraphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, c);
            guiGraphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, c);
        }
        int ink = plusMenuOpen ? TRAY_FRAME : 0xFF3A4456;
        int cx = x0 + icon.size() / 2, cy = y0 + icon.size() / 2 + (pressed ? 1 : 0);
        guiGraphics.fill(cx - 4, cy - 1, cx + 4, cy + 1, ink);
        guiGraphics.fill(cx - 1, cy - 4, cx + 1, cy + 4, ink);
    }

    /** The emoji centered in its square on its own drawn pixels (one pixel above the y it is given: see
     * EmojiDrawer), faded when the button is disabled. */
    private void drawBarGlyph(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, BarIcon icon, int down) {
        float[] b = fr.lordfinn.crazyphone.client.EmojiGlyphs.bounds(icon.glyph().codePointAt(0));
        float x, y;
        if (b != null) {
            x = icon.x() + (icon.size() - (b[2] - b[0])) / 2f - b[0];
            y = icon.y() + (icon.size() - (b[3] - b[1])) / 2f - b[1] + 1;
        } else {
            x = icon.x() + (icon.size() - (this.font.width(icon.glyph()) - 1)) / 2f;
            y = icon.y() + (icon.size() - 8) / 2f;
        }
        fr.lordfinn.crazyphone.utils.GuiCompat.pushPose(guiGraphics);
        fr.lordfinn.crazyphone.utils.GuiCompat.translate(guiGraphics, x, y + down);
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, Component.literal(icon.glyph()), 0, 0,
                icon.enabled() ? 0xFFFFFFFF : 0x66FFFFFF, false);
        fr.lordfinn.crazyphone.utils.GuiCompat.popPose(guiGraphics);
    }

    private List<Component> sendBarTooltip(double mouseX, double mouseY) {
        BarIcon icon = barIconAt(mouseX, mouseY);
        if (icon == null)
            return null;
        String base = "gui.crazyphone.crazy_phone_conversation.";
        return switch (icon.id()) {
            case BAR_PLUS -> List.of(Component.translatable(base + (plusMenuOpen ? "tooltip_attach_close" : "tooltip_attach")));
            case BAR_SMILEY -> List.of(Component.translatable("gui.crazyphone.emoji_drawer.button"));
            case BAR_ALBUM -> List.of(Component.translatable(base + (icon.enabled() ? "tooltip_send_image" : "tooltip_send_image_disabled")));
            case BAR_PHOTO -> List.of(Component.translatable(base + (icon.enabled() ? "tooltip_take_and_send_image" : "tooltip_take_and_send_image_disabled")));
            case BAR_VOICE -> List.of(Component.translatable(base + (icon.enabled() ? "tooltip_send_voice_message" : "tooltip_send_voice_message_disabled")));
            default -> null;
        };
    }

    private void renderMessageWidget(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTicks) {
        layoutMessageField();
        guiGraphics.enableScissor(this.leftPos, this.topPos + 27, this.leftPos + 200, this.topPos + 158 - messageFieldExtra);
        // Widgets outside the visible crop (scrolled off-screen) still get rendered here - the scissor
        // only clips what's drawn, not the widgets' own hover checks (image zoom/shadow, cursor change).
        // Feeding them an impossible coordinate when the real cursor is outside the crop zone makes every
        // hover check inside MessageWidget naturally fail, instead of teaching every such check about the
        // crop rect individually.
        boolean insideCrop = isWithinMessageCropZone(mouseX, mouseY);
        int effectiveMouseX = insideCrop ? mouseX : Integer.MIN_VALUE;
        int effectiveMouseY = insideCrop ? mouseY : Integer.MIN_VALUE;
        messageManager.render(guiGraphics, effectiveMouseX, effectiveMouseY);
        guiGraphics.disableScissor();
    }

    // Real 1.20.1 vanilla predates GuiEventListener's horizontal-scroll parameter (added by 1.20.4) - only
    // the vertical delta is ever used here either way.
    //? if >=1.20.4 {
    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (emojiDrawer.mouseScrolled(x, y, dy))
            return true;
        scrollPosition += dy * SCROLL_STEP;

        if (scrollPosition < 0 || messageManager.getTotalHeight() <= 132)
            scrollPosition = 0;
        else if (scrollPosition > (messageManager.getTotalHeight() - 132))
            scrollPosition = (messageManager.getTotalHeight() - 132);
        messageManager.setScrollOffset(scrollPosition);
        maybeLoadOlderMessages();
        return true;
    }
    //?}
    //? if <1.20.4 {
    /*@Override
    public boolean mouseScrolled(double x, double y, double dy) {
        if (emojiDrawer.mouseScrolled(x, y, dy))
            return true;
        scrollPosition += dy * SCROLL_STEP;

        if (scrollPosition < 0 || messageManager.getTotalHeight() <= 132)
            scrollPosition = 0;
        else if (scrollPosition > (messageManager.getTotalHeight() - 132))
            scrollPosition = (messageManager.getTotalHeight() - 132);
        messageManager.setScrollOffset(scrollPosition);
        maybeLoadOlderMessages();
        return true;
    }
    *///?}

    /** Fires a "load older" request the moment the player scrolls all the way to the top of what's
     * currently loaded - resetPositions()/setScrollOffset() above don't shift anything already on screen
     * when the response arrives (the newest message's Y only depends on scrollOffset, not on how many
     * older entries exist behind it), so the older content just appears further up as the player keeps
     * scrolling, exactly like any other lazy-loaded chat feed. */
    private void maybeLoadOlderMessages() {
        if (loadingOlderMessages || !hasMoreOlderMessages)
            return;
        if (messageManager.getTotalHeight() > 132 && scrollPosition < messageManager.getTotalHeight() - 132)
            return;
        loadingOlderMessages = true;
        NetworkAccess.sendToServer(new ConversationRequestPacket(this.menu.getConversationId(), receivedMessages.size()));
    }

    @Override
    public void init() {
        super.init();
        initializeButtons();
        initializeTextAndMessageWidget();
        initializeEditBox();
        addRenderableWidgets();
        emojiDrawer.setPhoneOrigin(this.leftPos, this.topPos);
        if (imagebutton_emoji == null)
            emojiDrawer.closeNow();
        requestFirstPageIfNeeded();
    }

    private void initializeTextAndMessageWidget() {
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);
        // fullWidth is deliberately capped to fit inside the phone's own 122px-wide frame (not the
        // message feed's scissor rect, which extends to leftPos+200 - well past the phone's right edge,
        // a pre-existing harmless quirk that only mattered once something this wide tried to use it): a
        // system message spanning past x=122 rendered outside the visible phone background entirely.
        // 106, not 108 - matches the symmetric 8px margin on both sides (122 - 8 - 8), same width as the
        // call screens' own GRID_WIDTH; 108 was overshooting the right margin by 2px.
        messageManager = new MessageDisplayManager(this.leftPos + 8, this.topPos + 157, 91, 106, 0.75f,
                this.menu.getContacts(), ownerNumber);

        // Replay whatever pages have already come back from the server (survives resize, which rebuilds
        // messageManager from scratch just like the old code that read the whole history from the menu).
        for (MessageData message : receivedMessages) {
            messageManager.addMessage(message);
        }
    }

    /**
     * Registers the client-side listener and asks the server for the first (most recent) page of this
     * conversation. Only done once per screen instance - guarded so a resize (which re-runs init()) doesn't
     * spam duplicate requests. Safe to call after {@link #messageManager} has been created since it's called
     * at the end of init(), after {@link #initializeTextAndMessageWidget()}.
     */
    private void requestFirstPageIfNeeded() {
        if (firstPageRequested)
            return;
        firstPageRequested = true;
        ConversationClientCache.setListener(conversationListener);
        NetworkAccess.sendToServer(new ConversationRequestPacket(this.menu.getConversationId(), 0));
    }

    private void onConversationPageReceived(String conversationId, ConversationPage page) {
        if (!conversationId.equals(this.menu.getConversationId()))
            return;

        List<MessageData> pageData = new ArrayList<>();
        for (CompoundTag messageTag : page.messages()) {
            MessageData data = CrazyPhoneHelper.getMessageFromTag(messageTag);
            if (data != null)
                pageData.add(data);
        }

        if (page.skipFromEnd() == 0) {
            // The newest page (initial open, or a live re-request) - server returns it oldest-first, and
            // repeatedly appending each as the new newest-end entry naturally rebuilds the correct order
            // (see MessageDisplayManager#addMessage), same as the resize-replay loop below does.
            for (MessageData data : pageData) {
                receivedMessages.add(data);
                if (messageManager != null)
                    // Deliberately NOT calling addRenderableWidget here: message widgets are rendered by
                    // messageManager.render(guiGraphics) inside the scissor block in renderMessageWidget(),
                    // and clicks are dispatched manually in mouseClicked() below. Registering them as
                    // renderable widgets too made the standard Screen render pass draw them a second time,
                    // unclipped - that's what caused messages to overflow above/below the visible feed area
                    // regardless of scroll position (the scroll math itself was fine).
                    messageManager.addMessage(data);
            }
        } else {
            // An older page (scrolled-to-top pagination) - keep receivedMessages in strict global
            // oldest-to-newest order by prepending the whole page at its front (the page itself already
            // arrives oldest-first, so a straight prepend preserves that), so a later resize's replay loop
            // (which always iterates forward and appends-as-newest) still rebuilds the correct final order.
            receivedMessages.addAll(0, pageData);
            if (messageManager != null) {
                // The live widget stack, unlike the replay list above, needs the page fed newest-of-page
                // first: prependOlderMessage always becomes the new oldest/top-most entry, so feeding it in
                // reverse is what makes B end up above A when the page (oldest-first) was [A, B].
                for (int i = pageData.size() - 1; i >= 0; i--)
                    messageManager.prependOlderMessage(pageData.get(i));
            }
            loadingOlderMessages = false;
        }
        hasMoreOlderMessages = page.hasMore();
    }

    @Override
    public void onClose() {
        super.onClose();
        ConversationClientCache.clearListener(conversationListener);
        // Leaving mid-recording (Escape, back button, etc.) must not leave the recorder stuck "on" -
        // it's a static, screen-independent flag that would otherwise keep intercepting normal mic audio.
        if (voiceRecordingState != VoiceRecordingState.NONE)
            VoiceMessageRecorder.discard();
    }

    /**
     * {@code onClose()} above is NOT reliably called for every way this screen goes away - it's only
     * ever invoked by code that explicitly calls it (Escape, an explicit "back" handler), never by the
     * far more common path here: the SERVER opening a different phone screen (contacts,
     * back button, closing the phone entirely...), which just replaces this screen out from under the
     * player via {@code Minecraft#setScreen(...)} - that's exactly the "typed something, navigated away,
     * came back and it was gone" bug this was. {@code removed()}, unlike {@code onClose()}, IS called by
     * vanilla on every single one of those paths (it's how {@code Minecraft#setScreen} always tears down
     * whatever screen it's replacing), making it the one reliable place to persist the draft.
     */
    @Override
    public void removed() {
        super.removed();
        emojiDrawer.closeNow();
        ClientMessageDraft.saveOnClose(this.menu.getConversationId(), message.getValue());
    }

    private void initializeEditBox() {
        message = new fr.lordfinn.crazyphone.client.gui.components.MessageTextBox(this.font, this.leftPos + 8, this.topPos + 158,
                imagebutton_emoji != null ? MESSAGE_FIELD_WIDTH_WITH_EMOJI : MESSAGE_FIELD_WIDTH,
                Component.translatable("gui.crazyphone.crazy_phone_conversation.message")) {
            @Override
            public void insertText(String text) {
                super.insertText(text);
                // Live shortcode/emoticon conversion: a single spacebar press is what "finishes" a token
                // while typing (paste operations, multi-char text, stay covered by the full-message pass in
                // sendCurrentMessage() instead - this is specifically the live-typing case) - convert just
                // the word immediately before the cursor, then re-set the value so the emoji (not the raw
                // shortcode) is what's actually sitting in the field afterward, cursor landing right after
                // the space that triggered it.
                if (" ".equals(text)) {
                    int cursor = getCursorPosition();
                    String beforeSpace = getValue().substring(0, cursor - 1);
                    String converted = fr.lordfinn.crazyphone.client.EmojiShortcodes.tryConvertLastToken(beforeSpace);
                    if (converted != null) {
                        String rest = getValue().substring(cursor);
                        setValue(converted + " " + rest);
                        //? if >=1.20.4 {
                        moveCursorTo(converted.length() + 1, false);
                        //?}
                        //? if <1.20.4 {
                        /*moveCursorTo(converted.length() + 1);
                        *///?}
                    }
                }
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
                if (getValue().isEmpty())
                    setSuggestion(
                            Component.translatable("gui.crazyphone.crazy_phone_conversation.message").getString());
                else
                    setSuggestion(null);
            }
        };
        message.setMaxLength(MAX_MESSAGE_LENGTH);
        message.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_conversation.message").getString());
        // Restores whatever was saved for this conversation the last time it was closed/navigated away
        // from with unsent text (see onClose).
        String restoredDraft = ClientMessageDraft.restore(this.menu.getConversationId());
        if (!restoredDraft.isEmpty())
            message.setValue(restoredDraft);
        guistate.put("text:message", message);
        this.addWidget(this.message);
    }

    private void initializeButtons() {
        button_envoyer = createSendMessageButton();
        imagebutton_emoji = ClientPrefs.emojiPickerEnabled() && !EmojiCatalog.categories().isEmpty()
                ? createEmojiButton() : null;
        imagebutton_crazyphoneaddimage = createImageButton();
        imagebutton_attachphoto = createGallerySendButton();
        if (VoicechatIntegration.isAvailable()) {
            imagebutton_crazyphonevoicemessage = createVoiceMessageButton();
            button_voicetrash = createSquareIconButton(this.leftPos + TRASH_X, this.topPos + RECORDING_ROW_Y,
                    Component.translatable("gui.crazyphone.crazy_phone_conversation.button_voice_trash"), PhoneButton.Style.DANGER,
                    e -> onTrashClicked());
            button_voicepause = createSquareIconButton(this.leftPos + PAUSE_SEND_X, this.topPos + RECORDING_ROW_Y,
                    Component.translatable("gui.crazyphone.crazy_phone_conversation.button_voice_pause"), PhoneButton.Style.NEUTRAL,
                    e -> onPauseSendClicked());
            // Same crazyphone-send-message.png button as the main send button - every "send" action in
            // this mod uses this one texture now, not the glyph-only square style trash/pause still use.
            //? if legacyforge {
            /*ResourceLocation voiceSendNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message.png");
            ResourceLocation voiceSendHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message-hover.png");
            button_voicesend = new ImageButton(this.leftPos + PAUSE_SEND_X, this.topPos + RECORDING_ROW_Y, 14, 14, 0, 0, 14, voiceSendNormal,
                    e -> onPauseSendClicked()) {
                private final WidgetSprites sprites = new WidgetSprites(voiceSendNormal, voiceSendHover);

                @Override
                public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
                }
            };
            *///? } else {
            button_voicesend = new ImageButton(this.leftPos + PAUSE_SEND_X, this.topPos + RECORDING_ROW_Y, 14, 14,
                    new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message.png"),
                            Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message-hover.png")),
                    e -> onPauseSendClicked()) {
                //? if >=26 {
                /*@Override
                public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                    // Manual blit, not vanilla ImageButton's default GUI-sprite-atlas lookup - same reason
                    // as every other send-style icon button in this class (see createSendMessageButton).
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
                }
                *///? } else {
                @Override
                public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                    // Manual blit, not vanilla ImageButton's default GUI-sprite-atlas lookup - same reason
                    // as every other send-style icon button in this class (see createSendMessageButton).
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
                }
                //?}
            };
            //?}
        }
    }

    /**
     * A 14x14 square Button showing a single centered icon glyph - same helper as the contacts screen's
     * remove/favorite buttons (CrazyPhoneContactsScreenScreen#createSquareIconButton). Vanilla's own text
     * centering truncates (buttonWidth - textWidth)/2 to an int, which for an odd leftover visibly biases
     * the glyph a pixel off-center - drawing it ourselves with a 0.5px sub-pixel pose translate lands it
     * exactly in the middle instead. The real button background still comes from vanilla (via
     * super.renderWidget with a blanked-out message), so hover/press/disabled states keep working normally.
     */
    private Button createSquareIconButton(int x, int y, Component icon, PhoneButton.Style style, Button.OnPress onPress) {
        return new PhoneIconButton(x, y, 14, () -> icon, onPress, style);
    }

    /** The emoji button between the message field and the send button: same 14x14 icon-button look as
     * the hover column (crazyphone-emoji.png), title + lore tooltip. Clicks are dispatched by hand in
     * mouseClickedImpl (the small-text field's own hit box reaches under it), keyboard focus still works. */
    private ImageButton createEmojiButton() {
        /*$ res_loc {*/ResourceLocation/*$}*/ emojiNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-emoji.png");
        /*$ res_loc {*/ResourceLocation/*$}*/ emojiHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-emoji-hover.png");
        //? if legacyforge {
        /*ImageButton button = new ImageButton(this.leftPos + EMOJI_BUTTON_X, this.topPos + 158, 14, 14, 0, 0, 14, emojiNormal,
                e -> toggleEmojiDrawer()) {
            private final WidgetSprites sprites = new WidgetSprites(emojiNormal, emojiHover);

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused() || emojiDrawer.isOpen()), getX(), getY(), 300, width, height);
            }
        };
        *///? } else {
        ImageButton button = new ImageButton(this.leftPos + EMOJI_BUTTON_X, this.topPos + 158, 14, 14,
                new WidgetSprites(emojiNormal, emojiHover), e -> toggleEmojiDrawer()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused() || emojiDrawer.isOpen()), getX(), getY(), 300, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                // Shown "pressed" (hover sprite) while the drawer is open, so it reads as a toggle.
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused() || emojiDrawer.isOpen()), getX(), getY(), 300, width, height);
            }
            //?}
        };
        //?}
        List<Component> lines = EmojiDrawer.buttonTooltip();
        button.setTooltip(Tooltip.create(lines.get(0).copy().append("\n").append(lines.get(1))));
        return button;
    }

    /** Dev only (/phoneui type): puts this text in the message field, as if typed, "\n" for a new line. */
    public void devTypeMessage(String text) {
        message.setValue(text);
        this.setFocused(message);
        message.setFocused(true);
    }

    /** Dev only (/phoneui send): sends what is in the message field, like Enter. */
    public void devSendMessage() {
        sendCurrentMessage();
    }

    /** Dev only (/phoneui emojitab): shows that tab of the emoji drawer. */
    /** Dev only (UI test bench): the middle of a send bar icon (see BAR_*), or of the field if it is not shown. */
    public int[] devBarIconCenter(int id) {
        for (BarIcon icon : barIcons)
            if (icon.id() == id)
                return new int[]{icon.x() + icon.size() / 2, icon.y() + icon.size() / 2};
        return new int[]{message.getX() + 4, message.getY() + 4};
    }

    /** Dev only (UI test bench): the middle of header slot n (0 = rightmost). */
    public int[] devHeaderSlotCenter(int n) {
        return new int[]{headerSlotX(n) + HEADER_SLOT / 2, this.topPos + HEADER_SLOT_Y + HEADER_SLOT / 2};
    }

    /** Dev only (/phoneui plus): opens or closes the + menu, like a click on the +. */
    public void devTogglePlus() {
        plusMenuOpen = !plusMenuOpen;
    }

    /** Dev only (/phoneui unfocus): leaves the message field, as a click elsewhere does. */
    public void devUnfocus() {
        message.setFocused(false);
        this.setFocused(null);
    }

    public void devEmojiTab(int tab) {
        emojiDrawer.selectTab(tab);
        emojiDrawer.devHighlight(10);
    }

    public void toggleEmojiDrawer() {
        if (imagebutton_emoji == null || voiceRecordingState != VoiceRecordingState.NONE) {
            emojiDrawer.closeNow();
            return;
        }
        emojiDrawer.toggle();
        // Keep typing in the message field while picking.
        this.setFocused(message);
        message.setFocused(true);
    }

    /**
     * Inserts a picked emoji at the message field's cursor (replacing the selection, if any), the same way
     * typing it would. Never inserts past the field's length cap (the emoji is skipped whole, never cut in
     * half), and never inside an existing emoji: a cursor sitting inside a surrogate pair, a ZWJ sequence,
     * a skin tone, a variation selector or a flag pair is moved to the end of that emoji first.
     */
    private void insertEmoji(EmojiCatalog.Emoji emoji) {
        if (emoji == null || message == null || voiceRecordingState != VoiceRecordingState.NONE)
            return;
        String glyph = emoji.glyph();
        if (glyph == null || glyph.isEmpty())
            return;
        String value = message.getValue();
        String selected = message.getHighlighted();
        int selectedLength = selected == null ? 0 : selected.length();
        if ((long) value.length() - selectedLength + glyph.length() > MAX_MESSAGE_LENGTH)
            return;
        if (selectedLength == 0) {
            int cursor = Math.max(0, Math.min(value.length(), message.getCursorPosition()));
            int safe = safeInsertionPoint(value, cursor);
            if (safe != cursor)
                moveMessageCursor(safe);
        }
        message.insertText(glyph);
        EmojiCatalog.recordUse(emoji);
        this.setFocused(message);
        message.setFocused(true);
    }

    private void moveMessageCursor(int pos) {
        //? if >=1.20.4 {
        message.moveCursorTo(pos, false);
        //?}
        //? if <1.20.4 {
        /*message.moveCursorTo(pos);
        *///?}
    }

    /** Nearest position at or after {@code cursor} that is not inside a multi-char emoji. */
    static int safeInsertionPoint(String text, int cursor) {
        int i = Math.max(0, Math.min(text.length(), cursor));
        // Bounded: a pathological string can never make this loop forever.
        for (int guard = 0; guard < 64 && i > 0 && i < text.length(); guard++) {
            char before = text.charAt(i - 1);
            int next = text.codePointAt(i);
            boolean insidePair = Character.isHighSurrogate(before) && Character.isLowSurrogate(text.charAt(i));
            boolean joinsPrevious = next == 0x200D || next == 0xFE0F || next == 0xFE0E || next == 0x20E3
                    || (next >= 0x1F3FB && next <= 0x1F3FF) || (next >= 0xE0020 && next <= 0xE007F);
            boolean afterJoiner = before == 0x200D;
            boolean insideFlag = !insidePair && isRegionalIndicator(text.codePointBefore(i)) && isRegionalIndicator(next)
                    && regionalIndicatorsBefore(text, i) % 2 == 1;
            if (!insidePair && !joinsPrevious && !afterJoiner && !insideFlag)
                break;
            i += insidePair ? 1 : Character.charCount(next);
        }
        return Math.min(i, text.length());
    }

    private static boolean isRegionalIndicator(int codepoint) {
        return codepoint >= 0x1F1E6 && codepoint <= 0x1F1FF;
    }

    private static int regionalIndicatorsBefore(String text, int index) {
        int count = 0;
        int i = index;
        while (i > 0 && isRegionalIndicator(text.codePointBefore(i))) {
            count++;
            i -= Character.charCount(text.codePointBefore(i));
        }
        return count;
    }

    private ImageButton createSendMessageButton() {
    /*$ res_loc {*/ResourceLocation/*$}*/ sendButtonImage = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message.png");
    /*$ res_loc {*/ResourceLocation/*$}*/ sendButtonHoverImage = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message-hover.png");

    //? if legacyforge {
    /*ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + 158, 14, 14, 0, 0, 14, sendButtonImage,
        e -> sendCurrentMessage()) {
            private final WidgetSprites sprites = new WidgetSprites(sendButtonImage, sendButtonHoverImage);

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
        };
    *///? } else {
    ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + 158, 14, 14,
        new WidgetSprites(sendButtonImage, sendButtonHoverImage),
        e -> sendCurrentMessage()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                // Z=300, not the MCreator-default 500 this used to carry: 500 sat above the vanilla
                // tooltip's own Z (400), so the button won the depth test and hid its own tooltip box
                // wherever they overlapped, regardless of draw order. 300 stays below the tooltip but
                // above message-feed content (bubbles, head icons rendered via GuiGraphics#renderItem,
                // which sit around Z 100-200), so it's still never covered when scrolled to the bottom -
                // see the comment above button_envoyer's usage for why this is re-rendered a second time.
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                // Z=300, not the MCreator-default 500 this used to carry: 500 sat above the vanilla
                // tooltip's own Z (400), so the button won the depth test and hid its own tooltip box
                // wherever they overlapped, regardless of draw order. 300 stays below the tooltip but
                // above message-feed content (bubbles, head icons rendered via GuiGraphics#renderItem,
                // which sit around Z 100-200), so it's still never covered when scrolled to the bottom -
                // see the comment above button_envoyer's usage for why this is re-rendered a second time.
                GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            //?}
        };
    //?}
    button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_send_message")));
    return button;
    }

    /**
     * Sends the currently-typed message: dispatches it to the server (which persists it via
     * ConversationSavedData and notifies the other participant) and, without waiting for any response,
     * appends it to this screen's own message list immediately. This deliberately avoids the old
     * "reopen the whole menu to show your own sent message" approach - see the comment in
     * CrazyPhoneConversationButtonMessage.handleButtonAction for why that caused the cursor to jump to
     * the center of the screen on every send.
     */
    private void sendCurrentMessage() {
        if (message.getValue().isEmpty())
            return;
        emojiDrawer.close();

        // :shortcode: -> emoji happens here, on the EditBox's own value, before anything else reads it -
        // both the optimistic local echo below and getEditBoxAndCheckBoxValues() (which re-reads this same
        // field for the actual network payload) need to agree on the same already-converted text, or the
        // sender's own local echo would show the emoji while what's actually saved/broadcast to the other
        // participant still shows the raw shortcode.
        message.setValue(fr.lordfinn.crazyphone.client.EmojiShortcodes.replace(message.getValue()));
        String text = message.getValue();
        NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
        CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());

        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);
        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        MessageData optimistic = new MessageData(timestampInMinutes, text, ownerNumber);
        receivedMessages.add(optimistic);
        // Not addRenderableWidget'd - see the comment in onConversationPageReceived for why that caused
        // the message feed to render unclipped, overflowing the crop area.
        messageManager.addMessage(optimistic);

        message.setValue("");
    }

    /** Optimistically appends a just-taken photo to THIS conversation's own feed the instant it's captured,
     * same "don't wait for any response" reasoning as {@link #sendCurrentMessage()}/{@link #onPauseSendClicked()}
     * - called by CrazyPhoneCaptureMode#triggerCapture, which already seeded FabricPictureCache with this
     * exact photoId's bytes right before calling this, so the bubble renders immediately with no loading
     * placeholder needed. A no-op if this screen isn't currently open for the exact conversation the photo
     * was sent to (player navigated away mid-capture, or this was a standalone/gallery-bound shot with no
     * conversation at all). Not addRenderableWidget'd, same reason as sendCurrentMessage's own optimistic
     * entry - see onConversationPageReceived's own comment on why that overflows the crop area. */
    public static void onLocalPhotoSent(String conversationId, UUID photoId) {
        if (!(Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhoneConversationScreen sc)
                || !sc.menu.getConversationId().equals(conversationId))
            return;
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(sc.menu.entity, null);
        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        MessageData optimistic = MessageData.image(timestampInMinutes, ownerNumber, photoId);
        sc.receivedMessages.add(optimistic);
        sc.messageManager.addMessage(optimistic);
    }

    private ImageButton createImageButton() {
        //? if legacyforge {
        /*ResourceLocation imgNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-gallery.png");
        ResourceLocation imgHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-gallery-hover.png");
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + TAKE_AND_SEND_IMAGE_ICON_Y, 14, 15, 0, 0, 15, imgNormal,
                e -> fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter(this.menu.getConversationId())) {
            private final WidgetSprites sprites = new WidgetSprites(imgNormal, imgHover);

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
        };
        *///? } else {
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + TAKE_AND_SEND_IMAGE_ICON_Y, 14, 15,
                new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-gallery.png"),
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-gallery-hover.png")),
                // "Take and send image" - opens the full-screen capture overlay (zoom, then click to shoot);
                // the shot lands in this conversation AND the phone's own My Photos list, same as any other
                // capture. Purely client-side (no server round trip needed to start framing a shot).
                e -> fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter(this.menu.getConversationId())) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                // No separate disabled sprite was provided to WidgetSprites (2-arg ctor), so sprites.get()
                // alone wouldn't visually dim this when inactive - unlike vanilla Button, which tints on its
                // own. Fading the alpha here is what actually makes the IMAGES-disabled state visible.
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                // No separate disabled sprite was provided to WidgetSprites (2-arg ctor), so sprites.get()
                // alone wouldn't visually dim this when inactive - unlike vanilla Button, which tints on its
                // own. Fading the alpha here is what actually makes the IMAGES-disabled state visible.
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            //?}
        };
        //?}
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_take_and_send_image")));
        button.visible = false;
        return button;
    }

    /** "Send image" - picks one or more existing photos from the My Photos gallery to send, closest to the
     * send button in the hover column (see updateButtonVisibility) since it's reached for most often. */
    private ImageButton createGallerySendButton() {
        //? if legacyforge {
        /*ResourceLocation gallNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-add-image.png");
        ResourceLocation gallHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-add-hover.png");
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + SEND_GALLERY_IMAGE_ICON_Y, 14, 15, 0, 0, 15, gallNormal,
                e -> onAttachExistingPhotoClicked()) {
            private final WidgetSprites sprites = new WidgetSprites(gallNormal, gallHover);

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
        };
        *///? } else {
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + SEND_GALLERY_IMAGE_ICON_Y, 14, 15,
                new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-add-image.png"),
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-add-hover.png")),
                e -> onAttachExistingPhotoClicked()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            //?}
        };
        //?}
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_send_image")));
        button.visible = false;
        return button;
    }

    /** Topmost of the three hover-reveal icons - shown/hidden together with the others (see
     * updateButtonVisibility). Only ever constructed when SVC is available. */
    private ImageButton createVoiceMessageButton() {
        //? if legacyforge {
        /*ResourceLocation voiceNormal = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-voice.png");
        ResourceLocation voiceHover = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-voice-hover.png");
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + SEND_VOICE_ICON_Y, 14, 15, 0, 0, 15, voiceNormal,
                e -> onMicIconClicked()) {
            private final WidgetSprites sprites = new WidgetSprites(voiceNormal, voiceHover);

            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
        };
        *///? } else {
        ImageButton button = new ImageButton(this.leftPos + 100, this.topPos + SEND_VOICE_ICON_Y, 14, 15,
                new WidgetSprites(Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-voice.png"),
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-voice-hover.png")),
                e -> onMicIconClicked()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                if (!isActive())
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height, 0.35f);
                else
                    GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 300, width, height);
            }
            //?}
        };
        //?}
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_send_voice_message")));
        button.visible = false;
        return button;
    }

    //? if <1.21.10 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseClickedImpl(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (mouseClickedImpl(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }
    *///?}

    /** Version-gated Button#onPress call, shared by every manually-dispatched click in mouseClickedImpl
     * (button_envoyer, and the voice-recording row's trash/pause/send, none of which vanilla's own click
     * routing reaches while this screen intercepts mouseClicked). */
    private static void pressButton(Button btn, double mouseX, double mouseY, int button) {
        //? if <1.21.10 {
        btn.onPress();
        //?}
        //? if >=1.21.10 {
        /*btn.onPress(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY,
                new net.minecraft.client.input.MouseButtonInfo(button, 0)));
        *///?}
    }

    private boolean mouseClickedImpl(double mouseX, double mouseY, int button) {
        // While recording/reviewing a voice message, the trash and pause/send controls are the only
        // interaction available - everything else (including the message feed behind the waveform row) is
        // deliberately unreachable until it's sent or deleted. This method's own wrapper never calls
        // super.mouseClicked() when this returns true, so returning true unconditionally here (as an
        // earlier version of this method did) silently ate the trash/pause/send buttons' own clicks too,
        // not just the ones meant to be swallowed - dispatch to whichever of them is actually visible and
        // hovered explicitly, then swallow everything else.
        if (voiceRecordingState != VoiceRecordingState.NONE) {
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button_voicetrash.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicetrash, mouseX, mouseY, button);
                return true;
            }
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button_voicepause.visible && button_voicepause.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicepause, mouseX, mouseY, button);
                return true;
            }
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button_voicesend.visible && button_voicesend.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicesend, mouseX, mouseY, button);
                return true;
            }
            return true;
        }

        BarIcon barHit = barIconAt(mouseX, mouseY);
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && barHit != null) {
            if (barHit.enabled()) {
                fr.lordfinn.crazyphone.client.gui.components.EmojiDrawer.playClick();
                onBarIcon(barHit.id());
            }
            return true;
        }
        if (plusMenuOpen && !button_envoyer.isMouseOver(mouseX, mouseY))
            plusMenuOpen = false;
        boolean onEmojiButton = barHit != null && barHit.id() == BAR_SMILEY;
        if (emojiDrawer.isOpen()) {
            if (emojiDrawer.mouseClicked(mouseX, mouseY, button))
                return true;
            // Clicking anywhere else closes it; the click itself still does what it normally does. The
            // message field (to move the cursor before picking) and the emoji button (its own toggle) are
            // the drawer's companions and keep it open.
            boolean onMessageField = mouseX >= message.getX() && mouseX < message.getX() + message.getRequestedWidth()
                    && mouseY >= message.getY() && mouseY < this.topPos + 172;
            if (!onEmojiButton && !onMessageField)
                emojiDrawer.close();
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && button_envoyer.isMouseOver(mouseX, mouseY)) {
            pressButton(button_envoyer, mouseX, mouseY, button);
            return true;
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
            NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(2, x, y, z, textstate));
            CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 2, x, y, z, textstate);
            return true;
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            onCallIconClicked();
            return true;
        }

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && isHoveringMuteIcon(mouseX, mouseY)) {
            onMuteIconClicked();
            return true;
        }

        if (!imagebutton_crazyphoneaddimage.visible && isWithinMessageCropZone((int) mouseX, (int) mouseY))
            for (MessageEntry entry : messageManager.getMessages()) {
                if (entry.widget().mouseClickedCompat(mouseX, mouseY, button)) {
                    return true;
                }
            }
        return false;
    }

    //? if <1.21.10 {
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (keyPressedImpl(key, (modifiers & GLFW_MOD_SHIFT) != 0)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (keyPressedImpl(event.key(), (event.modifiers() & GLFW_MOD_SHIFT) != 0)) return true;
        return super.keyPressed(event);
    }
    *///?}

    private boolean keyPressedImpl(int key, boolean shift) {
        // Open drawer first: Escape closes it (not the phone), arrows/Enter/Tab browse it.
        if (emojiDrawer.keyPressed(key, shift))
            return true;
        if (message.isFocused() && (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER)) {
            // Shift+Enter: a new line in the message; Enter alone sends it.
            if (shift)
                message.insertNewline();
            else
                sendCurrentMessage();
            return true;
        }
        if (message.isFocused() && (key == com.mojang.blaze3d.platform.InputConstants.KEY_UP || key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN))
            return message.moveVertically(key == com.mojang.blaze3d.platform.InputConstants.KEY_UP ? -1 : 1, shift);
        return false;
    }

    private void addRenderableWidgets() {
        this.addRenderableWidget(message);
        // Not addRenderableWidget: these two are rendered manually after the message feed in render() so
        // they always paint on top of it (see the comment there) - addWidget still registers them for
        // click handling, keyboard navigation and tooltips without adding a second automatic render pass.
        this.addWidget(imagebutton_crazyphoneaddimage);
        this.addWidget(imagebutton_attachphoto);
        this.addWidget(button_envoyer);
        if (imagebutton_emoji != null)
            this.addWidget(imagebutton_emoji);
        if (imagebutton_crazyphonevoicemessage != null)
            this.addWidget(imagebutton_crazyphonevoicemessage);
        if (button_voicetrash != null) {
            this.addWidget(button_voicetrash);
            this.addWidget(button_voicepause);
            this.addWidget(button_voicesend);
        }
    }

    /**
     * Called directly by {@link fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket} when
     * this screen is the currently open screen and a new message arrives for this conversation - same
     * contract as the old code's addMessage method.
     */
    public void addMessage(String senderName, MessageData newMessageData) {
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);

        // Skip if the message is from the owner (system events have no real sender, never skipped here)
        if (!newMessageData.isSystem() && newMessageData.getSender().equals(ownerNumber)) {
            return;
        }

        // Check if the sender is in the contacts (system events aren't "sent" by a contact at all)
        boolean isKnownContact = newMessageData.isSystem() || this.menu.getContacts().stream()
            .anyMatch(contact -> contact.getNumber().equals(newMessageData.getSender()));

        if (!isKnownContact) {
            return;
        }

        // Keep the buffer in sync so a later resize doesn't lose this live message.
        receivedMessages.add(newMessageData);

        // Deliberately NOT addRenderableWidget(entry.widget()) - same bug, same fix as
        // onConversationPageReceived's own loop above: messageManager.render(guiGraphics) already draws
        // every message (including this new one) inside renderMessageWidget()'s scissor block, and clicks
        // are dispatched manually too (see mouseClicked() below). Registering it a second time here made
        // the standard Screen render pass draw it AGAIN, unclipped - live-reported as a message popping up
        // in the empty space below the feed while scrolled up, since its own layout Y (below every
        // currently-visible message) was never scissored to the feed's actual bounds.
        messageManager.addMessage(newMessageData);
    }

    /** Called directly by {@link fr.lordfinn.crazyphone.network.CrazyPhoneNewCallDurationNotificationPacket}
     * when this screen is open for the conversation whose call just got its final duration - without this,
     * a widget that was never THIS client's own live call (a bystander watching someone else's call in a
     * group conversation) has no other way to learn it ended and would keep ticking an estimate forever.
     */
    public void updateCallDuration(String conversationId, UUID callId, long durationMillis) {
        if (!conversationId.equals(this.menu.getConversationId()))
            return;
        messageManager.updateCallDuration(callId, durationMillis);
    }
}
