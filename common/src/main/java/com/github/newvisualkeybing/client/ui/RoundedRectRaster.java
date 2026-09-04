package com.github.newvisualkeybing.client.ui;

/**
 * Anti-aliased rounded-rectangle rasterizer that decomposes a shape into a minimal set of
 * non-overlapping solid quads.
 *
 * <p>Design:
 * <ul>
 *   <li>Every corner radius {@code r} gets one cached {@link CornerMask} holding the exact
 *       disc/pixel coverage of a quarter circle (analytic area integral, 8-bit alpha). The mask is
 *       computed once in top-left orientation; the other three corners are mirrored arithmetically
 *       at emit time, so the cache is one entry per radius, not four.</li>
 *   <li>A fill is emitted as: one quad for the straight middle rows, one quad per <em>run</em> of
 *       corner-band rows sharing the same solid inset (rows merge vertically), and the pre-merged
 *       partial-coverage rectangles of each corner. A 1px stroke is emitted as four straight edges
 *       plus the pre-merged annulus rectangles of each corner.</li>
 *   <li>Quads never overlap, which matters because translucent colours would otherwise composite
 *       twice and produce visible seams.</li>
 * </ul>
 *
 * <p>This class does not touch Minecraft classes; see {@link QuadSink}.
 */
public final class RoundedRectRaster {

    private static final int MAX_CACHED_RADIUS = 128;
    private static final CornerMask[] CACHE = new CornerMask[MAX_CACHED_RADIUS + 1];

    private RoundedRectRaster() {}

    /**
     * Quarter-circle coverage for one radius, in top-left orientation: {@code dx}/{@code dy} count
     * pixels inward from the outer corner, the arc centre sits at {@code (r, r)}.
     */
    static final class CornerMask {
        final int r;
        /** Per corner row: first {@code dx} whose pixel is fully covered ({@code r} if none). */
        final int[] solidInset;
        /** Partial-coverage fill pixels merged into rects: {@code dx0, dy0, dx1, dy1, alpha} tuples. */
        final int[] fillRects;
        /** 1px annulus (stroke) pixels merged into rects, same tuple layout. */
        final int[] strokeRects;

        CornerMask(int r) {
            this.r = r;
            int[] fill = new int[r * r];
            int[] stroke = new int[r * r];
            solidInset = new int[r];
            for (int dy = 0; dy < r; dy++) {
                int inset = r;
                for (int dx = 0; dx < r; dx++) {
                    double outer = discArea(r, r, dx, dy);
                    int a = quantize(outer);
                    if (a >= 255) {
                        if (inset == r) inset = dx;
                        a = 255;
                    } else {
                        fill[dy * r + dx] = a;
                    }
                    double ring = outer - discArea(r, r - 1, dx, dy);
                    stroke[dy * r + dx] = quantize(ring);
                }
                solidInset[dy] = inset;
            }
            fillRects = AlphaGridRects.pack(fill, r, r);
            strokeRects = AlphaGridRects.pack(stroke, r, r);
        }
    }

    static CornerMask mask(int r) {
        if (r <= MAX_CACHED_RADIUS) {
            CornerMask m = CACHE[r];
            if (m == null) {
                m = new CornerMask(r);
                CACHE[r] = m;
            }
            return m;
        }
        return new CornerMask(r);
    }

    /**
     * Edge alpha is quantized to this many levels so neighbouring pixels of near-equal coverage
     * share a value and merge into one quad. 32 steps on a 1px edge are visually lossless.
     */
    private static final int ALPHA_LEVELS = 32;

    private static int quantize(double coverage) {
        if (coverage <= 0.02) return 0;
        if (coverage >= 0.98) return 255;
        int steps = ALPHA_LEVELS - 1;
        int level = Math.max(1, (int) Math.round(coverage * steps));
        return level * 255 / steps;
    }

