package fr.lordfinn.crazyphone.client.picture;

/**
 * Every adjustment the photo editor (CrazyPhonePhotoEditScreen) can apply, as plain numbers rather than
 * pixels - cheap enough that the screen's own undo/redo stack (see {@link fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoEditScreen})
 * just keeps a list of these instead of a list of intermediate images. The actual pixel work always starts
 * fresh from the ORIGINAL, never-modified image (see {@link PhotoImageOps#apply}) - re-deriving the full
 * result from one state object on every change is what makes "reset a single slider" and "undo" both exact
 * and trivial, with no rounding drift from repeatedly re-applying adjustments on top of each other.
 * <p>
 * {@code rotationDegrees} is an arbitrary angle (not limited to 90-degree steps), applied around the
 * image's own center on a canvas the SAME size as the source - any corner the rotated content no longer
 * covers comes out fully transparent rather than filled with a background color (see
 * {@link PhotoImageOps#rotateArbitrary}). {@code zoomScale} (>=1) zooms the ROTATED image in around its own
 * center (a centered crop-then-resize-back-to-the-same-canvas, see {@link PhotoImageOps#applyZoom}) -
 * primarily there so the "fill crop" convenience can grow the image just enough that a drawn crop rectangle
 * never lands on a rotation-exposed transparent corner, but it's a free-standing zoom either way.
 * {@code panX/panY} (fractions of the canvas's own width/height) slide the rotated+zoomed image around
 * within that same canvas (again with transparent fill for whatever edge it exposes) - this is what lets
 * the player reposition the PHOTO underneath a crop rectangle that stays fixed in place (see
 * {@link PhotoImageOps#applyPan}).
 * <p>
 * {@code cropLeft/Top/Right/Bottom} are fractions (0..1) of the ROTATED+ZOOMED+PANNED image, which is always
 * the SAME pixel size as the original regardless of angle/zoom/pan - the crop rectangle itself never
 * rotates, scales, or slides with the image; it's a fixed, axis-aligned window the player resizes from its
 * own corners (or redraws from scratch), and whatever ends up inside it (opaque or transparent) becomes the
 * final image. Rotation/zoom/pan deliberately never reset each other or the crop rectangle when changed -
 * they're meant to be dialed in together, watching the same fixed crop window the whole time, not as
 * mutually-destructive exclusive steps. {@code targetWidth/targetHeight} are pixel dimensions of the FINAL
 * image after cropping - {@code -1} means "no explicit resize, keep the cropped area's own size" (this is
 * the state a fresh edit starts in, and what each field's own Reset returns to).
 */
