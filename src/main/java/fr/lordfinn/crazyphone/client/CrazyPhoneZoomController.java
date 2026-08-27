package fr.lordfinn.crazyphone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Drives the capture overlay's zoom the way real zoom mods do it (OptiFine's C-key zoom, Zoomify, and
 * similar) - not just a bare FOV snap: narrow the field of view, proportionally reduce mouse sensitivity so
 * panning doesn't feel wild at high zoom, and smoothly interpolate both toward their target every tick
 * instead of jumping. All three come from temporarily overriding two existing vanilla option values and
 * restoring them when the overlay closes - no mixin, no new rendering machinery: vanilla's own
 * GameRenderer/mouse-look code already reads these options every frame, so the live preview is the real
 * zoomed-and-smoothed view for free, and the eventual screenshot naturally captures exactly what was framed.
 */
public final class CrazyPhoneZoomController {
    private static final float MIN_ZOOM = 1.0f;
    private static final float MAX_ZOOM = 4.0f;
    private static final float ZOOM_STEP = 0.25f;
    private static final float LERP_SPEED = 0.35f;

    private final int baseFov;
    private final double baseSensitivity;
    private float targetZoom = MIN_ZOOM;
    private float currentZoom = MIN_ZOOM;

    public CrazyPhoneZoomController(Minecraft mc) {
        this.baseFov = mc.options.fov().get();
        this.baseSensitivity = mc.options.sensitivity().get();
    }

    public void adjust(double scrollDelta) {
        targetZoom = Mth.clamp(targetZoom + (float) scrollDelta * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
    }

    /** Called once per client tick while the overlay is open - lerps toward the target zoom, then
     * re-derives FOV and sensitivity from the interpolated value so both move together smoothly. */
    public void tick(Minecraft mc) {
        currentZoom += (targetZoom - currentZoom) * LERP_SPEED;
        mc.options.fov().set(Math.round(baseFov / currentZoom));
        mc.options.sensitivity().set(baseSensitivity / currentZoom);
    }

    public float currentZoom() {
        return currentZoom;
    }

    public void restore(Minecraft mc) {
        mc.options.fov().set(baseFov);
        mc.options.sensitivity().set(baseSensitivity);
    }
}
