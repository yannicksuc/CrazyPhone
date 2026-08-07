package fr.lordfinn.crazyphone.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;

import fr.lordfinn.crazyphone.client.ConversationClientCache;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.client.ConversationClientCache.ConversationPage;
import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.client.gui.components.MessageDisplayManager;
import fr.lordfinn.crazyphone.client.gui.components.MessageDisplayManager.MessageEntry;
import fr.lordfinn.crazyphone.client.gui.components.SmallTextEditBox;
import fr.lordfinn.crazyphone.network.ConversationRequestPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneConversationButtonMessage;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
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
    /** Top-right corner of the header banner, same position/size convention as the contacts screen's
     * add-contact icon - only shown for group conversations. */
    private static final int GROUP_SETTINGS_ICON_X = 100;
    private static final int GROUP_SETTINGS_ICON_Y = 9;
    private final ItemStack groupSettingsIcon = CrazyPhoneConversationMenu.createGroupSettingsIcon();
    private final Component groupSettingsTooltip = Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_group_settings")
            .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    private EditBox message;
    private Button button_envoyer;
    private ImageButton imagebutton_crazyphoneaddimage;
    private int scrollPosition = 0;
    private static final int SCROLL_STEP = 10;
    private MessageDisplayManager messageManager;

    /** Messages received so far for this conversation (oldest first), replayed into {@link #messageManager} on every init() (e.g. after a resize) so they aren't lost. */
    private final List<MessageData> receivedMessages = new ArrayList<>();
    /** Stored so the exact same reference can be passed to both setListener and clearListener. */
    private final BiConsumer<String, ConversationPage> conversationListener = this::onConversationPageReceived;
    private boolean firstPageRequested = false;

    public CrazyPhoneConversationScreen(CrazyPhoneConversationMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        HashMap<String, String> textstate = new HashMap<>();
        if (Minecraft.getInstance().screen instanceof CrazyPhoneConversationScreen sc) {
            textstate.put("textin:message", sc.message.getValue());
            textstate.put("conversationId", sc.menu.getConversationId());
        }
        return textstate;
    }

    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        updateButtonVisibility(mouseX, mouseY);
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderBanner(guiGraphics);
        if (menu.isGroup())
            renderGroupSettingsIcon(guiGraphics, mouseX, mouseY);
        renderMessageWidget(guiGraphics, mouseX, mouseY, partialTicks);
        // Tooltip only, deferred until after the message feed: the feed's own (opaque) content renders
        // right after the icon in normal flow and, since the tooltip pops up below the cursor, overlapped
        // and painted over it - the box's thin border could survive at the edges while the text underneath
        // got covered, which is exactly the "wide box, no text" look this was producing.
        if (menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, List.of(groupSettingsTooltip), mouseX, mouseY);
        }
        // Drawn again here, after the message feed: button_envoyer/imagebutton_crazyphoneaddimage sit at
        // the bottom-right corner of the message crop zone (y 144-173, crop ends at 158) as a deliberate
        // floating overlay, but the standard renderable pass (inside super.render() above) draws them
        // BEFORE the message feed - so a right-aligned message bubble reaching that corner painted over
        // them, most visibly hiding the add-image icon right as hovering the send button revealed it.
        // They're registered via addWidget (not addRenderableWidget) so this is their only render call.
        button_envoyer.render(guiGraphics, mouseX, mouseY, partialTicks);
        imagebutton_crazyphoneaddimage.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHoveredHeadTooltip(guiGraphics, mouseX, mouseY);
        renderHoveredTimestampTooltip(guiGraphics, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        // CursorEffects.endFrame() is NOT called here - PhoneClickableCursorHandler does it once, in a
        // ScreenEvent.Render.Post listener that fires after this whole method returns, so it can also
        // pick up the standard-button hover requests it makes itself without a premature reset.
    }

    /**
     * The message feed is scissor-cropped to this rect (see renderMessageWidget) - hover tooltips must
     * only trigger when the cursor is actually within it, not just within a widget's raw (possibly
     * scrolled-off-screen) bounds. Also excludes the send/add-image buttons, which sit right at the
     * bottom-right corner of the crop zone and have their own tooltips (set via setTooltip, rendered by
     * the normal this.renderTooltip() call) - without this, a message/head happening to render behind
     * one of those buttons would show its own tooltip instead of "Send an image" / "Send the message".
     */
    private boolean isWithinMessageCropZone(int mouseX, int mouseY) {
        if (button_envoyer.isMouseOver(mouseX, mouseY) || imagebutton_crazyphoneaddimage.isMouseOver(mouseX, mouseY))
            return false;
        return mouseX >= this.leftPos && mouseX < this.leftPos + 200
                && mouseY >= this.topPos + 27 && mouseY < this.topPos + 158;
    }

    private void renderHoveredHeadTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!isWithinMessageCropZone(mouseX, mouseY))
            return;
        for (MessageEntry entry : messageManager.getMessages()) {
            if (entry.widget().isHeadHovered(mouseX, mouseY)) {
                // Same "Name • number" format as the contact heads in the contacts menu.
                guiGraphics.renderComponentTooltip(this.font, List.of(
                        CrazyPhoneHelper.formatContactDisplayName(entry.widget().getContactName(), entry.widget().getContactNumber())
                ), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderHoveredTimestampTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
                guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
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

    private void renderBanner(GuiGraphics guiGraphics) {
        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);

        // Filter out self from contacts
        List<Contact> otherContacts = menu.getContacts().stream()
                .filter(contact -> !contact.getNumber().equals(ownerNumber))
                .toList();

        ItemStack contactHead = resolveHeaderIcon(otherContacts);
        String names = otherContacts.stream().map(Contact::getName).collect(java.util.stream.Collectors.joining(", "));
        String title = (menu.isGroup() && !menu.getGroupName().isEmpty()) ? menu.getGroupName() : names;
        renderHeader(guiGraphics, contactHead, Component.literal(title));
    }

    private ItemStack resolveHeaderIcon(List<Contact> otherContacts) {
        if (menu.isGroup() && !menu.getGroupIcon().isEmpty())
            return menu.getGroupIcon();
        return otherContacts.isEmpty() ? ItemStack.EMPTY : CrazyPhoneHelper.createContactHead(otherContacts.get(0));
    }

    private void renderGroupSettingsIcon(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int iconX = this.leftPos + GROUP_SETTINGS_ICON_X;
        int iconY = this.topPos + GROUP_SETTINGS_ICON_Y;
        boolean hovered = isHoveringGroupSettingsIcon(mouseX, mouseY);
        if (hovered) {
            CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
        }
        guiGraphics.renderItem(groupSettingsIcon, iconX, iconY);
    }

    private boolean isHoveringGroupSettingsIcon(double mouseX, double mouseY) {
        int iconX = this.leftPos + GROUP_SETTINGS_ICON_X;
        int iconY = this.topPos + GROUP_SETTINGS_ICON_Y;
        return mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16;
    }

    private void updateButtonVisibility(int mouseX, int mouseY) {
        boolean isButtonEnvoyerHovered = button_envoyer.isMouseOver(mouseX, mouseY);
        boolean isImageButtonHovered = imagebutton_crazyphoneaddimage.isMouseOver(mouseX, mouseY);
        imagebutton_crazyphoneaddimage.visible = isButtonEnvoyerHovered || isImageButtonHovered;
        if (imagebutton_crazyphoneaddimage.visible && !isButtonEnvoyerHovered && !isImageButtonHovered) {
            imagebutton_crazyphoneaddimage.visible = false;
        } else if (!imagebutton_crazyphoneaddimage.visible && isButtonEnvoyerHovered) {
            imagebutton_crazyphoneaddimage.visible = true;
        }
    }

    private void renderMessageWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
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
        return true;
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
        messageManager = new MessageDisplayManager(this.leftPos + 7, this.topPos + 158, 93, 108, 0.75f,
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
        PacketDistributor.sendToServer(new ConversationRequestPacket(this.menu.getConversationId(), 0));
    }

    private void onConversationPageReceived(String conversationId, ConversationPage page) {
        if (!conversationId.equals(this.menu.getConversationId()))
            return;
        for (CompoundTag messageTag : page.messages()) {
            MessageData data = CrazyPhoneHelper.getMessageFromTag(messageTag);
            if (data == null)
                continue;
            receivedMessages.add(data);
            if (messageManager != null) {
                // Deliberately NOT calling addRenderableWidget here: message widgets are rendered by
                // messageManager.render(guiGraphics) inside the scissor block in renderMessageWidget(), and
                // clicks are dispatched manually in mouseClicked() below. Registering them as renderable
                // widgets too made the standard Screen render pass draw them a second time, unclipped -
                // that's what caused messages to overflow above/below the visible feed area regardless of
                // scroll position (the scroll math itself was fine).
                messageManager.addMessage(data);
            }
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        ConversationClientCache.clearListener(conversationListener);
    }

    private void initializeEditBox() {
        message = new SmallTextEditBox(this.font, this.leftPos + 7, this.topPos + 159, 93, 14,
                Component.translatable("gui.crazyphone.crazy_phone_conversation.message")) {
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
                if (getValue().isEmpty())
                    setSuggestion(
                            Component.translatable("gui.crazyphone.crazy_phone_conversation.message").getString());
                else
                    setSuggestion(null);
            }
        };
        message.setMaxLength(32767);
        message.setSuggestion(Component.translatable("gui.crazyphone.crazy_phone_conversation.message").getString());
        guistate.put("text:message", message);
        this.addWidget(this.message);
    }

    private void initializeButtons() {
        button_envoyer = createSendMessageButton();
        imagebutton_crazyphoneaddimage = createImageButton();
    }

    private ImageButton createSendMessageButton() {
    ResourceLocation sendButtonImage = ResourceLocation.parse("crazyphone:textures/screens/crazyphone-send-message.png");
    ResourceLocation sendButtonHoverImage = ResourceLocation.parse("crazyphone:textures/screens/crazyphone-send-message-hover.png");

    ImageButton button = new ImageButton(this.leftPos + 101, this.topPos + 159, 14, 14,
        new WidgetSprites(sendButtonImage, sendButtonHoverImage),
        e -> sendCurrentMessage()) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 500, 0, 0, width, height, width, height);
            }
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

        String text = message.getValue();
        PacketDistributor.sendToServer(new CrazyPhoneConversationButtonMessage(0, x, y, z, getEditBoxAndCheckBoxValues()));
        CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 0, x, y, z, getEditBoxAndCheckBoxValues());

        String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.menu.entity, null);
        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        MessageData optimistic = new MessageData(timestampInMinutes, text, ownerNumber, ItemStack.EMPTY);
        receivedMessages.add(optimistic);
        // Not addRenderableWidget'd - see the comment in onConversationPageReceived for why that caused
        // the message feed to render unclipped, overflowing the crop area.
        messageManager.addMessage(optimistic);

        message.setValue("");
    }

    private ImageButton createImageButton() {
        ImageButton button = new ImageButton(this.leftPos + 101, this.topPos + 144, 14, 15,
                new WidgetSprites(ResourceLocation.parse("crazyphone:textures/screens/crazyphone-add-image.png"),
                        ResourceLocation.parse("crazyphone:textures/screens/crazyphone-add-hover.png")),
                e -> {
                    PacketDistributor.sendToServer(new CrazyPhoneConversationButtonMessage(1, x, y, z, getEditBoxAndCheckBoxValues()));
                    CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 1, x, y, z, getEditBoxAndCheckBoxValues());
                }) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int x, int y, float partialTicks) {
                guiGraphics.blit(sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 500, 0, 0, width,
                        height, width, height);
            }
        };
        button.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_send_image")));
        button.visible = false;
        return button;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && button_envoyer.isMouseOver(mouseX, mouseY)) {
            button_envoyer.onPress();
            return true;
        }

        if (button == 0 && menu.isGroup() && isHoveringGroupSettingsIcon(mouseX, mouseY)) {
            HashMap<String, String> textstate = getEditBoxAndCheckBoxValues();
            PacketDistributor.sendToServer(new CrazyPhoneConversationButtonMessage(2, x, y, z, textstate));
            CrazyPhoneConversationButtonMessage.handleButtonAction(entity, 2, x, y, z, textstate);
            return true;
        }

        if (!imagebutton_crazyphoneaddimage.visible && isWithinMessageCropZone((int) mouseX, (int) mouseY))
            for (MessageEntry entry : messageManager.getMessages()) {
                if (entry.widget().mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (message.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            sendCurrentMessage();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void addRenderableWidgets() {
        this.addRenderableWidget(message);
        // Not addRenderableWidget: these two are rendered manually after the message feed in render() so
        // they always paint on top of it (see the comment there) - addWidget still registers them for
        // click handling, keyboard navigation and tooltips without adding a second automatic render pass.
        this.addWidget(imagebutton_crazyphoneaddimage);
        this.addWidget(button_envoyer);
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
}
