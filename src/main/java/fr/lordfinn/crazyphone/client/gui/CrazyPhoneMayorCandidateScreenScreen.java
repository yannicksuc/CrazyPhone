package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.client.ClientFeatureFlagState;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.PhotoResolution;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorCandidateScreenMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public class CrazyPhoneMayorCandidateScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneMayorCandidateScreenMenu> {
	private static final HashMap<String, Object> guistate = CrazyPhoneMayorCandidateScreenMenu.guistate;
	private Contact mayorCandidate;
	private UUID candidatePosterId = null;
	private ItemStack candidateHead = ItemStack.EMPTY;
	private int scrollOffsetY = 0;
	private int maxScroll = 0;
	// The candidate's poster photo is loaded async (FabricPictureCache) and its own aspect ratio is only
	// known once the texture arrives - these track the poster's rendered size (lazily computed the first
	// time the texture is available, in initImageScaling) independently of the screen's own imageWidth/
	// imageHeight (fixed at 122x195 by the base class constructor and otherwise unused for rendering - see
	// CrazyPhoneDefaultScreenScreen#drawScreenBackground, which draws the phone background at a hardcoded
	// size regardless). Pre-26 this reused the inherited imageWidth/imageHeight fields directly since they
	// happened to be freely settable after construction; 26.x made them constructor-only final, so this
	// poster-specific state needs its own fields instead.
	private int posterWidth = 0;
	private int posterHeight = 0;

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
		if (potentialImage instanceof CompoundTag posterTag && NbtCompat.contains(posterTag, "photo_id_most")) {
			candidatePosterId = new UUID(NbtCompat.getLong(posterTag, "photo_id_most"), NbtCompat.getLong(posterTag, "photo_id_least"));
		}
		this.posterWidth = 0;
		this.posterHeight = 0;

		candidateHead = CrazyPhoneHelper.createContactHead(mayorCandidate);

		boolean votingOpen = PhoneRegistrySavedData.get(world).isMayorVotingOn;
		boolean votingFeatureEnabled = ClientFeatureFlagState.isEnabled(FeatureFlag.MAYOR_VOTING);

		voteButton = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_mayor_candidate_screen.button_vote"), b -> {
			Minecraft.getInstance().player.connection.sendCommand("crazyphone mayor vote " + mayorNumber);
		}).bounds(this.leftPos + 6, this.topPos + 160, 110, 14)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable(!votingFeatureEnabled
								? "gui.crazyphone.crazy_phone_mayor_candidate_screen.tooltip_vote_disabled"
								: "gui.crazyphone.crazy_phone_mayor_candidate_screen.tooltip_vote")))
				.build();
		voteButton.active = votingOpen && votingFeatureEnabled;

		this.addRenderableWidget(voteButton);
	}

	//? if >=26 {
	/*@Override
	protected void drawScreenBackground(GuiGraphicsExtractor guiGraphics) {
		super.drawScreenBackground(guiGraphics);
		renderCandidateHeader(guiGraphics);
		renderScissoredPanel(guiGraphics);
	}
	*///? } else {
	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
		super.renderBg(guiGraphics, partialTicks, gx, gy);
		renderCandidateHeader(guiGraphics);
		renderScissoredPanel(guiGraphics);
	}
	//?}

	private void renderCandidateHeader(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
		renderHeader(guiGraphics, candidateHead, Component.literal(mayorCandidate.getName()));
	}

	private void renderScissoredPanel(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
		guiGraphics.enableScissor(this.leftPos, this.topPos + 27, this.leftPos + 200, this.topPos + 158);
		renderImage(guiGraphics);
		guiGraphics.disableScissor();
	}

    private void renderImage(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (candidatePosterId == null) return;

        FabricPictureCache.CachedTexture texture = FabricPictureCache.getOrRequest(candidatePosterId, PhotoResolution.FULL);
        if (texture == null) return;

        if (posterWidth <= 0 || posterHeight <= 0)
            initImageScaling(texture);

        int x = this.leftPos + 6;
        int y = this.topPos + 26 - scrollOffsetY + 1;

        GuiCompat.blit(guiGraphics, texture.location(), x, y, 0, posterWidth, posterHeight);
    }

    private void initImageScaling(FabricPictureCache.CachedTexture texture) {
		int maxWidth = 110;
		int maxHeight = 132; // height of scissor zone (158 - 26)

		this.posterWidth = maxWidth;
		this.posterHeight = Math.max(1, Math.round(maxWidth * ((float) texture.height() / texture.width())));

		this.maxScroll = Math.max(0, this.posterHeight - maxHeight);
	}

	    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (this.posterHeight > 0) {
			scrollOffsetY -= (int) (dy * 10); // Scroll speed
			scrollOffsetY = Math.max(0, Math.min(scrollOffsetY, maxScroll));
			return true;
		}
        return true;
    }

	//? if >=26 {
	/*@Override
	protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
	}
	*///? } else {
	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
	}
	//?}
}
