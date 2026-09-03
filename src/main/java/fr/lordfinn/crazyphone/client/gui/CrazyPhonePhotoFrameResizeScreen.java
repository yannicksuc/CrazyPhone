package fr.lordfinn.crazyphone.client.gui;

/**
 * Resize dialog for a placed photo frame - four +/- buttons (width/height) driving the menu's own
 * ContainerData-synced size, plus a live "W x H blocks" label. Extends AbstractContainerScreen (needed so
 * Minecraft#gameMode/the container-close packet machinery works normally) but deliberately skips its
 * background-panel rendering entirely (renderBg on <26, folded into extractContents on >=26 - a real API
 * rename confirmed via the real decompiled AbstractContainerScreen.java, not guessed) and tracks/draws its
 * own widgets manually instead, mirroring CrazyPhonePhotoViewerScreen's own established "skip
 * Screen#render's default background pass" pattern (see that class's own doc comment) - simplest way to
 * stay correct across both the <26 and >=26 API shapes without chasing the rename in the framework's own
 * plumbing this screen doesn't otherwise need (no slots, no themed panel texture).
 */
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePhotoFrameResizeMenu;

import java.util.ArrayList;
import java.util.List;

public class CrazyPhonePhotoFrameResizeScreen extends AbstractContainerScreen<CrazyPhonePhotoFrameResizeMenu> {
    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 90;
    // Same reasoning as CrazyPhonePhotoViewerScreen's own ownButtons field - this screen must skip the
    // default background/slot render pipeline (see this class's own doc comment), so it tracks its own
    // widgets to draw manually instead of relying on Screen's own private renderables list.
    private final List<Button> ownButtons = new ArrayList<>();

    //? if <26 {
    public CrazyPhonePhotoFrameResizeScreen(CrazyPhonePhotoFrameResizeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    // Still abstract here (only folded away >=26, see this class's own doc comment) - left empty since this
    // screen draws its own solid panel fill directly in render()/extractRenderState() instead, skipping
    // AbstractContainerScreen's own themed-9-slice-texture background this hook would otherwise be for.
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }
    //?}
    //? if >=26 {
    /*public CrazyPhonePhotoFrameResizeScreen(CrazyPhonePhotoFrameResizeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }
    *///?}

    @Override
    protected void init() {
        super.init();
        ownButtons.clear();
        int centerX = this.leftPos + PANEL_WIDTH / 2;
        int rowY = this.topPos + 40;
        int gap = 60;
        addButton("-", centerX - gap - 20, rowY, 0);
        addButton("+", centerX - gap + 20, rowY, 1);
        addButton("-", centerX + gap - 20, rowY, 2);
        addButton("+", centerX + gap + 20, rowY, 3);
    }

    private void addButton(String label, int x, int y, int buttonId) {
        // menu.clickMenuButton(...) directly would only mutate THIS client's own local menu instance - on a
        // real dedicated server the button press still needs to reach the server's own separate menu
        // instance (the one actually holding the entity reference). Minecraft#gameMode.handleInventoryButtonClick
        // is the vanilla client-side RPC for exactly that (ServerboundContainerButtonClickPacket under the
        // hood) - what every vanilla +/- style menu screen (enchanting table, etc.) actually calls.
        Button button = Button.builder(Component.literal(label), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId))
                .bounds(x - 10, y, 20, 20).build();
        addRenderableWidget(button);
        ownButtons.add(button);
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xE0101010);
        // >=26 renamed GuiGraphics#drawCenteredString to #centeredText (confirmed against the real
        // decompiled GuiGraphicsExtractor.java, not guessed) - same shape as every other GuiGraphics ->
        // GuiGraphicsExtractor rename this codebase has already reconciled elsewhere (see GuiCompat.java).
        guiGraphics.centeredText(this.font, this.title, leftPos + PANEL_WIDTH / 2, topPos + 8, 0xFFFFFF);
        guiGraphics.centeredText(this.font, sizeLabel(), leftPos + PANEL_WIDTH / 2, topPos + 22, 0xA0A0A0);
        for (Button button : ownButtons)
            button.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this./*$ gui_render_transparent_background {*/renderTransparentBackground/*$}*/(guiGraphics);
        guiGraphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + PANEL_WIDTH / 2, topPos + 8, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, sizeLabel(), leftPos + PANEL_WIDTH / 2, topPos + 22, 0xA0A0A0);
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}

    private Component sizeLabel() {
        float w = menu.widthUnits() / (float) CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK;
        float h = menu.heightUnits() / (float) CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK;
        return Component.translatable("gui.crazyphone.photo_frame_resize.size", String.format("%.2f", w), String.format("%.2f", h));
    }
}
