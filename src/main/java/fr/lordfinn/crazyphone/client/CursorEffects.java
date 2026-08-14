package fr.lordfinn.crazyphone.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Minecraft has no built-in per-widget "hover cursor" concept (unlike CSS's cursor: pointer), but since
 * it runs on GLFW under the hood, mods can swap the OS cursor icon directly. Widgets call
 * {@link #requestPointerCursor()} (generic hand, for buttons) or {@link #requestZoomCursor()} (a
 * procedurally-drawn magnifying glass, for hoverable images - GLFW's standard cursor set has no
 * magnifying-glass shape, so this builds one as a small custom cursor bitmap the first time it's needed)
 * during their render pass; the owning screen calls {@link #endFrame()} once, after everything has
 * rendered, to actually apply whichever was requested (zoom takes priority over the generic pointer if
 * both were requested in the same frame) or restore the default arrow if nothing requested one.
 * Centralizing the apply avoids flicker/redundant GLFW calls if multiple widgets each independently
 * tried to set the cursor.
 */
public final class CursorEffects {
    private static final int ZOOM_CURSOR_SIZE = 32;

    private enum Cursor {NONE, POINTER, ZOOM}

    private static Cursor requestedThisFrame = Cursor.NONE;
    private static Cursor currentlyActive = Cursor.NONE;
    private static long handCursor = 0L;
    private static long zoomCursor = 0L;

    private CursorEffects() {
    }

    public static void requestPointerCursor() {
        if (requestedThisFrame == Cursor.NONE)
            requestedThisFrame = Cursor.POINTER;
    }

    public static void requestZoomCursor() {
        requestedThisFrame = Cursor.ZOOM;
    }

    /** Call once per frame, after all rendering (and hover checks) for the screen are done. */
    public static void endFrame() {
        if (requestedThisFrame != currentlyActive) {
            currentlyActive = requestedThisFrame;
            //? if <1.21.10 {
            long window = Minecraft.getInstance().getWindow().getWindow();
            //? } else {
            /*long window = Minecraft.getInstance().getWindow().handle();
            *///?}
            switch (currentlyActive) {
                case ZOOM -> {
                    if (zoomCursor == 0L)
                        zoomCursor = createZoomCursor();
                    GLFW.glfwSetCursor(window, zoomCursor);
                }
                case POINTER -> {
                    if (handCursor == 0L)
                        handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
                    GLFW.glfwSetCursor(window, handCursor);
                }
                default -> GLFW.glfwSetCursor(window, 0L);
            }
        }
        requestedThisFrame = Cursor.NONE;
    }

    /**
     * Draws a conventional "zoom in" magnifying-glass cursor (ring lens with a + inside, diagonal
     * handle, white halo around a black stroke for contrast against any background) into a 32x32 RGBA
     * bitmap and registers it as a custom GLFW cursor.
     */
    private static long createZoomCursor() {
        int size = ZOOM_CURSOR_SIZE;
        ByteBuffer pixels = MemoryUtil.memAlloc(size * size * 4);
        try {
            float cx = 12f;
            float cy = 12f;
            float outerR = 8.5f;
            float innerR = 6.0f;
            float haloExtra = 1.4f;

            float plusHalfLen = 3.6f;
            float plusHalfThick = 1.0f;

            float hx1 = 17.5f, hy1 = 17.5f, hx2 = 28.5f, hy2 = 28.5f;
            float handleStroke = 2.2f;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float px = x + 0.5f;
                    float py = y + 0.5f;
                    float dx = px - cx;
                    float dy = py - cy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    boolean blackRing = dist <= outerR && dist >= innerR;
                    boolean whiteRing = !blackRing && dist <= outerR + haloExtra && dist >= innerR - haloExtra;

                    boolean insideLens = dist < innerR - handleStroke / 2f;
                    boolean blackPlus = insideLens
                            && ((Math.abs(dy) <= plusHalfThick && Math.abs(dx) <= plusHalfLen)
                                || (Math.abs(dx) <= plusHalfThick && Math.abs(dy) <= plusHalfLen));

                    float segDist = distanceToSegment(px, py, hx1, hy1, hx2, hy2);
                    boolean blackHandle = segDist <= handleStroke / 2f;
                    boolean whiteHandle = !blackHandle && segDist <= handleStroke / 2f + haloExtra;

                    boolean black = blackRing || blackPlus || blackHandle;
                    boolean white = !black && (whiteRing || whiteHandle);

                    int idx = (y * size + x) * 4;
                    if (black) {
                        pixels.put(idx, (byte) 0x15);
                        pixels.put(idx + 1, (byte) 0x15);
                        pixels.put(idx + 2, (byte) 0x15);
                        pixels.put(idx + 3, (byte) 0xFF);
                    } else if (white) {
                        pixels.put(idx, (byte) 0xFF);
                        pixels.put(idx + 1, (byte) 0xFF);
                        pixels.put(idx + 2, (byte) 0xFF);
                        pixels.put(idx + 3, (byte) 0xFF);
                    } else {
                        pixels.put(idx, (byte) 0xFF);
                        pixels.put(idx + 1, (byte) 0xFF);
                        pixels.put(idx + 2, (byte) 0xFF);
                        pixels.put(idx + 3, (byte) 0x00);
                    }
                }
            }
            pixels.flip();

            try (GLFWImage image = GLFWImage.malloc()) {
                image.set(size, size, pixels);
                // Hotspot at the lens center, matching where a real click would land on the image.
                return GLFW.glfwCreateCursor(image, (int) cx, (int) cy);
            }
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    private static float distanceToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float lengthSq = abx * abx + aby * aby;
        float t = lengthSq == 0 ? 0 : ((px - ax) * abx + (py - ay) * aby) / lengthSq;
        t = Math.max(0, Math.min(1, t));
        float closestX = ax + t * abx;
        float closestY = ay + t * aby;
        float dx = px - closestX;
        float dy = py - closestY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
