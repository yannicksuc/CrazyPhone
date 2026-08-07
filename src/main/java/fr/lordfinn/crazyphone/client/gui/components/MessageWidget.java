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
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MessageWidget extends AbstractWidget {
    private static final float HOVER_GROW_SCALE = 1.04f;

    private final WrappedTextWidget wrappedText;
    private final boolean isSender;
    private final ItemStack icon;
    private int scrollPosition;
    private int adjustedY;
    boolean showIcon = true;
    private ItemStack image = ItemStack.EMPTY;
    private int imageWidth = 0;
    private int imageHeight = 0;
    MessageDisplayManager manager;

    public MessageWidget(WrappedTextWidget wrappedText, boolean isSender, ItemStack icon, int scrollPosition, @Nullable ItemStack image, MessageDisplayManager messageDisplayManager) {
        super(wrappedText.getX(), wrappedText.getY(), wrappedText.getWidth(), wrappedText.getHeight(), wrappedText.getMessage());
        this.wrappedText = wrappedText;
        this.isSender = isSender;
        this.icon = icon;
        this.scrollPosition = scrollPosition;
        this.manager = messageDisplayManager;
        if (image != null && !image.isEmpty()) {
            this.image = image;
            initImageScaling();
        }
        adjustPosition(this.getX() + (isSender ? 0 : 15));
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
        renderItemHead(guiGraphics, mouseX, mouseY);
    }

    private void renderItemHead(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int itemX = isSender
                ? wrappedText.getX() + wrappedText.getWidth()
                : wrappedText.getX() - 16;
                int itemY = wrappedText.getY() + wrappedText.getHeight() - 16;
        if (!image.isEmpty())
            renderImage(guiGraphics, mouseX, mouseY);
        if (showIcon)
            guiGraphics.renderItem(icon, itemX, itemY);
     }

    /** Head icon hit box, matching the position computed in {@link #renderItemHead}. Used by the screen to show a name/number tooltip on hover. */
    public boolean isHeadHovered(double mouseX, double mouseY) {
        if (!showIcon)
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
        return false;
    }

    private void onImageClick(int button) {
        if (button == 0) {
            CameraModHelper.openImage(image);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void openClientGui(ItemStack stack) {
      Minecraft.getInstance().setScreen(new ImageScreen(stack));
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
