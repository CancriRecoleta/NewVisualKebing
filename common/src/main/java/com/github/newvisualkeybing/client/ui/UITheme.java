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

    private static Mode currentMode = Mode.DARK;
    private static Skin currentSkin = Skin.MODERN;
    // Bumped whenever the palette/skin changes so per-widget colour caches can detect a live switch.
    private static int themeVersion = 0;

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

    // ------------------------------------------------------------------ batching -----------------

    /**
     * Runs {@code body} with {@code GuiGraphics} in managed (batched) mode. On this Minecraft
     * version every unmanaged {@code fill}/{@code drawString} ends the vertex batch immediately,
     * i.e. becomes its own GPU draw call; the anti-aliased widgets here emit thousands of quads per
     * frame, so batching them is the single biggest rendering win. Submission order is preserved
     * (switching render type flushes the previous one), scissor/colour changes flush automatically,
     * but immediate-mode texture blits do not: call {@link #flushBatch} before any {@code blit}.
     */
    @SuppressWarnings("deprecation")
    public static void batched(GuiGraphics g, Runnable body) {
        g.drawManaged(body);
    }

    /** Draws everything queued so far; required before immediate-mode operations such as blits. */
    public static void flushBatch(GuiGraphics g) {
        g.flush();
    }

    // ------------------------------------------------------------------ quad sink ----------------

    /**
     * Reusable adapter from {@link QuadSink} to {@code GuiGraphics#fill}. Rendering is single
     * threaded (render thread only), so one shared instance avoids a lambda allocation per shape.
     */
    private static final class GraphicsSink implements QuadSink {
        GuiGraphics g;

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            g.fill(x0, y0, x1, y1, argb);
        }
    }

    private static final GraphicsSink SINK = new GraphicsSink();

    /** Binds the shared sink to {@code g}. Valid until the next call; do not retain. */
    public static QuadSink sink(GuiGraphics g) {
        SINK.g = g;
        return SINK;
    }

    // ------------------------------------------------------------------ rounded rects ------------

    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (currentSkin != Skin.MODERN) { g.fill(x, y, x + w, y + h, color); return; }
        RoundedRectRaster.fill(sink(g), x, y, w, h, radius, color);
    }

    /** Historical alias of {@link #fillRoundedRect}; the anti-aliased path is now the fast path too. */
    public static void fillRoundedRectFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        fillRoundedRect(g, x, y, w, h, radius, color);
    }

    public static void fillSoftRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        fillRoundedRect(g, x, y, w, h, radius, color);
    }

    public static void fillRoundedRectEx(GuiGraphics g, int x, int y, int w, int h,
                                         int rTL, int rTR, int rBR, int rBL, int color) {
        if (currentSkin != Skin.MODERN) { g.fill(x, y, x + w, y + h, color); return; }
        RoundedRectRaster.fillEx(sink(g), x, y, w, h, rTL, rTR, rBR, rBL, color);
    }

    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (currentSkin != Skin.MODERN) { drawVanillaBevel(g, x, y, w, h, color, true); return; }
        RoundedRectRaster.stroke(sink(g), x, y, w, h, radius, color);
    }

    /** Historical alias of {@link #drawRoundedBorder}. */
    public static void drawRoundedBorderFast(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedBorder(g, x, y, w, h, radius, color);
    }

    public static void drawSoftRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedBorder(g, x, y, w, h, radius, color);
    }

    public static void drawRoundedBorderEx(GuiGraphics g, int x, int y, int w, int h,
                                           int rTL, int rTR, int rBR, int rBL, int color) {
        if (currentSkin != Skin.MODERN) { drawVanillaBevel(g, x, y, w, h, color, true); return; }
        RoundedRectRaster.strokeEx(sink(g), x, y, w, h, rTL, rTR, rBR, rBL, color);
    }

    /**
     * Soft focus halo: three 1px anti-aliased rings of decreasing alpha outside the shape. Rings
     * (rather than nested fills) paint every halo pixel exactly once, so the alpha ramp is
     * controlled instead of accumulating through overlapping translucent layers.
     */
    public static void drawSoftGlow(GuiGraphics g, int x, int y, int w, int h, int radius, int color, int maxAlpha) {
        if (currentSkin != Skin.MODERN) return; // vanilla has no soft focus halos
        QuadSink s = sink(g);
        for (int i = 1; i <= 3; i++) {
            int alpha = Math.max(1, Math.round(maxAlpha * (1f - (i - 1) / 3f) * 0.9f));
            RoundedRectRaster.stroke(s, x - i, y - i, w + i * 2, h + i * 2, radius + i, withAlpha(color, alpha));
        }
    }

    /**
     * Replays a cached {@link SilhouetteRaster} rect list. Silhouette shapes are modern-skin only;
     * callers draw their flat fallback themselves.
     */
    public static void fillShape(GuiGraphics g, int[] rects, int ox, int oy, int color) {
        SilhouetteRaster.emit(sink(g), rects, ox, oy, color);
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
        g.fill(x, y + 1, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, br);
    }

    // ------------------------------------------------------------------ composite widgets --------

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
        int color = withAlpha(c.shadow(), 0x40);
        g.fill(x + 2, y + h, x + w + 2, y + h + 3, color);
        g.fill(x + w, y + 2, x + w + 3, y + h, color);
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
        drawCardShadow(g, x - 2, y - 2, w + 4, h + 4, radius + 2);
        fillSoftRoundedRect(g, x, y, w, h, radius, c.panelBg());
        drawSoftRoundedBorder(g, x, y, w, h, radius, c.widgetBorder());
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

    /** Corner radius of the modern tooltip frame; tooltip code aligns its inner chrome to it. */
    public static final int TOOLTIP_RADIUS = 8;

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
        int r = TOOLTIP_RADIUS;
        QuadSink s = sink(g);
        // Drop shadow: concentric 1px rings, each pixel painted once with a smooth falloff, biased
        // downward (+2px) so the card reads as floating above the UI.
        int shadowLayers = 6;
        for (int i = shadowLayers; i >= 1; i--) {
            float t = 1f - (i - 1) / (float) shadowLayers;
            int alpha = Math.max(2, Math.round(0x30 * t * t));
            RoundedRectRaster.stroke(s, x - i, y - i + 2, w + i * 2, h + i * 2, r + i, withAlpha(c.shadow(), alpha));
        }
        RoundedRectRaster.fill(s, x, y + 1, w, h, r, withAlpha(0x000000, 0x60));
        RoundedRectRaster.fill(s, x, y, w, h, r, withAlpha(c.headerBg(), 0xF0));
        // Glassy sheen over the top band.
        RoundedRectRaster.fillEx(s, x + 1, y + 1, w - 2, Math.max(6, h / 5),
                r - 1, r - 1, 2, 2, withAlpha(0xFFFFFF, 0x10));
        RoundedRectRaster.stroke(s, x, y, w, h, r, withAlpha(c.widgetBorderHover(), 0xC8));
        RoundedRectRaster.stroke(s, x + 1, y + 1, w - 2, h - 2, r - 1, withAlpha(0xFFFFFF, 0x10));
    }

    public static void drawHLine(GuiGraphics g, int x, int y, int width, int color) {
        g.fill(x, y, x + width, y + 1, color);
    }

    // ------------------------------------------------------------------ easing / colour ----------

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
