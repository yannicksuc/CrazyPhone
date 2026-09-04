package fr.lordfinn.crazyphone.client.gui;

/**
 * Resize/rotate dialog for a placed photo frame - a drag-select grid instead of +/- steppers: the blue
 * center cell is the block the frame is actually attached to (fixed, always inside any selection - it's
 * physically where the frame is stuck to the world). Clicking anywhere INSIDE the current selection - the
 * blue anchor square included - SHIFTS the whole selection (see #beginGesture/#updateShift), keeping its
 * SIZE fixed, since the anchor itself can never move (it's the real block the frame is attached to) - "the
 * center should be movable in the gui... the center is still the same block but selection around it move
 * accordingly" (live request). Clicking anywhere OUTSIDE the current selection instead grabs whichever of
 * the selection's four corners is nearest and starts RESIZING from there, leaving the opposite corner
 * exactly where it was (#beginNearestCornerDrag) - "in this position when i click in the gray area above
 * the red handle, the yellow handle move to the blue square... the yellow should not move in this
 * situation" (live request): an earlier version instead traced a brand new rectangle from the anchor to the
 * click, discarding the far corner back down to the anchor's own edge, which is exactly the bug reported
 * there. Shifting is HALF-block resolution throughout, same as every resize gesture in this screen - an
 * earlier version only gave half-block precision to a click specifically ON the anchor square and kept the
 * general click-anywhere-inside-selection shift whole-block, but that split just made the anchor square's
 * own hit area get stolen by the neighboring corner handles ("je peux pas bouger le centre de l'image... à
 * cause sûrement de la sélection qui trigger avant" - live request) without even giving the general case
 * what was wanted ("la sélection ne se déplace que sur des full cube pas des entre cube quand je la
 * déplace" - live request), so there is now exactly ONE shift mode, used identically no matter which cell
 * inside the selection is grabbed. Releasing any gesture commits the size; the grid always opens
 * pre-selected to the frame's CURRENT actual size, so re-opening it shows what's already there rather than
 * resetting. A Rotate button spins the photo 90° - see CrazyPhonePhotoFrameRenderer's own doc comment for
 * what "rotation" actually changes (a visual spin in place, not a width/height axis swap).
 *
 * Grid selection precision is HALF a block EVERYWHERE - the shift, starting a fresh selection, and both
 * corner-resize gestures below - not the whole-block granularity this screen used to be limited to, nor the
 * entity's own finer 1/8-block unit storage (a plain drag gesture over dozens of tiny sub-block cells would
 * still be far fussier to use than it's worth, so the STEP is half a block, never finer). The two named
 * corner handles (see #tryBeginCornerDrag/#updateCornerDrag) are a distinct resize gesture from the shift:
 * "also want top left and bottom left corners to be dragable also in mid positioning" (live request), each
 * a small square centered exactly on its corner point at half a normal cell's size - "red and yellow corner
 * handle are squares centered on corner with half the size" (live request, red = top-left, yellow = the
 * diagonally OPPOSITE corner, bottom-right - "yellow corner should be at the opposite of red", live
 * request), with a hit-test margin beyond that visual size so grabbing one doesn't need to be pixel-perfect
 * (see #HANDLE_HIT_MARGIN, live request) - applied ONLY on the two sides facing away from the selection, so
 * it can never eat into a cell the shift gesture still needs (see #hitTopLeftHandle/#hitBottomRightHandle's
 * own doc comment). #beginNearestCornerDrag is the more general form of the same gesture - it jump-grabs
 * whichever of the CURRENT selection's four logical corners (not just the two drawn ones -
 * #cornerDragCol/#cornerDragRow track each axis independently, so any combination works) is nearest to a
 * click, and starts dragging it from there - it's what every click OUTSIDE the selection uses (single click
 * suffices there - "si hors sélection, un clique simple suffit", live request) and what a DOUBLE-click on
 * any cell uses too, as a more forgiving alternative to grabbing a handle precisely even while still inside
 * the selection ("un double clique sur une case... doit faire bouger le corner le plus proche" - live
 * request). Because a selection edge can now land mid-cell, plain whole-cell shading
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
import fr.lordfinn.crazyphone.client.CursorEffects;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePhotoFrameResizeMenu;

import java.util.ArrayList;
import java.util.List;

public class CrazyPhonePhotoFrameResizeScreen extends AbstractContainerScreen<CrazyPhonePhotoFrameResizeMenu> {
    // Just the title now - the Rotate button used to also live in this strip, directly on top of the title
    // text ("the rotate button is on top of the Resize photo title" - live request), since both were
    // centered at the same X and its own bounds (topPos+6..+26) overlapped the title's own Y (topPos+8).
    // It's been moved into a hotbar row in the BOTTOM margin instead (see #HOTBAR_BUTTON_SIZE/#init), which
    // is why this shrank from the old 34.
    private static final int GRID_TOP_MARGIN = 20;
    // Room for the hotbar row (see #HOTBAR_BUTTON_SIZE) plus the existing size label below it.
    private static final int GRID_BOTTOM_MARGIN = 42;
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

    // Which side of an axis a corner-style drag is currently moving - NONE (that axis's edges are untouched
    // by this drag), NEG (the -negCol/-negRow side), POS (the +posCol/+posRow side). Two independent values
    // (one per axis, see #cornerDragCol/#cornerDragRow) rather than a single "which named corner" enum -
    // that's what lets #beginNearestCornerDrag pick any of the four logical corners (top-left, top-right,
    // bottom-left, bottom-right) even though only two are drawn as named handles.
    private static final int EDGE_NONE = 0;
    private static final int EDGE_NEG = 1;
    private static final int EDGE_POS = 2;

    // The grid's DISPLAY size (how many cells are drawn, how big each cell renders) is deliberately
    // decoupled from how big a selection is actually allowed to grow - "Wtf did you do i don't wanted the
    // gui grid to be bigger but the max authorized" / "i want a clamped limit of 32x32" / "the grid
    // displayed before was great" / "if they are linked, you need to separate them" (live request, all
    // four). Raising Config#maxPhotoFrameSizeBlocks used to directly enlarge #maxBlocks, which fed BOTH the
    // rendered grid's own cell count/size (#computeCellPx, #drawGrid's loop bounds) AND the actual clamp
    // ceiling (#clampHalf/#clampHalfExtent) - at the new 32-block ceiling that shrank every cell down to
    // #MIN_CELL_PX, making even the always-selected blue anchor square nearly unclickable (its own hit area
    // is a couple pixels, easily swallowed by the neighboring corner handles' hit margin). #maxBlocks/
    // #maxHalf now stay a small, comfortable DISPLAY bound (see #DISPLAY_MAX_BLOCKS_CAP) used for every
    // pixel/layout computation, while #limitHalf alone carries the real, possibly much larger, clamp
    // ceiling - a selection can still grow past what's drawn (see #updateShift/#updateCornerDrag/
    // #beginNearestCornerDrag, which all clamp against #limitHalf, not #maxHalf); it just won't have live cell
    // shading out there since nothing this small was ever meant to render 65 cells across.
    private static final int DISPLAY_MAX_BLOCKS_CAP = 8;

    private final List<Button> ownButtons = new ArrayList<>();
    // DISPLAY bound - drives every pixel/layout computation (cell count, cell size, gridLeft/Top,
    // colAt/rowAt/halfColAt/halfRowAt's own coordinate origin, drawGrid's loop range). See this class's own
    // field-level doc comment above for why this is capped independently of the real selection limit.
    private int maxBlocks;
    // maxBlocks expressed in half-units - same DISPLAY-only role, used for handle pixel positioning
    // (#topLeftHandleBox/#bottomRightHandleBox) and as the coordinate origin for #halfColAt/#halfRowAt.
    private int maxHalf;
    // The REAL clamp ceiling, in half-units, straight from Config#maxPhotoFrameSizeBlocks - the only field
    // #clampHalf/#clampHalfExtent (and so every resize/shift gesture) actually bounds against.
    private int limitHalf;
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
    // A second drag mode: clicking INSIDE the current selection (rather than anywhere else in the grid,
    // including right on the blue anchor square itself) shifts that whole selection, keeping its own SIZE
    // fixed, instead of resizing it - the anchor (0,0) stays exactly where the frame is physically attached
    // in the world (that never moves), but which part of the selection it sits under can - "the center
    // should be movable... the center is still the same block but selection around it move accordingly"
    // (live request). See #updateShift for the clamping that keeps the anchor inside the shifted selection
    // at all times. HALF-block resolution, using raw mouse position (#halfColAt/#halfRowAt) rather than a
    // block index - an earlier version of this was whole-block-only and dispatched to a DIFFERENT half-block
    // mode specifically when the anchor square itself was grabbed, but that split made the anchor square
    // itself nearly unclickable in practice (its own hit-test lost out to the corner handles' - "je peux pas
    // bouger le centre de l'image... à cause sûrement de la sélection qui trigger avant" - live request) and
    // didn't even give the ordinary case what was actually wanted ("la sélection ne se déplace que sur des
    // full cube pas des entre cube quand je la déplace" - live request) - so this is now the ONE shift mode,
    // used identically regardless of which cell inside the selection you grab.
    private boolean shifting;
    private int shiftStartHalfCol, shiftStartHalfRow;
    private int shiftBaseNegCol, shiftBasePosCol, shiftBaseNegRow, shiftBasePosRow;
    // A third drag mode: grabbing one of the two named corner handles (#tryBeginCornerDrag), OR
    // double-clicking any cell to jump-grab whichever corner of the CURRENT selection is nearest
    // (#beginNearestCornerDrag - "un double clique sur une case, même dans la sélection, doit faire bouger
    // le corner le plus proche... attention ne pas bouger l'autre coin" - live request), resizes at
    // half-block resolution, moving only the two edges (one from {negCol,posCol}, one from {negRow,posRow})
    // that meet at that corner - the opposite two edges never move on their own.
    private int cornerDragCol = EDGE_NONE, cornerDragRow = EDGE_NONE;

    private boolean isCornerDragging() {
        return cornerDragCol != EDGE_NONE || cornerDragRow != EDGE_NONE;
    }

    // Manual double-click detection, used only by the <26 mouseClicked body below - >=26's own
    // MouseButtonEvent-based mouseClicked already receives a doubleClick flag straight from the input
    // system (see the version-split bodies). Two clicks count as a double-click if they land in the same
    // grid CELL within DOUBLE_CLICK_MS, matching vanilla's own inventory double-click window.
    private static final long DOUBLE_CLICK_MS = 250;
    private long lastClickTimeMs = -1;
    private int lastClickCol = Integer.MIN_VALUE, lastClickRow = Integer.MIN_VALUE;

    private boolean consumeDoubleClick(double mouseX, double mouseY) {
        int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
        int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
        long now = System.currentTimeMillis();
        boolean isDouble = lastClickTimeMs >= 0 && now - lastClickTimeMs <= DOUBLE_CLICK_MS
                && col == lastClickCol && row == lastClickRow;
        lastClickTimeMs = isDouble ? -1 : now;
        lastClickCol = col;
        lastClickRow = row;
        return isDouble;
    }

    // The REAL clamp ceiling - straight from Config#maxPhotoFrameSizeBlocks via the menu, uncapped. Only
    // ever feeds #limitHalf; never used for layout.
    private static int computeLimitMaxBlocks(CrazyPhonePhotoFrameResizeMenu menu) {
        return Math.max(1, menu.maxUnits() / UNITS_PER_BLOCK);
    }

    // The DISPLAY bound - see this class's own field-level doc comment on #DISPLAY_MAX_BLOCKS_CAP for why
    // this is capped independently of (and can be much smaller than) the real limit above.
    private static int computeDisplayMaxBlocks(CrazyPhonePhotoFrameResizeMenu menu) {
        return Math.min(DISPLAY_MAX_BLOCKS_CAP, computeLimitMaxBlocks(menu));
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
        int maxBlocks = computeDisplayMaxBlocks(menu);
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
        maxBlocks = computeDisplayMaxBlocks(menu);
        maxHalf = maxBlocks * 2;
        limitHalf = computeLimitMaxBlocks(menu) * 2;
        gridCells = maxBlocks * 2 + 1;
        cellPx = computeCellPx(maxBlocks);
        halfCellPx = cellPx / 2;
        int gridPx = gridCells * cellPx;
        gridLeft = this.leftPos + (this.imageWidth - gridPx) / 2;
        gridTop = this.topPos + GRID_TOP_MARGIN;

        screenToPreview();

        // A row of small square icon buttons below the grid, currently just Rotate - "I would prefere a
        // hotbar bellow with different square buttons, icons... and a tooltip on hover" (live request).
        // Centered under the grid; a second button would just need HOTBAR_BUTTON_SIZE+HOTBAR_BUTTON_GAP
        // added per slot, same row.
        ownButtons.clear();
        int hotbarY = gridTop + gridPx + 4;
        int hotbarX = this.leftPos + this.imageWidth / 2 - HOTBAR_BUTTON_SIZE / 2;
        Button rotate = createSquareIconButton(hotbarX, hotbarY, ROTATE_ICON, b ->
                this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CrazyPhonePhotoFrameResizeMenu.ROTATE_BUTTON_ID));
        rotate.setTooltip(Tooltip.create(Component.translatable("gui.crazyphone.photo_frame_resize.rotate")));
        addRenderableWidget(rotate);
        ownButtons.add(rotate);
    }

    // A "counterclockwise arrows" emoji (U+1F504, ":arrows_counterclockwise:") from the bundled Pixel
    // Twemoji font (assets/minecraft/font/default.json) - "a colored text icon using the new resource pack
    // we added" (live request) - drawn as plain Component text via #createSquareIconButton, no image blit.
    private static final Component ROTATE_ICON = Component.literal("🔄");
    private static final int HOTBAR_BUTTON_SIZE = 14;

    // A small SQUARE Button showing a single centered icon glyph, using the real vanilla button background
    // (hover/press/disabled all keep working normally) - same technique as CrazyPhoneContactsScreenScreen's
    // own delete/favorite buttons ("same technique as the squre button for example in contacts page for
    // delete and favory" - live request): vanilla's own text centering truncates (buttonWidth-textWidth)/2
    // to an int, which for an odd leftover visibly biases the glyph a pixel off-center, so the icon is drawn
    // manually (with a 0.5px sub-pixel pose nudge) after blanking vanilla's own auto-centered label.
    private Button createSquareIconButton(int x, int y, Component icon, Button.OnPress onPress) {
        return new Button(x, y, HOTBAR_BUTTON_SIZE, HOTBAR_BUTTON_SIZE, icon, onPress, supplier -> icon.copy()) {
            //? if >=26 {
            /*@Override
            public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
                Component message = getMessage();
                this.extractDefaultSprite(guiGraphics);

                var font = Minecraft.getInstance().font;
                int textWidth = font.width(message);
                int drawX = getX() + (getWidth() - textWidth) / 2;
                int drawY = getY() + (getHeight() - 8) / 2;
                GuiCompat.pushPose(guiGraphics);
                GuiCompat.translate(guiGraphics, 0.5f, 0f);
                guiGraphics./^$ gui_draw_string {^/drawString/^$}^/(font, message, drawX, drawY, 0xFFFFFFFF, true);
                GuiCompat.popPose(guiGraphics);
            }
            *///? } else {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
                Component message = getMessage();
                setMessage(Component.empty());
                super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
                setMessage(message);

                var font = Minecraft.getInstance().font;
                int textWidth = font.width(message);
                int drawX = getX() + (getWidth() - textWidth) / 2;
                int drawY = getY() + (getHeight() - 8) / 2;
                GuiCompat.pushPose(guiGraphics);
                GuiCompat.translate(guiGraphics, 0.5f, 0f);
                guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(font, message, drawX, drawY, 0xFFFFFFFF, true);
                GuiCompat.popPose(guiGraphics);
            }
            //?}
        };
    }

    // Which screen direction (col+/right, row+/down) each server axis (U/V) actually corresponds to for the
    // CURRENT face/rotation - the whole reason this transform exists at all. Returns {swap, signU, signV}:
    // swap=0 means U comes from col and V comes from row (the "normal" case); swap=1 means U comes from row
    // and V comes from col instead (a floor/ceiling rotated an odd number of quarter turns). signU/signV are
    // +1 or -1, applied to whichever screen axis feeds that server axis.
    //
    // NORTH/SOUTH: "up" on screen grows the photo UP in the world (V = +row here - an earlier version had
    // this at -row, live-reported inverted: "north & south, up and down are inverted" - live request) -
    // "right" on screen only grows the photo toward world +X depending on which way the VIEWER is actually
    // facing that specific wall, which flips between opposite walls. Critical detail an earlier version of
    // this got backwards ("sur les murs gauche et droite sont inversé" - live request): attachFace() is
    // which face of the BLOCK the frame sits on, i.e. the direction the frame's own visible side points
    // TOWARD - a NORTH-attached frame points north, so the viewer looking at it is standing to the north
    // and facing SOUTH, back at the wall, the OPPOSITE of attachFace() itself. Standing in front of a wall
    // and facing it: facing north, east is your right; facing south, west is your right (ordinary compass
    // facts) - applied to face.getOpposite() (the viewer's actual facing), not face itself, then
    // cross-referenced against CrazyPhonePhotoFrameEntity#computeBoundingBox's own fixed U=+X/V=+Y axis
    // assignment: south already agrees with "posU = viewer's right" as computeBoundingBox defines it, north
    // doesn't (needs signU=-1).
    //
    // WEST/EAST: SWAPPED (screen row feeds server U, screen col feeds server V) - unlike north/south, which
    // don't swap at all. "west : up on the grid extend on right in the world... right extend up" / "east:
    // same as west but inverted (up in grid -> left in the world)" (live request) - confirmed live against
    // the actual 3D render, not just the grid preview. Both faces take the SAME {swap,signU,signV} values
    // below; the "inverted" difference the live report describes between them falls straight out of
    // computeBoundingBox's own U=+Z convention meaning the OPPOSITE compass direction (viewer's right vs
    // left) for these two opposite-facing walls, the same way "posU = viewer's right" already flips between
    // north and south above without signU itself needing a different value for every individual face.
    //
    // FLOOR/CEILING: FIXED, like the walls - NOT dependent on CrazyPhonePhotoFrameEntity#rotation() at all.
    // An earlier version made this depend on rotation, on the theory that "up" in the grid should track
    // "up" in the currently-displayed (rotated) picture - but CrazyPhonePhotoFrameRenderer's own doc comment
    // is explicit that rotation is a pure visual spin of the picture's PIXEL CONTENT within its existing
    // slot rectangle, "NOT a swap of which world axis width/height bind to" - the physical slot (what this
    // grid actually edits) never rotates, only the texture drawn inside it does, so tying the grid's own
    // axis mapping to rotation was mixing up two genuinely independent things. That mistake is exactly what
    // produced the reported bug: half the rotation states (whichever ones happened to share this fixed
    // mapping's own signs) worked, the other half didn't - "quand l'image est placée au sol... seulement si
    // l'image est orienté vers north ou sud [c'est haut/bas qui sont inversés]... sur west et est c'est
    // gauche et droite qui sont inversés" (live request). UP's value below is the one already confirmed
    // live ("les images placées au plafond ça marche" - live request, at whatever rotation was tested);
    // DOWN mirrors it with V flipped, matching how opposite WALLS also flip exactly one sign between them
    // (see the comment above) - floor and ceiling face fully opposite directions the same way north/south
    // or east/west do.
    private int[] axisTransform() {
        Direction face = menu.attachFace();
        if (face == Direction.UP)
            return new int[]{0, 1, -1};
        if (face == Direction.DOWN)
            return new int[]{0, 1, 1};
        if (face == Direction.WEST || face == Direction.EAST)
            return new int[]{1, -1, 1};
        int signU = (face == Direction.NORTH) ? -1 : 1;
        return new int[]{0, signU, 1};
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

    // Half-block BOUNDARY coordinates - unlike #colAt/#rowAt (which report which CELL a point falls inside,
    // correctly FLOOR-based since a point unambiguously belongs to exactly one cell), these report the
    // nearest grid-line BOUNDARY, so they round to the CLOSEST half-cell line rather than always the one
    // at-or-before the click. An earlier version used floor here too, which snapped a click ANYWHERE within
    // a given half-cell to that half-cell's own leading (up-left) edge - up to a whole half-cell's worth of
    // pixels short of the boundary the player actually meant to click, and always in the same up-left
    // direction, which is exactly the systematic "handle lands short of where I clicked, at what looks like
    // a cell center instead of the intersection" bug reported live ("the handle go overboard and create a
    // selection using the red handle that is at the center of the square on it's top left corner" - same
    // observed with yellow - live request). Drive every resize/shift gesture in this screen
    // (#updateCornerDrag, #beginNearestCornerDrag, #updateShift), all half-block resolution. signedHalfCol=
    // 0/1 are the anchor cell's own two halves (its left/neg and right/pos half respectively), matching how
    // previewNegCol/previewPosCol already exclude the anchor's own implicit halves - see this class's own
    // doc comment.
    private int halfColAt(double mouseX) {
        return (int) Math.round((mouseX - gridLeft) / (double) halfCellPx) - maxHalf;
    }

    private int halfRowAt(double mouseY) {
        return (int) Math.round((mouseY - gridTop) / (double) halfCellPx) - maxHalf;
    }

    // Bounds against #limitHalf (the REAL clamp ceiling), NOT #maxHalf (the display-only layout bound) -
    // see this class's own field-level doc comment on #DISPLAY_MAX_BLOCKS_CAP for why those two are kept
    // separate. Every resize/shift gesture routes its final value through one of these two.
    private int clampHalf(int v) {
        return Math.max(-limitHalf, Math.min(limitHalf, v));
    }

    private int clampHalfExtent(int v) {
        return Math.max(0, Math.min(limitHalf, v));
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        int gridPx = gridCells * cellPx;
        return mouseX >= gridLeft && mouseX < gridLeft + gridPx && mouseY >= gridTop && mouseY < gridTop + gridPx;
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
    // [0, limitHalf] for that axis's own (fixed) total width - the REAL clamp ceiling, not the display-only
    // #maxHalf (see this class's own field-level doc comment on #DISPLAY_MAX_BLOCKS_CAP). HALF-block
    // resolution throughout (see this class's own field-level doc comment on #shifting for why) - uses raw
    // mouse position (#halfColAt/#halfRowAt) rather than a block index.
    private void updateShift(double mouseX, double mouseY) {
        int hc = clampHalf(halfColAt(mouseX));
        int hr = clampHalf(halfRowAt(mouseY));
        int dCol = hc - shiftStartHalfCol;
        int dRow = hr - shiftStartHalfRow;
        int width = shiftBaseNegCol + shiftBasePosCol;
        int height = shiftBaseNegRow + shiftBasePosRow;
        int negCol = Math.max(Math.max(0, width - limitHalf), Math.min(Math.min(width, limitHalf), shiftBaseNegCol - dCol));
        int negRow = Math.max(Math.max(0, height - limitHalf), Math.min(Math.min(height, limitHalf), shiftBaseNegRow - dRow));
        previewNegCol = negCol;
        previewPosCol = width - negCol;
        previewNegRow = negRow;
        previewPosRow = height - negRow;
    }

    // Moves whichever edges #cornerDragCol/#cornerDragRow currently point at to the mouse's own half-block
    // boundary position, at half-block resolution - each edge independently clamped to [0, maxHalf], so the
    // anchor's own cell can never be pushed out of the selection (neither edge can go negative) and a drag
    // can never exceed the configured max size ("attention ne pas bouger l'autre coin sauf si pas dans la
    // taille max autorisé" - live request: the OTHER two edges are never touched here at all, and the ones
    // that are get the same ordinary clamp every other half-block edit in this screen already uses). The
    // two named handles (#tryBeginCornerDrag) and the double-click nearest-corner jump
    // (#beginNearestCornerDrag) both just set cornerDragCol/cornerDragRow differently before calling this.
    private void updateCornerDrag(double mouseX, double mouseY) {
        int hc = clampHalf(halfColAt(mouseX));
        int hr = clampHalf(halfRowAt(mouseY));
        if (cornerDragCol == EDGE_NEG)
            previewNegCol = clampHalfExtent(-hc);
        else if (cornerDragCol == EDGE_POS)
            previewPosCol = clampHalfExtent(hc - 2);
        if (cornerDragRow == EDGE_NEG)
            previewNegRow = clampHalfExtent(-hr);
        else if (cornerDragRow == EDGE_POS)
            previewPosRow = clampHalfExtent(hr - 2);
    }

    // Double-click alternative to precisely grabbing a named handle - "un double clique sur une case, même
    // dans la sélection, doit faire bouger si possible le corner le plus proche" (live request): picks
    // whichever of the CURRENT selection's four logical corners is nearest to the click (independently per
    // axis - nearest to the left edge or the right edge; nearest to the top edge or the bottom edge), starts
    // dragging exactly those two edges, and jumps them to the click position immediately (so a plain
    // double-click with no drag already resizes to there, same as a single click precisely on a handle
    // would after a small drag).
    private void beginNearestCornerDrag(double mouseX, double mouseY) {
        int hc = clampHalf(halfColAt(mouseX));
        int hr = clampHalf(halfRowAt(mouseY));
        int leftB = -previewNegCol, rightB = previewPosCol + 2;
        cornerDragCol = Math.abs(hc - rightB) < Math.abs(hc - leftB) ? EDGE_POS : EDGE_NEG;
        int topB = -previewNegRow, bottomB = previewPosRow + 2;
        cornerDragRow = Math.abs(hr - bottomB) < Math.abs(hr - topB) ? EDGE_POS : EDGE_NEG;
        updateCornerDrag(mouseX, mouseY);
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

    // Extra pixels added to a handle's own VISUAL box (see #topLeftHandleBox/#bottomRightHandleBox) for
    // hit-testing only - the drawn square stays its own small half-cell size, but grabbing it doesn't need
    // to be pixel-perfect ("add arround the red and yellow handle a small margin so it's not pixel perfect
    // when you grab them" - live request). Deliberately applied ONLY on the two OUTWARD sides (away from
    // the selection interior) - top-left grows further up-left, bottom-right grows further down-right - and
    // NEVER on the two sides facing the selection: an earlier version expanded all four sides equally,
    // which at small cell sizes made the margin swallow the ENTIRE anchor cell from both corners at once
    // (a plain 1x1 selection's two handles sit right on the anchor's own two opposite corners), leaving no
    // way to click the blue square itself to shift it ("blue square still can't be moved" - live request).
    // Growing only outward keeps the margin's whole purpose (forgiving grab, away from where anything else
    // interesting lives) without ever eating into cells the shift gesture still needs.
    private static final int HANDLE_HIT_MARGIN = 3;

    private static boolean hitTopLeftHandle(int[] box, double mouseX, double mouseY) {
        return mouseX >= box[0] - HANDLE_HIT_MARGIN && mouseX < box[2]
                && mouseY >= box[1] - HANDLE_HIT_MARGIN && mouseY < box[3];
    }

    private static boolean hitBottomRightHandle(int[] box, double mouseX, double mouseY) {
        return mouseX >= box[0] && mouseX < box[2] + HANDLE_HIT_MARGIN
                && mouseY >= box[1] && mouseY < box[3] + HANDLE_HIT_MARGIN;
    }

    // Checked BEFORE the ordinary shift-vs-resize dispatch (#beginGesture) in both mouseClicked bodies below
    // - the handles sit on top of the grid and take priority over whatever whole-block cell happens to be
    // underneath them.
    private boolean tryBeginCornerDrag(double mouseX, double mouseY) {
        if (hitTopLeftHandle(topLeftHandleBox(), mouseX, mouseY)) {
            cornerDragCol = EDGE_NEG;
            cornerDragRow = EDGE_NEG;
            return true;
        }
        if (hitBottomRightHandle(bottomRightHandleBox(), mouseX, mouseY)) {
            cornerDragCol = EDGE_POS;
            cornerDragRow = EDGE_POS;
            return true;
        }
        return false;
    }

    // Which cursor should be shown for whatever's currently under the mouse - CROSSHAIR (via
    // CursorEffects#requestResizeCursor) for anything that resizes (an active corner drag, hovering a
    // corner handle, or hovering outside the current selection where a click would start a fresh resize),
    // HAND (CursorEffects#requestPointerCursor) for anything that just moves the existing selection around
    // (an active shift, or hovering inside the current selection) - "i would like also the cursor to change
    // depending on the action possible" (live request). This screen isn't a PhoneScreen (it's a plain
    // right-click-in-world container dialog, not part of the phone's own screen stack), so
    // PhoneClickableCursorHandler's global per-frame CursorEffects#endFrame() call never reaches it - this
    // screen calls it directly at the end of its own render pass instead (see render()/extractRenderState()
    // below), which is safe precisely because it's never open at the same time as a PhoneScreen fighting
    // over the same per-frame state.
    private void updateCursor(double mouseX, double mouseY) {
        boolean resizeIntent = isCornerDragging()
                || hitTopLeftHandle(topLeftHandleBox(), mouseX, mouseY)
                || hitBottomRightHandle(bottomRightHandleBox(), mouseX, mouseY);
        if (!resizeIntent && !shifting && insideGrid(mouseX, mouseY)) {
            int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
            int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
            resizeIntent = !insideSelection(col, row);
        }
        if (resizeIntent)
            CursorEffects.requestResizeCursor();
        else if (shifting || insideGrid(mouseX, mouseY))
            CursorEffects.requestPointerCursor();
    }

    // Nothing requests a cursor after this screen closes, so without an explicit reset here the last
    // hand/crosshair cursor #updateCursor set would keep showing over the rest of the game indefinitely -
    // calling #endFrame() with no request pending applies its own default (plain arrow).
    @Override
    public void onClose() {
        CursorEffects.endFrame();
        super.onClose();
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
            beginGesture(mouseX, mouseY, consumeDoubleClick(mouseX, mouseY));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isCornerDragging()) {
            updateCornerDrag(mouseX, mouseY);
            return true;
        }
        if (shifting) {
            updateShift(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (shifting || isCornerDragging()) {
            shifting = false;
            cornerDragCol = EDGE_NONE;
            cornerDragRow = EDGE_NONE;
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
            beginGesture(event.x(), event.y(), doubleClick);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (isCornerDragging()) {
            updateCornerDrag(event.x(), event.y());
            return true;
        }
        if (shifting) {
            updateShift(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (shifting || isCornerDragging()) {
            shifting = false;
            cornerDragCol = EDGE_NONE;
            cornerDragRow = EDGE_NONE;
            commitSize();
            return true;
        }
        return super.mouseReleased(event);
    }
    *///?}

    // Two ways to click inside the grid (after #tryBeginCornerDrag has already had first refusal on the two
    // named corner handles): a click OUTSIDE the current selection, or a DOUBLE click anywhere (inside or
    // outside), jump-grabs whichever corner of the selection is nearest and starts resizing from there
    // (#beginNearestCornerDrag) - a single click already suffices outside the selection ("si hors sélection,
    // un clique simple suffit" - live request) since there's no shift there to disambiguate from; a single
    // click INSIDE the current selection - the blue anchor square included - instead starts a half-block
    // -precision shift of the whole selection (#updateShift). An earlier version gave a single click outside
    // the selection a DIFFERENT behavior (tracing a brand new rectangle from the anchor, discarding the far
    // corner back down to the anchor's own edge) - "the yellow handle move to the blue square... the yellow
    // should not move in this situation" (live request) - clicking outside is now just the single-click form
    // of the exact same nearest-corner gesture double-click already uses everywhere else, so the untouched
    // corner really does stay untouched. Not version-split itself, called from both mouseClicked bodies
    // above.
    private void beginGesture(double mouseX, double mouseY, boolean doubleClick) {
        int col = Math.max(-maxBlocks, Math.min(maxBlocks, colAt(mouseX)));
        int row = Math.max(-maxBlocks, Math.min(maxBlocks, rowAt(mouseY)));
        if (!doubleClick && insideSelection(col, row)) {
            shifting = true;
            shiftStartHalfCol = clampHalf(halfColAt(mouseX));
            shiftStartHalfRow = clampHalf(halfRowAt(mouseY));
            shiftBaseNegCol = previewNegCol;
            shiftBasePosCol = previewPosCol;
            shiftBaseNegRow = previewNegRow;
            shiftBasePosRow = previewPosRow;
        } else {
            beginNearestCornerDrag(mouseX, mouseY);
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
        if (shifting || isCornerDragging())
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
        updateCursor(mouseX, mouseY);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.centeredText(this.font, this.title, leftPos + imageWidth / 2, topPos + 8, 0xA0A0A0);
        guiGraphics.centeredText(this.font, sizeLabel(), leftPos + imageWidth / 2, topPos + imageHeight - 16, 0xFFFFFF);
        drawGrid(guiGraphics);
        for (Button button : ownButtons)
            button.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        CursorEffects.endFrame();
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        syncFromMenuIfIdle();
        updateCursor(mouseX, mouseY);
        this./*$ gui_render_transparent_background {*/renderTransparentBackground/*$}*/(guiGraphics);
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, leftPos + imageWidth / 2, topPos + 8, 0xA0A0A0);
        guiGraphics.drawCenteredString(this.font, sizeLabel(), leftPos + imageWidth / 2, topPos + imageHeight - 16, 0xFFFFFF);
        drawGrid(guiGraphics);
        for (Button button : ownButtons)
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        CursorEffects.endFrame();
    }
    //?}
}
