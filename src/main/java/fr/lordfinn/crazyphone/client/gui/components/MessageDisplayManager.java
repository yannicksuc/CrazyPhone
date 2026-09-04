package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.client.EmojiShortcodes;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MessageDisplayManager {
    private final List<MessageEntry> messageEntries = new ArrayList<>();
    private final int x;
    private final int y;
    private final int width;
    /** Width used for a system-event entry instead of {@link #width} - spans (nearly) the full feed area
     * since it isn't a left/right-aligned chat bubble with head-icon clearance to leave room for. */
    private final int fullWidth;
    private final float scale;
    private int scrollOffset = 0;
    private int PADDING = 5;
    private List<Contact> contacts;
    private HashMap<String, ItemStack> icons = new HashMap<>();
    private String ownerNumber;
    private int totalHeight = 0;

    public record MessageEntry(MessageData data, MessageWidget widget) {}

    public MessageDisplayManager(int x, int y, int width, float scale, List<Contact> contacts, String ownerNumber) {
        this(x, y, width, width + 15, scale, contacts, ownerNumber);
    }

    public MessageDisplayManager(int x, int y, int width, int fullWidth, float scale, List<Contact> contacts, String ownerNumber) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.fullWidth = fullWidth;
        this.scale = scale;
        this.contacts = contacts;
        this.ownerNumber = ownerNumber;

        for (Contact contact : contacts) {
            icons.put(contact.getNumber(), CrazyPhoneHelper.createContactHead(contact));
        }
    }

    public void render(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        // A call entry's own text (and therefore height) changes every frame while live - see
        // MessageWidget#computeCallText, called from its renderWidget before this loop even runs - but
        // resetPositions() was previously only invoked on scroll/add/prepend, never in response to a
        // widget's height changing on its own. That left every OTHER entry's cached Y position stale
        // relative to a call bubble that had since grown or shrunk (e.g. from the empty placeholder text
        // it's constructed with to its real "in progress"/"interrupted"/summary text on the very next
        // frame), overlapping whichever entry sits just above it. Recomputing every frame is cheap for a
        // handful of visible messages and guarantees positions never drift out of sync with actual heights.
        resetPositions();
        MessageEntry hoveredImageEntry = null;
        for (MessageEntry entry : messageEntries) {
            if (entry.widget().isImageHovered(mouseX, mouseY)) {
                // Defer to a second pass below, so its grown/shadowed image paints on top of every other
                // message instead of risking being covered by whichever neighbor renders later in normal
                // (chronological) order.
                hoveredImageEntry = entry;
                continue;
            }
            entry.widget./*$ widget_render {*/render/*$}*/(guiGraphics, mouseX, mouseY, 0);
        }
        if (hoveredImageEntry != null) {
            hoveredImageEntry.widget./*$ widget_render {*/render/*$}*/(guiGraphics, mouseX, mouseY, 0);
        }
    }

    public void resetPositions() {
    int currentY = y + scrollOffset;
    totalHeight = 0;
    String previousSender = null;
    for (int i = 0; i < messageEntries.size(); i++) {
        MessageEntry currentEntry = messageEntries.get(i);
        String currentSender = currentEntry.data().getSender();

        String nextSender = (i + 1 < messageEntries.size()) ? messageEntries.get(i + 1).data().getSender() : null;
        boolean sameAsNext = currentSender.equals(nextSender);
        boolean sameAsPrevious = currentSender.equals(previousSender);


        currentEntry.widget.setY(currentY);
        currentEntry.widget.setShowIcon(!sameAsPrevious);
        currentEntry.widget.adjustPosition();

        int paddingBelow = sameAsNext ? 0 : PADDING;
        currentY -= currentEntry.widget.getHeight() + paddingBelow;
        totalHeight += currentEntry.widget.getHeight() + paddingBelow;
        previousSender = currentSender;
    }
}

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
        resetPositions();
    }

    private static final int SYSTEM_BACKGROUND_COLOR = 0xCCFFF3B0; // light yellow
    private static final int CALL_BACKGROUND_COLOR = 0xCCB0D9FF; // light blue - distinct from the system-event yellow

    public MessageEntry addMessage(MessageData newMessage) {
        return insert(newMessage, true);
    }

    /** For "load older messages" pagination (scrolling to the top of the feed): unlike {@link
     * #addMessage}, which always becomes the newest/bottom-most entry, this becomes the new oldest/top-most
     * entry instead - the caller (CrazyPhoneConversationScreen) must feed each page's messages in
     * newest-to-oldest order (the reverse of how the server returns a page) so repeated calls build up the
     * correct chronological order above whatever was already loaded. */
    public MessageEntry prependOlderMessage(MessageData olderMessage) {
        return insert(olderMessage, false);
    }

    private MessageEntry insert(MessageData newMessage, boolean atNewestEnd) {
        MessageEntry entry;
        if (newMessage.isCall())
            entry = buildCallEntry(newMessage);
        else if (newMessage.isSystem())
            entry = buildSystemEntry(newMessage);
        else
            entry = buildTextEntry(newMessage);
        if (atNewestEnd)
            messageEntries.add(0, entry);
        else
            messageEntries.add(entry);
        resetPositions();
        return entry;
    }

    private MessageEntry buildTextEntry(MessageData newMessage) {
        boolean isSender = ownerNumber.equals(newMessage.getSender());

        // Empty text: WrappedTextWidget treats a blank message as "no background" (used for image messages,
        // which draw their own content edge-to-edge) - a voice message still wants the normal colored
        // chat-bubble background though, just with custom content (play icon/time/waveform/speed) drawn
        // over it by MessageWidget instead of wrapped text, so its background is forced on explicitly
        // regardless of the (blank) placeholder text.
        String bubbleText = newMessage.isVoice() ? "" : newMessage.getMessage();
        boolean transparentBackground = bubbleText.isBlank() && !newMessage.isVoice();
        WrappedTextWidget wrapped = new WrappedTextWidget(
            Minecraft.getInstance().font,
            x,
            0, // temp y, updated in render
            width,
            EmojiShortcodes.styleForDisplay(bubbleText),
            scale,
            (!isSender ? 0xff000000 : 0xffffffff),
            (transparentBackground ? 0x00ffffff : (!isSender ? 0xccfafafa : 0xcc0084ff))
        );
        if (newMessage.isVoice())
            wrapped.setMinHeight(14); // even - the waveform bars center on bubbleH/2 with no rounding remainder
        ItemStack icon = icons.get(newMessage.getSender());
        if (icon == null)
            icon = new ItemStack(Items.PLAYER_HEAD);
        MessageWidget widget = new MessageWidget(wrapped, isSender, icon, 0, this, false,
                newMessage.getVoiceId(), newMessage.getVoiceDurationTicks(), newMessage.getVoiceEnvelope(), newMessage.getImageId());
        return new MessageEntry(newMessage, widget);
    }

    private MessageEntry buildCallEntry(MessageData newMessage) {
        WrappedTextWidget wrapped = new WrappedTextWidget(
            Minecraft.getInstance().font,
            x,
            0, // temp y, updated in render
            fullWidth,
            Component.empty(), // real text is computed live every frame - see MessageWidget#computeCallText
            scale,
            0xff000000,
            CALL_BACKGROUND_COLOR,
            3, 3, 4, 3,
            new ItemStack(ModItems.CRAZY_PHONE.get())
        );
        MessageWidget widget = new MessageWidget(wrapped, this, newMessage.getCallId(), newMessage.getCallStartMillis(), newMessage.getCallDurationMillis());
        widget.setShowIcon(false);
        return new MessageEntry(newMessage, widget);
    }

    private MessageEntry buildSystemEntry(MessageData newMessage) {
        WrappedTextWidget wrapped = new WrappedTextWidget(
            Minecraft.getInstance().font,
            x,
            0, // temp y, updated in render
            fullWidth,
            newMessage.getSystemText(),
            scale,
            0xff000000,
            SYSTEM_BACKGROUND_COLOR,
            3, 3, 4, 3,
            newMessage.getSystemIcon()
        );
        MessageWidget widget = new MessageWidget(wrapped, false, ItemStack.EMPTY, 0, this, true);
        widget.setShowIcon(false);
        return new MessageEntry(newMessage, widget);
    }

    public int getTotalHeight() {
        return this.totalHeight;
    }

    public List<MessageEntry> getMessages() {
        return messageEntries;
    }

    /** Finds the call entry matching {@code callId} (if it's currently loaded in this screen's history) and
     * pushes the server's authoritative final duration into it - see MessageWidget#applyFinalizedDuration.
     * No-op if that call's entry isn't loaded here (evicted, or this conversation's history hasn't scrolled
     * back far enough to include it), same as the pre-existing "next full reload picks up the real value"
     * fallback for that case. */
    public void updateCallDuration(java.util.UUID callId, long durationMillis) {
        for (MessageEntry entry : messageEntries) {
            if (callId.equals(entry.widget().getCallId())) {
                entry.widget().applyFinalizedDuration(durationMillis);
                return;
            }
        }
    }
}
