package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MessageData {
    private final int timecode;
    private final String message;
    private final String sender;
    private final boolean system;
    private final Component systemText;
    private final ItemStack systemIcon;
    private final UUID voiceId;
    private final int voiceDurationTicks;
    private final byte[] voiceEnvelope;
    private final UUID callId;
    private final long callStartMillis;
    private final long callDurationMillis;
    private final UUID imageId;

    public MessageData(int timecode, String message, String sender) {
        this.timecode = timecode;
        this.message = message;
        this.sender = sender;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
        this.callId = null;
        this.callStartMillis = 0;
        this.callDurationMillis = -1;
        this.imageId = null;
    }

    private MessageData(int timecode, Component systemText, ItemStack systemIcon) {
        this.timecode = timecode;
        this.message = "";
        this.sender = "";
        this.system = true;
        this.systemText = systemText;
        this.systemIcon = systemIcon;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
        this.callId = null;
        this.callStartMillis = 0;
        this.callDurationMillis = -1;
        this.imageId = null;
    }

    private MessageData(int timecode, String sender, UUID voiceId, int voiceDurationTicks, byte[] voiceEnvelope) {
        this.timecode = timecode;
        this.message = "";
        this.sender = sender;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = voiceId;
        this.voiceDurationTicks = voiceDurationTicks;
        this.voiceEnvelope = voiceEnvelope;
        this.callId = null;
        this.callStartMillis = 0;
        this.callDurationMillis = -1;
        this.imageId = null;
    }

    private MessageData(int timecode, UUID callId, long callStartMillis, long callDurationMillis) {
        this.timecode = timecode;
        this.message = "";
        this.sender = "";
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
        this.callId = callId;
        this.callStartMillis = callStartMillis;
        this.callDurationMillis = callDurationMillis;
        this.imageId = null;
    }

    /** A photo message - the actual PNG bytes are fetched from the server on demand, only when this
     * message's bubble is actually rendered (see FabricPictureCache), same lazy shape as the voice-message
     * audio fetch. */
    private MessageData(int timecode, String sender, UUID imageId) {
        this.timecode = timecode;
        this.message = "";
        this.sender = sender;
        this.system = false;
        this.systemText = null;
        this.systemIcon = ItemStack.EMPTY;
        this.voiceId = null;
        this.voiceDurationTicks = 0;
        this.voiceEnvelope = new byte[0];
        this.callId = null;
        this.callStartMillis = 0;
        this.callDurationMillis = -1;
        this.imageId = imageId;
    }

    public static MessageData image(int timecode, String sender, UUID imageId) {
        return new MessageData(timecode, sender, imageId);
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

    /** A call log entry - posted the moment a call connects and, unlike every other message type, mutated
     * in place (see CrazyPhoneHelper#finalizeCallMessage) once it ends rather than getting a second entry.
     * {@code callDurationMillis} is -1 while the call is still ongoing; the widget computes and displays a
     * live-ticking elapsed time from {@code callStartMillis} in that case (see MessageWidget). */
    public static MessageData call(int timecode, UUID callId, long callStartMillis, long callDurationMillis) {
        return new MessageData(timecode, callId, callStartMillis, callDurationMillis);
    }

    public boolean isImage() {
        return imageId != null;
    }

    public UUID getImageId() {
        return imageId;
    }

    public boolean isVoice() {
        return voiceId != null;
    }

    public boolean isCall() {
        return callId != null;
    }

    public UUID getCallId() {
        return callId;
    }

    public long getCallStartMillis() {
        return callStartMillis;
    }

    public long getCallDurationMillis() {
        return callDurationMillis;
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
