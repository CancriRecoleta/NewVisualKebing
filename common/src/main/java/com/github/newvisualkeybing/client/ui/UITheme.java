package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.gui.GuiGraphics;





public final class UITheme {

    public enum Mode { DARK, LIGHT }

    /**
     * Visual skin, orthogonal to {@link Mode}. {@code MODERN} is the rounded, glassy default;
     * {@code VANILLA} repaints the same widgets in a flat, blocky Minecraft-classic style (sharp
     * corners + two-tone bevels) via branches in the low-level draw helpers, so no call site changes.
     */
    public enum Skin { MODERN, VANILLA, CUSTOM }

    /**
     * MD3 shape scale in GUI pixels (≈ dp). Vanilla/custom skins collapse
     * every radius to 0 via {@link #radius(Shape)}.
     */
    public enum Shape {
        NONE(0),
        /** Extra-small — chips, badges, icon buttons, keycaps. */
        XS(4),
        /** Small — buttons, text fields, menus. */
        SM(8),
        /** Medium — cards, side panels. */
        MD(12),
        /** Large — dialogs, sheets. */
        LG(16),
        /** Extra-large — large surfaces. Capped from MD3 28 dp for MC density. */
        XL(20);

        public final int px;

        Shape(int px) {
            this.px = px;
        }
    }

    /** MD3 hover state-layer opacity (8%). */
    public static final float STATE_HOVER = 0.08f;
    /** MD3 focus state-layer opacity (10%). */
    public static final float STATE_FOCUS = 0.10f;
    /** MD3 pressed state-layer opacity (12%). */
    public static final float STATE_PRESSED = 0.12f;
    /** MD3 dragged state-layer opacity (16%). */
    public static final float STATE_DRAGGED = 0.16f;

