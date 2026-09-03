package fr.lordfinn.crazyphone.client.gui;

/**
 * Resize/rotate dialog for a placed photo frame - a drag-select grid instead of +/- steppers: the blue
 * center cell is the block the frame is actually attached to (fixed, always inside any selection - it's
 * physically where the frame is stuck to the world), and dragging from anywhere selects a free rectangle
 * around it - the anchor can end up anywhere within that rectangle (a corner, an edge, dead center),
 * matching how {@link CrazyPhonePhotoFrameEntity#setExtents} lets the slot sit off-center. Releasing the
 * drag commits the size; the grid always opens pre-selected to the frame's CURRENT actual size, so
 * re-opening it shows what's already there rather than resetting. A Rotate button spins the photo 90° -
 * see CrazyPhonePhotoFrameRenderer's own doc comment for what "rotation" actually changes (a visual spin
 * in place, not a width/height axis swap).
 *
 * Grid granularity is whole blocks, not the entity's own finer 1/8-block unit storage - a drag gesture over
 * dozens of tiny sub-block cells would be far fussier to use than it's worth; UNITS_PER_BLOCK is still
 * exactly what gets sent to the server (see #commitSize), so the underlying size storage is untouched.
 * Cell size is computed from the ACTUAL current screen size ({@code Minecraft#getWindow()}'s GUI-scaled
 * width/height, read at construction since AbstractContainerScreen needs imageWidth/imageHeight up front on
 * >=26 - see the two constructors below) rather than a fixed pixel budget, so the panel stays a bounded
 * fraction of the screen regardless of resolution or GUI scale.
 *
 * Extends AbstractContainerScreen (needed so Minecraft#gameMode/the container-close packet machinery works
 * normally) but deliberately skips its background-panel rendering entirely (renderBg on <26, folded into
 * extractContents on >=26 - a real API rename confirmed via the real decompiled AbstractContainerScreen.java,
 * not guessed) and draws its own grid/buttons manually instead, mirroring CrazyPhonePhotoViewerScreen's own
 * established "skip Screen#render's default background pass" pattern (see that class's own doc comment).
 */
