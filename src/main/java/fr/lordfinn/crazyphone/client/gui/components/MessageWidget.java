package fr.lordfinn.crazyphone.client.gui.components;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import com.mojang.blaze3d.platform.NativeImage;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.TextureCache;
import de.maxhenkel.camera.gui.ImageScreen;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.network.VoiceMessageAudioRequestPacket;
import fr.lordfinn.crazyphone.network.VoiceMessageStopPacket;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
    private int imageWidth = 0;
    private int imageHeight = 0;
    MessageDisplayManager manager;
    /** Non-null for a voice message bubble. Play/speed state is purely local to this widget - there is no
     * server ack, so "still playing" is simulated from elapsed wall-clock time against the known duration
     * (adjusted for the selected speed), matching what the server-side AudioPlayer is actually doing. */
    private final java.util.UUID voiceId;
    private final int voiceDurationTicks;
    private final byte[] voiceEnvelope;
    private static final float[] VOICE_SPEEDS = {0.5f, 1f, 2f};
    private int voiceSpeedIndex = 1;
    private long voicePlayStartMs = -1;
    /** Original-clip tick position where the CURRENT playback segment began - not always 0: changing speed
     * mid-play restarts the server-side AudioPlayer from here (see onSpeedLabelClicked) rather than from
     * the beginning, so switching speed continues in real time instead of restarting the clip. */
    private int voicePlayStartTick = 0;
    /** Cached each render so mouseClicked can hit-test the same regions without recomputing font metrics. */
    private int voicePlayIconX, voicePlayIconY, voiceSpeedLabelX, voiceSpeedLabelWidth, voiceLabelY;

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, false, null, 0, null);
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem) {
        this(wrappedText, isSender, icon, scrollPosition, image, messageDisplayManager, isSystem, null, 0, null);
    }

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager, boolean isSystem,
                          @Nullable java.util.UUID voiceId, int voiceDurationTicks, @Nullable byte[] voiceEnvelope) {
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
        if (image != null && !image.isEmpty()) {
            this.image = image;
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
        wrappedText.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
        if (voiceId != null)
            renderVoiceContent(guiGraphics, mouseX, mouseY);
        renderItemHead(guiGraphics, mouseX, mouseY);
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
        boolean hoveringSpeed = mouseX >= voiceSpeedLabelX && mouseX < voiceSpeedLabelX + voiceSpeedLabelWidth
                && mouseY >= voiceLabelY && mouseY < voiceLabelY + glyphHeight;
        if (hoveringSpeed)
            CursorEffects.requestPointerCursor();

        // All three labels drawn scaled (matching the surrounding chat text's own size) in one pushPose
        // block, same technique WrappedTextWidget itself uses - coordinates are divided by textScale since
        // the transform re-multiplies them back up to real screen pixels.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(textScale, textScale, 1.0F);
        guiGraphics.drawString(font, playIcon, (int) (voicePlayIconX / textScale), (int) (voicePlayIconY / textScale), accentColor, false);
        guiGraphics.drawString(font, timeLabel, (int) (timeX / textScale), (int) (voiceLabelY / textScale), accentColor, false);
        guiGraphics.drawString(font, speedLabel, (int) (voiceSpeedLabelX / textScale), (int) (voiceLabelY / textScale), hoveringSpeed ? 0xFFFFEE00 : accentColor, false);
        guiGraphics.pose().popPose();

        // Live waveform, to the right of the time - a static preview of the whole clip's envelope normally,
        // scrubbing left-to-right in sync with elapsed playback time while playing.
        int waveformX = timeX + timeWidth + 3;
        int waveformEnd = voiceSpeedLabelX - 3;
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
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(1f, 0.5f, 1f);
        int centerY2x = centerY * 2;
        for (int i = 0; i < barCount; i++) {
            int level = voiceEnvelope[i] & 0xFF;
            int barHeight = Math.max(1, level * (bubbleH - 4) / 255);
            int barX = startX + i * barWidth;
            int color = playing && i < progressBar ? 0xFFFFEE00 : accentColor;
            guiGraphics.fill(barX, centerY2x - barHeight, barX + Math.max(1, barWidth - 1), centerY2x + barHeight, color);
        }
        guiGraphics.pose().popPose();
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
        return icon.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getString(key);
    }

    /** Hit box for the message bubble itself (not the head icon), used by the screen to show a sent-at timestamp tooltip on hover. */
    public boolean isBubbleHovered(double mouseX, double mouseY) {
        return mouseX >= wrappedText.getX() && mouseX < wrappedText.getX() + wrappedText.getWidth()
                && mouseY >= wrappedText.getY() && mouseY < wrappedText.getY() + wrappedText.getHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                    PacketDistributor.sendToServer(new VoiceMessageStopPacket());
                    voicePlayStartMs = -1;
                } else {
                    voicePlayStartTick = 0;
                    voicePlayStartMs = System.currentTimeMillis();
                    PacketDistributor.sendToServer(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], 0));
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
            PacketDistributor.sendToServer(new VoiceMessageAudioRequestPacket(voiceId, VOICE_SPEEDS[voiceSpeedIndex], resumeTick));
        }
    }

    private void onImageClick(int button) {
        if (button == 0) {
            CameraModHelper.openImage(image);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Optional: narration support
    }

    public void setShowIcon(boolean b) {
        this.showIcon = b;
    }

    /** Public so {@link MessageDisplayManager} can defer this widget to a second render pass when its image is hovered, so the grown/shadowed image always paints on top of neighboring messages instead of being covered by whichever one renders later in normal order. */
    public boolean isImageHovered(int mouseX, int mouseY) {
        if (image.isEmpty())
            return false;
        int x = wrappedText.getX();
        int y = wrappedText.getY();
        return mouseX >= x && mouseX < x + imageWidth && mouseY >= y && mouseY < y + imageHeight;
    }

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
    guiGraphics.pose().pushPose();
    guiGraphics.pose().translate(x, y, 0);

    RenderSystem.setShader(GameRenderer::getPositionTexShader);
    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    ResourceLocation location = TextureCache.instance().getImage(uuid);

    float imageWidth = 12.0F;
    float imageHeight = 8.0F;

    if (location == null) {
        RenderSystem.setShaderTexture(0, ImageScreen.DEFAULT_IMAGE);
    } else {
        RenderSystem.setShaderTexture(0, location);
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

    Matrix4f matrix = guiGraphics.pose().last().pose();
    BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

    buffer.addVertex(matrix, left, top, zLevel).setUv(0.0F, 0.0F);
    buffer.addVertex(matrix, left, top + hnew, zLevel).setUv(0.0F, 1.0F);
    buffer.addVertex(matrix, left + wnew, top + hnew, zLevel).setUv(1.0F, 1.0F);
    buffer.addVertex(matrix, left + wnew, top, zLevel).setUv(1.0F, 0.0F);

    // This is a raw Tesselator draw that bypasses GuiGraphics's own Z-tracking (used by blit()/fill()/
    // renderTooltip() etc. to guarantee later-drawn elements like tooltips appear on top). Without
    // disabling depth testing here, this quad can write a depth value that makes a legitimately
    // later-drawn, higher-Z tooltip fail the depth test and render as hidden behind message images -
    // exactly the bug this fixes (tooltips appearing under the send/add-image buttons).
    RenderSystem.disableDepthTest();
    BufferUploader.drawWithShader(buffer.buildOrThrow());
    RenderSystem.enableDepthTest();

    guiGraphics.pose().popPose();
}

}
