package com.github.newvisualkeybing.client.ui;

/**
 * Rasterizes smooth, vertically-convex silhouettes described by a per-row left/right edge
 * function (e.g. the egg-shaped mouse body) into anti-aliased, non-overlapping solid quads.
 *
 * <p>Coverage is exact horizontally and sampled {@value #SUBROWS} times vertically per pixel row,
 * which anti-aliases both the near-vertical flanks and the near-horizontal nose/tail of a shape.
 * Output uses the same packed {@code x0, y0, x1, y1, alpha} rect tuples as
 * {@link RoundedRectRaster}, in shape-local pixel coordinates, so a cached rasterization is
 * replayed each frame with just an origin offset and a colour.
 *
 * <p>MC-free by design (see {@link QuadSink}).
 */
public final class SilhouetteRaster {

    public static final int SUBROWS = 4;
    /**
     * Edge alpha is quantized to this many levels before rect merging. Fewer levels let more
     * neighbouring edge pixels share a value and collapse into one quad; 32 steps on a 1px
     * anti-aliased edge are visually indistinguishable from 256.
     */
    private static final int ALPHA_LEVELS = 32;

    private SilhouetteRaster() {}

    /** Continuous outline: for a shape-local {@code y} returns the left/right edge x positions. */
    public interface Profile {
        float left(float y);

        float right(float y);
    }

    /** Rasterizes {@code p} restricted to the clip box {@code [x0, x1) x [y0, y1)}, anti-aliased. */
    public static int[] fill(Profile p, int x0, int y0, int x1, int y1) {
        return fill(p, x0, y0, x1, y1, true);
    }

    /**
     * Rasterizes {@code p} restricted to the clip box. With {@code antiAlias == false} every pixel
     * is either fully in or out (threshold at half coverage), which collapses rows into far fewer
     * quads; use it for layers whose edge is hidden under another shape (shadows, interior shading).
     */
    public static int[] fill(Profile p, int x0, int y0, int x1, int y1, boolean antiAlias) {
        int gw = x1 - x0, gh = y1 - y0;
        if (gw <= 0 || gh <= 0) return new int[0];
        float[] cov = coverage(p, x0, y0, x1, y1);
        return packOffset(antiAlias ? quantize(cov) : threshold(cov), gw, gh, x0, y0);
    }

    /**
     * Anti-aliased outline as the coverage difference between an outer shape (clipped to the
     * outer box) and an inner shape (clipped to the inner box). Passing the inner box shrunk by one
     * pixel on each side strokes the straight clip edges too, which is what a button region cut
     * out of the body needs.
     */
    public static int[] stroke(Profile outer, int ox0, int oy0, int ox1, int oy1,
                               Profile inner, int ix0, int iy0, int ix1, int iy1) {
        int gw = ox1 - ox0, gh = oy1 - oy0;
        if (gw <= 0 || gh <= 0) return new int[0];
        float[] cov = coverage(outer, ox0, oy0, ox1, oy1);
        if (ix1 > ix0 && iy1 > iy0) {
            float[] in = coverage(inner, ix0, iy0, ix1, iy1);
            int iw = ix1 - ix0;
            for (int y = iy0; y < iy1; y++) {
                if (y < oy0 || y >= oy1) continue;
                for (int x = ix0; x < ix1; x++) {
                    if (x < ox0 || x >= ox1) continue;
                    int oi = (y - oy0) * gw + (x - ox0);
                    cov[oi] -= in[(y - iy0) * iw + (x - ix0)];
                }
            }
        }
        return packOffset(quantize(cov), gw, gh, ox0, oy0);
    }

    /** Replays packed rects at origin {@code (ox, oy)} with {@code color} (alpha scaled per rect). */
    public static void emit(QuadSink s, int[] rects, int ox, int oy, int color) {
        if (color >>> 24 == 0) return;
        for (int i = 0; i < rects.length; i += 5) {
            int c = RoundedRectRaster.scaleAlpha(color, rects[i + 4]);
            if (c == 0) continue;
            s.fill(ox + rects[i], oy + rects[i + 1], ox + rects[i + 2], oy + rects[i + 3], c);
        }
    }

    /** Number of quads a packed rect list will emit. */
    public static int quadCount(int[] rects) {
        return rects.length / 5;
    }

    static float[] coverage(Profile p, int x0, int y0, int x1, int y1) {
        int gw = x1 - x0, gh = y1 - y0;
        float[] cov = new float[gw * gh];
        float weight = 1f / SUBROWS;
        for (int py = 0; py < gh; py++) {
            int row = py * gw;
            for (int k = 0; k < SUBROWS; k++) {
                float yc = y0 + py + (k + 0.5f) / SUBROWS;
                float l = Math.max(x0, p.left(yc));
                float r = Math.min(x1, p.right(yc));
                if (r <= l) continue;
                int startPx = (int) Math.floor(l);
                int endPx = (int) Math.ceil(r);
                for (int px = startPx; px < endPx; px++) {
                    float c = Math.min(r, px + 1) - Math.max(l, px);
                    if (c > 0) cov[row + (px - x0)] += c * weight;
                }
            }
        }
        return cov;
    }

    private static int[] quantize(float[] cov) {
        int[] a = new int[cov.length];
        int steps = ALPHA_LEVELS - 1;
        for (int i = 0; i < cov.length; i++) {
            float c = cov[i];
            if (c <= 0.02f) continue;
            if (c >= 0.98f) {
                a[i] = 255;
            } else {
                int level = Math.max(1, Math.round(c * steps));
                a[i] = level * 255 / steps;
            }
        }
        return a;
    }

    private static int[] threshold(float[] cov) {
        int[] a = new int[cov.length];
        for (int i = 0; i < cov.length; i++) {
            if (cov[i] >= 0.5f) a[i] = 255;
        }
        return a;
    }

    private static int[] packOffset(int[] alpha, int gw, int gh, int ox, int oy) {
        int[] rects = AlphaGridRects.pack(alpha, gw, gh);
        for (int i = 0; i < rects.length; i += 5) {
            rects[i] += ox;
            rects[i + 1] += oy;
            rects[i + 2] += ox;
            rects[i + 3] += oy;
        }
        return rects;
    }
}