    /**
     * Exact area of the unit pixel {@code [dx, dx+1] x [dy, dy+1]} (outer-corner coordinates of a
     * corner square of size {@code r}) that lies inside the disc of radius {@code rad} centred at
     * {@code (r, r)}. Integrates the circle's lower boundary over the pixel's horizontal extent.
     */
    static double discArea(int r, double rad, int dx, int dy) {
        if (rad <= 0) return 0;
        double u0 = r - dx - 1, u1 = r - dx;
        double v0 = r - dy - 1, v1 = r - dy;
        double rad2 = rad * rad;
        if (u1 * u1 + v1 * v1 <= rad2) return 1;
        if (u0 * u0 + v0 * v0 >= rad2) return 0;
        // ua/ub: horizontal positions where the arc crosses the pixel's top and bottom edges.
        double ua = Math.sqrt(Math.max(0, rad2 - v1 * v1));
        double ub = Math.sqrt(Math.max(0, rad2 - v0 * v0));
        double a = clamp(ua, u0, u1);
        double b = clamp(ub, u0, u1);
        double area = (a - u0) + (arcIntegral(rad, b) - arcIntegral(rad, a)) - v0 * (b - a);
        return clamp(area, 0, 1);
    }

    /** Antiderivative of sqrt(rad^2 - u^2). */
    private static double arcIntegral(double rad, double u) {
        u = Math.min(u, rad);
        return 0.5 * (u * Math.sqrt(Math.max(0, rad * rad - u * u)) + rad * rad * Math.asin(u / rad));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Scales the alpha byte of {@code color} by {@code alpha8 / 255}; returns 0 when fully transparent. */
    public static int scaleAlpha(int color, int alpha8) {
        int base = color >>> 24;
        int a = (base * alpha8 + 127) / 255;
        return a == 0 ? 0 : (a << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ fills --------------------

    public static void fill(QuadSink s, int x, int y, int w, int h, int r, int color) {
        fillEx(s, x, y, w, h, r, r, r, r, color);
    }

    public static void fillEx(QuadSink s, int x, int y, int w, int h,
                              int rTL, int rTR, int rBR, int rBL, int color) {
        if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
        int maxR = Math.min(w, h) / 2;
        rTL = clampRadius(rTL, maxR);
        rTR = clampRadius(rTR, maxR);
        rBR = clampRadius(rBR, maxR);
        rBL = clampRadius(rBL, maxR);
        if ((rTL | rTR | rBR | rBL) == 0) {
            s.fill(x, y, x + w, y + h, color);
            return;
        }
        CornerMask mTL = rTL > 0 ? mask(rTL) : null;
        CornerMask mTR = rTR > 0 ? mask(rTR) : null;
        CornerMask mBR = rBR > 0 ? mask(rBR) : null;
        CornerMask mBL = rBL > 0 ? mask(rBL) : null;
        int topH = Math.max(rTL, rTR);
        int botH = Math.max(rBL, rBR);

        if (topH < h - botH) s.fill(x, y + topH, x + w, y + h - botH, color);
        emitBand(s, x, y, w, h, topH, mTL, rTL, mTR, rTR, false, color);
        emitBand(s, x, y, w, h, botH, mBL, rBL, mBR, rBR, true, color);

        if (mTL != null) emitCorner(s, mTL.fillRects, x, y, w, h, false, false, color);
        if (mTR != null) emitCorner(s, mTR.fillRects, x, y, w, h, true, false, color);
        if (mBL != null) emitCorner(s, mBL.fillRects, x, y, w, h, false, true, color);
        if (mBR != null) emitCorner(s, mBR.fillRects, x, y, w, h, true, true, color);
    }

    /**
     * Solid part of a corner band (top or bottom {@code bandH} rows): each row spans from the
     * left corner's solid inset to the right corner's; consecutive rows with identical insets are
     * merged into one quad.
     */
    private static void emitBand(QuadSink s, int x, int y, int w, int h, int bandH,
                                 CornerMask ml, int rl, CornerMask mr, int rr,
                                 boolean bottom, int color) {
        int i = 0;
        while (i < bandH) {
            int li = i < rl ? ml.solidInset[i] : 0;
            int ri = i < rr ? mr.solidInset[i] : 0;
            int j = i + 1;
            while (j < bandH
                    && (j < rl ? ml.solidInset[j] : 0) == li
                    && (j < rr ? mr.solidInset[j] : 0) == ri) {
                j++;
            }
            int x0 = x + li;
            int x1 = x + w - ri;
            if (x0 < x1) {
                if (bottom) s.fill(x0, y + h - j, x1, y + h - i, color);
                else s.fill(x0, y + i, x1, y + j, color);
            }
            i = j;
        }
    }

    private static void emitCorner(QuadSink s, int[] rects, int x, int y, int w, int h,
                                   boolean mirrorX, boolean mirrorY, int color) {
        for (int i = 0; i < rects.length; i += 5) {
            int c = scaleAlpha(color, rects[i + 4]);
            if (c == 0) continue;
            int dx0 = rects[i], dy0 = rects[i + 1], dx1 = rects[i + 2], dy1 = rects[i + 3];
            int px0 = mirrorX ? x + w - dx1 : x + dx0;
            int px1 = mirrorX ? x + w - dx0 : x + dx1;
            int py0 = mirrorY ? y + h - dy1 : y + dy0;
            int py1 = mirrorY ? y + h - dy0 : y + dy1;
            s.fill(px0, py0, px1, py1, c);
        }
    }

    // ------------------------------------------------------------------ strokes ------------------

    public static void stroke(QuadSink s, int x, int y, int w, int h, int r, int color) {
        strokeEx(s, x, y, w, h, r, r, r, r, color);
    }

    /** 1px anti-aliased outline. Straight edges and corner arcs never share a pixel. */
    public static void strokeEx(QuadSink s, int x, int y, int w, int h,
                                int rTL, int rTR, int rBR, int rBL, int color) {
        if (w <= 0 || h <= 0 || color >>> 24 == 0) return;
        if (w <= 2 || h <= 2) {
            s.fill(x, y, x + w, y + h, color);
            return;
        }
        int maxR = Math.min(w, h) / 2;
        rTL = clampRadius(rTL, maxR);
        rTR = clampRadius(rTR, maxR);
        rBR = clampRadius(rBR, maxR);
        rBL = clampRadius(rBL, maxR);

        // Straight edges. A sharp (r == 0) corner belongs to the horizontal edge, so the vertical
        // edges start one pixel later there to avoid double-compositing the corner pixel.
        int top0 = x + rTL, top1 = x + w - rTR;
        int bot0 = x + rBL, bot1 = x + w - rBR;
        if (top0 < top1) s.fill(top0, y, top1, y + 1, color);
        if (bot0 < bot1) s.fill(bot0, y + h - 1, bot1, y + h, color);
        int left0 = y + (rTL == 0 ? 1 : rTL), left1 = y + h - (rBL == 0 ? 1 : rBL);
        int right0 = y + (rTR == 0 ? 1 : rTR), right1 = y + h - (rBR == 0 ? 1 : rBR);
        if (left0 < left1) s.fill(x, left0, x + 1, left1, color);
        if (right0 < right1) s.fill(x + w - 1, right0, x + w, right1, color);

        if (rTL > 0) emitCorner(s, mask(rTL).strokeRects, x, y, w, h, false, false, color);
        if (rTR > 0) emitCorner(s, mask(rTR).strokeRects, x, y, w, h, true, false, color);
        if (rBL > 0) emitCorner(s, mask(rBL).strokeRects, x, y, w, h, false, true, color);
        if (rBR > 0) emitCorner(s, mask(rBR).strokeRects, x, y, w, h, true, true, color);
    }

    private static int clampRadius(int r, int maxR) {
        return r <= 0 ? 0 : Math.min(r, maxR);
    }
}
