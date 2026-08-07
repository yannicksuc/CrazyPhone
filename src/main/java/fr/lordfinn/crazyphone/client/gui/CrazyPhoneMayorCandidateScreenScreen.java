package fr.lordfinn.crazyphone.client.gui;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.TextureCache;
import de.maxhenkel.camera.gui.ImageScreen;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorCandidateScreenMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.UUID;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

public class CrazyPhoneMayorCandidateScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneMayorCandidateScreenMenu> {
	private static final HashMap<String, Object> guistate = CrazyPhoneMayorCandidateScreenMenu.guistate;
	private Contact mayorCandidate;
	private ImageData candidatePosterData = null;
	private ItemStack candidateHead = ItemStack.EMPTY;
	private int scrollOffsetY = 0;
	private int maxScroll = 0;

	private Button voteButton;

	public CrazyPhoneMayorCandidateScreenScreen(CrazyPhoneMayorCandidateScreenMenu container, Inventory inventory, Component text) {
		super(container, inventory, text);
	}

	public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
		return new HashMap<>();
	}

	@Override
	public HashMap<String, Object> getWidgets() {
		return guistate;
	}

	@Override
	public void init() {
		super.init();

		String mayorNumber = this.menu.mayorNumber;
		CompoundTag mayorPhone = (PhoneRegistrySavedData.get(world).phones.get(mayorNumber)) instanceof CompoundTag _compoundTag ? _compoundTag.copy() : new CompoundTag();
		if (mayorPhone == null) return;
		mayorCandidate = CrazyPhoneHelper.getContact(this.menu.world, mayorNumber);
		Tag potentialImage = PhoneRegistrySavedData.get(world).mayorsCandidates.get(mayorNumber);
        if (potentialImage instanceof CompoundTag) {
            candidatePosterData = ImageData.fromImageTag((CompoundTag) potentialImage);
        }
		this.imageWidth = 0;
		this.imageHeight = 0;

		candidateHead = CrazyPhoneHelper.createContactHead(mayorCandidate);

		boolean votingOpen = PhoneRegistrySavedData.get(world).isMayorVotingOn;

		voteButton = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_mayor_candidate_screen.button_vote"), b -> {
			Minecraft.getInstance().player.connection.sendCommand("phoneVoteForMayor " + mayorNumber);
		}).bounds(this.leftPos + 6, this.topPos + 160, 110, 14)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("gui.crazyphone.crazy_phone_mayor_candidate_screen.tooltip_vote")))
				.build();
		voteButton.active = votingOpen;

		this.addRenderableWidget(voteButton);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		super.renderBg(guiGraphics, partialTicks, gx, gy);
		renderCandidateHeader(guiGraphics);
		renderScissoredPanel(guiGraphics);
	}

	private void renderCandidateHeader(GuiGraphics guiGraphics) {
		renderHeader(guiGraphics, candidateHead, Component.literal(mayorCandidate.getName()));
	}

	private void renderScissoredPanel(GuiGraphics guiGraphics) {
		guiGraphics.enableScissor(this.leftPos, this.topPos + 27, this.leftPos + 200, this.topPos + 158);
		renderImage(guiGraphics);
		guiGraphics.disableScissor();
	}

    private void renderImage(GuiGraphics guiGraphics) {
        if (candidatePosterData == null) return;

        if (imageWidth <= 0 || imageHeight <= 0) {
            initImageScaling();
        }

        UUID imageID = candidatePosterData.getId();
        if (imageID == null) return;

        int x = this.leftPos + 6;
        int y = this.topPos + 26 - scrollOffsetY;

        drawImage(guiGraphics, Minecraft.getInstance(), x, y + 1, imageWidth, imageHeight, 0f, imageID);
    }

    public void initImageScaling() {
		ImageData imageData = candidatePosterData;
		if (imageData != null) {
			UUID imageID = imageData.getId();
			NativeImage nativeImage = TextureCache.instance().getNativeImage(imageID);
			if (nativeImage != null) {
				float imgWidth = nativeImage.getWidth();
				float imgHeight = nativeImage.getHeight();
				int maxWidth = 110;
				int maxHeight = 132; // height of scissor zone (158 - 26)

				this.imageWidth = maxWidth;
				this.imageHeight = (int) (maxWidth * imgHeight / imgWidth);

				this.maxScroll = Math.max(0, this.imageHeight - maxHeight);
			}
		}
	}

	    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (this.imageHeight > 0) {
			scrollOffsetY -= (int) (dy * 10); // Scroll speed
			scrollOffsetY = Math.max(0, Math.min(scrollOffsetY, maxScroll));
			return true;
		}
        return true;
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

    // Calcul du redimensionnement proportionnel pour tenir dans width x height
    float ws = (float) width;
    float hs = (float) height;
    float rs = ws / hs;
    float ri = imageWidth / imageHeight;
    float wnew;
    float hnew;

    if (rs > ri) {
        wnew = imageWidth * hs / imageHeight;
        hnew = hs;
    } else {
        wnew = ws;
        hnew = imageHeight * ws / imageWidth;
    }

    // Centrage dans la zone width x height
    float left = (ws - wnew) / 2.0F;
    float top = (hs - hnew) / 2.0F;

    Matrix4f matrix = guiGraphics.pose().last().pose();
    BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

    buffer.addVertex(matrix, left, top, zLevel).setUv(0.0F, 0.0F);
    buffer.addVertex(matrix, left, top + hnew, zLevel).setUv(0.0F, 1.0F);
    buffer.addVertex(matrix, left + wnew, top + hnew, zLevel).setUv(1.0F, 1.0F);
    buffer.addVertex(matrix, left + wnew, top, zLevel).setUv(1.0F, 0.0F);

    BufferUploader.drawWithShader(buffer.buildOrThrow());

    guiGraphics.pose().popPose();
}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}
}
