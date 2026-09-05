package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    /** One "Today"/"Yesterday"/dd/MM/yyyy divider drawn between two consecutive messages (any type - text,
     * voice, image, call or system, all carry a timecode) that don't fall on the same calendar day in the
     * CLIENT's own local time zone. Purely a rendering concern - nothing here is synced or persisted, and
     * no new MessageEntry is added to {@link #messageEntries} for it (so it never interferes with the
     * head/timestamp tooltip hover loops in CrazyPhoneConversationScreen, which iterate {@link #getMessages()}
     * directly). {@code y} is the top of this divider's own reserved band, computed in {@link #resetPositions()}. */
    private record DateSeparator(int y, String label) {}
    private final List<DateSeparator> dateSeparators = new ArrayList<>();
    /** Reserved vertical space for one divider - a little taller than its own text (see
     * DATE_SEPARATOR_SCALE) so it reads as a distinct row rather than crowding its neighbors. */
    private static final int DATE_SEPARATOR_HEIGHT = 10;
    /** Same text size as a standard row in CrazyPhoneContactsScreenScreen's own contact list (that screen's
     * SECTION_TITLE_SCALE) - the only other place in this mod that draws a plain text line in a contact/
     * conversation list, reused here so the two stay visually consistent. Always full-alpha white
     * (0xFFFFFFFF, not 0xFFFFFF) - drawString silently drops a zero-alpha color on >=26. */
    private static final float DATE_SEPARATOR_SCALE = 0.85f;
    private static final int DATE_SEPARATOR_COLOR = 0xFFFFFFFF;
    private static final DateTimeFormatter DATE_SEPARATOR_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
        renderDateSeparators(guiGraphics);
    }

    /** Draws every divider computed by the last {@link #resetPositions()} call, centered across the feed's
     * full width - each one already has its own reserved, non-overlapping band, so draw order relative to
     * the message widgets above doesn't matter. No background (this is a plain text line, not a bubble). */
    private void renderDateSeparators(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        var font = Minecraft.getInstance().font;
        int lineHeight = Math.round(font.lineHeight * DATE_SEPARATOR_SCALE);
        for (DateSeparator separator : dateSeparators) {
            int textWidth = Math.round(font.width(separator.label()) * DATE_SEPARATOR_SCALE);
            int drawX = x + Math.max(0, (fullWidth - textWidth) / 2);
            int drawY = separator.y() + Math.max(0, (DATE_SEPARATOR_HEIGHT - lineHeight) / 2);
            GuiCompat.pushPose(guiGraphics);
            GuiCompat.translate(guiGraphics, drawX, drawY);
            GuiCompat.scale(guiGraphics, DATE_SEPARATOR_SCALE, DATE_SEPARATOR_SCALE);
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, separator.label(), 0, 0, DATE_SEPARATOR_COLOR, false);
            GuiCompat.popPose(guiGraphics);
        }
    }

    public void resetPositions() {
    int currentY = y + scrollOffset;
    totalHeight = 0;
    dateSeparators.clear();
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

        // A day divider belongs strictly BETWEEN two messages from different calendar days - checked
        // against the OLDER neighbor (index i+1, which sits ABOVE this one on screen: index 0 is always
        // the newest entry - see #insert - and currentY only ever decreases/rises going forward in this
        // loop), so the reserved gap ends up right above entry i, reading as "here is where entry i's own
        // day begins" when scrolling from old (top) to new (bottom), same convention as most chat UIs.
        if (i + 1 < messageEntries.size()
                && !isSameLocalDay(currentEntry.data().getTimecode(), messageEntries.get(i + 1).data().getTimecode())) {
            currentY -= DATE_SEPARATOR_HEIGHT;
            totalHeight += DATE_SEPARATOR_HEIGHT;
            dateSeparators.add(new DateSeparator(currentY, formatDateSeparatorLabel(currentEntry.data().getTimecode())));
        }

        previousSender = currentSender;
    }
}

    /** Whether two timecodes (minutes since epoch) fall on the same calendar day in the CLIENT's own local
     * time zone - purely a display grouping, no server precision/synchronization needed (see this class's
     * own doc comment on {@link #resetPositions()}). */
    private static boolean isSameLocalDay(int timecodeMinutesA, int timecodeMinutesB) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate dayA = Instant.ofEpochSecond(timecodeMinutesA * 60L).atZone(zone).toLocalDate();
        LocalDate dayB = Instant.ofEpochSecond(timecodeMinutesB * 60L).atZone(zone).toLocalDate();
        return dayA.equals(dayB);
    }

    /** "Today"/"Yesterday" (translated) for the two special-cased days, otherwise a plain dd/MM/yyyy - the
     * full-date fallback intentionally has no translation key of its own (see this class's own callers). */
    private static String formatDateSeparatorLabel(int timecodeMinutes) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = Instant.ofEpochSecond(timecodeMinutes * 60L).atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        if (day.equals(today))
            return Component.translatable("gui.crazyphone.crazy_phone_conversation.date_today").getString();
        if (day.equals(today.minusDays(1)))
            return Component.translatable("gui.crazyphone.crazy_phone_conversation.date_yesterday").getString();
        return day.format(DATE_SEPARATOR_FORMAT);
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
            Component.literal(bubbleText),
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
