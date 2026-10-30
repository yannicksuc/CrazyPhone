package fr.lordfinn.crazyphone.client.livescreen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A painted live screen turned into as few solid-color rectangles as possible, ready to be emitted as quads
 * on the phone's screen face. Drawing colored quads instead of uploading a texture means there is no GPU
 * resource to create, refresh or free at all: nothing can leak on a disconnect, a dimension change, a
 * resource reload or a window resize, and shader packs see an ordinary full-bright entity quad.
 *
 * Meshes are cached by {@link LiveScreenView} (a small LRU): a view only changes a few times per second at
 * most (clock minute, call timer second, 4 fps animations), and every phone showing the same view shares one
 * mesh, so a crowd of locked phones costs a single paint.
 */
public final class LiveScreenMesh {
    private static final int CACHE_SIZE = 64;
    private static final Map<LiveScreenView, LiveScreenMesh> CACHE = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LiveScreenView, LiveScreenMesh> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** x0, y0, x1, y1 per rectangle, in canvas pixels (y down). */
    final short[] rects;
    /** ARGB per rectangle. */
    final int[] colors;
    final int count;

    private LiveScreenMesh(short[] rects, int[] colors, int count) {
        this.rects = rects;
        this.colors = colors;
        this.count = count;
    }

    public int quadCount() {
        return count;
    }

    /** Render thread only. */
    public static LiveScreenMesh of(LiveScreenView view) {
        LiveScreenMesh mesh = CACHE.get(view);
        if (mesh == null) {
            mesh = build(LiveScreenPainter.paint(view));
            CACHE.put(view, mesh);
        }
        return mesh;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Greedy merge: horizontal runs of one color per row, then each run is extended downwards over every
     * following row that has the exact same run. Transparent pixels (0) are skipped.
     */
    static LiveScreenMesh build(int[] px) {
        int w = LiveScreenPainter.WIDTH, h = LiveScreenPainter.HEIGHT;
        boolean[] used = new boolean[w * h];
        short[] rects = new short[64 * 4];
        int[] colors = new int[64];
        int count = 0;
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                int color = px[y * w + x];
                if (color == 0 || used[y * w + x]) {
                    x++;
                    continue;
                }
                int x1 = x + 1;
                while (x1 < w && px[y * w + x1] == color && !used[y * w + x1])
                    x1++;
                int y1 = y + 1;
                while (y1 < h && rowMatches(px, used, y1, x, x1, color))
                    y1++;
                for (int yy = y; yy < y1; yy++)
                    for (int xx = x; xx < x1; xx++)
                        used[yy * w + xx] = true;
                if (count == colors.length) {
                    colors = Arrays.copyOf(colors, count * 2);
                    rects = Arrays.copyOf(rects, count * 8);
                }
                rects[count * 4] = (short) x;
                rects[count * 4 + 1] = (short) y;
                rects[count * 4 + 2] = (short) x1;
                rects[count * 4 + 3] = (short) y1;
                colors[count] = color;
                count++;
                x = x1;
            }
        }
        return new LiveScreenMesh(rects, colors, count);
    }

    private static boolean rowMatches(int[] px, boolean[] used, int y, int x0, int x1, int color) {
        int w = LiveScreenPainter.WIDTH;
        for (int x = x0; x < x1; x++)
            if (px[y * w + x] != color || used[y * w + x])
                return false;
        return true;
    }
}
