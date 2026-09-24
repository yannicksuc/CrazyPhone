package fr.lordfinn.crazyphone.client.gui;

/**
 * Standalone photo editor - reached from the {@link CrazyPhonePhotoViewerScreen}'s own "Edit" button. Every
 * adjustment (hue/contrast/brightness/rotate/crop/resize) is a pure function of the ORIGINAL, untouched image
 * plus a {@link fr.lordfinn.crazyphone.client.picture.PhotoEditState} snapshot (see that record's own doc
 * comment) - the on-screen preview always re-derives from scratch rather than compounding edits on top of
 * each other, which is what makes both per-control Reset and Undo/Redo exact.
 * <p>
 * Deliberately client-side-only and non-destructive to the server's own copy: "Replace" and "Create a copy"
 * both upload the edited result as a brand-new photo through the exact same {@code CrazyPhoneUploadPicturePacket}
 * every capture/import already uses - "Replace" additionally asks the server to drop the ORIGINAL id from
 * this owner's own gallery list ({@code CrazyPhoneMyPhotosActionMessage.DELETE}, the same action the My
 * Photos screen's own delete button sends). No new server-authorized action exists for this feature at all:
 * a photo's own bytes, once captured, stay immutable for the lifetime of that id everywhere else in the mod
 * (see FabricPictureCache's own doc comment on that invariant) - true in-place mutation would mean
 * invalidating every OTHER client's cache of that id and every physical Photo item/frame that already
 * references it, which this sidesteps entirely by always minting a fresh id instead.
 * <p>
 * The control panel (right side) is its own small scrollable viewport, not a flat list of
 * addRenderableWidget calls - live-reported as overflowing off screen at higher GUI scales with no way to
 * reach the bottom rows. Every control is tracked as a (widget, logicalY) pair and repositioned by
 * scrollOffset on every scroll/resize, clipped with enableScissor so a partially-scrolled-out row never
 * bleeds past the panel - see repositionPanelWidgets/drawPanel.
 * <p>
 * Rotation is a draggable handle on the preview image itself (arbitrary angle, not stepped) - the crop
 * rectangle is a fixed, axis-aligned window that never rotates or scales with the image (see
 * {@link fr.lordfinn.crazyphone.client.picture.PhotoEditState}'s own doc comment); any corner the rotated
 * image no longer covers renders fully transparent rather than any background color (see
 * {@link fr.lordfinn.crazyphone.client.picture.PhotoImageOps#rotateArbitrary}).
 * <p>
 * Animated (GIF-derived) photos can be opened here too - every adjustment is applied UNIFORMLY to every
 * frame (the same {@link fr.lordfinn.crazyphone.client.picture.PhotoEditState} rendered once per frame at
 * commit time - see {@link #renderFinalAnimated}), not edited frame-by-frame; the live preview only ever
 * shows/edits the first frame, since every other frame gets the exact same treatment anyway. Deliberately
 * NOT a per-frame editor (trim frames, different crop per frame, etc.) - that's a real follow-up feature
 * of its own, not attempted here.
 */
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.client.picture.AnimatedPhotoCodec;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;
import fr.lordfinn.crazyphone.client.picture.PhotoEditState;
import fr.lordfinn.crazyphone.client.picture.PhotoImageOps;
import fr.lordfinn.crazyphone.client.picture.PixelArtDownscaler;
import fr.lordfinn.crazyphone.network.CrazyPhoneMyPhotosActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket;
import fr.lordfinn.crazyphone.utils.GuiCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CrazyPhonePhotoEditScreen extends Screen implements PhoneScreen {
    // The longer side a downscaled working copy is capped to for on-screen preview/interaction - every
    // stepper/rotate/crop change re-renders from this, not the full original, so even a big photo stays
    // responsive while editing. The FULL-resolution original is only ever touched once, at Replace/Create
    // Copy time (see renderFinal()).
    private static final int PREVIEW_MAX_DIMENSION = 420;
    private static final float HUE_STEP = 15f;
    private static final float CONTRAST_STEP = 10f;
    private static final float BRIGHTNESS_STEP = 10f;
    private static final int CROP_HANDLE_PX = 5;
    private static final int ROTATE_HANDLE_PX = 5;
    // Distance the draggable rotation handle sits outside the preview image's own shorter half-dimension -
    // just enough that it doesn't sit on top of the image content itself.
    private static final int ROTATE_HANDLE_GAP = 14;
    private static final int SCROLLBAR_WIDTH = 6;
    // Step per scroll-wheel tick for zooming the image while hovering the preview (live request).
    private static final float ZOOM_STEP = 0.1f;
    // Every panel button's own LABEL TEXT is tinted by what the button DOES, grouped rather than left as a
    // wall of identical grey buttons - live-reported as "tout les boutons gris comme ca on est perdu". A
    // colored border around each button was tried first, but was reported back as "pas tres minecrafty" and
    // getting clipped by the panel's own scissor rectangle - text color is a look vanilla itself already
    // uses everywhere (disabled/success/danger text), so it stays in-theme and needs no extra rendering.
    private static final int ACCENT_NEUTRAL = 0xFF4A90D9;
    private static final int ACCENT_RESET = 0xFFE0952B;
    private static final int ACCENT_CONFIRM = 0xFF3FA34D;
    private static final int ACCENT_TOGGLE = 0xFF2FA7A0;
    private static final int ACCENT_UNDOREDO = 0xFF8A6FD1;
    private static final int ACCENT_DANGER = 0xFFD1453B;
    // Vertical gap a row caption ("Hue"/"Contrast"/"Brightness") reserves ABOVE its own row - live-reported
    // overlapping the row above it when this was smaller than the font's own line height plus a little
    // breathing room.
    private static final int ROW_LABEL_GAP = 12;

    private static final Executor COMMIT_IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crazyphone-photo-edit-commit");
        t.setDaemon(true);
        return t;
    });

    /** Whether this photo can even be opened here - the FULL texture must have already resolved (the Viewer
     * that owns the Edit button is the only caller, and it's already fetching FULL for its own display, so
     * this is normally already true by the time the button is clickable at all). Animated photos ARE
     * editable now (see this class's own doc comment) - no longer gated on {@code !isAnimated()}. */
    public static boolean canEdit(UUID photoId) {
        return FabricPictureCache.getOrRequest(photoId, PhotoResolution.FULL) != null;
    }

    private final UUID photoId;
    private final CrazyPhonePhotoViewerScreen viewerScreen;
    /** Where onClose() actually returns to - the Viewer by default (Cancel/Escape), redirected to point past
     * it (the Viewer's own previousScreen) the moment Replace/Create Copy actually succeeds, so the player
     * doesn't land back on a screen showing the stale pre-edit image for a moment. */
    private Screen closeTarget;

    /** Non-null only for an animated photo - {@link #original} then points at ITS frame 0 (owned by this,
     * not separately), and {@link #renderFinalAnimated} re-applies the edit to every one of its frames at
     * commit time instead of the plain single-image path. */
    private AnimatedPhotoCodec.Animation animation;
    private NativeImage original;
    private NativeImage previewSource;
    private PhotoEditState state = PhotoEditState.DEFAULT;
    private final List<PhotoEditState> history = new ArrayList<>();
    private int historyIndex = -1;

    private /*$ res_loc {*/ResourceLocation/*$}*/ previewTexture;
    private int previewTextureWidth, previewTextureHeight;
    private int previewNameCounter = 0;

    /** Live crop rectangle while a drag is in progress (left,top,right,bottom, normalized) - null the rest
     * of the time, when the committed {@link #state}'s own crop fields are what's drawn instead. Kept
     * separate so every mouse-move during a drag doesn't spam the undo history - only mouseReleased commits. */
    private float[] liveCrop;
    /** -2 = dragging out a brand new rectangle from scratch, 0-3 = resizing from that corner (TL, TR, BR,
     * BL), null = not dragging. Dragging INSIDE the existing rectangle pans the IMAGE instead (see
     * draggingImagePan below) - the rectangle itself is a fixed frame, only resizable from its own corners
     * or redrawn from scratch, not moved bodily by a drag (live request: "le crop tourne pas ... et
     * definira la nouvelle image" plus the later "drag l'image ... dans l'image crop"). */
    private Integer dragMode;
    private double dragAnchorFracX, dragAnchorFracY;

    // ---- image panning (drag INSIDE the crop rectangle - live request) ------------------------------------
    private boolean draggingImagePan = false;
    private float livePanX, livePanY;
    private double panDragStartMouseX, panDragStartMouseY;
    private float panDragStartPanX, panDragStartPanY;

    private int previewX, previewY, previewW, previewH;

    private boolean committing = false;

    // ---- scrollable control panel ------------------------------------------------------------------------

    private record PanelEntry(AbstractWidget widget, int logicalY) {
    }

    private record PanelLabel(Component text, int logicalY) {
    }

    private final List<PanelEntry> panelWidgets = new ArrayList<>();
    private final List<PanelLabel> panelLabels = new ArrayList<>();
    private int panelContentHeight;
    private int scrollOffset = 0;
    private int viewportX0, viewportY0, viewportX1, viewportY1;
    private boolean draggingScrollbar = false;

    private Button undoButton, redoButton, replaceButton, createCopyButton;
    private Button hueValueLabel, contrastValueLabel, brightnessValueLabel;
    private Button cropSquareButton;
    private EditBox widthField, heightField;
    private Button lockRatioButton;
    private boolean suppressResizeFieldFeedback = false;

    // ---- rotation handle (arbitrary-angle, drag-controlled - replaces the old 90-degree step button) -----
    // Rotate and crop are complementary, both always on screen and usable at the same time (live request -
    // there is no exclusive "crop mode" to enter/exit anymore; the crop rectangle + handles and the rotate
    // handle are simply both drawn and both draggable at all times, and the backdrop always shows the
    // rotated/zoomed/color-adjusted image UNCROPPED, with the crop rectangle overlaid on top of it - see
    // drawEditor/refreshPreview).
    /** Non-null only while the rotate handle is actively being dragged - the LIVE angle shown in the preview
     * (see refreshPreview) without touching undo history on every mouse-move; mouseReleased is what actually
     * commits it, same "defer to release" pattern the crop rectangle's own liveCrop already uses. */
    private Float liveRotationDegrees;
    private boolean draggingRotateHandle = false;

    public CrazyPhonePhotoEditScreen(UUID photoId, CrazyPhonePhotoViewerScreen viewerScreen) {
        super(Component.translatable("gui.crazyphone.photo_edit_screen.title"));
        this.photoId = photoId;
        this.viewerScreen = viewerScreen;
        this.closeTarget = viewerScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        byte[] bytes = FabricPictureCache.readCachedBytesBlocking(photoId, PhotoResolution.FULL);
        if (bytes == null) {
            // Genuinely shouldn't happen - canEdit() already gates the button on the exact same fetch having
            // resolved - but the disk cache is still a file on disk, and files can vanish; fail safe rather
            // than NPE deeper in.
            if (this.minecraft != null && this.minecraft.player != null)
                fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(this.minecraft.player,
                        Component.translatable("message.crazyphone.photo_edit_not_ready"), true);
            onClose();
            return;
        }
        try {
            if (AnimatedPhotoCodec.isAnimatedContainer(bytes)) {
                this.animation = AnimatedPhotoCodec.decodeContainer(bytes);
                this.original = animation.frames().get(0).image();
            } else {
                this.original = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Failed to open photo {} for editing", photoId, e);
            onClose();
            return;
        }
        this.previewSource = downscaleForPreview(original);
        history.clear();
        history.add(PhotoEditState.DEFAULT);
        historyIndex = 0;

        panelWidgets.clear();
        panelLabels.clear();
        scrollOffset = 0;
        layoutPanelViewport();
        buildWidgets();
        repositionPanelWidgets();
        refreshPreview();
    }

    private static NativeImage downscaleForPreview(NativeImage source) {
        int w = source.getWidth(), h = source.getHeight();
        double scale = Math.min(1.0, (double) PREVIEW_MAX_DIMENSION / Math.max(w, h));
        if (scale >= 1.0) {
            NativeImage copy = new NativeImage(w, h, false);
            source.resizeSubRectTo(0, 0, w, h, copy);
            return copy;
        }
        return PhotoImageOps.resize(source, Math.max(1, Math.round(w * (float) scale)), Math.max(1, Math.round(h * (float) scale)));
    }

    // ---- widget layout ---------------------------------------------------------------------------------

    private void layoutPanelViewport() {
        viewportX0 = (int) (this.width * 0.58f);
        viewportX1 = this.width - 8 - SCROLLBAR_WIDTH - 6;
        viewportY0 = 8;
        viewportY1 = this.height - 8;
    }

    /** Registers a control for input dispatch (addWidget, not addRenderableWidget - this class draws its own
     * panel widgets itself, inside an enableScissor block, so a row scrolled outside the viewport never
     * paints over the preview image or off-screen) at a LOGICAL y (panel-content-relative, not a screen
     * coordinate) - repositionPanelWidgets() converts every one of these to a real screen y (and hides it
     * entirely once scrolled out of view) on init and on every scroll. */
    private <T extends AbstractWidget> T addPanelWidget(T widget, int logicalY) {
        addWidget(widget);
        panelWidgets.add(new PanelEntry(widget, logicalY));
        return widget;
    }

    private void addPanelLabel(Component text, int logicalY) {
        panelLabels.add(new PanelLabel(text, logicalY));
    }

    /** A Button whose label text is tinted by {@code accentColor} - see the ACCENT_* constants' own doc
     * comment for why text color, not a border. */
    private Button accentButton(Component label, Button.OnPress onPress, int x, int y, int w, int h, int accentColor) {
        return Button.builder(colorize(label, accentColor), onPress).bounds(x, y, w, h).build();
    }

    private static Component colorize(Component label, int rgb) {
        return label.copy().withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb & 0xFFFFFF)));
    }

    private void buildWidgets() {
        int panelX = viewportX0;
        int panelWidth = viewportX1 - viewportX0;
        // Starts at ROW_LABEL_GAP, not 0 - the FIRST row's own caption sits ABOVE it (same as every other
        // labeled row), and with no headroom reserved before the very first row, that caption's y landed
        // above the viewport's own top edge and got scissor-clipped away entirely (live-reported: "on ne
        // voit pas le premier label").
        int y = ROW_LABEL_GAP;
        int rowHeight = 22, gap = 4;

        y = addAdjustmentRow(panelX, y, panelWidth, rowHeight,
                Component.translatable("gui.crazyphone.photo_edit_screen.hue"), state.hueDegrees(),
                v -> commit(state.withHue(v)), () -> commit(state.resetHue()), HUE_STEP, "hue") + gap + ROW_LABEL_GAP;
        y = addAdjustmentRow(panelX, y, panelWidth, rowHeight,
                Component.translatable("gui.crazyphone.photo_edit_screen.contrast"), state.contrast(),
                v -> commit(state.withContrast(v)), () -> commit(state.resetContrast()), CONTRAST_STEP, "contrast") + gap + ROW_LABEL_GAP;
        y = addAdjustmentRow(panelX, y, panelWidth, rowHeight,
                Component.translatable("gui.crazyphone.photo_edit_screen.brightness"), state.brightness(),
                v -> commit(state.withBrightness(v)), () -> commit(state.resetBrightness()), BRIGHTNESS_STEP, "brightness") + gap;

        y += gap * 2;
        int thirdW = (panelWidth - gap * 2) / 3;
        // No Rotate/Crop-mode toggle buttons here anymore - rotation is now the draggable handle on the
        // preview image itself (see drawRotateHandle/tryStartRotateDrag), and crop's own rectangle+handles
        // are simply always shown and always draggable alongside it (live request: "rotate et crop sont
        // complementaires, on doit pouvoir utiliser les deux outils en meme temps" - no exclusive mode to
        // switch between at all anymore).
        int halfW = (panelWidth - gap) / 2;
        Button resetRotationButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.reset_rotation"),
                b -> commit(state.resetRotation()), panelX, y, halfW, rowHeight, ACCENT_RESET);
        addPanelWidget(resetRotationButton, y);
        Button resetAllButtonTop = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.reset_all"),
                b -> commit(PhotoEditState.DEFAULT), panelX + halfW + gap, y, halfW, rowHeight, ACCENT_RESET);
        addPanelWidget(resetAllButtonTop, y);
        y += rowHeight + gap;

        cropSquareButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.crop_square"),
                b -> commit(squareCrop()), panelX, y, thirdW, rowHeight, ACCENT_CONFIRM);
        addPanelWidget(cropSquareButton, y);
        Button cropFillButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.crop_fill"),
                b -> commit(fillCrop()), panelX + thirdW + gap, y, thirdW, rowHeight, ACCENT_CONFIRM);
        addPanelWidget(cropFillButton, y);
        Button cropResetButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.reset"),
                b -> commit(state.resetCrop()), panelX + (thirdW + gap) * 2, y, thirdW, rowHeight, ACCENT_RESET);
        addPanelWidget(cropResetButton, y);
        y += rowHeight + gap * 2;

        int fieldW = (panelWidth - gap * 2) / 3;
        widthField = new EditBox(this.font, panelX, y, fieldW, rowHeight, Component.translatable("gui.crazyphone.photo_edit_screen.width"));
        widthField.setValue(String.valueOf(currentCroppedWidth()));
        widthField.setResponder(this::onWidthFieldChanged);
        addPanelWidget(widthField, y);
        heightField = new EditBox(this.font, panelX + fieldW + gap, y, fieldW, rowHeight, Component.translatable("gui.crazyphone.photo_edit_screen.height"));
        heightField.setValue(String.valueOf(currentCroppedHeight()));
        heightField.setResponder(this::onHeightFieldChanged);
        addPanelWidget(heightField, y);
        // Short label text ("Locked"/"Free") - live-reported the longer "Ratio: locked" phrasing clipping
        // past this button's own edge at this panel width.
        lockRatioButton = accentButton(lockRatioLabel(), b -> {
            state = state.withLockAspectRatio(!state.lockAspectRatio());
            lockRatioButton.setMessage(colorize(lockRatioLabel(), ACCENT_NEUTRAL));
        }, panelX + (fieldW + gap) * 2, y, fieldW, rowHeight, ACCENT_NEUTRAL);
        addPanelWidget(lockRatioButton, y);
        y += rowHeight + gap;

        Button applySizeButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.resize_apply"),
                b -> applyResizeFields(), panelX, y, thirdW, rowHeight, ACCENT_CONFIRM);
        addPanelWidget(applySizeButton, y);
        Button resizeResetButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.reset"), b -> {
            commit(state.resetResize());
            suppressResizeFieldFeedback = true;
            widthField.setValue(String.valueOf(currentCroppedWidth()));
            heightField.setValue(String.valueOf(currentCroppedHeight()));
            suppressResizeFieldFeedback = false;
        }, panelX + thirdW + gap, y, thirdW, rowHeight, ACCENT_RESET);
        addPanelWidget(resizeResetButton, y);
        y += rowHeight + gap * 3;

        undoButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.undo"), b -> undo(),
                panelX, y, thirdW, rowHeight, ACCENT_UNDOREDO);
        addPanelWidget(undoButton, y);
        redoButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.redo"), b -> redo(),
                panelX + thirdW + gap, y, thirdW, rowHeight, ACCENT_UNDOREDO);
        addPanelWidget(redoButton, y);
        Button cancelButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.cancel"), b -> onClose(),
                panelX + (thirdW + gap) * 2, y, thirdW, rowHeight, ACCENT_DANGER);
        addPanelWidget(cancelButton, y);
        y += rowHeight + gap * 2;

        replaceButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.replace"), b -> commitAndUpload(true),
                panelX, y, halfW, rowHeight, ACCENT_DANGER);
        addPanelWidget(replaceButton, y);
        createCopyButton = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.create_copy"), b -> commitAndUpload(false),
                panelX + halfW + gap, y, halfW, rowHeight, ACCENT_CONFIRM);
        addPanelWidget(createCopyButton, y);
        y += rowHeight;

        panelContentHeight = y;
        updateUndoRedoButtons();
    }

    private Component lockRatioLabel() {
        return Component.translatable(state.lockAspectRatio()
                ? "gui.crazyphone.photo_edit_screen.lock_ratio_on"
                : "gui.crazyphone.photo_edit_screen.lock_ratio_off");
    }

    /** One "[-] value [+]  [Reset]" row (its own text CAPTION is a separate panel label drawn above it - see
     * addPanelLabel), returning the logical y just past it. Deliberately +/- steppers, not a draggable
     * slider - no version-stable slider base class this project already leans on across every supported
     * version, and a stepper still satisfies "adjust this with a reset button" without betting on one.
     * Mouse-wheel-over-the-row also nudges the value by one step (see mouseScrolled) - not just the buttons. */
    private int addAdjustmentRow(int x, int y, int width, int rowHeight, Component label, float currentValue,
                                  java.util.function.Consumer<Float> onChange, Runnable onReset, float step, String kind) {
        addPanelLabel(label, y);
        // Derived as percentages of the row's OWN width, not fixed pixel constants - at a narrow logical
        // panel width (high GUI scale), fixed widths for +/-/Reset overflowed past the row's right edge and
        // got scissor-clipped (live-reported: "les boutons sont a moitié crop sur la droite").
        int smallButtonW = Math.max(14, width * 16 / 100);
        int resetW = Math.max(30, width * 20 / 100);
        int gap2 = 2;
        int valueLabelW = Math.max(16, width - smallButtonW * 2 - resetW - gap2 * 3);
        int xMinus = x;
        int xValue = xMinus + smallButtonW + gap2;
        int xPlus = xValue + valueLabelW + gap2;
        int xReset = xPlus + smallButtonW + gap2;
        Button minus = accentButton(Component.literal("-"), b -> onChange.accept(clampAdjustment(currentValueOf(kind) - step, kind)),
                xMinus, y, smallButtonW, rowHeight, ACCENT_NEUTRAL);
        addPanelWidget(minus, y);
        Button valueLabel = Button.builder(Component.literal(formatAdjustment(currentValue)), b -> {
        }).bounds(xValue, y, valueLabelW, rowHeight).build();
        valueLabel.active = false;
        addPanelWidget(valueLabel, y);
        Button plus = accentButton(Component.literal("+"), b -> onChange.accept(clampAdjustment(currentValueOf(kind) + step, kind)),
                xPlus, y, smallButtonW, rowHeight, ACCENT_NEUTRAL);
        addPanelWidget(plus, y);
        Button reset = accentButton(Component.translatable("gui.crazyphone.photo_edit_screen.reset"), b -> onReset.run(),
                xReset, y, resetW, rowHeight, ACCENT_RESET);
        addPanelWidget(reset, y);
        switch (kind) {
            case "hue" -> hueValueLabel = valueLabel;
            case "contrast" -> contrastValueLabel = valueLabel;
            case "brightness" -> brightnessValueLabel = valueLabel;
        }
        return y + rowHeight;
    }

    private float currentValueOf(String kind) {
        return switch (kind) {
            case "hue" -> state.hueDegrees();
            case "contrast" -> state.contrast();
            case "brightness" -> state.brightness();
            default -> 0f;
        };
    }

    private float clampAdjustment(float value, String kind) {
        return kind.equals("hue") ? PhotoEditState.wrapDegrees(value) : Math.max(-100f, Math.min(100f, value));
    }

    private static String formatAdjustment(float value) {
        return (value > 0 ? "+" : "") + Math.round(value);
    }

    private PhotoEditState squareCrop() {
        float left = liveCrop != null ? liveCrop[0] : state.cropLeft();
        float top = liveCrop != null ? liveCrop[1] : state.cropTop();
        float right = liveCrop != null ? liveCrop[2] : state.cropRight();
        float bottom = liveCrop != null ? liveCrop[3] : state.cropBottom();
        int imgW = previewSource.getWidth(), imgH = previewSource.getHeight();
        float widthPx = (right - left) * imgW, heightPx = (bottom - top) * imgH;
        float side = Math.min(widthPx, heightPx);
        float centerX = (left + right) / 2f * imgW, centerY = (top + bottom) / 2f * imgH;
        float halfSide = side / 2f;
        return state.withCrop(
                Math.max(0f, (centerX - halfSide) / imgW), Math.max(0f, (centerY - halfSide) / imgH),
                Math.min(1f, (centerX + halfSide) / imgW), Math.min(1f, (centerY + halfSide) / imgH));
    }

    /** "Maximize/fill crop" (live request) - zooms the image in around its own center just enough that the
     * CURRENTLY drawn crop rectangle is fully covered by OPAQUE content on both axes. When the image is
     * rotated, a plain axis-aligned "cover" fit (cropWidth/imageWidth) is wrong - a rotated rectangle's own
     * opaque footprint within the canvas isn't the same as the canvas's own width/height (live-reported:
     * "quand l'image est rotate forcement le fill faut le calculer differement"), so this instead checks
     * each of the 4 crop-rectangle corners against the INVERSE-rotated source rectangle (the same math
     * {@link fr.lordfinn.crazyphone.client.picture.PhotoImageOps#rotateArbitrary} itself samples with) and
     * takes the largest scale any corner needs. Pan is undone first too (pan runs AFTER zoom in the
     * pipeline - see PhotoImageOps#apply - so the crop corners are mapped back into the rotated-but-unpanned
     * frame this check actually reasons about). Never shrinks below 1 - PhotoEditState#withZoom itself
     * clamps to >=1, and computed fresh from zoom=1 every time so repeated presses are idempotent rather
     * than compounding. */
    private PhotoEditState fillCrop() {
        float left = liveCrop != null ? liveCrop[0] : state.cropLeft();
        float top = liveCrop != null ? liveCrop[1] : state.cropTop();
        float right = liveCrop != null ? liveCrop[2] : state.cropRight();
        float bottom = liveCrop != null ? liveCrop[3] : state.cropBottom();
        if (right <= left || bottom <= top)
            return state;
        int imgW = previewSource.getWidth(), imgH = previewSource.getHeight();
        float hw = imgW / 2f, hh = imgH / 2f;
        double rad = Math.toRadians(state.rotationDegrees());
        double cos = Math.cos(rad), sin = Math.sin(rad);
        float panXpx = state.panX() * imgW, panYpx = state.panY() * imgH;
        float[] xs = {(left - 0.5f) * imgW - panXpx, (right - 0.5f) * imgW - panXpx};
        float[] ys = {(top - 0.5f) * imgH - panYpx, (bottom - 0.5f) * imgH - panYpx};
        float scale = 1f;
        for (float x : xs) {
            for (float y : ys) {
                double xp = x * cos + y * sin;
                double yp = -x * sin + y * cos;
                scale = (float) Math.max(scale, Math.max(Math.abs(xp) / hw, Math.abs(yp) / hh));
            }
        }
        return state.withZoom(scale);
    }

    private int currentCroppedWidth() {
        return Math.max(1, Math.round((state.cropRight() - state.cropLeft()) * previewSource.getWidth()));
    }

    private int currentCroppedHeight() {
        return Math.max(1, Math.round((state.cropBottom() - state.cropTop()) * previewSource.getHeight()));
    }

    private void onWidthFieldChanged(String text) {
        if (suppressResizeFieldFeedback) return;
        Integer width = parsePositiveInt(text);
        if (width == null || !state.lockAspectRatio()) return;
        float aspect = (float) currentCroppedWidth() / currentCroppedHeight();
        int height = Math.max(1, Math.round(width / aspect));
        suppressResizeFieldFeedback = true;
        heightField.setValue(String.valueOf(height));
        suppressResizeFieldFeedback = false;
    }

    private void onHeightFieldChanged(String text) {
        if (suppressResizeFieldFeedback) return;
        Integer height = parsePositiveInt(text);
        if (height == null || !state.lockAspectRatio()) return;
        float aspect = (float) currentCroppedWidth() / currentCroppedHeight();
        int width = Math.max(1, Math.round(height * aspect));
        suppressResizeFieldFeedback = true;
        widthField.setValue(String.valueOf(width));
        suppressResizeFieldFeedback = false;
    }

    private static Integer parsePositiveInt(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void applyResizeFields() {
        Integer width = parsePositiveInt(widthField.getValue());
        Integer height = parsePositiveInt(heightField.getValue());
        if (width == null || height == null)
            return;
        commit(state.withResize(width, height));
    }

    /** Scroll-wheel-over-a-resize-field support (live request) - one pixel per tick, explicitly re-running
     * the SAME responder callback setValue would otherwise (maybe) already trigger, so lock-ratio propagation
     * to the other field is guaranteed regardless of whether EditBox#setValue fires it internally on its own. */
    private void adjustResizeField(EditBox field, boolean isWidthField, int delta) {
        Integer current = parsePositiveInt(field.getValue());
        int value = Math.max(1, (current == null ? 1 : current) + delta);
        field.setValue(String.valueOf(value));
        if (isWidthField)
            onWidthFieldChanged(field.getValue());
        else
            onHeightFieldChanged(field.getValue());
    }

    // ---- undo/redo/state --------------------------------------------------------------------------------

    /** Every discrete edit (a stepper click, a rotate, a finished crop drag, a resize apply, a reset) goes
     * through here - pushes the new state as the latest history entry (discarding any redo branch past the
     * current point, standard undo/redo semantics) and refreshes the preview. */
    private void commit(PhotoEditState newState) {
        if (newState.equals(state))
            return;
        state = newState;
        while (history.size() > historyIndex + 1)
            history.remove(history.size() - 1);
        history.add(state);
        historyIndex = history.size() - 1;
        onStateChanged();
    }

    private void undo() {
        if (historyIndex <= 0) return;
        historyIndex--;
        state = history.get(historyIndex);
        onStateChanged();
    }

    private void redo() {
        if (historyIndex >= history.size() - 1) return;
        historyIndex++;
        state = history.get(historyIndex);
        onStateChanged();
    }

    private void onStateChanged() {
        liveCrop = null;
        dragMode = null;
        liveRotationDegrees = null;
        draggingRotateHandle = false;
        draggingImagePan = false;
        refreshPreview();
        updateUndoRedoButtons();
        if (lockRatioButton != null)
            lockRatioButton.setMessage(colorize(lockRatioLabel(), ACCENT_NEUTRAL));
        if (widthField != null && heightField != null) {
            suppressResizeFieldFeedback = true;
            widthField.setValue(String.valueOf(currentCroppedWidth()));
            heightField.setValue(String.valueOf(currentCroppedHeight()));
            suppressResizeFieldFeedback = false;
        }
        if (hueValueLabel != null) hueValueLabel.setMessage(Component.literal(formatAdjustment(state.hueDegrees())));
        if (contrastValueLabel != null) contrastValueLabel.setMessage(Component.literal(formatAdjustment(state.contrast())));
        if (brightnessValueLabel != null) brightnessValueLabel.setMessage(Component.literal(formatAdjustment(state.brightness())));
    }

    private void updateUndoRedoButtons() {
        if (undoButton != null) undoButton.active = historyIndex > 0;
        if (redoButton != null) redoButton.active = historyIndex < history.size() - 1;
    }

    // ---- preview rendering --------------------------------------------------------------------------------

    private void refreshPreview() {
        // liveRotationDegrees/livePan override state.rotationDegrees()/panX/panY for the on-screen preview
        // only, while a handle or the image itself is actively being dragged - see those fields' own doc
        // comments for why the actual commit() waits for mouseReleased instead of firing on every mouse-move.
        PhotoEditState renderState = liveRotationDegrees != null ? state.withRotationDegrees(liveRotationDegrees) : state;
        if (draggingImagePan)
            renderState = renderState.withPan(livePanX, livePanY);
        // Always renders the UNCROPPED (rotated/zoomed/panned/color-adjusted) backdrop, with the real crop
        // rectangle drawn as an overlay on top of it (see drawCropOverlay) - crop and rotate are always
        // both live and both draggable at once now (live request), so there's no separate "crop mode" whose
        // backdrop would show the truly final cropped result instead; Replace/Create Copy still commits the
        // REAL state.crop() fields regardless of what the backdrop happens to show here.
        // withoutCropForPreview(), NOT resetCrop() - resetCrop() ALSO zeroes pan (intentional for the crop
        // "Reset" BUTTON, see its own doc comment), which silently zeroed pan out of THIS preview render
        // too: a live pan drag, and any already-committed pan, never actually showed up on screen even
        // though it was still correctly applied at commit time (caught in a cleanliness pass, not
        // live-reported).
        NativeImage rendered = PhotoImageOps.apply(previewSource, renderState.withoutCropForPreview());
        releasePreviewTexture();
        previewTexture = registerScratchTexture(rendered);
        previewTextureWidth = rendered.getWidth();
        previewTextureHeight = rendered.getHeight();
        // registerScratchTexture hands ownership of `rendered` to the GPU texture it just registered (same
        // "DynamicTexture keeps it readable/eventually closes it" contract FabricPictureCache's own
        // registerDynamicTexture already relies on) - never closed here.
    }

    private void releasePreviewTexture() {
        if (previewTexture != null)
            Minecraft.getInstance().getTextureManager().release(previewTexture);
        previewTexture = null;
    }

    private /*$ res_loc {*/ResourceLocation/*$}*/ registerScratchTexture(NativeImage image) {
        String name = "crazyphone-photo-edit-preview-" + (previewNameCounter++);
        //? if <1.21.10 {
        net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
        texture.setFilter(false, false);
        return Minecraft.getInstance().getTextureManager().register(name, texture);
        //? } else {
        /*net.minecraft.resources./^$ res_loc {^/ResourceLocation/^$}^/ id = fr.lordfinn.crazyphone.Crazyphone.resource(name);
        net.minecraft.client.renderer.texture.DynamicTexture texture = new net.minecraft.client.renderer.texture.DynamicTexture(id::toString, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
        *///?}
    }

    private void layoutPreviewRect() {
        int boxX = 8, boxY = 14;
        int boxW = viewportX0 - 16;
        int boxH = this.height - 28;
        double scale = Math.min((double) boxW / previewTextureWidth, (double) boxH / previewTextureHeight);
        previewW = (int) Math.round(previewTextureWidth * scale);
        previewH = (int) Math.round(previewTextureHeight * scale);
        previewX = boxX + (boxW - previewW) / 2;
        previewY = boxY + (boxH - previewH) / 2;
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        drawEditor(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawEditor(guiGraphics, mouseX, mouseY, partialTick);
    }
    //?}

    private void drawEditor(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (previewTexture == null)
            return;
        layoutPreviewRect();
        guiGraphics.fill(previewX - 2, previewY - 2, previewX + previewW + 2, previewY + previewH + 2, 0xFF1E1E1E);
        GuiCompat.drawTexturedQuad(guiGraphics, previewTexture, previewX, previewY, previewX + previewW, previewY + previewH, 0f, 0f, 1f, 1f);
        // Both always drawn (and both always draggable - see tryStartCropDrag/tryStartRotateDrag) - crop
        // and rotate are complementary tools the player can reach for at the same time, not an exclusive
        // either/or mode (live request).
        drawCropOverlay(guiGraphics);
        drawRotateHandle(guiGraphics);
        drawPanel(guiGraphics, mouseX, mouseY, partialTick);
        if (committing)
            guiGraphics./*$ gui_draw_centered_string {*/drawCenteredString/*$}*/(this.font, Component.translatable("gui.crazyphone.photo_edit_screen.processing"),
                    this.width / 2, this.height - 10, 0xFFFFFFFF);
    }

    /** Draws every panel widget and row caption itself (not left to super.render() - these are registered
     * via addWidget, not addRenderableWidget, specifically so this can clip them to the viewport rather than
     * letting a scrolled-out row paint over the preview image or off the bottom of the screen). */
    private void drawPanel(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.enableScissor(viewportX0, viewportY0, viewportX1, viewportY1);
        for (PanelLabel label : panelLabels) {
            int screenY = viewportY0 + label.logicalY() - scrollOffset - ROW_LABEL_GAP;
            if (screenY + this.font.lineHeight < viewportY0 || screenY > viewportY1)
                continue;
            guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, label.text(), viewportX0, screenY, 0xFFAAAAAA, false);
        }
        for (PanelEntry entry : panelWidgets) {
            if (!entry.widget().visible)
                continue;
            renderPanelWidget(entry.widget(), guiGraphics, mouseX, mouseY, partialTick);
        }
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics);
    }

    //? if <26 {
    private void renderPanelWidget(AbstractWidget widget, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        widget.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    //? } else {
    /*private void renderPanelWidget(AbstractWidget widget, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        widget.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    *///?}

    private void drawScrollbar(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0)
            return;
        int trackX = viewportX1 + 4;
        int trackHeight = viewportY1 - viewportY0;
        int thumbHeight = Math.max(20, trackHeight * trackHeight / panelContentHeight);
        int thumbY = viewportY0 + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        guiGraphics.fill(trackX, viewportY0, trackX + SCROLLBAR_WIDTH, viewportY1, 0x40FFFFFF);
        guiGraphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private int maxScroll() {
        int viewportHeight = viewportY1 - viewportY0;
        return Math.max(0, panelContentHeight - viewportHeight);
    }

    /** Repositions every tracked panel widget (and, implicitly, every panel label - drawPanel reads
     * scrollOffset directly) from its LOGICAL y and the current scrollOffset, hiding (and thereby also
     * disabling click/focus for - AbstractWidget's own isMouseOver already checks .visible) anything that
     * ends up entirely outside the viewport. Called on init and on every scroll. */
    private void repositionPanelWidgets() {
        int clamped = Math.max(0, Math.min(maxScroll(), scrollOffset));
        scrollOffset = clamped;
        for (PanelEntry entry : panelWidgets) {
            int screenY = viewportY0 + entry.logicalY() - scrollOffset;
            entry.widget().setY(screenY);
            entry.widget().visible = screenY + entry.widget().getHeight() > viewportY0 && screenY < viewportY1;
        }
    }

    private void drawCropOverlay(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        float left = liveCrop != null ? liveCrop[0] : state.cropLeft();
        float top = liveCrop != null ? liveCrop[1] : state.cropTop();
        float right = liveCrop != null ? liveCrop[2] : state.cropRight();
        float bottom = liveCrop != null ? liveCrop[3] : state.cropBottom();
        int rx0 = previewX + Math.round(left * previewW), ry0 = previewY + Math.round(top * previewH);
        int rx1 = previewX + Math.round(right * previewW), ry1 = previewY + Math.round(bottom * previewH);

        int dim = 0xA0000000;
        guiGraphics.fill(previewX, previewY, previewX + previewW, ry0, dim);
        guiGraphics.fill(previewX, ry1, previewX + previewW, previewY + previewH, dim);
        guiGraphics.fill(previewX, ry0, rx0, ry1, dim);
        guiGraphics.fill(rx1, ry0, previewX + previewW, ry1, dim);

        int border = 0xFFFFFFFF;
        guiGraphics.fill(rx0, ry0, rx1, ry0 + 1, border);
        guiGraphics.fill(rx0, ry1 - 1, rx1, ry1, border);
        guiGraphics.fill(rx0, ry0, rx0 + 1, ry1, border);
        guiGraphics.fill(rx1 - 1, ry0, rx1, ry1, border);

        int h = CROP_HANDLE_PX;
        int handleColor = 0xFFFFDD55;
        guiGraphics.fill(rx0 - h, ry0 - h, rx0 + h, ry0 + h, handleColor);
        guiGraphics.fill(rx1 - h, ry0 - h, rx1 + h, ry0 + h, handleColor);
        guiGraphics.fill(rx1 - h, ry1 - h, rx1 + h, ry1 + h, handleColor);
        guiGraphics.fill(rx0 - h, ry1 - h, rx0 + h, ry1 + h, handleColor);
    }

    // ---- rotation handle: draw / hit-test / drag ---------------------------------------------------------

    private double rotateHandleCenterX() {
        return previewX + previewW / 2.0;
    }

    private double rotateHandleCenterY() {
        return previewY + previewH / 2.0;
    }

    private double rotateHandleRadius() {
        return Math.min(previewW, previewH) / 2.0 + ROTATE_HANDLE_GAP;
    }

    private double[] rotateHandlePosition(float degrees) {
        double rad = Math.toRadians(degrees);
        double radius = rotateHandleRadius();
        return new double[]{rotateHandleCenterX() + radius * Math.sin(rad), rotateHandleCenterY() - radius * Math.cos(rad)};
    }

    /** A short dotted spoke from the image's own center out to the handle, plus the handle itself - the
     * only visual affordance for "this is draggable, and this is the current angle" (GuiGraphics has no
     * line-drawing primitive to reach for here, only axis-aligned fill() rectangles, hence the dots rather
     * than a solid line). */
    private void drawRotateHandle(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (previewW <= 0 || previewH <= 0)
            return;
        float degrees = liveRotationDegrees != null ? liveRotationDegrees : state.rotationDegrees();
        double cx = rotateHandleCenterX(), cy = rotateHandleCenterY();
        double[] pos = rotateHandlePosition(degrees);
        int dots = 10;
        for (int i = 1; i < dots; i++) {
            double t = i / (double) dots;
            int lx = (int) Math.round(cx + (pos[0] - cx) * t);
            int ly = (int) Math.round(cy + (pos[1] - cy) * t);
            guiGraphics.fill(lx - 1, ly - 1, lx + 1, ly + 1, 0x90FFFFFF);
        }
        int h = ROTATE_HANDLE_PX;
        int handleColor = draggingRotateHandle ? 0xFFFFFFFF : 0xFFFFDD55;
        int hx = (int) Math.round(pos[0]), hy = (int) Math.round(pos[1]);
        guiGraphics.fill(hx - h, hy - h, hx + h, hy + h, handleColor);
    }

    private boolean tryStartRotateDrag(double mouseX, double mouseY) {
        if (previewW <= 0 || previewH <= 0)
            return false;
        double[] pos = rotateHandlePosition(state.rotationDegrees());
        double hitRadius = ROTATE_HANDLE_PX + 3;
        if (Math.abs(mouseX - pos[0]) > hitRadius || Math.abs(mouseY - pos[1]) > hitRadius)
            return false;
        draggingRotateHandle = true;
        liveRotationDegrees = state.rotationDegrees();
        return true;
    }

    private void updateRotateDrag(double mouseX, double mouseY) {
        double cx = rotateHandleCenterX(), cy = rotateHandleCenterY();
        double angleRad = Math.atan2(mouseX - cx, -(mouseY - cy)); // 0 degrees = straight up, clockwise positive
        liveRotationDegrees = PhotoEditState.wrapDegrees((float) Math.toDegrees(angleRad));
        refreshPreview();
    }

    private void finishRotateDrag() {
        draggingRotateHandle = false;
        if (liveRotationDegrees != null) {
            float finalDegrees = liveRotationDegrees;
            liveRotationDegrees = null;
            commit(state.withRotationDegrees(finalDegrees));
        }
    }

    // ---- crop drag input --------------------------------------------------------------------------------

    private boolean tryStartCropDrag(double mouseX, double mouseY) {
        if (previewW <= 0 || previewH <= 0)
            return false;
        float left = state.cropLeft(), top = state.cropTop(), right = state.cropRight(), bottom = state.cropBottom();
        int rx0 = previewX + Math.round(left * previewW), ry0 = previewY + Math.round(top * previewH);
        int rx1 = previewX + Math.round(right * previewW), ry1 = previewY + Math.round(bottom * previewH);
        int h = CROP_HANDLE_PX + 2;

        // Corner handles are checked BEFORE the strict "inside the image" bounds check below - a crop
        // rectangle that touches the image's own edge (the default full-frame crop, before the player ever
        // drags it in) has HALF of each corner handle's own visible square sitting just outside the image
        // bounds, and that half needs to stay clickable too (live-reported: "la handle n'est utilisable que
        // dans le coin dans l'image, pas sur tout le carre jaune").
        int corner = hitTestCorner(mouseX, mouseY, rx0, ry0, rx1, ry1, h);
        if (corner >= 0) {
            liveCrop = new float[]{left, top, right, bottom};
            dragMode = corner;
            return true;
        }
        if (mouseX < previewX || mouseX > previewX + previewW || mouseY < previewY || mouseY > previewY + previewH)
            return false;
        if (mouseX >= rx0 && mouseX <= rx1 && mouseY >= ry0 && mouseY <= ry1) {
            // LEFT-click inside the rectangle does nothing on purpose - it used to pan the image here, but
            // that made left-click ambiguous with resizing/redrawing the rectangle itself (live-reported:
            // "quand je clique ca change la zone croppee, je ne peux pas move l'image derriere"). Panning is
            // now its own dedicated RIGHT-click-drag gesture over the whole preview (see
            // tryStartImagePanDrag) - left click stays purely a crop tool (corners resize, outside draws a
            // new rectangle), right click is purely the pan tool, so the two never fight over the same click.
            return false;
        }
        dragMode = -2;
        dragAnchorFracX = (mouseX - previewX) / previewW;
        dragAnchorFracY = (mouseY - previewY) / previewH;
        liveCrop = new float[]{(float) dragAnchorFracX, (float) dragAnchorFracY, (float) dragAnchorFracX, (float) dragAnchorFracY};
        return true;
    }

    // ---- image panning (RIGHT-click-drag anywhere over the preview) -------------------------------------

    private boolean tryStartImagePanDrag(double mouseX, double mouseY) {
        if (previewW <= 0 || previewH <= 0)
            return false;
        if (mouseX < previewX || mouseX > previewX + previewW || mouseY < previewY || mouseY > previewY + previewH)
            return false;
        draggingImagePan = true;
        panDragStartMouseX = mouseX;
        panDragStartMouseY = mouseY;
        panDragStartPanX = state.panX();
        panDragStartPanY = state.panY();
        livePanX = panDragStartPanX;
        livePanY = panDragStartPanY;
        return true;
    }

    // Raw, deliberately unclamped here - PhotoEditState#withPan itself clamps, and both consumers of these
    // fields (refreshPreview's own withPan call, and finishPanDrag's commit below) already go through it,
    // so a second copy of the same clamp here would just be redundant (caught in a cleanliness pass).
    private void updatePanDrag(double mouseX, double mouseY) {
        float dxFrac = (float) ((mouseX - panDragStartMouseX) / previewW);
        float dyFrac = (float) ((mouseY - panDragStartMouseY) / previewH);
        livePanX = panDragStartPanX + dxFrac;
        livePanY = panDragStartPanY + dyFrac;
        refreshPreview();
    }

    private void finishPanDrag() {
        draggingImagePan = false;
        commit(state.withPan(livePanX, livePanY));
    }

    private int hitTestCorner(double mouseX, double mouseY, int rx0, int ry0, int rx1, int ry1, int radius) {
        if (Math.abs(mouseX - rx0) <= radius && Math.abs(mouseY - ry0) <= radius) return 0; // TL
        if (Math.abs(mouseX - rx1) <= radius && Math.abs(mouseY - ry0) <= radius) return 1; // TR
        if (Math.abs(mouseX - rx1) <= radius && Math.abs(mouseY - ry1) <= radius) return 2; // BR
        if (Math.abs(mouseX - rx0) <= radius && Math.abs(mouseY - ry1) <= radius) return 3; // BL
        return -1;
    }

    private void updateCropDrag(double mouseX, double mouseY) {
        if (dragMode == null || liveCrop == null)
            return;
        float fx = clamp01((float) ((mouseX - previewX) / previewW));
        float fy = clamp01((float) ((mouseY - previewY) / previewH));
        switch (dragMode) {
            case -2 -> {
                liveCrop[0] = Math.min((float) dragAnchorFracX, fx);
                liveCrop[1] = Math.min((float) dragAnchorFracY, fy);
                liveCrop[2] = Math.max((float) dragAnchorFracX, fx);
                liveCrop[3] = Math.max((float) dragAnchorFracY, fy);
            }
            case 0 -> { // TL
                liveCrop[0] = Math.min(fx, liveCrop[2]);
                liveCrop[1] = Math.min(fy, liveCrop[3]);
            }
            case 1 -> { // TR
                liveCrop[2] = Math.max(fx, liveCrop[0]);
                liveCrop[1] = Math.min(fy, liveCrop[3]);
            }
            case 2 -> { // BR
                liveCrop[2] = Math.max(fx, liveCrop[0]);
                liveCrop[3] = Math.max(fy, liveCrop[1]);
            }
            case 3 -> { // BL
                liveCrop[0] = Math.min(fx, liveCrop[2]);
                liveCrop[3] = Math.max(fy, liveCrop[1]);
            }
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void finishCropDrag() {
        if (dragMode == null || liveCrop == null) {
            dragMode = null;
            return;
        }
        float width = liveCrop[2] - liveCrop[0], height = liveCrop[3] - liveCrop[1];
        dragMode = null;
        float[] finished = liveCrop;
        liveCrop = null;
        // Too small to be a real selection (a stray click, not a drag) - ignore rather than committing a
        // degenerate near-zero crop.
        if (width < 0.02f || height < 0.02f)
            return;
        commit(state.withCrop(finished[0], finished[1], finished[2], finished[3]));
    }

    // ---- scrollbar drag input ----------------------------------------------------------------------------

    private boolean isOverScrollbarTrack(double mouseX, double mouseY) {
        if (maxScroll() <= 0)
            return false;
        int trackX = viewportX1 + 4;
        return mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH && mouseY >= viewportY0 && mouseY <= viewportY1;
    }

    private boolean tryStartScrollbarDrag(double mouseX, double mouseY) {
        if (!isOverScrollbarTrack(mouseX, mouseY))
            return false;
        draggingScrollbar = true;
        updateScrollbarDrag(mouseY);
        return true;
    }

    /** Jump-to-click-position, same convenience most vanilla-adjacent scrollbars offer - clicking anywhere
     * on the track (not just precisely on the thumb) starts a drag from there, not just a fixed-size nudge. */
    private void updateScrollbarDrag(double mouseY) {
        int trackHeight = viewportY1 - viewportY0;
        int thumbHeight = Math.max(20, trackHeight * trackHeight / Math.max(1, panelContentHeight));
        double usable = trackHeight - thumbHeight;
        double frac = usable <= 0 ? 0 : (mouseY - viewportY0 - thumbHeight / 2.0) / usable;
        scrollOffset = Math.round(clamp01((float) frac) * maxScroll());
        repositionPanelWidgets();
    }

    // ---- mouse wheel: adjustment rows / resize fields / panel scroll ------------------------------------

    /** Shared logic both mouseScrolled overloads below delegate to - real 1.20.1 vanilla predates
     * GuiEventListener's horizontal-scroll parameter (added by 1.20.4, same split CrazyPhoneMyPhotosScreenScreen's
     * own mouseScrolled already needed), and this editor never reads horizontal scroll anyway. Returns true
     * if something was consumed (an adjustment row, a resize field, or the panel itself scrolled). */
    private boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY == 0)
            return false;
        if (tryScrollAdjustmentRow(mouseX, mouseY, scrollY))
            return true;
        if (tryScrollResizeField(mouseX, mouseY, scrollY))
            return true;
        if (tryScrollZoomOverPreview(mouseX, mouseY, scrollY))
            return true;
        if (mouseX >= viewportX0 && mouseX <= viewportX1 + SCROLLBAR_WIDTH + 8
                && mouseY >= viewportY0 && mouseY <= viewportY1 && maxScroll() > 0) {
            scrollOffset -= Math.round(scrollY * 16);
            repositionPanelWidgets();
            return true;
        }
        return false;
    }

    //? if >=1.20.4 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (handleMouseScrolled(mouseX, mouseY, scrollY))
            return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?}
    //? if <1.20.4 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (handleMouseScrolled(mouseX, mouseY, scrollY))
            return true;
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }
    *///?}

    private boolean tryScrollAdjustmentRow(double mouseX, double mouseY, double scrollY) {
        float sign = (float) Math.signum(scrollY);
        if (isRowHovered(hueValueLabel, mouseX, mouseY)) {
            commit(state.withHue(clampAdjustment(state.hueDegrees() + sign * HUE_STEP, "hue")));
            return true;
        }
        if (isRowHovered(contrastValueLabel, mouseX, mouseY)) {
            commit(state.withContrast(clampAdjustment(state.contrast() + sign * CONTRAST_STEP, "contrast")));
            return true;
        }
        if (isRowHovered(brightnessValueLabel, mouseX, mouseY)) {
            commit(state.withBrightness(clampAdjustment(state.brightness() + sign * BRIGHTNESS_STEP, "brightness")));
            return true;
        }
        return false;
    }

    private boolean isRowHovered(Button rowValueLabel, double mouseX, double mouseY) {
        if (rowValueLabel == null || !rowValueLabel.visible)
            return false;
        return mouseX >= viewportX0 && mouseX <= viewportX1
                && mouseY >= rowValueLabel.getY() && mouseY <= rowValueLabel.getY() + rowValueLabel.getHeight();
    }

    /** Scroll-wheel-over-the-preview-image zooms the image in/out within the crop area (live request) -
     * reuses the same {@link PhotoEditState#withZoom} centered-zoom the "Fill" crop button already drives,
     * just nudged one step per tick instead of jumped to an exact cover-fit value. */
    private boolean tryScrollZoomOverPreview(double mouseX, double mouseY, double scrollY) {
        if (previewW <= 0 || previewH <= 0)
            return false;
        if (mouseX < previewX || mouseX > previewX + previewW || mouseY < previewY || mouseY > previewY + previewH)
            return false;
        float sign = (float) Math.signum(scrollY);
        commit(state.withZoom(state.zoomScale() + sign * ZOOM_STEP));
        return true;
    }

    private boolean tryScrollResizeField(double mouseX, double mouseY, double scrollY) {
        int delta = (int) Math.signum(scrollY);
        if (widthField != null && widthField.visible && widthField.isMouseOver(mouseX, mouseY)) {
            adjustResizeField(widthField, true, delta);
            return true;
        }
        if (heightField != null && heightField.visible && heightField.isMouseOver(mouseX, mouseY)) {
            adjustResizeField(heightField, false, delta);
            return true;
        }
        return false;
    }

    //? if <26 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tryStartScrollbarDrag(mouseX, mouseY))
            return true;
        if (button == 0 && tryStartCropDrag(mouseX, mouseY))
            return true;
        if (button == 0 && tryStartRotateDrag(mouseX, mouseY))
            return true;
        if (button == 1 && tryStartImagePanDrag(mouseX, mouseY))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollbarDrag(mouseY);
            return true;
        }
        if (dragMode != null) {
            updateCropDrag(mouseX, mouseY);
            return true;
        }
        if (draggingImagePan) {
            updatePanDrag(mouseX, mouseY);
            return true;
        }
        if (draggingRotateHandle) {
            updateRotateDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (dragMode != null) {
            finishCropDrag();
            return true;
        }
        if (draggingImagePan) {
            finishPanDrag();
            return true;
        }
        if (draggingRotateHandle) {
            finishRotateDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    //? } else {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && tryStartScrollbarDrag(event.x(), event.y()))
            return true;
        if (event.button() == 0 && tryStartCropDrag(event.x(), event.y()))
            return true;
        if (event.button() == 0 && tryStartRotateDrag(event.x(), event.y()))
            return true;
        if (event.button() == 1 && tryStartImagePanDrag(event.x(), event.y()))
            return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollbarDrag(event.y());
            return true;
        }
        if (dragMode != null) {
            updateCropDrag(event.x(), event.y());
            return true;
        }
        if (draggingImagePan) {
            updatePanDrag(event.x(), event.y());
            return true;
        }
        if (draggingRotateHandle) {
            updateRotateDrag(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (dragMode != null) {
            finishCropDrag();
            return true;
        }
        if (draggingImagePan) {
            finishPanDrag();
            return true;
        }
        if (draggingRotateHandle) {
            finishRotateDrag();
            return true;
        }
        return super.mouseReleased(event);
    }
    *///?}

    // ---- commit (Replace / Create Copy) ------------------------------------------------------------------

    private void commitAndUpload(boolean replaceOriginal) {
        if (committing)
            return;
        committing = true;
        replaceButton.active = false;
        createCopyButton.active = false;
        PhotoEditState finalState = state;
        CompletableFuture.supplyAsync(() -> renderFinal(finalState), COMMIT_IO)
                .thenAcceptAsync(result -> onCommitComplete(result, replaceOriginal), Minecraft.getInstance());
    }

    private record CommitResult(byte[] thumbnailPng, byte[] fullPng) {
    }

    /** Runs off-thread (COMMIT_IO) - the ONLY point in the whole editor that ever touches the full-resolution
     * {@link #original}/{@link #animation}, and only once per Replace/Create Copy click, so a big photo's
     * worth of pixel work never has to happen on every preview update the way it would if this ran against
     * the full image live. */
    @javax.annotation.Nullable
    private CommitResult renderFinal(PhotoEditState finalState) {
        return animation != null ? renderFinalAnimated(finalState) : renderFinalStatic(finalState);
    }

    /** The SAME {@code finalState} re-applied to every one of {@link #animation}'s own frames (see this
     * class's own doc comment on why this edits uniformly, not frame-by-frame) - then re-encoded through
     * the exact same "CPAG" container path {@link fr.lordfinn.crazyphone.client.picture.PhotoImporter}
     * itself uses for a freshly-imported animated GIF. */
    @javax.annotation.Nullable
    private CommitResult renderFinalAnimated(PhotoEditState finalState) {
        List<AnimatedPhotoCodec.RawFrame> editedFrames = new ArrayList<>(animation.frames().size());
        try {
            for (AnimatedPhotoCodec.RawFrame frame : animation.frames())
                editedFrames.add(new AnimatedPhotoCodec.RawFrame(PhotoImageOps.apply(frame.image(), finalState), frame.delayMillis()));
        } catch (Exception e) {
            for (AnimatedPhotoCodec.RawFrame frame : editedFrames)
                frame.image().close();
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Failed to render edited animated photo {}", photoId, e);
            return null;
        }
        int width = editedFrames.get(0).image().getWidth(), height = editedFrames.get(0).image().getHeight();
        try (AnimatedPhotoCodec.Animation edited = new AnimatedPhotoCodec.Animation(editedFrames, width, height)) {
            // Cap frames at whatever the animation already has - no reason to drop any unless the upload
            // budget genuinely forces it (encodeWithinBudget itself shrinks this further if needed).
            byte[] fullPng = AnimatedPhotoCodec.encodeWithinBudget(edited, Config.photoFullMaxDimension,
                    edited.frames().size(), Config.photoFullMaxUploadBytes);
            if (fullPng == null)
                return null;
            NativeImage firstFrame = edited.frames().get(0).image();
            int thumbnailHeight = Config.photoThumbnailPixelHeight;
            byte[] thumbnailPng;
            if (thumbnailHeight <= 0 || thumbnailHeight >= firstFrame.getHeight()) {
                thumbnailPng = PhotoImageOps.toPngBytes(firstFrame);
            } else {
                try (NativeImage thumbnail = PixelArtDownscaler.downscaleToHeight(firstFrame, thumbnailHeight)) {
                    thumbnailPng = PhotoImageOps.toPngBytes(thumbnail);
                }
            }
            return new CommitResult(thumbnailPng, fullPng);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Failed to render edited animated photo {}", photoId, e);
            return null;
        }
    }

    @javax.annotation.Nullable
    private CommitResult renderFinalStatic(PhotoEditState finalState) {
        try (NativeImage edited = PhotoImageOps.apply(original, finalState)) {
            int maxDimension = Config.photoFullMaxDimension;
            int uploadLimit = Config.photoFullMaxUploadBytes;
            byte[] fullPng;
            NativeImage forUpload = edited;
            boolean ownsForUpload = false;
            while (true) {
                NativeImage candidate = (forUpload.getWidth() <= maxDimension && forUpload.getHeight() <= maxDimension) ? forUpload
                        : PhotoImageOps.resize(forUpload, scaledDimension(forUpload.getWidth(), forUpload, maxDimension),
                        scaledDimension(forUpload.getHeight(), forUpload, maxDimension));
                fullPng = PhotoImageOps.toPngBytes(candidate);
                if (candidate != forUpload) {
                    if (ownsForUpload)
                        forUpload.close();
                    forUpload = candidate;
                    ownsForUpload = true;
                }
                if (fullPng.length <= uploadLimit || maxDimension <= 128)
                    break;
                maxDimension = Math.max(128, maxDimension * 3 / 4);
            }
            int thumbnailHeight = Config.photoThumbnailPixelHeight;
            byte[] thumbnailPng;
            if (thumbnailHeight <= 0 || thumbnailHeight >= forUpload.getHeight()) {
                thumbnailPng = fullPng;
            } else {
                try (NativeImage thumbnail = PixelArtDownscaler.downscaleToHeight(forUpload, thumbnailHeight)) {
                    thumbnailPng = PhotoImageOps.toPngBytes(thumbnail);
                }
            }
            if (ownsForUpload)
                forUpload.close();
            return new CommitResult(thumbnailPng, fullPng);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("crazyphone").warn("Failed to render edited photo {}", photoId, e);
            return null;
        }
    }

    private static int scaledDimension(int dimension, NativeImage reference, int maxDimension) {
        double scale = (double) maxDimension / Math.max(reference.getWidth(), reference.getHeight());
        return Math.max(1, Math.round((float) (dimension * scale)));
    }

    private void onCommitComplete(@javax.annotation.Nullable CommitResult result, boolean replaceOriginal) {
        committing = false;
        if (this.minecraft == null)
            return;
        if (result == null) {
            replaceButton.active = true;
            createCopyButton.active = true;
            if (this.minecraft.player != null)
                fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(this.minecraft.player,
                        Component.translatable("message.crazyphone.photo_edit_failed"), true);
            return;
        }
        UUID newPhotoId = UUID.randomUUID();
        FabricPictureCache.seedFromLocalCapture(newPhotoId, result.thumbnailPng(), result.fullPng());
        // Edited from a held Photo item: the result becomes a physical item (creative, or one Paper consumed).
        // Edited from the phone (gallery or conversation): the result stays in the phone's gallery.
        // Replace on a held photo swaps that item over to the edited image; on the phone it swaps the gallery entry.
        boolean toPhysicalItem = viewerScreen.getOrigin() == CrazyPhonePhotoViewerScreen.Origin.HELD_ITEM;
        UUID replaceHeldPhotoId = toPhysicalItem && replaceOriginal ? photoId : null;
        NetworkAccess.sendToServer(new CrazyPhoneUploadPicturePacket("", newPhotoId, result.thumbnailPng(), result.fullPng(), toPhysicalItem, replaceHeldPhotoId));
        if (replaceOriginal && !toPhysicalItem)
            NetworkAccess.sendToServer(new CrazyPhoneMyPhotosActionMessage(CrazyPhoneMyPhotosActionMessage.Action.DELETE, List.of(photoId), ""));
        if (this.minecraft.player != null) {
            this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1f, 1f);
            // Sent before the server replies, so for a physical item the wording can't claim where it landed
            // (paper may be missing in survival - the server then sends its own follow-up message).
            String messageKey = replaceOriginal ? "message.crazyphone.photo_edit_replaced"
                    : toPhysicalItem ? "message.crazyphone.photo_edit_copy_created"
                    : "message.crazyphone.photo_edit_copy_saved";
            fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.sendClientMessage(this.minecraft.player, Component.translatable(messageKey), true);
        }
        closeTarget = viewerScreen.getPreviousScreen();
        if (closeTarget instanceof CrazyPhoneMyPhotosScreenScreen gallery) {
            if (replaceOriginal)
                gallery.getPhotoIds().remove(photoId);
            gallery.getPhotoIds().add(0, newPhotoId);
        }
        onClose();
    }

    // ---- lifecycle --------------------------------------------------------------------------------------

    @Override
    public void onClose() {
        releasePreviewTexture();
        if (animation != null) {
            // Closes EVERY frame, including the one `original` itself points at - original is just a
            // reference into this animation's own frame list for an animated photo, not a separate image,
            // so it must not also be closed below (that would double-close the same native buffer).
            animation.close();
            animation = null;
            original = null;
        } else if (original != null) {
            original.close();
            original = null;
        }
        if (previewSource != null) {
            previewSource.close();
            previewSource = null;
        }
        Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(closeTarget);
    }
}
