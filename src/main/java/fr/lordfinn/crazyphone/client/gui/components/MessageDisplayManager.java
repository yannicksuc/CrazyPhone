package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        MessageEntry hoveredImageEntry = null;
        for (MessageEntry entry : messageEntries) {
            if (entry.widget().isImageHovered(mouseX, mouseY)) {
                // Defer to a second pass below, so its grown/shadowed image paints on top of every other
                // message instead of risking being covered by whichever neighbor renders later in normal
                // (chronological) order.
                hoveredImageEntry = entry;
                continue;
            }
            entry.widget.render(guiGraphics, mouseX, mouseY, 0);
        }
        if (hoveredImageEntry != null) {
            hoveredImageEntry.widget.render(guiGraphics, mouseX, mouseY, 0);
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

    public MessageEntry addMessage(MessageData newMessage) {
        if (newMessage.isSystem())
            return addSystemMessage(newMessage);

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
        MessageWidget widget = new MessageWidget(wrapped, isSender, icon, 0, newMessage.getImage(), this, false,
                newMessage.getVoiceId(), newMessage.getVoiceDurationTicks(), newMessage.getVoiceEnvelope());
        MessageEntry entry = new MessageEntry(newMessage, widget);
        messageEntries.add(0, entry);
        resetPositions();
        return entry;
    }

    private MessageEntry addSystemMessage(MessageData newMessage) {
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
        MessageWidget widget = new MessageWidget(wrapped, false, ItemStack.EMPTY, 0, null, this, true);
        widget.setShowIcon(false);
        MessageEntry entry = new MessageEntry(newMessage, widget);
        messageEntries.add(0, entry);
        resetPositions();
        return entry;
    }

    public int getTotalHeight() {
        return this.totalHeight;
    }

    public List<MessageEntry> getMessages() {
        return messageEntries;
    }
}
