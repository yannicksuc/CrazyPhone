package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MessageData {
    private final int timecode;
    private final String message;
    private final String sender;
    private final ItemStack image;
    private final boolean system;
    private final Component systemText;
    private final ItemStack systemIcon;
    private final UUID voiceId;
    private final int voiceDurationTicks;
    private final byte[] voiceEnvelope;

    public MessageData(int timecode, String message, String sender) {
        this.timecode = timecode;
        this.message = message;
        this.sender = sender;
        this.image = ItemStack.EMPTY;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
    }

    public MessageData(int timecode, String message, String sender, ItemStack stack) {
        this.timecode = timecode;
        this.message = message;
        this.sender = sender;
        this.image = stack;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
    }

    private MessageData(int timecode, Component systemText, ItemStack systemIcon) {
        this.timecode = timecode;
        this.message = "";
        this.sender = "";
        this.image = ItemStack.EMPTY;
        this.system = true;
        this.systemText = systemText;
        this.systemIcon = systemIcon;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
    }

    private MessageData(int timecode, String sender, UUID voiceId, int voiceDurationTicks, byte[] voiceEnvelope) {
        this.timecode = timecode;
        this.message = "";
        this.sender = sender;
        this.image = ItemStack.EMPTY;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = voiceId;
        this.voiceDurationTicks = voiceDurationTicks;
        this.voiceEnvelope = voiceEnvelope;
    }

    /** A system event (rename / icon change / member excluded / admin reassigned) - not sent by anyone,
     * rendered full-width with no sender head, see {@link MessageDisplayManager#addMessage}. */
    public static MessageData system(int timecode, Component text, ItemStack icon) {
        return new MessageData(timecode, text, icon);
    }

    /** A voice message - only its metadata (id, duration, and a small precomputed amplitude envelope for
     * the "live" waveform animation) is ever carried here; the actual audio is fetched from the server on
     * demand, only when the recipient clicks play (see VoiceMessageAudioRequestPacket). */
    public static MessageData voice(int timecode, String sender, UUID voiceId, int voiceDurationTicks, byte[] voiceEnvelope) {
        return new MessageData(timecode, sender, voiceId, voiceDurationTicks, voiceEnvelope);
    }

    public boolean isVoice() {
        return voiceId != null;
    }

    public UUID getVoiceId() {
        return voiceId;
    }

    public int getVoiceDurationTicks() {
        return voiceDurationTicks;
    }

    public byte[] getVoiceEnvelope() {
        return voiceEnvelope;
    }

    public int getTimecode() {
        return timecode;
    }

    public String getMessage() {
        return message;
    }

    public String getSender() {
        return sender;
    }

    public ItemStack getImage() {
        return image;
    }

    public boolean isSystem() {
        return system;
    }

    public Component getSystemText() {
        return systemText;
    }

    public ItemStack getSystemIcon() {
        return systemIcon;
    }
}
