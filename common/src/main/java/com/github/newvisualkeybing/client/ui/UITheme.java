package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.gui.GuiGraphics;





public final class UITheme {

    public enum Mode { DARK, LIGHT }

    private static Mode currentMode = Mode.DARK;
    private static final int COVERAGE_RADIUS_LIMIT = 96;
    private static final int[][] FILL_CORNER_COVERAGE = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] BORDER_CORNER_COVERAGE = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] FILL_CORNER_SPANS = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] BORDER_CORNER_SPANS = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];


    private static final ColorPalette DARK = new ColorPalette(

            0xF008090C, 0xFF111317, 0xFF1A1D22, 0xFF2A2D33, 0xFF7A7E87,

            0xFF4A7BFF, 0xFF6B95FF, 0xFF9DBAFF,

            0xFFF5F6F7, 0xFFC2C6CC, 0xFF7B8089,

            0xFF3DD68C, 0xFFE5A33A, 0xFFFF5C5C,

            0xFF0A0C0F, 0xFF1A1D22, 0xFF3F434A,

            0x60000000,

            0xFF08090C, 0xFF2A2D33, 0xFF4A7BFF,

            0xFF3457D5, 0xFF7E5BD9,

            0xFF1B7A4A, 0xFFC53737,

            0xE0FFFFFF, 0xFF2A2D33
    );

    private static final ColorPalette LIGHT = new ColorPalette(
            0xF0FFFFFF, 0xFFF6F8FA, 0xFFFFFFFF, 0xFFD0D7DE, 0xFF57606A,
            0xFF0969DA, 0xFF0550AE, 0xFF54AEFF,
            0xFF1F2328, 0xFF656D76, 0xFF8C959F,
            0xFF1A7F37, 0xFF9A6700, 0xFFCF222E,
            0xFFF6F8FA, 0xFFEAEEF2, 0xFFAFB8C1,
            0x20000000,
            0xFFFFFFFF, 0xFFD8DEE4, 0xFF0969DA,
            0xFF8250DF, 0xFFBF3989,
            0xFFDAFBE1, 0xFFFFE7E7,
            0xE0FFFFFF, 0xFFD8DEE4
    );

    private UITheme() {}

    public static void setMode(Mode mode) { currentMode = mode; }
    public static Mode getMode() { return currentMode; }
    public static ColorPalette colors() { return currentMode == Mode.DARK ? DARK : LIGHT; }


    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + radius, y, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + radius, y + h - radius, color);
        g.fill(x + w - radius, y + radius, x + w, y + h - radius, color);
        fillRoundedCorner(g, x, y, radius, color, true, true);
        fillRoundedCorner(g, x + w - radius, y, radius, color, false, true);
        fillRoundedCorner(g, x, y + h - radius, radius, color, true, false);
        fillRoundedCorner(g, x + w - radius, y + h - radius, radius, color, false, false);
    }

    private static final int[][] FAST_INSETS = new int[33][];

    private static int[] fastInsets(int r) {
        if (r <= 0) return EMPTY_INSETS;
        if (r < FAST_INSETS.length) {
            int[] cached = FAST_INSETS[r];
            if (cached != null) return cached;
            int[] built = computeFastInsets(r);
            FAST_INSETS[r] = built;
            return built;
        }
        return computeFastInsets(r);
    }

    private static final int[] EMPTY_INSETS = new int[0];

    private static int[] computeFastInsets(int r) {
        int[] insets = new int[r];
        float r2 = (float) r * r;
        for (int dy = 0; dy < r; dy++) {
            float fy = r - dy - 0.5f;
            int dx;
            for (dx = 0; dx < r; dx++) {
                float fx = r - dx - 0.5f;
                if (fx * fx + fy * fy <= r2) break;
            }
            insets[dy] = dx;
        }
        return insets;
    }

    public static void fillRoundedRectFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int[] insets = fastInsets(radius);
        int skip = 0;
        while (skip < radius && insets[skip] > 0) skip++;
        g.fill(x, y + skip, x + w, y + h - skip, color);
        for (int i = 0; i < skip; i++) {
            int inset = insets[i];
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    public static void fillSoftRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        fillRoundedRect(g, x, y, w, h, radius, color);
    }

    public static void drawSoftRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedBorder(g, x, y, w, h, radius, color);
    }

    public static void drawSoftGlow(GuiGraphics g, int x, int y, int w, int h, int radius, int color, int maxAlpha) {
        for (int i = 3; i >= 1; i--) {
            int alpha = Math.max(1, maxAlpha / (i + 1));
            fillSoftRoundedRect(g, x - i, y - i, w + i * 2, h + i * 2, radius + i, withAlpha(color, alpha));
        }
    }

    public static void drawRoundedBorderFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        int[] insets = fastInsets(radius);
        int skip = 0;
        while (skip < radius && insets[skip] > 0) skip++;
        int outerInset = insets[0];
        g.fill(x + outerInset, y, x + w - outerInset, y + 1, color);
        g.fill(x + outerInset, y + h - 1, x + w - outerInset, y + h, color);
        g.fill(x, y + skip, x + 1, y + h - skip, color);
        g.fill(x + w - 1, y + skip, x + w, y + h - skip, color);
        for (int i = 1; i < skip; i++) {
            int inset = insets[i];
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }


    private static int scaleAlpha(int color, float coverage) {
        if (coverage <= 0f) return 0;
        if (coverage >= 1f) return color;
        int baseAlpha = (color >>> 24) & 0xFF;
        int newAlpha = Math.round(baseAlpha * coverage);
        if (newAlpha <= 0) return 0;
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }


    private static void fillRoundedCorner(GuiGraphics g, int cx, int cy, int r, int color, boolean left, boolean top) {
        if (r <= 0) return;
        if (r == 1) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        drawCornerSpans(g, cx, cy, fillCornerSpans(r, left, top), color);
    }

    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (radius <= 0) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        g.fill(x + radius, y, x + w - radius, y + 1, color);
        g.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        g.fill(x, y + radius, x + 1, y + h - radius, color);
        g.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        drawCornerArc(g, x, y, radius, color, true, true);
        drawCornerArc(g, x + w - radius, y, radius, color, false, true);
        drawCornerArc(g, x, y + h - radius, radius, color, true, false);
        drawCornerArc(g, x + w - radius, y + h - radius, radius, color, false, false);
    }

    
    public static void fillRoundedRectEx(GuiGraphics g, int x, int y, int w, int h,
                                         int rTL, int rTR, int rBR, int rBL, int color) {
        rTL = Math.min(rTL, Math.min(w / 2, h / 2));
        rTR = Math.min(rTR, Math.min(w / 2, h / 2));
        rBR = Math.min(rBR, Math.min(w / 2, h / 2));
        rBL = Math.min(rBL, Math.min(w / 2, h / 2));

        int topMax = Math.max(rTL, rTR);
        int botMax = Math.max(rBL, rBR);

        
        if (h - topMax - botMax > 0) {
            g.fill(x, y + topMax, x + w, y + h - botMax, color);
        }

        
        g.fill(x + rTL, y, x + w - rTR, y + topMax, color);
        if (rTL < topMax) g.fill(x, y + rTL, x + rTL, y + topMax, color);
        if (rTR < topMax) g.fill(x + w - rTR, y + rTR, x + w, y + topMax, color);

        
        g.fill(x + rBL, y + h - botMax, x + w - rBR, y + h, color);
        if (rBL < botMax) g.fill(x, y + h - botMax, x + rBL, y + h - rBL, color);
        if (rBR < botMax) g.fill(x + w - rBR, y + h - botMax, x + w, y + h - rBR, color);

        
        if (rTL > 0) fillRoundedCorner(g, x, y, rTL, color, true, true);
        if (rTR > 0) fillRoundedCorner(g, x + w - rTR, y, rTR, color, false, true);
        if (rBL > 0) fillRoundedCorner(g, x, y + h - rBL, rBL, color, true, false);
        if (rBR > 0) fillRoundedCorner(g, x + w - rBR, y + h - rBR, rBR, color, false, false);
    }

    
    public static void drawRoundedBorderEx(GuiGraphics g, int x, int y, int w, int h,
                                           int rTL, int rTR, int rBR, int rBL, int color) {
        rTL = Math.min(rTL, Math.min(w / 2, h / 2));
        rTR = Math.min(rTR, Math.min(w / 2, h / 2));
        rBR = Math.min(rBR, Math.min(w / 2, h / 2));
        rBL = Math.min(rBL, Math.min(w / 2, h / 2));

        
        g.fill(x + rTL, y, x + w - rTR, y + 1, color);
        g.fill(x + rBL, y + h - 1, x + w - rBR, y + h, color);
        
        int leftStart = Math.max(rTL, 0);
        int leftEnd = h - Math.max(rBL, 0);
        g.fill(x, y + leftStart, x + 1, y + leftEnd, color);
        int rightStart = Math.max(rTR, 0);
        int rightEnd = h - Math.max(rBR, 0);
        g.fill(x + w - 1, y + rightStart, x + w, y + rightEnd, color);

        
        drawCornerArc(g, x, y, rTL, color, true, true);
        drawCornerArc(g, x + w - rTR, y, rTR, color, false, true);
        drawCornerArc(g, x, y + h - rBL, rBL, color, true, false);
        drawCornerArc(g, x + w - rBR, y + h - rBR, rBR, color, false, false);
    }

    

    private static void drawCornerArc(GuiGraphics g, int cx, int cy, int r, int color,
                                      boolean left, boolean top) {
        if (r <= 0) return;
        if (r == 1) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            return;
        }
        drawCornerSpans(g, cx, cy, borderCornerSpans(r, left, top), color);
    }

    private static void drawCornerSpans(GuiGraphics g, int cx, int cy, int[] spans, int color) {
        int i = 0;
        while (i < spans.length) {
            int y = spans[i];
            int x1 = spans[i + 1];
            int x2 = spans[i + 2];
            int hits = spans[i + 3];
            int next = i + 4;
            int endY = y + 1;
            while (next < spans.length
                    && spans[next] == endY
                    && spans[next + 1] == x1
                    && spans[next + 2] == x2
                    && spans[next + 3] == hits) {
                endY++;
                next += 4;
            }
            int finalColor = hits == 16 ? color : scaleAlpha(color, hits / 16f);
            g.fill(cx + x1, cy + y, cx + x2, cy + endY, finalColor);
            i = next;
        }
    }

    private static int[] fillCornerCoverage(int r, boolean left, boolean top) {
        int key = coverageKey(r, left, top);
        if (key >= 0) {
            int[] cached = FILL_CORNER_COVERAGE[key];
            if (cached != null) return cached;
            int[] built = buildFillCornerCoverage(r, left, top);
            FILL_CORNER_COVERAGE[key] = built;
            return built;
        }
        return buildFillCornerCoverage(r, left, top);
    }

    private static int[] borderCornerCoverage(int r, boolean left, boolean top) {
        int key = coverageKey(r, left, top);
        if (key >= 0) {
            int[] cached = BORDER_CORNER_COVERAGE[key];
            if (cached != null) return cached;
            int[] built = buildBorderCornerCoverage(r, left, top);
            BORDER_CORNER_COVERAGE[key] = built;
            return built;
        }
        return buildBorderCornerCoverage(r, left, top);
    }

    private static int[] fillCornerSpans(int r, boolean left, boolean top) {
        int key = coverageKey(r, left, top);
        if (key >= 0) {
            int[] cached = FILL_CORNER_SPANS[key];
            if (cached != null) return cached;
            int[] built = buildCornerSpans(fillCornerCoverage(r, left, top), r);
            FILL_CORNER_SPANS[key] = built;
            return built;
        }
        return buildCornerSpans(buildFillCornerCoverage(r, left, top), r);
    }

    private static int[] borderCornerSpans(int r, boolean left, boolean top) {
        int key = coverageKey(r, left, top);
        if (key >= 0) {
            int[] cached = BORDER_CORNER_SPANS[key];
            if (cached != null) return cached;
            int[] built = buildCornerSpans(borderCornerCoverage(r, left, top), r);
            BORDER_CORNER_SPANS[key] = built;
            return built;
        }
        return buildCornerSpans(buildBorderCornerCoverage(r, left, top), r);
    }

    private static int coverageKey(int r, boolean left, boolean top) {
        if (r > COVERAGE_RADIUS_LIMIT) return -1;
        return (r << 2) | (left ? 1 : 0) | (top ? 2 : 0);
    }

    private static int[] buildFillCornerCoverage(int r, boolean left, boolean top) {
        int[] coverage = new int[r * r];
        float r2 = (float) r * r;
        float rInner = Math.max(0f, r - 1.5f);
        float rInner2 = rInner * rInner;
        float rOuter = r + 1.5f;
        float rOuter2 = rOuter * rOuter;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                float pxC = dx + 0.5f;
                float pyC = dy + 0.5f;
                float dXc = left ? (r - pxC) : pxC;
                float dYc = top ? (r - pyC) : pyC;
                float dCenter2 = dXc * dXc + dYc * dYc;
                if (dCenter2 <= rInner2) {
                    coverage[dy * r + dx] = 16;
                    continue;
                }
                if (dCenter2 > rOuter2) continue;
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float fx = dx + (sx + 0.5f) / 4f;
                        float fy = dy + (sy + 0.5f) / 4f;
                        float dXs = left ? (r - fx) : fx;
                        float dYs = top ? (r - fy) : fy;
                        if (dXs * dXs + dYs * dYs <= r2) hits++;
                    }
                }
                coverage[dy * r + dx] = hits;
            }
        }
        return coverage;
    }

    private static int[] buildBorderCornerCoverage(int r, boolean left, boolean top) {
        int[] coverage = new int[r * r];
        float rOuter2 = (float) r * r;
        float rInner = r - 1f;
        float rInner2 = rInner * rInner;
        for (int dy = 0; dy < r; dy++) {
            for (int dx = 0; dx < r; dx++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        float fx = dx + (sx + 0.5f) / 4f;
                        float fy = dy + (sy + 0.5f) / 4f;
                        float dXs = left ? (r - fx) : fx;
                        float dYs = top ? (r - fy) : fy;
                        float d2 = dXs * dXs + dYs * dYs;
                        if (d2 <= rOuter2 && d2 >= rInner2) hits++;
                    }
                }
                coverage[dy * r + dx] = hits;
            }
        }
        return coverage;
    }

    private static int[] buildCornerSpans(int[] coverage, int r) {
        int count = 0;
        for (int dy = 0; dy < r; dy++) {
            int dx = 0;
            while (dx < r) {
                int hits = coverage[dy * r + dx];
                if (hits == 0) {
                    dx++;
                    continue;
                }
                count++;
                dx++;
                while (dx < r && coverage[dy * r + dx] == hits) dx++;
            }
        }
        int[] spans = new int[count * 4];
        int pos = 0;
        for (int dy = 0; dy < r; dy++) {
            int dx = 0;
            while (dx < r) {
                int hits = coverage[dy * r + dx];
                if (hits == 0) {
                    dx++;
                    continue;
                }
                int start = dx++;
                while (dx < r && coverage[dy * r + dx] == hits) dx++;
                spans[pos++] = dy;
                spans[pos++] = start;
                spans[pos++] = dx;
                spans[pos++] = hits;
            }
        }
        return spans;
    }

    public static void fillGradient(GuiGraphics g, int x, int y, int w, int h, int colorTop, int colorBottom) {
        if (h <= 0 || w <= 0) return;
        int steps = Math.min(h, 16);
        int stepH = Math.max(h / steps, 1);
        for (int i = 0; i < steps; i++) {
            float t = (float) i / Math.max(1, steps - 1);
            int color = lerpColor(colorTop, colorBottom, t);
            int sy = y + i * stepH;
            int ey = (i == steps - 1) ? y + h : sy + stepH;
            g.fill(x, sy, x + w, ey, color);
        }
    }

    public static void drawCardShadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        int color = withAlpha(c.shadow(), 0x40);
        g.fill(x + 2, y + h, x + w + 2, y + h + 3, color);
        g.fill(x + w, y + 2, x + w + 3, y + h, color);
    }

    public static void drawGlassBackground(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        fillRoundedRect(g, x, y, w, h, radius, c.glassBg());
        fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, 0x08));
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(c.widgetBorder(), 0x60));
    }


    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        drawCardShadow(g, x - 2, y - 2, w + 4, h + 4, radius + 2);
        fillSoftRoundedRect(g, x, y, w, h, radius, c.panelBg());
        drawSoftRoundedBorder(g, x, y, w, h, radius, c.widgetBorder());
    }

    public static void drawGradientButton(GuiGraphics g, int x, int y, int w, int h, int radius,
                                          int colorTop, int colorBottom, float hoverProgress) {
        fillGradient(g, x, y, w, h, colorTop, colorBottom);
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(0xFFFFFF, 0x20));
        if (hoverProgress > 0.01f) {
            int glowAlpha = (int) (30 * hoverProgress);
            fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, glowAlpha));
        }
    }

    public static void renderTooltipBackground(GuiGraphics g, int x, int y, int w, int h) {
        var c = colors();
        for (int i = 7; i >= 1; i--) {
            int alpha = Math.max(2, 18 - i * 2);
            fillSoftRoundedRect(g, x - i, y - i + 2, w + i * 2, h + i * 2, 9 + i, withAlpha(c.shadow(), alpha));
        }
        fillSoftRoundedRect(g, x + 1, y + 2, w, h, 9, withAlpha(0x000000, 0x70));
        fillSoftRoundedRect(g, x, y, w, h, 9, withAlpha(c.headerBg(), 0xEA));
        fillRoundedRectEx(g, x + 1, y + 1, w - 2, Math.max(6, h / 5),
                8, 8, 2, 2, withAlpha(0xFFFFFF, 0x12));
        drawSoftRoundedBorder(g, x, y, w, h, 9, withAlpha(c.widgetBorderHover(), 0xC8));
        drawSoftRoundedBorder(g, x + 1, y + 1, w - 2, h - 2, 8, withAlpha(0xFFFFFF, 0x12));
        fillSoftRoundedRect(g, x + 8, y, w - 16, 2, 1, withAlpha(0xFFFFFF, 0x28));
    }

    public static void drawHLine(GuiGraphics g, int x, int y, int width, int color) {
        g.fill(x, y, x + width, y + 1, color);
    }

    
    
    

    public static float easeOutCubic(float t) {
        t = Math.max(0, Math.min(1, t));
        float f = 1 - t;
        return 1 - f * f * f;
    }

    public static float easeInOutQuad(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 2 * t * t : 1 - (-2 * t + 2) * (-2 * t + 2) / 2;
    }

    public static float smoothDamp(float current, float target, float speed) {
        return current + (target - current) * Math.min(1.0f, speed);
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c1 >> 24) & 0xFF) + (((c2 >> 24) & 0xFF) - ((c1 >> 24) & 0xFF)) * t);
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int multiplyAlpha(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        int scaled = Math.round(a * Math.max(0f, Math.min(1f, factor)));
        return (color & 0x00FFFFFF) | (scaled << 24);
    }

    public static int brighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * (1 + factor)));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * (1 + factor)));
        int b = Math.min(255, (int) ((color & 0xFF) * (1 + factor)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    



    public record ColorPalette(
            int panelBg,
            int headerBg,
            int widgetBg,
            int widgetBorder,
            int widgetBorderHover,
            int accent,
            int accentHover,
            int accentLight,
            int textPrimary,
            int textSecondary,
            int textMuted,
            int successColor,
            int warningColor,
            int dangerColor,
            int inputBg,
            int scrollbarTrack,
            int scrollbarThumb,
            int shadow,
            int graphBg,
            int gridLine,
            int graphLine,
            int accentSecondary,
            int accentTertiary,
            int successBg,
            int dangerBg,
            int glassBg,
            int divider
    ) {
        public int success() { return successColor; }
        public int danger() { return dangerColor; }
        public int warning() { return warningColor; }
        public int accentAlt() { return accentLight; }
        public int dimOverlay() { return widgetBorderHover; }
    }
}
