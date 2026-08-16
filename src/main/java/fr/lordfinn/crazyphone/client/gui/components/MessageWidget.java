package fr.lordfinn.crazyphone.client.gui.components;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.BufferUploader;
//?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import com.mojang.blaze3d.platform.NativeImage;

//? if neoforge {
import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.TextureCache;
import de.maxhenkel.camera.gui.ImageScreen;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.network.VoiceMessageAudioRequestPacket;
import fr.lordfinn.crazyphone.network.VoiceMessageStopPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MessageWidget extends AbstractWidget {
    private static final float HOVER_GROW_SCALE = 1.04f;

    private final WrappedTextWidget wrappedText;
    private final boolean isSender;
    private final ItemStack icon;
    /** True for a system event entry (rename / icon change / member excluded / admin reassigned) - no
     * sender head floats beside it, and it isn't offset left/right like a normal chat bubble. */
    private final boolean isSystem;
    private int scrollPosition;
    private int adjustedY;
    boolean showIcon = true;
    private ItemStack image = ItemStack.EMPTY;
    /** Fabric-native picture pipeline (task #165) counterpart to {@link #image} - NeoForge's image messages
     * carry a real Camera-mod ItemStack instead and leave this null; Fabric's carry this instead and leave
     * {@code image} empty. Exactly one of the two is ever set for an image message, on either loader. */
    private java.util.UUID fabricImageId;
    private int imageWidth = 0;
    private int imageHeight = 0;
    MessageDisplayManager manager;
    /** Non-null for a voice message bubble. Play/speed state is purely local to this widget - there is no
     * server ack, so "still playing" is simulated from elapsed wall-clock time against the known duration
     * (adjusted for the selected speed), matching what the server-side AudioPlayer is actually doing. */
    private final java.util.UUID voiceId;
    private final int voiceDurationTicks;
    private final byte[] voiceEnvelope;
    private static final float[] VOICE_SPEEDS = {0.5f, 1f, 1.5f, 2f};
    private int voiceSpeedIndex = 1;
    private long voicePlayStartMs = -1;
    /** Original-clip tick position where the CURRENT playback segment began - not always 0: changing speed
     * mid-play restarts the server-side AudioPlayer from here (see onSpeedLabelClicked) rather than from
     * the beginning, so switching speed continues in real time instead of restarting the clip. */
    private int voicePlayStartTick = 0;
    /** Cached each render so mouseClicked can hit-test the same regions without recomputing font metrics. */
    private int voicePlayIconX, voicePlayIconY, voiceSpeedLabelX, voiceSpeedLabelWidth, voiceLabelY;
    /** Non-null for a call log entry. Unlike every other message type this one's displayed TEXT changes
     * after construction: while callDurationMillis is -1 (still ongoing), computeCallText() recomputes it
     * fresh every render from wall-clock time - see the field javadocs below and WrappedTextWidget, which
     * re-reads its message fresh each frame too, so this needs no other plumbing to actually animate. */
    private final java.util.UUID callId;
    private final long callStartMillis;
    /** -1 while ongoing. Gets set locally (see computeCallText) the moment this client notices its own call
     * ended, independent of whatever the server eventually persists - both converge on the same value since
     * they're measuring the same real-world event, just observed a few ms apart. */
    private long callDurationMillis;
    /** Whether ClientCallState was ever observed matching this exact call while active - only a client who
     * was themselves genuinely on this call should freeze it locally on a state change; a bystander merely
     * viewing the conversation was never "live" for it and should keep deferring to the server's own value. */
    private boolean callWasEverMine = false;
    private static final DateTimeFormatter CALL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** Null for every non-call message type. */
    @Nullable
    public java.util.UUID getCallId() {
        return callId;
    }

    /** Applied when the server pushes the real finalized duration for this call (see
     * CrazyPhoneNewCallDurationNotificationPacket) - closes the gap where a widget that was never "mine"
     * (a bystander watching someone else's call in a group conversation) would otherwise keep ticking an
     * estimate forever, since only a client who lived through the call themselves ever locally freezes it
     * (see {@link #callWasEverMine}). Harmless no-op if this widget already froze itself with the same value
     * a few ms earlier. */
    public void applyFinalizedDuration(long durationMillis) {
        this.callDurationMillis = durationMillis;
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, false, null, 0, null, null, 0, -1, null);
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, isSystem, null, 0, null, null, 0, -1, null);
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem,
                          @Nullable java.util.UUID voiceId, int voiceDurationTicks, @Nullable byte[] voiceEnvelope) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, isSystem, voiceId, voiceDurationTicks, voiceEnvelope, null, 0, -1, null);
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem,
                          @Nullable java.util.UUID voiceId, int voiceDurationTicks, @Nullable byte[] voiceEnvelope, @Nullable java.util.UUID fabricImageId) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, isSystem, voiceId, voiceDurationTicks, voiceEnvelope, null, 0, -1, fabricImageId);
    }

    /** Call log entry - see the callId/callStartMillis/callDurationMillis field javadocs. */
    public MessageWidget(WrappedTextWidget wrappedText, MessageDisplayManager messageDisplayManager,
                          java.util.UUID callId, long callStartMillis, long callDurationMillis) {
        this(wrappedText, false, ItemStack.EMPTY, 0, null, messageDisplayManager, true, null, 0, null, callId, callStartMillis, callDurationMillis, null);
    }

    private MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem,
                          @Nullable java.util.UUID voiceId, int voiceDurationTicks, @Nullable byte[] voiceEnvelope,
                          @Nullable java.util.UUID callId, long callStartMillis, long callDurationMillis, @Nullable java.util.UUID fabricImageId) {
        super(wrappedText.getX(), wrappedText.getY(), wrappedText.getWidth(), wrappedText.getHeight(), wrappedText.getMessage());
        this.wrappedText = wrappedText;
        this.isSender = isSender;
        this.icon = icon;
        this.isSystem = isSystem;
        this.scrollPosition = scrollPosition;
        this.manager = messageDisplayManager;
        this.voiceId = voiceId;
        this.voiceDurationTicks = voiceDurationTicks;
        this.voiceEnvelope = voiceEnvelope == null ? new byte[0] : voiceEnvelope;
        this.callId = callId;
        this.callStartMillis = callStartMillis;
        this.callDurationMillis = callDurationMillis;
        this.fabricImageId = fabricImageId;
        if (image != null && !image.isEmpty()) {
            this.image = image;
            initImageScaling();
        } else if (fabricImageId != null) {
            initImageScaling();
        }
        adjustPosition(this.getX() + (isSystem || isSender ? 0 : 15));
    }

    public void setScrollPosition(int scrollPosition) {
        this.scrollPosition = scrollPosition;
        adjustPosition();
    }

    public void adjustPosition() {
        this.adjustPosition(wrappedText.getX());
    }
    private void adjustPosition(int x) {
        this.adjustedY = this.getY() + scrollPosition - wrappedText.getHeight();
        wrappedText.setPosition(x, adjustedY);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (callId != null) {
            wrappedText.setMessage(computeCallText());
            // AbstractWidget's own height field (what MessageDisplayManager#resetPositions actually reads
            // via getHeight()) is otherwise frozen at whatever it was when this widget was constructed - see
            // the constructor's super() call - so without this, a call entry whose real text (set above)
            // needs more room than the empty placeholder it was built with (e.g. a 2-line "interrupted"
            // message vs the 1-line construction-time height) permanently understates its own height to the
            // layout pass, and the neighbor above it never gets pushed up to make room.
            this.setHeight(wrappedText.getHeight());
        }
        wrappedText.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        if (voiceId != null)
            renderVoiceContent(guiGraphics, mouseX, mouseY);
        renderItemHead(guiGraphics, mouseX, mouseY);
    }

    /** Recomputed every frame while the call is still ongoing (callDurationMillis == -1) - see the field's
     * own javadoc and WrappedTextWidget#renderWidget, which re-reads its message fresh each frame too, so
     * calling setMessage() here each render is all live-ticking needs, no separate animation/tick hook. */
    private Component computeCallText() {
        // The server marks a call this way at startup if it was still connected when the previous server
        // process ended (crash, forced kill, shutdown) - CallRegistry never got to run its normal end-of-call
        // finalize for it, so there's no real duration to show, and none of the branches below (which all
        // exist to freeze/estimate a duration for a call that genuinely ran to completion) apply here. Without
        // this early return this would otherwise fall into the "never mine" branch and tick a fake elapsed
        // time forever, since it can never become this client's own active call again.
        if (callDurationMillis == fr.lordfinn.crazyphone.data.ConversationSavedData.ORPHANED_CALL_DURATION_MILLIS) {
            String startTime = CALL_TIME_FORMATTER.format(Instant.ofEpochMilli(callStartMillis).atZone(ZoneId.systemDefault()));
            return Component.translatable("message.crazyphone.call_interrupted", startTime);
        }

        long elapsedMillis;
        if (callDurationMillis >= 0) {
            elapsedMillis = callDurationMillis;
        } else if (ClientCallState.isActiveCall() && callId.equals(ClientCallState.getCallId())) {
            callWasEverMine = true;
            elapsedMillis = System.currentTimeMillis() - callStartMillis;
        } else if (callWasEverMine) {
            // This client just noticed its own call ended (received the ENDED state sync it already gets
            // regardless) - freeze here rather than keep ticking. The server independently finalizes the
            // stored duration around the same real-world moment, so the two values converge.
            callDurationMillis = System.currentTimeMillis() - callStartMillis;
            elapsedMillis = callDurationMillis;
        } else {
            // Never was this client's own call (a bystander watching someone else's call in a group
            // conversation, or reopening before the server's finalized value has been (re)fetched) - best
            // effort: keep ticking from the start time until the real value arrives on the next page load.
            elapsedMillis = System.currentTimeMillis() - callStartMillis;
        }

        String time = CALL_TIME_FORMATTER.format(Instant.ofEpochMilli(callStartMillis).atZone(ZoneId.systemDefault()));
        String duration = formatCallDuration(elapsedMillis);
        String key = callDurationMillis < 0
                ? "message.crazyphone.call_in_progress"
                : "message.crazyphone.call_summary";
        return Component.translatable(key, time, duration);
    }

    private static String formatCallDuration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Whether playback should still be showing as "in progress" - simulated from elapsed wall-clock time
     * since the current segment started, since there's no server ack to drive this off of (see the field
     * javadoc). Accounts for {@link #voicePlayStartTick} so resuming partway through (a speed change)
     * measures against the REMAINING clip, not the full original duration. */
    private boolean isVoiceStillPlaying() {
        if (voicePlayStartMs < 0)
            return false;
        int remainingTicks = voiceDurationTicks - voicePlayStartTick;
        long durationMs = (long) (remainingTicks * 50 / VOICE_SPEEDS[voiceSpeedIndex]);
        boolean stillPlaying = System.currentTimeMillis() - voicePlayStartMs < durationMs;
        if (!stillPlaying) {
            voicePlayStartMs = -1;
            voicePlayStartTick = 0;
        }
        return stillPlaying;
    }

    /** Current position within the ORIGINAL (unsped-up/slowed-down) clip, in ticks - used both for the
     * displayed elapsed time and as the seek point if the player changes speed mid-playback. */
    private int currentOriginalTick() {
        if (voicePlayStartMs < 0)
            return 0;
        long elapsedMs = System.currentTimeMillis() - voicePlayStartMs;
        return voicePlayStartTick + (int) (elapsedMs * VOICE_SPEEDS[voiceSpeedIndex] / 50);
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = ticks / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /** Sender bubbles are blue (0xcc0084ff) - white reads fine there. Receiver bubbles are near-white
     * (0xccfafafa) - white icons/text/bars would be nearly invisible, so those use the app's blue accent
     * instead (same blue as the sender bubble itself), matching the existing black-vs-white text-color
     * convention {@link MessageDisplayManager#addMessage} already applies to normal text bubbles. */
    private int voiceAccentColor() {
        return isSender ? 0xFFFFFFFF : 0xFF0084FF;
    }

    private void renderVoiceContent(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        float textScale = wrappedText.getTextScale();
        int bubbleX = wrappedText.getX();
        int bubbleY = wrappedText.getY();
        int bubbleW = wrappedText.getWidth();
        int bubbleH = wrappedText.getHeight();
        boolean playing = isVoiceStillPlaying();
        int accentColor = voiceAccentColor();

        // On-screen glyph height/width scale with textScale, same as every other chat bubble's text
        // (see WrappedTextWidget.renderWidget) - font.width() itself always returns the UNscaled glyph
        // width, so on-screen sizes below are that value times textScale, not the raw font metric.
        int glyphHeight = Math.round(font.lineHeight * textScale);
        voiceLabelY = bubbleY + (bubbleH - glyphHeight) / 2 + 2;
        voicePlayIconX = bubbleX + 2;
        voicePlayIconY = voiceLabelY;
        String playIcon = playing ? "⏸" : "▶";

        int elapsedTicks = playing ? currentOriginalTick() : 0;
        String timeLabel = formatTicks(playing ? elapsedTicks : voiceDurationTicks);
        int timeX = voicePlayIconX + Math.round(8 * textScale) + 1;
        int timeWidth = Math.round(font.width(timeLabel) * textScale);

        String speedLabel = "x" + (VOICE_SPEEDS[voiceSpeedIndex] == (int) VOICE_SPEEDS[voiceSpeedIndex]
                ? String.valueOf((int) VOICE_SPEEDS[voiceSpeedIndex]) : String.valueOf(VOICE_SPEEDS[voiceSpeedIndex]));
        voiceSpeedLabelWidth = Math.round(font.width(speedLabel) * textScale);
        voiceSpeedLabelX = bubbleX + bubbleW - voiceSpeedLabelWidth - 2;
        // The waveform's right edge is pinned to the WIDEST possible speed label ("x0.5"/"x1.5", 4 chars),
        // not this specific label's own (narrower, for "x1"/"x2") width - otherwise cycling speed shifts
        // where the waveform ends and the whole histogram visibly resizes/redraws on every click, which
        // should never happen since the waveform's shape has nothing to do with playback speed.
        int maxSpeedLabelWidth = 0;
        for (float speed : VOICE_SPEEDS) {
            String label = "x" + (speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed));
            maxSpeedLabelWidth = Math.max(maxSpeedLabelWidth, Math.round(font.width(label) * textScale));
        }
        int waveformRightBound = bubbleX + bubbleW - maxSpeedLabelWidth - 2;
        boolean hoveringSpeed = mouseX >= voiceSpeedLabelX && mouseX < voiceSpeedLabelX + voiceSpeedLabelWidth
                && mouseY >= voiceLabelY && mouseY < voiceLabelY + glyphHeight;
        if (hoveringSpeed)
            CursorEffects.requestPointerCursor();

        // All three labels drawn scaled (matching the surrounding chat text's own size) in one pushPose
        // block, same technique WrappedTextWidget itself uses - coordinates are divided by textScale since
        // the transform re-multiplies them back up to real screen pixels.
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.scale(guiGraphics, textScale, textScale);
        guiGraphics.drawString(font, playIcon, (int) (voicePlayIconX / textScale), (int) (voicePlayIconY / textScale), accentColor, false);
        guiGraphics.drawString(font, timeLabel, (int) (timeX / textScale), (int) (voiceLabelY / textScale), accentColor, false);
        guiGraphics.drawString(font, speedLabel, (int) (voiceSpeedLabelX / textScale), (int) (voiceLabelY / textScale), hoveringSpeed ? CrazyPhoneColors.ACCENT_YELLOW : accentColor, false);
        GuiCompat.popPose(guiGraphics);

        // Live waveform, to the right of the time - a static preview of the whole clip's envelope normally,
        // scrubbing left-to-right in sync with elapsed playback time while playing.
        int waveformX = timeX + timeWidth + 3;
        int waveformEnd = waveformRightBound - 3;
        renderVoiceWaveform(guiGraphics, waveformX, waveformEnd, bubbleY, bubbleH, playing, elapsedTicks, accentColor);
    }

    private void renderVoiceWaveform(GuiGraphics guiGraphics, int startX, int endX, int bubbleY, int bubbleH, boolean playing, int elapsedTicks, int accentColor) {
        if (voiceEnvelope.length == 0 || endX <= startX)
            return;
        int barCount = voiceEnvelope.length;
        int totalWidth = endX - startX;
        int barWidth = Math.max(1, totalWidth / barCount);
        int centerY = bubbleY + bubbleH / 2;
        int progressBar = playing && voiceDurationTicks > 0 ? (elapsedTicks * barCount) / voiceDurationTicks : barCount;

        // Integer pixel math can only center a bar exactly when its height is even - an odd-height bar
        // (level/255 * (bubbleH-4) doesn't always land on an even number) would otherwise get its leftover
        // half-pixel dumped entirely on one side, biasing it visibly off-center. Halving the Y scale here
        // gives half-pixel precision: coordinates below are in "doubled" Y units, so a bar's true edges can
        // land exactly on a half-pixel boundary (straddling a real pixel row) and render genuinely centered
        // on centerY regardless of parity - same pushPose/scale/popPose technique renderVoiceContent already
        // uses for text, just scaling Y only (X stays at 1:1, unaffected) instead of both axes.
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.scale(guiGraphics, 1f, 0.5f);
        int centerY2x = centerY * 2;
        for (int i = 0; i < barCount; i++) {
            int level = voiceEnvelope[i] & 0xFF;
            int barHeight = Math.max(1, level * (bubbleH - 4) / 255);
            int barX = startX + i * barWidth;
            int color = playing && i < progressBar ? CrazyPhoneColors.ACCENT_YELLOW : accentColor;
            guiGraphics.fill(barX, centerY2x - barHeight, barX + Math.max(1, barWidth - 1), centerY2x + barHeight, color);
        }
        GuiCompat.popPose(guiGraphics);
    }

    private boolean isHoveringVoicePlayIcon(double mouseX, double mouseY) {
        return mouseX >= voicePlayIconX && mouseX < voicePlayIconX + 8 && mouseY >= voicePlayIconY && mouseY < voicePlayIconY + 8;
    }

    private boolean isHoveringVoiceSpeedLabel(double mouseX, double mouseY) {
        return mouseX >= voiceSpeedLabelX && mouseX < voiceSpeedLabelX + voiceSpeedLabelWidth
                && mouseY >= voiceLabelY && mouseY < voiceLabelY + 8;
    }

    private void renderItemHead(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int itemX = isSender
                ? wrappedText.getX() + wrappedText.getWidth()
                : wrappedText.getX() - 16;
                int itemY = wrappedText.getY() + wrappedText.getHeight() - 16;
        if (!image.isEmpty())
            renderImage(guiGraphics, mouseX, mouseY);
        if (showIcon && !isSystem)
            guiGraphics.renderItem(icon, itemX, itemY);
     }

    /** Head icon hit box, matching the position computed in {@link #renderItemHead}. Used by the screen to show a name/number tooltip on hover. */
    public boolean isHeadHovered(double mouseX, double mouseY) {
        if (!showIcon || isSystem)
            return false;
        int itemX = isSender ? wrappedText.getX() + wrappedText.getWidth() : wrappedText.getX() - 16;
        int itemY = wrappedText.getY() + wrappedText.getHeight() - 16;
        return mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16;
    }

    public String getContactName() {
        return readIconCustomData("name");
    }

    public String getContactNumber() {
        return readIconCustomData("number");
    }

    private String readIconCustomData(String key) {
        return fr.lordfinn.crazyphone.utils.NbtCompat.getString(fr.lordfinn.crazyphone.utils.PhoneTagAccess.getTag(icon), key);
    }

    /** Hit box for the message bubble itself (not the head icon), used by the screen to show a sent-at timestamp tooltip on hover. */
    public boolean isBubbleHovered(double mouseX, double mouseY) {
        return mouseX >= wrappedText.getX() && mouseX < wrappedText.getX() + wrappedText.getWidth()
                && mouseY >= wrappedText.getY() && mouseY < wrappedText.getY() + wrappedText.getHeight();
    }

    //? if <1.21.10 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return mouseClickedCompat(mouseX, mouseY, button);
    }
    //?}
    //? if >=1.21.10 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return mouseClickedCompat(event.x(), event.y(), event.button());
    }
    *///?}

    /** Version-stable entry point other classes (CrazyPhoneConversationScreen's own message-feed hit test)
     *  can call directly, without needing their own knowledge of whichever mouseClicked signature this
     *  version of GuiEventListener/AbstractWidget actually declares. */
    public boolean mouseClickedCompat(double mouseX, double mouseY, int button) {
        if (isImageHovered((int) mouseX, (int) mouseY)) {
            onImageClick(button);
            return true; // prevent further handling
        }
        if (voiceId != null && button == 0) {
            if (isHoveringVoiceSpeedLabel(mouseX, mouseY)) {
                onSpeedLabelClicked();
                return true;
            }
            if (isHoveringVoicePlayIcon(mouseX, mouseY) || isBubbleHovered(mouseX, mouseY)) {
                if (isVoiceStillPlaying()) {
                    // Already playing - this click is the pause icon, actually stop the server-side
                    // AudioPlayer rather than just resetting the local timer (which would leave the real
                    // audio still playing out even though the icon flipped back to "play").
                    //? if >=1.20.5 {
                    /*NetworkAccess.sendToServer(new VoiceMessageStopPacket());
                    *///? } else {
                    PacketDistributor.SERVER.noArg().send(new VoiceMessageStopPacket());
                    //?}
                    voicePlayStartMs = -1;
                } else {
                    voicePlayStartTick = 0;
                    voicePlayStartMs = System.currentTimeMillis();
                    //? if >=1.20.5 {
                    /*NetworkAccess.sendToServer(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], 0));
                    *///? } else {
                    PacketDistributor.SERVER.noArg().send(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], 0));
                    //?}
                }
                return true;
            }
        }
        return false;
    }

    /** Cycling the speed while nothing is playing just changes what the NEXT play click will use. While
     * actively playing, though, the change takes effect immediately: seeks the server-side AudioPlayer to
     * wherever this clip currently is and restarts it at the new speed from there, so playback continues in
     * real time instead of resetting to the beginning. */
    private void onSpeedLabelClicked() {
        boolean wasPlaying = isVoiceStillPlaying();
        int resumeTick = wasPlaying ? currentOriginalTick() : 0;
        voiceSpeedIndex = (voiceSpeedIndex + 1) % VOICE_SPEEDS.length;
        if (wasPlaying) {
            voicePlayStartTick = resumeTick;
            voicePlayStartMs = System.currentTimeMillis();
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], resumeTick));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], resumeTick));
            //?}
        }
    }

    //? if neoforge {
    private void onImageClick(int button) {
        if (button == 0) {
            CameraModHelper.openImage(image);
        }
    }
    //?}
    //? if fabric {
    /*private void onImageClick(int button) {
        // No full-screen image viewer on Fabric yet (task #165 follow-up) - the hover-zoom in renderImage
        // already shows the picture larger in place, which covers the common case of "let me see that
        // properly" without needing a whole extra screen.
    }
    *///?}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Optional: narration support
    }

    public void setShowIcon(boolean b) {
        this.showIcon = b;
    }

    /** Public so {@link MessageDisplayManager} can defer this widget to a second render pass when its image is hovered, so the grown/shadowed image always paints on top of neighboring messages instead of being covered by whichever one renders later in normal order. */
    public boolean isImageHovered(int mouseX, int mouseY) {
        if (image.isEmpty() && fabricImageId == null)
            return false;
        int x = wrappedText.getX();
        int y = wrappedText.getY();
        return mouseX >= x && mouseX < x + imageWidth && mouseY >= y && mouseY < y + imageHeight;
    }

    //? if neoforge {
    private void renderImage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (image.isEmpty()) return;

        if (imageWidth <= 0 || imageHeight <= 0) {
            initImageScaling();
            adjustPosition();
            manager.resetPositions();
        }

        ImageData imageData = ImageData.fromStack(image);
        if (imageData == null) return;

        UUID imageID = imageData.getId();
        if (imageID == null) return;

        int x = wrappedText.getX();
        int y = wrappedText.getY();
        int baseWidth = imageWidth;
        // Must match the aspect ratio initImageScaling() computed (imageWidth/imageHeight, fit to the
        // message bubble's full width) - previously this passed imageHeight-2 into drawImage() below,
        // which doesn't match that ratio and made drawImage()'s own aspect-fit-and-center logic think the
        // box was slightly too wide, letterboxing the image ~2px narrower on each side than the bubble.
        // That gap was also why the shadow (sized to the full box) looked asymmetric/oversized on one
        // side: it was drawn relative to the box, not the narrower letterboxed image content.
        int baseHeight = imageHeight;

        // drawY must be exactly y (not y+1) now that baseHeight is the FULL imageHeight: wrappedText's
        // own layout height (set via setMinHeight(imageHeight) in initImageScaling) is what both the
        // head icon position (renderItemHead) and the next message's position (resetPositions) are
        // computed from - any extra offset here makes the actually-rendered image bottom edge land past
        // that logical boundary, misaligning the head and overlapping whatever renders below it.
        int drawX = x;
        int drawY = y;
        int drawWidth = baseWidth;
        int drawHeight = baseHeight;

        if (isImageHovered(mouseX, mouseY)) {
            CursorEffects.requestZoomCursor();

            // Grow around the center. MessageDisplayManager defers rendering of a hovered-image widget to
            // a second pass, after every other message, so the grown/shadowed image always paints on top
            // regardless of neighboring messages - no need to constrain growth direction here anymore.
            drawWidth = Math.round(baseWidth * HOVER_GROW_SCALE);
            drawHeight = Math.round(baseHeight * HOVER_GROW_SCALE);
            drawX = x - (drawWidth - baseWidth) / 2;
            drawY = y - (drawHeight - baseHeight) / 2;

            // Soft drop shadow behind the (grown) image - 1px right, 1px down.
            guiGraphics.fill(drawX + 1, drawY + 1, drawX + drawWidth + 1, drawY + drawHeight + 1, 0x66000000);
        }

        drawImage(guiGraphics, Minecraft.getInstance(), drawX, drawY, drawWidth, drawHeight, 0f, imageID);
    }

    public void initImageScaling() {
        ImageData imageData = ImageData.fromStack(image);
        if (imageData != null) {
            UUID imageID = imageData.getId();
            NativeImage nativeImage = TextureCache.instance().getNativeImage(imageID);
            if (nativeImage != null) {
                float imgWidth = nativeImage.getWidth();
                float imgHeight = nativeImage.getHeight();
                int maxWidth = wrappedText.getWidth();

                this.imageWidth = maxWidth;
                this.imageHeight = (int) (maxWidth * imgHeight / imgWidth);

                // Ajuste la hauteur minimale du texte pour contenir l'image
                wrappedText.setMinHeight(Math.max(18, this.imageHeight));
                this.setHeight(wrappedText.getHeight());
            }
        }
    }

    public static void drawImage(GuiGraphics guiGraphics, Minecraft minecraft, int x, int y, int width, int height, float zLevel, UUID uuid) {
    GuiCompat.pushPose(guiGraphics);
    GuiCompat.translate(guiGraphics, x, y);

    ResourceLocation location = TextureCache.instance().getImage(uuid);
    ResourceLocation texture = location == null ? ImageScreen.DEFAULT_IMAGE : location;

    float imageWidth = 12.0F;
    float imageHeight = 8.0F;

    if (location != null) {
        NativeImage image = TextureCache.instance().getNativeImage(uuid);
        if (image != null) {
            imageWidth = (float) image.getWidth();
            imageHeight = (float) image.getHeight();
        }
    }

    // Always fit to the full requested width rather than picking width-fit vs height-fit based on
    // comparing aspect ratios: the caller (MessageWidget.renderImage) already sizes the box to match the
    // message bubble's width exactly and derives height from the native aspect ratio via
    // initImageScaling() - but that height gets rounded to an int, so re-deriving "which dimension to fit"
    // from the (now slightly off) box aspect ratio could still pick the height-fit branch and letterbox
    // the image a fraction of a pixel narrower than the bubble on each side. Always fitting to width
    // guarantees the image visually fills exactly the same width as a text message, at the cost of a
    // sub-pixel (imperceptible) vertical over/undershoot when the rounding doesn't line up perfectly.
    float ws = (float) width;
    float hs = (float) height;
    float wnew = ws;
    float hnew = imageHeight * ws / imageWidth;

    // Centrage dans la zone width x height
    float left = 0.0F;
    float top = (hs - hnew) / 2.0F;

    // This bypasses GuiGraphics's own Z-tracking (used by blit()/fill()/renderTooltip() etc. to guarantee
    // later-drawn elements like tooltips appear on top). Pre-1.21.10, without disabling depth testing here,
    // this quad can write a depth value that makes a legitimately later-drawn, higher-Z tooltip fail the
    // depth test and render as hidden behind message images - exactly the bug this fixes (tooltips
    // appearing under the send/add-image buttons). 1.21.10's GuiGraphics.blit goes through the same
    // stratum-ordered GuiRenderState every other GUI element does, so it no longer needs (or has) a manual
    // depth-test toggle to get the same guarantee.
    //? if <1.21.10 {
    RenderSystem.disableDepthTest();
    //?}
    GuiCompat.drawTexturedQuad(guiGraphics, texture, left, top, left + wnew, top + hnew, 0.0F, 0.0F, 1.0F, 1.0F);
    //? if <1.21.10 {
    RenderSystem.enableDepthTest();
    //?}

    GuiCompat.popPose(guiGraphics);
}
    //?}
    //? if fabric {
    /*// Fabric-native picture pipeline (task #165) - same layout/hover-grow/shadow logic as the NeoForge
    // body above, just resolving the texture through FabricPictureCache (lazy server fetch keyed by
    // fabricImageId) instead of Camera mod's TextureCache. No full-screen viewer to open on click yet (see
    // onImageClick's own note) - the hover-zoom below is this loader's only "look closer" affordance so far.
    private void renderImage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (fabricImageId == null) return;

        if (imageWidth <= 0 || imageHeight <= 0) {
            initImageScaling();
            adjustPosition();
            manager.resetPositions();
        }

        net.minecraft.resources.ResourceLocation texture = fr.lordfinn.crazyphone.client.picture.FabricPictureCache.getOrRequest(fabricImageId);
        if (texture == null) return;

        int x = wrappedText.getX();
        int y = wrappedText.getY();
        int drawX = x;
        int drawY = y;
        int drawWidth = imageWidth;
        int drawHeight = imageHeight;

        if (isImageHovered(mouseX, mouseY)) {
            CursorEffects.requestZoomCursor();
            drawWidth = Math.round(imageWidth * HOVER_GROW_SCALE);
            drawHeight = Math.round(imageHeight * HOVER_GROW_SCALE);
            drawX = x - (drawWidth - imageWidth) / 2;
            drawY = y - (drawHeight - imageHeight) / 2;
            guiGraphics.fill(drawX + 1, drawY + 1, drawX + drawWidth + 1, drawY + drawHeight + 1, 0x66000000);
        }

        GuiCompat.blit(guiGraphics, texture, drawX, drawY, 300, drawWidth, drawHeight);
    }

    public void initImageScaling() {
        if (fabricImageId == null) return;
        // Width is fixed to the message bubble's width regardless of the source image's own aspect ratio
        // until the texture actually arrives (server fetch is async) - same "reasonable default, corrected
        // once real data is in" shape as everything else in this lazy-fetch pipeline. Once
        // FabricPictureCache resolves the texture this stays a fixed square; a true aspect-ratio-aware
        // version would need the source dimensions threaded back from the fetch, left as follow-up polish.
        int maxWidth = wrappedText.getWidth();
        this.imageWidth = maxWidth;
        this.imageHeight = maxWidth;
        wrappedText.setMinHeight(Math.max(18, this.imageHeight));
        this.setHeight(wrappedText.getHeight());
    }
    *///?}

}
