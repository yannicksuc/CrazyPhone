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
 * Grid selection precision is HALF a block, not the whole-block granularity this screen used to be limited
 * to, nor the entity's own finer 1/8-block unit storage (a plain drag gesture over dozens of tiny sub-block
 * cells would still be far fussier to use than it's worth, so the ordinary area-drag - click anywhere that
 * isn't the current selection or a corner handle - still snaps to whole blocks). The two named corner
 * handles (see #tryBeginCornerDrag/#updateCornerDrag) are the one exception: "also want top left and bottom
 * left corners to be dragable also in mid positioning" (live request), each a small square centered exactly
 * on its corner point at half a normal cell's size - "red and yellow corner handle are squares centered on
 * corner with half the size" (live request, red = top-left, yellow = the diagonally OPPOSITE corner,
 * bottom-right - "yellow corner should be at the opposite of red", live request) - offering half-block
 * resolution specifically there. Because a selection edge can now land mid-cell, plain whole-cell shading
 * would lie about what's actually selected, so every non-anchor cell renders as two independently-colored
 * halves along whichever axis a handle can move - "the gray square can be divided in quarters for
 * independent coloration based on selection" (live request) - each surviving cell corner-carved on both
 * axes ends up an ordinary 2x2 quarter grid. The anchor cell itself is never split (both its halves are
 * always selected by construction - it's the block the frame is attached to) and stays the plain solid blue
 * square it always was. UNITS_PER_BLOCK is still exactly what gets sent to the server (see #commitSize), so
 * the underlying entity-side size storage is untouched - only this screen's own selection resolution moved
 * from whole blocks to half blocks.
 *
 * Cell size is computed from the ACTUAL current screen size ({@code Minecraft#getWindow()}'s GUI-scaled
 * width/height, read at construction since AbstractContainerScreen needs imageWidth/imageHeight up front on
 * >=26 - see the two constructors below) rather than a fixed pixel budget, so the panel stays a bounded
 * fraction of the screen regardless of resolution or GUI scale. Cell pixel size is always forced EVEN (see
 * #computeCellPx) so halving it for the half-block grid never loses a pixel to rounding.
 *
 * SCREEN-space vs SERVER-space: the fields tracking the selection (previewNegCol/posCol/negRow/posRow) are
 * pure screen directions in HALF-BLOCK units - col+ is always "right on screen", row+ is always "down on
 * screen", regardless of which face the frame is on. What the entity actually stores (negU/posU/negV/posV,
 * matching CrazyPhonePhotoFrameEntity#computeBoundingBox's own U/V axes) depends on the attach face and,
 * for floor/ceiling, the frame's own rotation - dragging "right" on a SOUTH wall grows the photo toward the
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
    // Half a block, in the entity's own 1/8-block unit storage - the amount each half-unit step in this
    // screen's own selection (previewNegCol etc, see this class's own doc comment) is worth once sent to
    // the server. See #commitSize/#axisToHalf for the exact formula this feeds into.
    private static final int HALF_STEP = UNITS_PER_BLOCK / 2;
    private static final int HANDLE_COLOR_TOP_LEFT = 0xFFFF3333;
    private static final int HANDLE_COLOR_BOTTOM_RIGHT = 0xFFFFDD33;

    private static final int CORNER_NONE = 0;
    private static final int CORNER_TOP_LEFT = 1;
    private static final int CORNER_BOTTOM_RIGHT = 2;

    private final List<Button> ownButtons = new ArrayList<>();
    private int maxBlocks;
    // maxBlocks expressed in half-units - the bound every half-unit field below is clamped to.
    private int maxHalf;
    private int cellPx;
    // Half of cellPx - always exact, #computeCellPx forces cellPx even for exactly this reason.
    private int halfCellPx;
    private int gridLeft, gridTop, gridCells;
    // Selection boundaries in SCREEN directions from the anchor cell (0,0), in HALF-BLOCK units - col+ is
    // always right on screen, row+ is always down on screen, regardless of face/rotation (see this class's
    // own doc comment for why these are kept separate from the entity's own U/V). The anchor's own two
    // halves are implicit (not counted here) - a fully-default selection is previewNegCol=previewPosCol=
    // previewNegRow=previewPosRow=0, matching just the anchor cell, 1 block total. NOT forced symmetric:
    // the anchor can sit anywhere inside the resulting rectangle, including right on its own edge.
    private int previewNegCol, previewPosCol, previewNegRow, previewPosRow;
    private boolean dragging;
    private int dragStartCol, dragStartRow;
    // A second drag mode: clicking INSIDE the current selection (rather than anywhere else in the grid)
    // shifts that whole selection, keeping its own SIZE fixed, instead of resizing it - the anchor (0,0)
    // stays exactly where the frame is physically attached in the world (that never moves), but which part
    // of the selection it sits under can - "the center should be movable... the center is still the same
    // block but selection around it move accordingly" (live request). See #updateShift for the clamping
    // that keeps the anchor inside the shifted selection at all times. Still whole-block, not half-block -
    // shifting only moves position, never touches size, so any existing half-block fraction rides along
    // unchanged regardless of the step granularity used to move it.
    private boolean shifting;
    private int shiftStartCol, shiftStartRow;
    private int shiftBaseNegCol, shiftBasePosCol, shiftBaseNegRow, shiftBasePosRow;
    // A third drag mode: grabbing one of the two corner handles (see this class's own doc comment) resizes
    // at half-block resolution instead of whole-block, moving only the two edges that meet at that corner.
    private int cornerDrag = CORNER_NONE;

    private static int computeMaxBlocks(CrazyPhonePhotoFrameResizeMenu menu) {
        return Math.max(1, menu.maxUnits() / UNITS_PER_BLOCK);
    }

    // Forced even so halving it for the half-block grid (see #halfCellPx) never loses a pixel to rounding.
    private static int computeCellPx(int maxBlocks) {
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int budget = (int) (Math.min(guiWidth, guiHeight) * MAX_SCREEN_FRACTION) - GRID_TOP_MARGIN - GRID_BOTTOM_MARGIN;
        int gridCells = maxBlocks * 2 + 1;
        int cellPx = Math.max(MIN_CELL_PX, Math.min(MAX_CELL_PX, budget / gridCells));
        return cellPx - (cellPx % 2);
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
        maxHalf = maxBlocks * 2;
        gridCells = maxBlocks * 2 + 1;
        cellPx = computeCellPx(maxBlocks);
        halfCellPx = cellPx / 2;
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
    // comment. Values here are HALF-block units on both sides of the transform (a swap-plus-sign-flip is
    // unit-agnostic, so this needed no change when the grid moved from whole-block to half-block precision).
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
        int[] axisU = axisToHalf(menu.negUUnits(), menu.posUUnits());
        int[] axisV = axisToHalf(menu.negVUnits(), menu.posVUnits());
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

    // Inverse of commitSize's own negU/posU = (half+1)*HALF_STEP formula (see that method's own doc
    // comment for the full derivation) - the anchor's own column always accounts for exactly one half-block
    // on each side by itself, so subtracting that back out before dividing is what makes "just the anchor
    // cell" (negUnits=posUnits=HALF_STEP, the default 1x1 size) round-trip to exactly 0 extra half-blocks
    // each side, not 1 ("pour une image de 1x1 ça affiche une taille par défaut de 2x2" - an earlier version
    // rounded each side to the nearest WHOLE block independently instead, which doubled a half-block default
    // up to a full block on both sides). A size that DIDN'T originate from this grid's own half-block
    // formula (the default symmetric placement, or a Silk-Touch-restored symmetric size - see
    // CrazyPhonePhotoFrameEntity#setSizeUnits) only round-trips exactly here when it already lands on a
    // half-block boundary; anything finer just rounds to the nearest half-block instead.
    private static int[] axisToHalf(int negUnits, int posUnits) {
        int negHalf = Math.max(0, Math.round(negUnits / (float) HALF_STEP) - 1);
        int posHalf = Math.max(0, Math.round(posUnits / (float) HALF_STEP) - 1);
        return new int[]{negHalf, posHalf};
    }

    private int colAt(double mouseX) {
        return (int) Math.floor((mouseX - gridLeft) / cellPx) - maxBlocks;
    }

    private int rowAt(double mouseY) {
        return (int) Math.floor((mouseY - gridTop) / cellPx) - maxBlocks;
    }

    // Half-block equivalents of #colAt/#rowAt - only ever used to drive the two corner handles (see
    // #updateCornerDrag), since ordinary area-dragging still snaps to whole blocks. signedHalfCol=0/1 are
    // the anchor cell's own two halves (its left/neg and right/pos half respectively), matching how
    // previewNegCol/previewPosCol already exclude the anchor's own implicit halves - see this class's own
    // doc comment.
    private int halfColAt(double mouseX) {
        return (int) Math.floor((mouseX - gridLeft) / halfCellPx) - maxHalf;
    }

    private int halfRowAt(double mouseY) {
        return (int) Math.floor((mouseY - gridTop) / halfCellPx) - maxHalf;
    }

    private int clampHalf(int v) {
        return Math.max(-maxHalf, Math.min(maxHalf, v));
    }

    private int clampHalfExtent(int v) {
        return Math.max(0, Math.min(maxHalf, v));
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
    // up somewhere in the interior instead. Still whole-block (col/row are block indices, from #colAt/
    // #rowAt) - only the two corner handles get half-block precision - so the result is doubled into
    // half-block units at the very end to match previewNegCol/etc's own storage unit.
    private void updatePreview(int col, int row) {
        int clampedCol = Math.max(-maxBlocks, Math.min(maxBlocks, col));
        int clampedRow = Math.max(-maxBlocks, Math.min(maxBlocks, row));
        int minCol = Math.min(0, Math.min(dragStartCol, clampedCol));
        int maxCol = Math.max(0, Math.max(dragStartCol, clampedCol));
        int minRow = Math.min(0, Math.min(dragStartRow, clampedRow));
        int maxRow = Math.max(0, Math.max(dragStartRow, clampedRow));
        previewNegCol = -minCol * 2;
        previewPosCol = maxCol * 2;
        previewNegRow = -minRow * 2;
        previewPosRow = maxRow * 2;
    }

    // Whole-block-cell overlap test (col/row are block indices, from #colAt/#rowAt) - true if that block's
    // own half-block range intersects the current (possibly half-block-fractional) selection at all, so
    // clicking anywhere on a partially-selected cell (see this class's own doc comment on quarter shading)
    // still starts a shift rather than a fresh resize.
    private boolean insideSelection(int col, int row) {
        int halfMin = 2 * col, halfMax = 2 * col + 1;
        boolean overlapCol = halfMax >= -previewNegCol && halfMin <= previewPosCol + 1;
        int rowMin = 2 * row, rowMax = 2 * row + 1;
        boolean overlapRow = rowMax >= -previewNegRow && rowMin <= previewPosRow + 1;
        return overlapCol && overlapRow;
    }

    // Moves the WHOLE selection by the drag delta while keeping its own size fixed (negCol+posCol and
    // negRow+posRow both stay constant, even if that size is half-block-fractional from a corner-handle
    // drag) - the anchor (grid cell 0,0, where the frame is actually attached in the world) can never leave
    // the selection, so each axis is clamped to the range that keeps BOTH sides simultaneously within
    // [0, maxHalf] for that axis's own (fixed) total width. col/row are still whole-block indices (from
    // #colAt/#rowAt) - shifting only moves position, never touches size, so it stays whole-block-stepped
    // like the ordinary area-drag; the delta is doubled into half-block units to match previewNegCol/etc.
    private void updateShift(int col, int row) {
        int clampedCol = Math.max(-maxBlocks, Math.min(maxBlocks, col));
        int clampedRow = Math.max(-maxBlocks, Math.min(maxBlocks, row));
        int dCol = (clampedCol - shiftStartCol) * 2;
        int dRow = (clampedRow - shiftStartRow) * 2;
        int width = shiftBaseNegCol + shiftBasePosCol;
        int height = shiftBaseNegRow + shiftBasePosRow;
        int negCol = Math.max(Math.max(0, width - maxHalf), Math.min(Math.min(width, maxHalf), shiftBaseNegCol - dCol));
        int negRow = Math.max(Math.max(0, height - maxHalf), Math.min(Math.min(height, maxHalf), shiftBaseNegRow - dRow));
        previewNegCol = negCol;
        previewPosCol = width - negCol;
        previewNegRow = negRow;
        previewPosRow = height - negRow;
    }

    // Drags the top-left corner of the selection (see this class's own doc comment) - moves the left edge
    // (negCol) and top edge (negRow) together, at half-block resolution, leaving the opposite (bottom-right)
    // corner fixed. Both edges are independently clamped to [0, maxHalf] - the anchor's own cell can never
    // be pushed out of the selection since neither edge can go negative.
    // Drags the bottom-right corner instead - right edge (posCol) and bottom edge (posRow) together, the
    // corner diagonally OPPOSITE the top-left/red one ("yellow corner should be at the opposite of red" -
    // live request), leaving the top-left corner fixed.
    private void updateCornerDrag(double mouseX, double mouseY) {
        int hc = clampHalf(halfColAt(mouseX));
        int hr = clampHalf(halfRowAt(mouseY));
        if (cornerDrag == CORNER_TOP_LEFT) {
            previewNegCol = clampHalfExtent(-hc);
            previewNegRow = clampHalfExtent(-hr);
        } else if (cornerDrag == CORNER_BOTTOM_RIGHT) {
            previewPosCol = clampHalfExtent(hc - 2);
            previewPosRow = clampHalfExtent(hr - 2);
        }
    }

    // Pixel box of the top-left/bottom-right corner handles - a square centered exactly on the selection's
    // own corner point, sized half a normal cell ("red and yellow corner handle are squares centered on
    // corner with half the size" - live request). The two handles sit on diagonally opposite corners of the
    // selection rectangle, each independently draggable, each moving only the two edges that meet there.
    private int[] topLeftHandleBox() {
        int px = gridLeft + (-previewNegCol + maxHalf) * halfCellPx;
        int py = gridTop + (-previewNegRow + maxHalf) * halfCellPx;
        int r = halfCellPx / 2;
        return new int[]{px - r, py - r, px + r, py + r};
    }

    private int[] bottomRightHandleBox() {
        int px = gridLeft + (previewPosCol + 2 + maxHalf) * halfCellPx;
        int py = gridTop + (previewPosRow + 2 + maxHalf) * halfCellPx;
        int r = halfCellPx / 2;
        return new int[]{px - r, py - r, px + r, py + r};
    }

    private static boolean hitBox(int[] box, double mouseX, double mouseY) {
        return mouseX >= box[0] && mouseX < box[2] && mouseY >= box[1] && mouseY < box[3];
    }

    // Checked BEFORE the ordinary shift-vs-resize dispatch (#beginGesture) in both mouseClicked bodies below
    // - the handles sit on top of the grid and take priority over whatever whole-block cell happens to be
    // underneath them.
    private boolean tryBeginCornerDrag(double mouseX, double mouseY) {
        if (hitBox(topLeftHandleBox(), mouseX, mouseY)) {
            cornerDrag = CORNER_TOP_LEFT;
            return true;
        }
        if (hitBox(bottomRightHandleBox(), mouseX, mouseY)) {
            cornerDrag = CORNER_BOTTOM_RIGHT;
            return true;
        }
        return false;
    }

    // previewNegCol/posCol/negRow/posRow count EXTRA half-blocks beyond the anchor's own (0 each = just the
    // anchor cell = 1 block total, not 0 - "sélectionner 2x2 cases devrait résulter en une image de 2x2",
    // "que le centre devrait afficher un cube de 1x1" - live request), so the anchor's own cell always
    // contributes one half-block on EACH side, with every extra half-block selected adding HALF_STEP units
    // beyond that - see this method's own derivation: a selection spanning grid columns [-previewNegCol,
    // +previewPosCol] in half-block units covers world space from (attachPos - previewNegCol/2) to
    // (attachPos + previewPosCol/2 + 1) blocks along whichever world axis that screen column currently maps
    // to.
    private void commitSize() {
        int[][] uv = screenToServer();
        int negU = (uv[0][0] + 1) * HALF_STEP, posU = (uv[0][1] + 1) * HALF_STEP;
        int negV = (uv[1][0] + 1) * HALF_STEP, posV = (uv[1][1] + 1) * HALF_STEP;
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
            if (tryBeginCornerDrag(mouseX, mouseY))
                return true;
            int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
            int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
            beginGesture(col, row);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (cornerDrag != CORNER_NONE) {
            updateCornerDrag(mouseX, mouseY);
            return true;
        }
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
        if (dragging || shifting || cornerDrag != CORNER_NONE) {
            dragging = false;
            shifting = false;
            cornerDrag = CORNER_NONE;
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
            if (tryBeginCornerDrag(event.x(), event.y()))
                return true;
            int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(event.x())));
            int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(event.y())));
            beginGesture(col, row);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (cornerDrag != CORNER_NONE) {
            updateCornerDrag(event.x(), event.y());
            return true;
        }
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
        if (dragging || shifting || cornerDrag != CORNER_NONE) {
            dragging = false;
            shifting = false;
            cornerDrag = CORNER_NONE;
            commitSize();
            return true;
        }
        return super.mouseReleased(event);
    }
    *///?}

    // Clicking INSIDE the current selection shifts it (see #updateShift); clicking anywhere else in the
    // grid starts a fresh resize drag (see #updatePreview) - not version-split itself, called from both
    // mouseClicked bodies above (after #tryBeginCornerDrag has already had first refusal on the corner
    // handles).
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
        if (dragging || shifting || cornerDrag != CORNER_NONE)
            return;
        screenToPreview();
    }

    // +2 (not +1): previewNegCol/posCol/negRow/posRow count EXTRA half-blocks beyond the anchor's own two
    // implicit halves (see this class's own doc comment), so the total in half-blocks is always even for a
    // whole-block selection and odd for one with a corner-handle half-block fraction on it. Deliberately
    // shown as screen col/row counts, not server U/V - "2 columns wide on screen" and "2 blocks wide" mean
    // the same thing to whoever's looking at the grid regardless of which server axis that ends up being.
    private static String formatHalfBlocks(int half) {
        int whole = half / 2;
        return half % 2 == 0 ? Integer.toString(whole) : (whole + ".5");
    }

    private Component sizeLabel() {
        int widthHalf = previewNegCol + previewPosCol + 2, heightHalf = previewNegRow + previewPosRow + 2;
        return Component.translatable("gui.crazyphone.photo_frame_resize.size", formatHalfBlocks(widthHalf), formatHalfBlocks(heightHalf));
    }

    // Renders the grid: the anchor cell (0,0) is always a single solid blue square (both its halves are
    // always selected by construction, so splitting it would show nothing new). Every other cell is drawn as
    // an independent 2x2 quarter grid - each quarter (qc,qr in {0,1}, left/right and top/bottom half) is
    // colored on its own selection state, since a corner-handle drag can now leave a selection edge sitting
    // mid-cell ("the gray square can be divided in quarters for independent coloration based on selection" -
    // live request). Only the OUTER edge of each whole cell gets the usual 1px gap from its neighbors -
    // the internal seam between a cell's own two quarters is left flush so same-colored halves visually
    // merge into one shape.
    private void drawGrid(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        for (int row = -maxBlocks; row <= maxBlocks; row++) {
            for (int col = -maxBlocks; col <= maxBlocks; col++) {
                int cellX = gridLeft + (col + maxBlocks) * cellPx;
                int cellY = gridTop + (row + maxBlocks) * cellPx;
                if (col == 0 && row == 0) {
                    guiGraphics.fill(cellX + 1, cellY + 1, cellX + cellPx - 1, cellY + cellPx - 1, 0xFF3388FF);
                    continue;
                }
                for (int qr = 0; qr < 2; qr++) {
                    int halfRow = 2 * row + qr;
                    boolean rowSelected = halfRow >= -previewNegRow && halfRow <= previewPosRow + 1;
                    for (int qc = 0; qc < 2; qc++) {
                        int halfCol = 2 * col + qc;
                        boolean colSelected = halfCol >= -previewNegCol && halfCol <= previewPosCol + 1;
                        boolean selected = rowSelected && colSelected;
                        int x0 = cellX + qc * halfCellPx + (qc == 0 ? 1 : 0);
                        int x1 = cellX + (qc + 1) * halfCellPx - (qc == 1 ? 1 : 0);
                        int y0 = cellY + qr * halfCellPx + (qr == 0 ? 1 : 0);
                        int y1 = cellY + (qr + 1) * halfCellPx - (qr == 1 ? 1 : 0);
                        guiGraphics.fill(x0, y0, x1, y1, selected ? 0x8033AAFF : 0x30FFFFFF);
                    }
                }
            }
        }
        int[] topLeft = topLeftHandleBox();
        int[] bottomRight = bottomRightHandleBox();
        guiGraphics.fill(topLeft[0], topLeft[1], topLeft[2], topLeft[3], HANDLE_COLOR_TOP_LEFT);
        guiGraphics.fill(bottomRight[0], bottomRight[1], bottomRight[2], bottomRight[3], HANDLE_COLOR_BOTTOM_RIGHT);
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromMenuIfIdle();
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.centeredText(this.font, this.title, leftPos + imageWidth / 2, topPos + 8, 0xA0A0A0);
        guiGraphics.centeredText(this.font, sizeLabel(), leftPos + imageWidth / 2, topPos + imageHeight - 16, 0xFFFFFF);
        drawGrid(guiGraphics);
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
        drawGrid(guiGraphics);
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}
}