import net.minecraft.client.Minecraft;
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
    private static final int GRID_TOP_MARGIN = 34;
    private static final int GRID_BOTTOM_MARGIN = 32;
    private static final int SIDE_MARGIN = 16;
    private static final int MIN_CELL_PX = 6;
    private static final int MAX_CELL_PX = 24;
    // The grid (plus its margins) never claims more than this fraction of the smaller screen dimension -
    // "Grid is too big and should adapt to the screen" (live request).
    private static final double MAX_SCREEN_FRACTION = 0.75;
    private static final int UNITS_PER_BLOCK = CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK;

    private final List<Button> ownButtons = new ArrayList<>();
    private int maxBlocks;
    private int cellPx;
    private int gridLeft, gridTop, gridCells;
    // Selection boundaries in blocks from the anchor cell (0,0) - NOT forced symmetric, see this class's
    // own doc comment. negU/negV are the extent in the negative column/row direction, posU/posV in the
    // positive direction; the anchor itself can sit anywhere inside [-negU,posU]x[-negV,posV], including
    // right on its own edge.
    private int previewNegU, previewPosU, previewNegV, previewPosV;
    private boolean dragging;
    private int dragStartCol, dragStartRow;

    private static int computeMaxBlocks(CrazyPhonePhotoFrameResizeMenu menu) {
        return Math.max(1, menu.maxUnits() / UNITS_PER_BLOCK);
    }

    private static int computeCellPx(int maxBlocks) {
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int budget = (int) (Math.min(guiWidth, guiHeight) * MAX_SCREEN_FRACTION) - GRID_TOP_MARGIN - GRID_BOTTOM_MARGIN;
        int gridCells = maxBlocks * 2 + 1;
        return Math.max(MIN_CELL_PX, Math.min(MAX_CELL_PX, budget / gridCells));
    }

    private static int computeGridPx(CrazyPhonePhotoFrameResizeMenu menu) {
        int maxBlocks = computeMaxBlocks(menu);
        return (maxBlocks * 2 + 1) * computeCellPx(maxBlocks);
    }

    private static int computeImageWidth(CrazyPhonePhotoFrameResizeMenu menu) {
        return Math.max(computeGridPx(menu) + SIDE_MARGIN * 2, 176);
    }

    private static int computeImageHeight(CrazyPhonePhotoFrameResizeMenu menu) {
        return computeGridPx(menu) + GRID_TOP_MARGIN + GRID_BOTTOM_MARGIN;
    }

    //? if <26 {
    public CrazyPhonePhotoFrameResizeScreen(CrazyPhonePhotoFrameResizeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = computeImageWidth(menu);
        this.imageHeight = computeImageHeight(menu);
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
        super(menu, inventory, title, computeImageWidth(menu), computeImageHeight(menu));
    }
    *///?}

    @Override
    protected void init() {
        super.init();
        maxBlocks = computeMaxBlocks(menu);
        gridCells = maxBlocks * 2 + 1;
        cellPx = computeCellPx(maxBlocks);
        int gridPx = gridCells * cellPx;
        gridLeft = this.leftPos + (this.imageWidth - gridPx) / 2;
        gridTop = this.topPos + GRID_TOP_MARGIN;

        previewNegU = unitsToBlocks(menu.negUUnits());
        previewPosU = unitsToBlocks(menu.posUUnits());
        previewNegV = unitsToBlocks(menu.negVUnits());
        previewPosV = unitsToBlocks(menu.posVUnits());

        ownButtons.clear();
        Button rotate = Button.builder(Component.translatable("gui.crazyphone.photo_frame_resize.rotate"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrazyPhonePhotoFrameResizeMenu.ROTATE_BUTTON_ID))
                .bounds(this.leftPos + this.imageWidth / 2 - 40, this.topPos + 6, 80, 20).build();
        addRenderableWidget(rotate);
        ownButtons.add(rotate);
    }

    private static int unitsToBlocks(int units) {
        return Math.max(0, Math.round(units / (float) UNITS_PER_BLOCK));
    }

    private int colAt(double mouseX) {
        return (int) Math.floor((mouseX - gridLeft) / cellPx) - maxBlocks;
    }

    private int rowAt(double mouseY) {
        return (int) Math.floor((mouseY - gridTop) / cellPx) - maxBlocks;
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        int gridPx = gridCells * cellPx;
        return mouseX >= gridLeft && mouseX < gridLeft + gridPx && mouseY >= gridTop && mouseY < gridTop + gridPx;
    }

    // Bounding box of the drag's two endpoints AND the anchor (0,0) - this is what guarantees the anchor
    // always ends up inside the resulting rectangle without ever forcing it to the CENTER of that rectangle
    // ("I should be able to trace a rectangle with the blue square on the bottom left corner for example" -
    // live request): if the whole drag happens on one side of the anchor, the rectangle just extends from
    // the anchor to the cursor, anchor included at its own edge; if the drag straddles the anchor, it ends
    // up somewhere in the interior instead.
    private void updatePreview(int col, int row) {
        int clampedCol = Math.max(-maxBlocks, Math.min(maxBlocks, col));
        int clampedRow = Math.max(-maxBlocks, Math.min(maxBlocks, row));
        int minCol = Math.min(0, Math.min(dragStartCol, clampedCol));
        int maxCol = Math.max(0, Math.max(dragStartCol, clampedCol));
        int minRow = Math.min(0, Math.min(dragStartRow, clampedRow));
        int maxRow = Math.max(0, Math.max(dragStartRow, clampedRow));
        previewNegU = -minCol;
        previewPosU = maxCol;
        previewNegV = -minRow;
        previewPosV = maxRow;
    }

    private void commitSize() {
        int negU = previewNegU * UNITS_PER_BLOCK, posU = previewPosU * UNITS_PER_BLOCK;
        int negV = previewNegV * UNITS_PER_BLOCK, posV = previewPosV * UNITS_PER_BLOCK;
        this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrazyPhonePhotoFrameResizeMenu.encodeAxisU(negU, posU));
        this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrazyPhonePhotoFrameResizeMenu.encodeAxisV(negV, posV));
    }

    // Screen's own mouse-event methods took plain (mouseX, mouseY, button) doubles/int through <26; >=26
    // reworked the whole GuiEventListener contract to pass a single MouseButtonEvent(x, y, buttonInfo)
    // record instead (confirmed against the real decompiled GuiEventListener.java/MouseButtonEvent.java,
    // not guessed) - two full override bodies below rather than trying to bridge one shared shape, same
    // approach every other >=26 API break in this codebase already takes.
    //? if <26 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideGrid(mouseX, mouseY)) {
            dragging = true;
            dragStartCol = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
            dragStartRow = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
            updatePreview(dragStartCol, dragStartRow);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updatePreview(colAt(mouseX), rowAt(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            commitSize();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    //?}
    //? if >=26 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && insideGrid(event.x(), event.y())) {
            dragging = true;
            dragStartCol = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(event.x())));
            dragStartRow = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(event.y())));
            updatePreview(dragStartCol, dragStartRow);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            updatePreview(colAt(event.x()), rowAt(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            commitSize();
            return true;
        }
        return super.mouseReleased(event);
    }
    *///?}

    // ContainerData isn't guaranteed populated the instant this screen opens - the real values arrive via a
    // separate sync packet a moment later, so reading menu.negUUnits() etc only once at init() (or in either
    // constructor) can catch the client's still-zeroed placeholder ContainerData and show "0x0" until the
    // screen is closed and reopened ("ça m'affiche 0x0 indépendamment de la taille enregistrée" - live
    // request). Re-syncing every frame until the user actually starts dragging means the FIRST frame
    // rendered after that sync packet lands self-corrects automatically, and also keeps satisfying "Grid
    // should keep in memory the size and display it by default" if the size changes from elsewhere.
    private void syncFromMenuIfIdle() {
        if (dragging)
            return;
        previewNegU = unitsToBlocks(menu.negUUnits());
        previewPosU = unitsToBlocks(menu.posUUnits());
        previewNegV = unitsToBlocks(menu.negVUnits());
        previewPosV = unitsToBlocks(menu.posVUnits());
    }

    private Component sizeLabel() {
        int widthBlocks = previewNegU + previewPosU, heightBlocks = previewNegV + previewPosV;
        return Component.translatable("gui.crazyphone.photo_frame_resize.size", widthBlocks, heightBlocks);
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromMenuIfIdle();
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.centeredText(this.font, this.title, leftPos + imageWidth / 2, topPos + 8, 0xA0A0A0);
        guiGraphics.centeredText(this.font, sizeLabel(), leftPos + imageWidth / 2, topPos + imageHeight - 16, 0xFFFFFF);
        for (int row = -maxBlocks; row <= maxBlocks; row++) {
            for (int col = -maxBlocks; col <= maxBlocks; col++) {
                int x = gridLeft + (col + maxBlocks) * cellPx;
                int y = gridTop + (row + maxBlocks) * cellPx;
                boolean anchor = col == 0 && row == 0;
                boolean selected = col >= -previewNegU && col <= previewPosU && row >= -previewNegV && row <= previewPosV;
                int fillColor = anchor ? 0xFF3388FF : (selected ? 0x8033AAFF : 0x30FFFFFF);
                guiGraphics.fill(x + 1, y + 1, x + cellPx - 1, y + cellPx - 1, fillColor);
            }
        }
        for (Button button : ownButtons)
            button.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromMenuIfIdle();
        this./*$ gui_render_transparent_background {*/renderTransparentBackground/*$}*/(guiGraphics);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + imageWidth / 2, topPos + 8, 0xA0A0A0);
        guiGraphics.drawCenteredString(this.font, sizeLabel(), leftPos + imageWidth / 2, topPos + imageHeight - 16, 0xFFFFFF);
        for (int row = -maxBlocks; row <= maxBlocks; row++) {
            for (int col = -maxBlocks; col <= maxBlocks; col++) {
                int x = gridLeft + (col + maxBlocks) * cellPx;
                int y = gridTop + (row + maxBlocks) * cellPx;
                boolean anchor = col == 0 && row == 0;
                boolean selected = col >= -previewNegU && col <= previewPosU && row >= -previewNegV && row <= previewPosV;
                int fillColor = anchor ? 0xFF3388FF : (selected ? 0x8033AAFF : 0x30FFFFFF);
                guiGraphics.fill(x + 1, y + 1, x + cellPx - 1, y + cellPx - 1, fillColor);
            }
        }
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}
}
