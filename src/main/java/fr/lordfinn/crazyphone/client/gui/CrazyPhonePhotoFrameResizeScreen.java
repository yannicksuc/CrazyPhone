package fr.lordfinn.crazyphone.client.gui;

/**
 * Resize/rotate dialog for a placed photo frame - a drag-select grid instead of +/- steppers: the blue
 * center cell is the block the frame is actually attached to (fixed, always inside any selection - it's
 * physically where the frame is stuck to the world), and dragging from anywhere OUTSIDE the current
 * selection selects a free rectangle around it - the anchor can end up anywhere within that rectangle (a
 * corner, an edge, dead center), matching how {@link CrazyPhonePhotoFrameEntity#setExtents} lets the slot
 * sit off-center. Dragging from INSIDE the current selection instead SHIFTS it (see #beginGesture/
 * #updateShift) - same size, different position relative to the anchor, since the anchor itself can never
 * move (it's the real block the frame is attached to) - "the center should be movable in the gui... the
 * center is still the same block but selection around it move accordingly" (live request). Releasing
 * either gesture commits the size; the grid always opens pre-selected to the frame's CURRENT actual size,
 * so re-opening it shows what's already there rather than resetting. A Rotate button spins the photo 90° -
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
 * SCREEN-space vs SERVER-space: the fields tracking the drag (previewNegCol/posCol/negRow/posRow) are
 * pure screen directions - col+ is always "right on screen", row+ is always "down on screen", regardless
 * of which face the frame is on. What the entity actually stores (negU/posU/negV/posV, matching
 * CrazyPhonePhotoFrameEntity#computeBoundingBox's own U/V axes) depends on the attach face and, for
 * floor/ceiling, the frame's own rotation - dragging "right" on a SOUTH wall grows the photo toward the
 * viewer's own right, but that's the OPPOSITE world axis from dragging "right" on a NORTH wall. The
 * transform between the two only ever happens at the two points that actually cross the boundary:
 * #commitSize (screen -> server, on drag release) and #screenToPreview (server -> screen, whenever the
 * grid needs to display the frame's current actual size) - see #axisTransform's own doc comment for the
 * derivation ("L'agrandissement dans la grille est inversé... Pour les images placées au plafond et au
 * sol ce n'est pas dépendant de la direction dans laquelle l'image est orientée" - live request).
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
import net.minecraft.core.Direction;
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
    // Selection boundaries in SCREEN directions from the anchor cell (0,0) - col+ is always right on
    // screen, row+ is always down on screen, regardless of face/rotation (see this class's own doc comment
    // for why these are kept separate from the entity's own U/V). NOT forced symmetric: the anchor can sit
    // anywhere inside [-negCol,posCol]x[-negRow,posRow], including right on its own edge.
    private int previewNegCol, previewPosCol, previewNegRow, previewPosRow;
    private boolean dragging;
    private int dragStartCol, dragStartRow;
    // A second drag mode: clicking INSIDE the current selection (rather than anywhere else in the grid)
    // shifts that whole selection, keeping its own SIZE fixed, instead of resizing it - the anchor (0,0)
    // stays exactly where the frame is physically attached in the world (that never moves), but which part
    // of the selection it sits under can - "the center should be movable... the center is still the same
    // block but selection around it move accordingly" (live request). See #updateShift for the clamping
    // that keeps the anchor inside the shifted selection at all times.
    private boolean shifting;
    private int shiftStartCol, shiftStartRow;
    private int shiftBaseNegCol, shiftBasePosCol, shiftBaseNegRow, shiftBasePosRow;

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

        screenToPreview();

        ownButtons.clear();
        Button rotate = Button.builder(Component.translatable("gui.crazyphone.photo_frame_resize.rotate"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrazyPhonePhotoFrameResizeMenu.ROTATE_BUTTON_ID))
                .bounds(this.leftPos + this.imageWidth / 2 - 40, this.topPos + 6, 80, 20).build();
        addRenderableWidget(rotate);
        ownButtons.add(rotate);
    }

    // Which screen direction (col+/right, row+/down) each server axis (U/V) actually corresponds to for the
    // CURRENT face/rotation - the whole reason this transform exists at all. Returns {swap, signU, signV}:
    // swap=0 means U comes from col and V comes from row (the "normal" case); swap=1 means U comes from row
    // and V comes from col instead (a floor/ceiling rotated an odd number of quarter turns). signU/signV are
    // +1 or -1, applied to whichever screen axis feeds that server axis.
    //
    // WALLS (north/south/east/west): "up" on screen always grows the photo UP in the world (V = -row,
    // gravity gives every wall the same vertical reference) - but "right" on screen only grows the photo
    // toward world +X or +Z depending on which way the VIEWER is actually facing that specific wall, which
    // flips between opposite walls. Critical detail an earlier version of this got backwards ("sur les murs
    // gauche et droite sont inversé" - live request): attachFace() is which face of the BLOCK the frame sits
    // on, i.e. the direction the frame's own visible side points TOWARD - a NORTH-attached frame points
    // north, so the viewer looking at it is standing to the north and facing SOUTH, back at the wall, the
    // OPPOSITE of attachFace() itself. Standing in front of a wall and facing it: facing north, east is your
    // right; facing south, west is your right; facing east, south is your right; facing west, north is your
    // right (ordinary compass facts) - applied to face.getOpposite() (the viewer's actual facing), not face
    // itself, then cross-referenced against CrazyPhonePhotoFrameEntity#computeBoundingBox's own fixed
    // U=+X/V=+Y (north/south) or U=+Z/V=+Y (east/west) axis assignment: south and west already agree with
    // "posU = viewer's right" as computeBoundingBox defines it, north and east don't (need signU=-1).
    //
    // FLOOR/CEILING: no gravity reference at all - "up" in the image instead follows the frame's own
    // rotation (CrazyPhonePhotoFrameEntity#rotation(), a 90-degree-per-step visual spin - see
    // CrazyPhonePhotoFrameRenderer's own doc comment). Every extra rotation step turns the screen's own
    // col/row axes by one more quarter turn relative to computeBoundingBox's fixed U=X/V=Z assignment - an
    // ordinary 90-degree rotation of an axis-aligned rectangle is always exactly a swap-plus-sign-flip (never
    // a true diagonal), so this is expressed the same {swap, signU, signV} way as the wall case rather than
    // needing real matrix math.
    private int[] axisTransform() {
        Direction face = menu.attachFace();
        if (face.getAxis() == Direction.Axis.Y) {
            return switch (Math.floorMod(menu.rotation(), 4)) {
                case 0 -> new int[]{0, 1, -1};
                case 1 -> new int[]{1, 1, 1};
                case 2 -> new int[]{0, -1, 1};
                default -> new int[]{1, -1, -1};
            };
        }
        int signU = (face == Direction.NORTH || face == Direction.EAST) ? -1 : 1;
        return new int[]{0, signU, -1};
    }

    private static int[] applySign(int neg, int pos, int sign) {
        return sign > 0 ? new int[]{neg, pos} : new int[]{pos, neg};
    }

    // Screen (col/row) extents -> server (U/V) extents, on drag release - see #axisTransform's own doc
    // comment.
    private int[][] screenToServer() {
        int[] t = axisTransform();
        int[] u, v;
        if (t[0] == 0) {
            u = applySign(previewNegCol, previewPosCol, t[1]);
            v = applySign(previewNegRow, previewPosRow, t[2]);
        } else {
            u = applySign(previewNegRow, previewPosRow, t[1]);
            v = applySign(previewNegCol, previewPosCol, t[2]);
        }
        return new int[][]{u, v};
    }

    // Server (U/V) extents -> screen (col/row) extents, whenever the grid needs to display the frame's
    // current actual size - the structural inverse of #screenToServer (applySign is its own inverse for a
    // given sign, and the swap direction is simply read the other way).
    private void screenToPreview() {
        int[] axisU = axisToBlocks(menu.negUUnits(), menu.posUUnits());
        int[] axisV = axisToBlocks(menu.negVUnits(), menu.posVUnits());
        int[] t = axisTransform();
        int[] col, row;
        if (t[0] == 0) {
            col = applySign(axisU[0], axisU[1], t[1]);
            row = applySign(axisV[0], axisV[1], t[2]);
        } else {
            col = applySign(axisV[0], axisV[1], t[2]);
            row = applySign(axisU[0], axisU[1], t[1]);
        }
        previewNegCol = col[0];
        previewPosCol = col[1];
        previewNegRow = row[0];
        previewPosRow = row[1];
    }

    // Inverse of commitSize's own negU/posU = blocks*UNITS_PER_BLOCK + half formula (see that method's own
    // doc comment for the full derivation) - the anchor's own column always accounts for exactly half a
    // block on each side, so subtracting that back out before dividing is what makes "just the anchor cell"
    // (negUnits=posUnits=4, the default 1x1 size) round-trip to exactly 0 extra columns each side, not 1
    // ("pour une image de 1x1 ça affiche une taille par défaut de 2x2" - an earlier version rounded each
    // side to the nearest WHOLE block independently instead, which doubled a 0.5-block default up to a full
    // block on both sides). A size that DIDN'T originate from this grid (the default symmetric placement, or
    // a Silk-Touch-restored symmetric size - see CrazyPhonePhotoFrameEntity#setSizeUnits) only round-trips
    // exactly here for odd total block counts; even totals are inherently off-grid by half a column under a
    // column-boundary-aligned-to-attachPos grid and just round to the nearest whole column instead.
    private static int[] axisToBlocks(int negUnits, int posUnits) {
        int half = UNITS_PER_BLOCK / 2;
        int negBlocks = Math.max(0, Math.round((negUnits - half) / (float) UNITS_PER_BLOCK));
        int posBlocks = Math.max(0, Math.round((posUnits - half) / (float) UNITS_PER_BLOCK));
        return new int[]{negBlocks, posBlocks};
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
        previewNegCol = -minCol;
        previewPosCol = maxCol;
        previewNegRow = -minRow;
        previewPosRow = maxRow;
    }

    private boolean insideSelection(int col, int row) {
        return col >= -previewNegCol && col <= previewPosCol && row >= -previewNegRow && row <= previewPosRow;
    }

    // Moves the WHOLE selection by the drag delta while keeping its own size fixed (negCol+posCol and
    // negRow+posRow both stay constant) - the anchor (grid cell 0,0, where the frame is actually attached in
    // the world) can never leave the selection, so each axis is clamped to the range that keeps BOTH sides
    // simultaneously within [0, maxBlocks] for that axis's own (fixed) total width.
    private void updateShift(int col, int row) {
        int clampedCol = Math.max(-maxBlocks, Math.min(maxBlocks, col));
        int clampedRow = Math.max(-maxBlocks, Math.min(maxBlocks, row));
        int dCol = clampedCol - shiftStartCol;
        int dRow = clampedRow - shiftStartRow;
        int width = shiftBaseNegCol + shiftBasePosCol;
        int height = shiftBaseNegRow + shiftBasePosRow;
        int negCol = Math.max(Math.max(0, width - maxBlocks), Math.min(Math.min(width, maxBlocks), shiftBaseNegCol - dCol));
        int negRow = Math.max(Math.max(0, height - maxBlocks), Math.min(Math.min(height, maxBlocks), shiftBaseNegRow - dRow));
        previewNegCol = negCol;
        previewPosCol = width - negCol;
        previewNegRow = negRow;
        previewPosRow = height - negRow;
    }

    // previewNegCol/posCol/negRow/posRow count EXTRA cells beyond the anchor's own (0 each = just the anchor
    // cell = 1 block total, not 0 - "sélectionner 2x2 cases devrait résulter en une image de 2x2", "que le
    // centre devrait afficher un cube de 1x1" - live request), so the anchor's own cell always contributes
    // half a block on EACH side, with every whole extra selected cell adding a full block beyond that - see
    // this method's own derivation: a selection spanning grid columns [-previewNegCol, +previewPosCol]
    // (previewNegCol+previewPosCol+1 columns, matching sizeLabel/the highlighted cell count exactly) covers
    // world space from (attachPos - previewNegCol) to (attachPos + previewPosCol + 1) blocks along whichever
    // world axis that screen column currently maps to.
    private void commitSize() {
        int half = UNITS_PER_BLOCK / 2;
        int[][] uv = screenToServer();
        int negU = uv[0][0] * UNITS_PER_BLOCK + half, posU = uv[0][1] * UNITS_PER_BLOCK + half;
        int negV = uv[1][0] * UNITS_PER_BLOCK + half, posV = uv[1][1] * UNITS_PER_BLOCK + half;
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
            int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
            int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
            beginGesture(col, row);
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
        if (shifting) {
            updateShift(colAt(mouseX), rowAt(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging || shifting) {
            dragging = false;
            shifting = false;
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
            int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(event.x())));
            int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(event.y())));
            beginGesture(col, row);
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
        if (shifting) {
            updateShift(colAt(event.x()), rowAt(event.y()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging || shifting) {
            dragging = false;
            shifting = false;
            commitSize();
            return true;
        }
        return super.mouseReleased(event);
    }
    *///?}

    // Clicking INSIDE the current selection shifts it (see #updateShift); clicking anywhere else in the
    // grid starts a fresh resize drag (see #updatePreview) - not version-split itself, called from both
    // mouseClicked bodies above.
    private void beginGesture(int col, int row) {
        if (insideSelection(col, row)) {
            shifting = true;
            shiftStartCol = col;
            shiftStartRow = row;
            shiftBaseNegCol = previewNegCol;
            shiftBasePosCol = previewPosCol;
            shiftBaseNegRow = previewNegRow;
            shiftBasePosRow = previewPosRow;
        } else {
            dragging = true;
            dragStartCol = col;
            dragStartRow = row;
            updatePreview(col, row);
        }
    }

    // ContainerData isn't guaranteed populated the instant this screen opens - the real values arrive via a
    // separate sync packet a moment later, so reading menu.negUUnits() etc only once at init() (or in either
    // constructor) can catch the client's still-zeroed placeholder ContainerData and show "0x0" until the
    // screen is closed and reopened ("ça m'affiche 0x0 indépendamment de la taille enregistrée" - live
    // request). Re-syncing every frame until the user actually starts dragging means the FIRST frame
    // rendered after that sync packet lands self-corrects automatically, and also keeps satisfying "Grid
    // should keep in memory the size and display it by default" if the size changes from elsewhere.
    private void syncFromMenuIfIdle() {
        if (dragging || shifting)
            return;
        screenToPreview();
    }

    // +1: previewNegCol/posCol count EXTRA cells beyond the anchor's own - the anchor's cell always counts
    // for one block by itself, matching the highlighted cell count and commitSize's own math (see that
    // method's doc comment). Deliberately shown as screen col/row counts, not server U/V - "2 columns wide
    // on screen" and "2 blocks wide" mean the same thing to whoever's looking at the grid regardless of
    // which server axis that ends up being.
    private Component sizeLabel() {
        int widthBlocks = previewNegCol + previewPosCol + 1, heightBlocks = previewNegRow + previewPosRow + 1;
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
                boolean selected = col >= -previewNegCol && col <= previewPosCol && row >= -previewNegRow && row <= previewPosRow;
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
                boolean selected = col >= -previewNegCol && col <= previewPosCol && row >= -previewNegRow && row <= previewPosRow;
                int fillColor = anchor ? 0xFF3388FF : (selected ? 0x8033AAFF : 0x30FFFFFF);
                guiGraphics.fill(x + 1, y + 1, x + cellPx - 1, y + cellPx - 1, fillColor);
            }
        }
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}
}
