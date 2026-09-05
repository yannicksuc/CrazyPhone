package fr.lordfinn.crazyphone.client.gui;

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
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import org.lwjgl.glfw.GLFW;

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
    private static final int GROUP_SETTINGS_ICON_X = 83;
    private static final int GROUP_SETTINGS_ICON_Y = 9;
    private final ItemStack groupSettingsIcon = CrazyPhoneConversationMenu.createGroupSettingsIcon();
    private final Component groupSettingsTooltip = Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_group_settings")
            .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    /** Sits immediately left of the group-settings icon when both are shown (group conversations, flush
     * against it - no gap between the two buttons), or immediately left of the mute icon when there's no
     * group icon to share a slot with (1:1 conversations). */
    private static final int CALL_ICON_Y = 9;
    private int callIconX() {
        return this.leftPos + (menu.isGroup() ? GROUP_SETTINGS_ICON_X - 16 : MUTE_ICON_X - 16);
    }
    /** Rightmost slot of the header banner's icon row - unlike the group-settings/call icons above, this
     * one is ALWAYS shown regardless of conversation type, since muting applies to any conversation. */
    private static final int MUTE_ICON_X = 99;
    private static final int MUTE_ICON_Y = 9;
    private EditBox message;
    private Button button_envoyer;
    /** "Take and send image" - opens the capture overlay, then uploads and posts the shot into this
     * conversation directly (also lands in the phone's own My Photos list, same as any other capture). */
    private ImageButton imagebutton_crazyphoneaddimage;
    /** "Send image" - browses the My Photos gallery in send mode instead of taking a fresh shot, one or
     * several at once. Same hover-reveal column as imagebutton_crazyphoneaddimage/imagebutton_crazyphonevoicemessage
     * (see updateButtonVisibility) - closest to the send button, matching how often it's reached for. */
    private ImageButton imagebutton_attachphoto;
    private static final int SEND_GALLERY_IMAGE_ICON_Y = 143;
    private static final int TAKE_AND_SEND_IMAGE_ICON_Y = 128;

    /** Voice message recording, replaces the text input row while active. NONE = normal text input;
     * RECORDING = mic capture in progress, [trash][waveform][pause] shown; REVIEWING = paused, waiting for
     * send or delete - no other interaction is possible in that state. */
    private enum VoiceRecordingState { NONE, RECORDING, REVIEWING }
    private VoiceRecordingState voiceRecordingState = VoiceRecordingState.NONE;
    private byte[] recordedAudio = new byte[0];
    /** Topmost of the three hover-reveal icons (furthest from the send button) - only constructed/shown
     * when SVC is available (see VoicechatIntegration), guarded with a null check everywhere it's touched. */
    private ImageButton imagebutton_crazyphonevoicemessage;
    private static final int SEND_VOICE_ICON_Y = 113;
    private static final int RECORDING_ROW_Y = 158;
    private static final int TRASH_X = 8;
    private static final int WAVEFORM_X = 23;
    private static final int WAVEFORM_WIDTH = 76;
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
            guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(groupSettingsTooltip), mouseX, mouseY);
        }
        if (VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(callIconTooltip()), mouseX, mouseY);
        }
        if (isHoveringMuteIcon(mouseX, mouseY)) {
            guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(muteIconTooltip()), mouseX, mouseY);
        }
        // Drawn again here, after the message feed: button_envoyer/imagebutton_crazyphoneaddimage sit at
        // the bottom-right corner of the message crop zone (y 144-173, crop ends at 158) as a deliberate
        // floating overlay, but the standard renderable pass (inside super.render() above) draws them
        // BEFORE the message feed - so a right-aligned message bubble reaching that corner painted over
        // them, most visibly hiding the add-image icon right as hovering the send button revealed it.
        // They're registered via addWidget (not addRenderableWidget) so this is their only render call.
        if (voiceRecordingState == VoiceRecordingState.NONE) {
            button_envoyer.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_crazyphoneaddimage.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_attachphoto.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
            if (imagebutton_crazyphonevoicemessage != null)
                imagebutton_crazyphonevoicemessage.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            renderVoiceRecordingRow(guiGraphics, mouseX, mouseY, partialTicks);
        }
        renderHoveredHeadTooltip(guiGraphics, mouseX, mouseY);
        renderHoveredTimestampTooltip(guiGraphics, mouseX, mouseY);
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
            guiGraphics.renderComponentTooltip(this.font, List.of(groupSettingsTooltip), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(groupSettingsTooltip), mouseX, mouseY);
            *///?}
        }
        if (VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, List.of(callIconTooltip()), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(callIconTooltip()), mouseX, mouseY);
            *///?}
        }
        if (isHoveringMuteIcon(mouseX, mouseY)) {
            //? if <1.21.10 {
            guiGraphics.renderComponentTooltip(this.font, List.of(muteIconTooltip()), mouseX, mouseY);
            //? } else {
            /*guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(muteIconTooltip()), mouseX, mouseY);
            *///?}
        }
        // Drawn again here, after the message feed: button_envoyer/imagebutton_crazyphoneaddimage sit at
        // the bottom-right corner of the message crop zone (y 144-173, crop ends at 158) as a deliberate
        // floating overlay, but the standard renderable pass (inside super.render() above) draws them
        // BEFORE the message feed - so a right-aligned message bubble reaching that corner painted over
        // them, most visibly hiding the add-image icon right as hovering the send button revealed it.
        // They're registered via addWidget (not addRenderableWidget) so this is their only render call.
        if (voiceRecordingState == VoiceRecordingState.NONE) {
            button_envoyer.render(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_crazyphoneaddimage.render(guiGraphics, mouseX, mouseY, partialTicks);
            imagebutton_attachphoto.render(guiGraphics, mouseX, mouseY, partialTicks);
            if (imagebutton_crazyphonevoicemessage != null)
                imagebutton_crazyphonevoicemessage.render(guiGraphics, mouseX, mouseY, partialTicks);
        } else {
            renderVoiceRecordingRow(guiGraphics, mouseX, mouseY, partialTicks);
        }
        renderHoveredHeadTooltip(guiGraphics, mouseX, mouseY);
        renderHoveredTimestampTooltip(guiGraphics, mouseX, mouseY);
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
        if (button_envoyer.isMouseOver(mouseX, mouseY) || imagebutton_crazyphoneaddimage.isMouseOver(mouseX, mouseY)
                || imagebutton_attachphoto.isMouseOver(mouseX, mouseY)
                || (imagebutton_crazyphonevoicemessage != null && imagebutton_crazyphonevoicemessage.isMouseOver(mouseX, mouseY)))
            return false;
        return mouseX >= this.leftPos && mouseX < this.leftPos + 200
                && mouseY >= this.topPos + 27 && mouseY < this.topPos + 158;
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
                guiGraphics.renderComponentTooltip(this.font, headTooltip, mouseX, mouseY);
                //? } else {
                /*guiGraphics.setComponentTooltipForNextFrame(this.font, headTooltip, mouseX, mouseY);
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
                guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                //? } else {
                /*guiGraphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
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
        renderHeader(guiGraphics, contactHead, Component.literal(title), callIconX() - this.leftPos);
    }

    private ItemStack resolveHeaderIcon(List<Contact> otherContacts) {
        if (menu.isGroup() && !menu.getGroupIcon().isEmpty())
            return menu.getGroupIcon();
        return otherContacts.isEmpty() ? ItemStack.EMPTY : CrazyPhoneHelper.createContactHead(otherContacts.get(0));
    }

    private void renderGroupSettingsIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        int iconX = this.leftPos + GROUP_SETTINGS_ICON_X;
        int iconY = this.topPos + GROUP_SETTINGS_ICON_Y;
        boolean hovered = isHoveringGroupSettingsIcon(mouseX, mouseY);
        if (hovered) {
            CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
        }
        guiGraphics./*$ gui_render_item {*/renderItem/*$}*/(groupSettingsIcon, iconX, iconY);
    }

    private boolean isHoveringGroupSettingsIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + GROUP_SETTINGS_ICON_X;
        int iconY = this.topPos + GROUP_SETTINGS_ICON_Y;
        return mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
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
        int iconX = callIconX();
        int iconY = this.topPos + CALL_ICON_Y;
        boolean hovered = isHoveringCallIcon(mouseX, mouseY);
        if (hovered && !isCallStartDisabled()) {
            CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
        }
        int color;
        if (isCallStartDisabled())
            color = 0x80FFFFFF;
        else if (hasMyActiveCallHere())
            color = 0xFF44FF66;
        else if (ClientCallState.hasJoinableCallElsewhere(menu.getConversationId()))
            color = CrazyPhoneColors.ACCENT_YELLOW; // same "you could join/rejoin this" color as the contacts-list badge
        else
            color = 0xFFFFFFFF;
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, "📞", iconX + 3, iconY + 4, color, true);
    }

    private boolean isHoveringCallIcon(double mouseX, double mouseY) {
        int iconX = callIconX();
        int iconY = this.topPos + CALL_ICON_Y;
        return mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
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
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(action, menu.getConversationId()));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new CrazyPhoneCallActionMessage(action, menu.getConversationId()));
        //?}
    }

    /** Always shown (unlike the group-settings/call icons above), regardless of conversation type - mutes
     * just THIS player's own sound/toast for THIS specific conversation (see
     * CrazyPhoneHelper#toggleMutedConversation); the unread-notification badge is a separate mechanism and
     * keeps appearing normally either way. Bell/no-bell glyph mirrors CrazyPhonePhotoFrameResizeScreen's
     * Fullbright hotbar button - same "icon + tooltip both re-read current state every frame" technique. */
    private void renderMuteIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        int iconX = this.leftPos + MUTE_ICON_X;
        int iconY = this.topPos + MUTE_ICON_Y;
        boolean hovered = isHoveringMuteIcon(mouseX, mouseY);
        if (hovered) {
            CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
        }
        String glyph = isConversationMuted() ? "🔕" : "🔔";
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, glyph, iconX + 3, iconY + 4, 0xFFFFFFFF, true);
    }

    private boolean isHoveringMuteIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + MUTE_ICON_X;
        int iconY = this.topPos + MUTE_ICON_Y;
        return mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
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
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new CrazyPhoneMuteConversationMessage(menu.getConversationId()));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new CrazyPhoneMuteConversationMessage(menu.getConversationId()));
        //?}
    }

    private void onMicIconClicked() {
        if (!ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES))
            return;
        voiceRecordingState = VoiceRecordingState.RECORDING;
        VoiceMessageRecorder.startRecording();
    }

    private void onAttachExistingPhotoClicked() {
        HashMap<String, String> values = getEditBoxAndCheckBoxValues();
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(1, x, y, z, values));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new CrazyPhoneConversationButtonMessage(1, x, y, z, values));
        //?}
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
                //? if >=1.20.5 {
                /*NetworkAccess.sendToServer(new VoiceMessageUploadPacket(menu.getConversationId(), voiceId, recordedAudio, durationTicks, envelope));
                *///? } else {
                PacketDistributor.SERVER.noArg().send(new VoiceMessageUploadPacket(menu.getConversationId(), voiceId, recordedAudio, durationTicks, envelope));
                //?}

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
    private void updateButtonVisibility(int mouseX, int mouseY) {
        int columnLeft = this.leftPos + 100;
        int columnRight = columnLeft + 14;
        int columnTop = this.topPos + SEND_VOICE_ICON_Y;
        int columnBottom = button_envoyer.getY() + button_envoyer.getHeight();
        boolean hoveringColumn = mouseX >= columnLeft && mouseX < columnRight
                && mouseY >= columnTop && mouseY < columnBottom;
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

    private void renderMessageWidget(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.enableScissor(this.leftPos, this.topPos + 27, this.leftPos + 200, this.topPos + 158);
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

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scrollPosition += dy * SCROLL_STEP;

        if (scrollPosition < 0 || messageManager.getTotalHeight() <= 132)
            scrollPosition = 0;
        else if (scrollPosition > (messageManager.getTotalHeight() - 132))
            scrollPosition = (messageManager.getTotalHeight() - 132);
        messageManager.setScrollOffset(scrollPosition);
        maybeLoadOlderMessages();
        return true;
    }

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
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new ConversationRequestPacket(this.menu.getConversationId(), receivedMessages.size()));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new ConversationRequestPacket(this.menu.getConversationId(), receivedMessages.size()));
        //?}
    }

    @Override
    public void init() {
        super.init();
        initializeButtons();
        initializeTextAndMessageWidget();
        initializeEditBox();
        addRenderableWidgets();
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
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new ConversationRequestPacket(this.menu.getConversationId(), 0));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new ConversationRequestPacket(this.menu.getConversationId(), 0));
        //?}
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
        ClientMessageDraft.saveOnClose(this.menu.getConversationId(), message.getValue());
    }

    private void initializeEditBox() {
        message = new SmallTextEditBox(this.font, this.leftPos + 8, this.topPos + 158, 91, 14,
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
                        moveCursorTo(converted.length() + 1, false);
                    }
                }
                updateSuggestion();
            }

            @Override
            public void moveCursorTo(int pos, boolean flag) {
                super.moveCursorTo(pos, flag);
                updateSuggestion();
            }

            private void updateSuggestion() {
                if (getValue().isEmpty())
                    setSuggestion(
                            Component.translatable("gui.crazyphone.crazy_phone_conversation.message").getString());
                else
                    setSuggestion(null);
            }
        };
        message.setMaxLength(32767);
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
        imagebutton_crazyphoneaddimage = createImageButton();
        imagebutton_attachphoto = createGallerySendButton();
        if (VoicechatIntegration.isAvailable()) {
            imagebutton_crazyphonevoicemessage = createVoiceMessageButton();
            button_voicetrash = createSquareIconButton(this.leftPos + TRASH_X, this.topPos + RECORDING_ROW_Y,
                    Component.translatable("gui.crazyphone.crazy_phone_conversation.button_voice_trash").withStyle(ChatFormatting.RED),
                    e -> onTrashClicked());
            button_voicepause = createSquareIconButton(this.leftPos + PAUSE_SEND_X, this.topPos + RECORDING_ROW_Y,
                    Component.translatable("gui.crazyphone.crazy_phone_conversation.button_voice_pause"),
                    e -> onPauseSendClicked());
            // Same crazyphone-send-message.png button as the main send button - every "send" action in
            // this mod uses this one texture now, not the glyph-only square style trash/pause still use.
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
    private Button createSquareIconButton(int x, int y, Component icon, Button.OnPress onPress) {
        return new Button(x, y, 14, 14, icon, onPress, supplier -> icon.copy()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
                // 26.x's AbstractButton has no default extractContents body left to call via super - the
                // background sprite and default label are now two independent, separately-invokable pieces
                // (extractDefaultSprite(GuiGraphicsExtractor)/extractDefaultLabel(ActiveTextCollector), the
                // latter part of a new text-collection pipeline this button never needs to touch since it
                // always draws its own custom-positioned label instead of the default one) - only the sprite
                // half is relevant here, so call that directly instead of blanking/restoring the message
                // around a super call that no longer exists.
                Component message = getMessage();
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
                Component message = getMessage();
                setMessage(Component.empty());
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                setMessage(message);

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

    private ImageButton createSendMessageButton() {
    /*$ res_loc {*/ResourceLocation/*$}*/ sendButtonImage = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message.png");
    /*$ res_loc {*/ResourceLocation/*$}*/ sendButtonHoverImage = Crazyphone.parseId("crazyphone:textures/screens/crazyphone-send-message-hover.png");

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

        // :shortcode: -> emoji happens here, on the EditBox's own value, before anything else reads it -
        // both the optimistic local echo below and getEditBoxAndCheckBoxValues() (which re-reads this same
        // field for the actual network payload) need to agree on the same already-converted text, or the
        // sender's own local echo would show the emoji while what's actually saved/broadcast to the other
        // participant still shows the raw shortcode.
        message.setValue(fr.lordfinn.crazyphone.client.EmojiShortcodes.replace(message.getValue()));
        String text = message.getValue();
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new CrazyPhoneConversationButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
        //?}
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
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_take_and_send_image")));
        button.visible = false;
        return button;
    }

    /** "Send image" - picks one or more existing photos from the My Photos gallery to send, closest to the
     * send button in the hover column (see updateButtonVisibility) since it's reached for most often. */
    private ImageButton createGallerySendButton() {
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
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_send_image")));
        button.visible = false;
        return button;
    }

    /** Topmost of the three hover-reveal icons - shown/hidden together with the others (see
     * updateButtonVisibility). Only ever constructed when SVC is available. */
    private ImageButton createVoiceMessageButton() {
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
            if (button == 0 && button_voicetrash.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicetrash, mouseX, mouseY, button);
                return true;
            }
            if (button == 0 && button_voicepause.visible && button_voicepause.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicepause, mouseX, mouseY, button);
                return true;
            }
            if (button == 0 && button_voicesend.visible && button_voicesend.isMouseOver(mouseX, mouseY)) {
                pressButton(button_voicesend, mouseX, mouseY, button);
                return true;
            }
            return true;
        }

        if (button == 0 && button_envoyer.isMouseOver(mouseX, mouseY)) {
            pressButton(button_envoyer, mouseX, mouseY, button);
            return true;
        }

        if (button == 0 && menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyPhoneConversationButtonMessage(2, x, y, z, textstate));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyPhoneConversationButtonMessage(2, x, y, z, textstate));
            //?}
            CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 2, x, y, z, textstate);
            return true;
        }

        if (button == 0 && VoicechatIntegration.isAvailable() && isHoveringCallIcon(mouseX, mouseY)) {
            onCallIconClicked();
            return true;
        }

        if (button == 0 && isHoveringMuteIcon(mouseX, mouseY)) {
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
        if (keyPressedImpl(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (keyPressedImpl(event.key())) return true;
        return super.keyPressed(event);
    }
    *///?}

    private boolean keyPressedImpl(int key) {
        if (message.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendCurrentMessage();
            return true;
        }
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

        // Add message
        MessageEntry entry = messageManager.addMessage(newMessageData);
        this.addRenderableWidget(entry.widget());
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