public record PhotoEditState(
        float hueDegrees,
        float contrast,
        float brightness,
        float rotationDegrees,
        float zoomScale,
        float panX, float panY,
        float cropLeft, float cropTop, float cropRight, float cropBottom,
        int targetWidth, int targetHeight,
        boolean lockAspectRatio
) {
    public static final PhotoEditState DEFAULT = new PhotoEditState(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, -1, -1, true);

    public boolean hasResize() {
        return targetWidth > 0 && targetHeight > 0;
    }

    public boolean hasCrop() {
        return cropLeft > 0.0001f || cropTop > 0.0001f || cropRight < 0.9999f || cropBottom < 0.9999f;
    }

    public boolean hasZoom() {
        return zoomScale > 1.0001f;
    }

    public boolean hasPan() {
        return Math.abs(panX) > 0.0001f || Math.abs(panY) > 0.0001f;
    }

    public PhotoEditState withHue(float value) {
        return new PhotoEditState(value, contrast, brightness, rotationDegrees, zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    public PhotoEditState withContrast(float value) {
        return new PhotoEditState(hueDegrees, value, brightness, rotationDegrees, zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    public PhotoEditState withBrightness(float value) {
        return new PhotoEditState(hueDegrees, contrast, value, rotationDegrees, zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    /** Deliberately does NOT touch crop, zoom or pan - see this record's own doc comment on why
     * rotation/zoom/pan/crop never reset each other. */
    public PhotoEditState withRotationDegrees(float value) {
        return new PhotoEditState(hueDegrees, contrast, brightness, wrapDegrees(value), zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    /** Public so the screen's own rotate-handle drag math (angle from mouse position, easily past +/-360)
     * can wrap through the exact same formula rather than keeping a second copy. */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) wrapped -= 360f;
        if (wrapped < -180f) wrapped += 360f;
        return wrapped;
    }

    /** Deliberately does NOT touch crop or pan - see this record's own doc comment. */
    public PhotoEditState withZoom(float value) {
        float clamped = Math.max(1f, Math.min(8f, value));
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, clamped, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    /** Deliberately does NOT touch crop - see this record's own doc comment. Loosely clamped (not to the
     * exact point where content stops overlapping the canvas at all) just to keep the player from dragging
     * into a degenerate, entirely-off-screen state by accident. */
    public PhotoEditState withPan(float x, float y) {
        float clampedX = Math.max(-1.5f, Math.min(1.5f, x));
        float clampedY = Math.max(-1.5f, Math.min(1.5f, y));
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, clampedX, clampedY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, lockAspectRatio);
    }

    public PhotoEditState withCrop(float left, float top, float right, float bottom) {
        // Clearing any previous resize target on a NEW crop - a target picked for one crop rectangle's own
        // aspect ratio rarely still makes sense for a different one, and hasResize()/hasCrop() both reading
        // "-1" as "not set" keeps Reset semantics simple (see this record's own doc comment on always
        // starting from the original rather than compounding).
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, panX, panY, left, top, right, bottom, -1, -1, lockAspectRatio);
    }

    public PhotoEditState withResize(int width, int height) {
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, width, height, lockAspectRatio);
    }

    public PhotoEditState withLockAspectRatio(boolean value) {
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, panX, panY, cropLeft, cropTop, cropRight, cropBottom, targetWidth, targetHeight, value);
    }

    public PhotoEditState resetHue() {
        return withHue(DEFAULT.hueDegrees());
    }

    public PhotoEditState resetContrast() {
        return withContrast(DEFAULT.contrast());
    }

    public PhotoEditState resetBrightness() {
        return withBrightness(DEFAULT.brightness());
    }

    public PhotoEditState resetRotation() {
        return withRotationDegrees(0f);
    }

    public PhotoEditState resetZoom() {
        return withZoom(1f);
    }

    /** Also resets pan back to centered - both concern "where the crop rectangle's fixed window ends up
     * landing on the image", so a fresh crop rectangle starting from a panned-away position would be
     * confusing rather than helpful. Only for the "Reset" BUTTON - see {@link #withoutCropForPreview} for
     * the separate, pan-preserving read the live preview's own backdrop needs instead. */
    public PhotoEditState resetCrop() {
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, 0f, 0f, 0f, 0f, 1f, 1f, -1, -1, lockAspectRatio);
    }

    /** Full-frame crop (and no resize target) with EVERYTHING else - rotation, zoom, and crucially pan -
     * left untouched, unlike {@link #resetCrop}. The live preview's own backdrop always renders through
     * this (see CrazyPhonePhotoEditScreen#refreshPreview): it shows the whole rotated/zoomed/panned canvas
     * with the real crop rectangle drawn as a separate overlay on top, never baked into the rendered pixels
     * - using resetCrop() there instead silently zeroed pan out of the preview too (a live drag, and any
     * already-committed pan, simply never showed up on screen even though it was still applied correctly
     * at commit time - caught in a cleanliness pass, not live-reported). */
    public PhotoEditState withoutCropForPreview() {
        return new PhotoEditState(hueDegrees, contrast, brightness, rotationDegrees, zoomScale, panX, panY, 0f, 0f, 1f, 1f, -1, -1, lockAspectRatio);
    }

    public PhotoEditState resetResize() {
        return withResize(-1, -1);
    }
}