    private static Mode currentMode = Mode.DARK;
    private static Skin currentSkin = Skin.MODERN;
    // Bumped whenever the palette/skin changes so per-widget colour caches can detect a live switch.
    private static int themeVersion = 0;
    private static final int COVERAGE_RADIUS_LIMIT = 96;
    private static final int[][] FILL_CORNER_COVERAGE = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] BORDER_CORNER_COVERAGE = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] FILL_CORNER_SPANS = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];
    private static final int[][] BORDER_CORNER_SPANS = new int[(COVERAGE_RADIUS_LIMIT + 1) * 4][];


    // Tonal dark surfaces follow MD3 container steps (lowest → highest) while
    // keeping the existing blue primary. Avoid pure #000 — it smears on OLED
    // and flattens elevation.
    private static final ColorPalette DARK = new ColorPalette(
            0xF0111217, 0xFF1A1B21, 0xFF22232A, 0xFF3E4048, 0xFF8E9099,
            0xFF4A7BFF, 0xFF6B95FF, 0xFFB4C5FF,
            0xFFE8E6ED, 0xFFC5C6D0, 0xFF8E9099,
            0xFF3DD68C, 0xFFE5A33A, 0xFFFF5C5C,
            0xFF0D0E12, 0xFF1A1B21, 0xFF3E4048,
            0x66000000,
            0xFF0D0E12, 0xFF2A2B33, 0xFF4A7BFF,
            0xFF3457D5, 0xFF7E5BD9,
            0xFF1B7A4A, 0xFFC53737,
            0xE0111217, 0xFF2A2B33
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

    // Minecraft-classic palette. The dark chrome mirrors the in-game Controls menu (dimmed world +
    // stone-gray widgets + white text). Status hues are the literal §-code colours (green/gold/red/
    // aqua/purple) so the keyboard reads as vanilla. widgetBorder is black and widgetBorderHover is
    // white to match the vanilla button/slot bevel and selection outline.
    private static final ColorPalette VANILLA = new ColorPalette(
            0xFF1C1C1C, 0xFF121212, 0xFF6E6E6E, 0xFF000000, 0xFFFFFFFF,
            0xFF5C8AC4, 0xFF73A0D6, 0xFF9DBEE6,
            0xFFFFFFFF, 0xFFC6C6C6, 0xFFA0A0A0,
            0xFF55FF55, 0xFFFFAA00, 0xFFFF5555,
            0xFF000000, 0xFF000000, 0xFF8B8B8B,
            0x90000000,
            0xFF0F0F0F, 0xFF3A3A3A, 0xFF55FF55,
            0xFFAA00AA, 0xFF55FFFF,
            0xFF1E3A1E, 0xFF3A1E1E,
            0xC0101010, 0xFF000000
    );

    private UITheme() {}

    public static void setMode(Mode mode) {
        if (mode != null && mode != currentMode) { currentMode = mode; themeVersion++; }
    }
    public static Mode getMode() { return currentMode; }

    public static void setSkin(Skin skin) {
        if (skin != null && skin != currentSkin) { currentSkin = skin; themeVersion++; }
    }
    public static Skin getSkin() { return currentSkin; }
    public static boolean vanilla() { return currentSkin == Skin.VANILLA; }
    public static boolean custom() { return currentSkin == Skin.CUSTOM; }
    /** True for any non-modern skin (vanilla or custom), which share the flat/blocky procedural base. */
    public static boolean flat() { return currentSkin != Skin.MODERN; }
    /** Monotonic counter; widgets compare it to drop colour caches when the mode/skin changes. */
    public static int themeVersion() { return themeVersion; }

    public static ColorPalette colors() {
        if (currentSkin != Skin.MODERN) return VANILLA;
        return currentMode == Mode.DARK ? DARK : LIGHT;
    }

    public static int radius(Shape shape) {
        if (shape == null || currentSkin != Skin.MODERN) return 0;
        return shape.px;
    }

    public static int radius(Shape shape, int w, int h) {
        int r = radius(shape);
        if (r <= 0) return 0;
        int cap = Math.min(Math.max(w, 0), Math.max(h, 0)) / 2;
        return Math.min(r, cap);
    }

    public static int pill(int w, int h) {
        if (currentSkin != Skin.MODERN) return 0;
        return Math.max(0, Math.min(w, h) / 2);
    }

    public static int stateLayer(int onColor, float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        return withAlpha(onColor, alpha);
    }

    public static void fillStateLayer(GuiGraphics g, int x, int y, int w, int h,
                                      int radius, int onColor, float opacity) {
        if (opacity <= 0.01f || w <= 0 || h <= 0) return;
        fillRoundedRectFast(g, x, y, w, h, radius, stateLayer(onColor, opacity));
    }


    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (currentSkin != Skin.MODERN) { g.fill(x, y, x + w, y + h, color); return; }
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
        if (currentSkin != Skin.MODERN) { g.fill(x, y, x + w, y + h, color); return; }
        if (radius <= 0 || w < radius * 2 || h < radius * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int[] insets = fastInsets(radius);
        int skip = 0;
        while (skip < radius && insets[skip] > 0) skip++;
        g.fill(x, y + skip, x + w, y + h - skip, color);
        // Emit one fill per RUN of equal-inset corner rows instead of one per row. The FAST path uses a
        // binary inset profile, so consecutive rows that share an inset cover an identical x-span and
        // merge into a single taller fill — pixel-identical output, far fewer g.fill calls (each fill is
        // a GuiRenderState quad). Mirrors the run-merge drawCornerSpans already does for the coverage path.
        int i = 0;
        while (i < skip) {
            int inset = insets[i];
            int j = i + 1;
            while (j < skip && insets[j] == inset) j++;
            g.fill(x + inset, y + i, x + w - inset, y + j, color);
            g.fill(x + inset, y + h - j, x + w - inset, y + h - i, color);
            i = j;
        }
    }

    public static void fillSoftRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        fillRoundedRect(g, x, y, w, h, radius, color);
    }

    public static void drawSoftRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedBorder(g, x, y, w, h, radius, color);
    }

    public static void drawSoftGlow(GuiGraphics g, int x, int y, int w, int h, int radius, int color, int maxAlpha) {
        if (currentSkin != Skin.MODERN) return; // vanilla has no soft focus halos
        for (int i = 3; i >= 1; i--) {
            int alpha = Math.max(1, maxAlpha / (i + 1));
            fillSoftRoundedRect(g, x - i, y - i, w + i * 2, h + i * 2, radius + i, withAlpha(color, alpha));
        }
    }

    /**
     * Two-tone Minecraft bevel: light top/left + dark bottom/right (raised) or the reverse (sunken),
     * with sharp corners. Tones are derived from {@code color} so semantic hues (red danger, accent,
     * …) keep tinting their frame. Faint outlines (low alpha) fall back to a flat 1px border so
     * decorative hairlines don't turn into harsh bevels.
     */
    public static void drawVanillaBevel(GuiGraphics g, int x, int y, int w, int h, int color, boolean raised) {
        if (w <= 0 || h <= 0) return;
        int a = (color >>> 24) & 0xFF;
        if (a < 0x60) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
            return;
        }
        // Tone the RGB toward white/black but keep the caller's alpha so semi-transparent borders
        // stay semi-transparent (only fully-faint <0x60 colours took the flat path above).
        int light = (color & 0xFF000000) | (lerpColor(color, 0xFFFFFFFF, 0.55f) & 0xFFFFFF);
        int dark = (color & 0xFF000000) | (lerpColor(color, 0xFF000000, 0.55f) & 0xFFFFFF);
        int tl = raised ? light : dark;
        int br = raised ? dark : light;
        g.fill(x, y, x + w, y + 1, tl);
        g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y, x + w, y + h, br);
    }

    public static void drawRoundedBorderFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (currentSkin != Skin.MODERN) { drawVanillaBevel(g, x, y, w, h, color, true); return; }
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
        // Run-merge consecutive equal-inset corner rows into a single vertical fill per corner: the
        // border pixel sits at the same column across those rows, so this is pixel-identical with fewer
        // g.fill calls.
        int i = 1;
        while (i < skip) {
            int inset = insets[i];
            int j = i + 1;
            while (j < skip && insets[j] == inset) j++;
            g.fill(x + inset, y + i, x + inset + 1, y + j, color);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + j, color);
            g.fill(x + inset, y + h - j, x + inset + 1, y + h - i, color);
            g.fill(x + w - inset - 1, y + h - j, x + w - inset, y + h - i, color);
            i = j;
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
        if (currentSkin != Skin.MODERN) { drawVanillaBevel(g, x, y, w, h, color, true); return; }
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
        if (currentSkin != Skin.MODERN) { g.fill(x, y, x + w, y + h, color); return; }
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
        if (currentSkin != Skin.MODERN) { drawVanillaBevel(g, x, y, w, h, color, true); return; }
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
        if (currentSkin != Skin.MODERN) return; // vanilla has no soft drop shadows
        var c = colors();
        // MD3 elevation 1: a downward umbra, not an L-shaped hard edge.
        fillRoundedRectFast(g, x + 1, y + 2, w, h, radius, withAlpha(c.shadow(), 0x28));
        fillRoundedRectFast(g, x + 2, y + 4, w, h, radius, withAlpha(c.shadow(), 0x14));
    }

    public static void drawGlassBackground(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        if (currentSkin != Skin.MODERN) {
            g.fill(x, y, x + w, y + h, c.glassBg());
            drawVanillaBevel(g, x, y, w, h, c.widgetBg(), false);
            return;
        }
        fillRoundedRect(g, x, y, w, h, radius, c.glassBg());
        fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, 0x08));
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(c.widgetBorder(), 0x60));
    }


    public static void drawGlassPanel(GuiGraphics g, int x, int y, int w, int h, int radius) {
        var c = colors();
        if (currentSkin == Skin.CUSTOM && UITextureStore.global().draw(UITextureSlot.PANEL, g, x, y, w, h)) {
            return;
        }
        if (currentSkin != Skin.MODERN) {
            // Stone window: a dark interior framed by the classic MC bevel — light gray top/left,
            // dark gray bottom/right — with a 1px black outline, like an inventory/container border.
            int interior = 0xFF000000 | (lerpColor(c.panelBg(), 0xFFFFFFFF, 0.06f) & 0xFFFFFF);
            g.fill(x, y, x + w, y + h, interior);
            g.fill(x, y, x + w, y + 1, 0xFFC6C6C6);
            g.fill(x, y, x + 1, y + h, 0xFFC6C6C6);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF373737);
            g.fill(x + w - 1, y, x + w, y + h, 0xFF373737);
            return;
        }
        int r = radius > 0 ? radius : UITheme.radius(Shape.MD, w, h);
        drawCardShadow(g, x, y, w, h, r);
        fillSoftRoundedRect(g, x, y, w, h, r, c.panelBg());
        fillRoundedRectEx(g, x + 1, y + 1, w - 2, Math.max(4, h / 8),
                Math.max(1, r - 1), Math.max(1, r - 1), 2, 2, withAlpha(0xFFFFFF, 0x0A));
        drawSoftRoundedBorder(g, x, y, w, h, r, withAlpha(c.outlineVariant(), 0xB8));
    }

    public static void drawGradientButton(GuiGraphics g, int x, int y, int w, int h, int radius,
                                          int colorTop, int colorBottom, float hoverProgress) {
        if (currentSkin != Skin.MODERN) {
            g.fill(x, y, x + w, y + h, colorBottom);
            drawVanillaBevel(g, x, y, w, h, colorTop, true);
            if (hoverProgress > 0.01f) {
                g.fill(x + 1, y + 1, x + w - 1, y + 1 + Math.max(1, h / 3),
                        withAlpha(0xFFFFFF, (int) (30 * hoverProgress)));
            }
            return;
        }
        fillGradient(g, x, y, w, h, colorTop, colorBottom);
        drawRoundedBorder(g, x, y, w, h, radius, withAlpha(0xFFFFFF, 0x20));
        if (hoverProgress > 0.01f) {
            int glowAlpha = (int) (30 * hoverProgress);
            fillRoundedRect(g, x, y, w, h / 2, radius, withAlpha(0xFFFFFF, glowAlpha));
        }
    }

    public static void renderTooltipBackground(GuiGraphics g, int x, int y, int w, int h) {
        var c = colors();
        if (currentSkin == Skin.CUSTOM && UITextureStore.global().draw(UITextureSlot.TOOLTIP, g, x, y, w, h)) {
            return;
        }
        if (currentSkin != Skin.MODERN) {
            // Faithful MC tooltip frame: 0xF0100010 fill with the iconic purple gradient border
            // (top 0x505000FF → bottom 0x5028007F) inset by 1px on all sides.
            int bg = 0xF0100010;
            int bTop = 0x505000FF;
            int bBot = 0x5028007F;
            int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
            g.fill(x0, y0, x1, y1, bg);
            fillGradient(g, x0, y0 + 1, 1, h - 2, bTop, bBot);       // left
            fillGradient(g, x1 - 1, y0 + 1, 1, h - 2, bTop, bBot);   // right
            g.fill(x0, y0, x1, y0 + 1, bTop);                        // top
            g.fill(x0, y1 - 1, x1, y1, bBot);                        // bottom
            return;
        }
        int r = radius(Shape.SM, w, h);
        int inner = Math.max(1, r - 1);
        for (int i = 4; i >= 1; i--) {
            int alpha = Math.max(2, 16 - i * 3);
            fillSoftRoundedRect(g, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, withAlpha(c.shadow(), alpha));
        }
        fillSoftRoundedRect(g, x + 1, y + 2, w, h, r, withAlpha(0x000000, 0x70));
        fillSoftRoundedRect(g, x, y, w, h, r, withAlpha(c.headerBg(), 0xEA));
        fillRoundedRectEx(g, x + 1, y + 1, w - 2, Math.max(6, h / 5),
                inner, inner, 2, 2, withAlpha(0xFFFFFF, 0x12));
        drawSoftRoundedBorder(g, x, y, w, h, r, withAlpha(c.outline(), 0xC8));
        drawSoftRoundedBorder(g, x + 1, y + 1, w - 2, h - 2, inner, withAlpha(0xFFFFFF, 0x12));
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

        public int surface() { return panelBg; }
        public int surfaceContainerLowest() { return inputBg; }
        public int surfaceContainerLow() { return headerBg; }
        public int surfaceContainer() { return widgetBg; }
        public int outline() { return widgetBorderHover; }
        public int outlineVariant() { return widgetBorder; }
        public int onSurface() { return textPrimary; }
        public int onSurfaceVariant() { return textSecondary; }
        public int primary() { return accent; }
    }
}
