package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.client.ui.UITextureSlot;
import com.github.newvisualkeybing.client.ui.UITextureStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Draws the mouse panel: a top-down mouse whose two main buttons are plates cut out of an
 * egg-shaped body, a recessed wheel slot with the wheel (middle button) and scroll ticks, and
 * thumb/side buttons attached to the flanks. The modern skin renders the silhouette through
 * {@link MouseSilhouette}; the flat skins (vanilla / custom textures) keep a blocky rectangle body.
 */
final class KeybindMouseRenderer {

    static final int MOUSE_BODY_W = 78;
    static final int MOUSE_BODY_H = 118;
    static final int SIDE_W = 15;
    static final int SIDE_H = 15;
    static final int SIDE_GAP = 3;
    /** How far a side button sticks out beyond the body outline. */
    static final int SIDE_PROTRUDE = 6;
    /** Vertical position (fraction of body height) of the first left / right side button. */
    private static final float LEFT_SIDE_T = 0.40f;
    private static final float RIGHT_SIDE_T = 0.31f;

    private static final int PANEL_PAD = 12;
    private static final int PANEL_CONTENT_TOP = 28;

    private static final int IDX_LMB = 0;
    private static final int IDX_MMB = 1;
    private static final int IDX_RMB = 2;
    private static final int IDX_M4 = 3;
    private static final int IDX_M8 = 7;
    private static final int IDX_WHEEL_UP = 8;
    private static final int IDX_WHEEL_DOWN = 9;

    private final KeyBindingScanner scanner;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final Rect[] bounds = new Rect[KeyboardLayoutData.MOUSE_KEYS.size()];
    private int bodyX = Integer.MIN_VALUE;
    private int bodyY = Integer.MIN_VALUE;
    private int bodyW = Integer.MIN_VALUE;
    private int bodyH = Integer.MIN_VALUE;
    private MouseSilhouette silhouette;
    private final int[] labelWidths = new int[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final KeyBindingScanner.KeyStatus[] cachedStatuses =
            new KeyBindingScanner.KeyStatus[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final int[] cachedBindingCounts = new int[KeyboardLayoutData.MOUSE_KEYS.size()];
    private Font cachedLabelFont;
    private final float[] hoverProgress = new float[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final float[] selectProgress = new float[KeyboardLayoutData.MOUSE_KEYS.size()];
    private long cachedDataVersion = Long.MIN_VALUE;
    private long cachedComboVersion = Long.MIN_VALUE;
    private Set<Integer> cachedComboKeys = java.util.Collections.emptySet();
    private long lastFrameMs;

    /** Per-frame flags, filled by {@link #updateStates} and read by the drawing passes. */
    private final boolean[] matched = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final boolean[] hidden = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final boolean[] hover = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final boolean[] selected = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final boolean[] searchMatch = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final boolean[] combo = new boolean[KeyboardLayoutData.MOUSE_KEYS.size()];
    private int pulseAccent;
    private int searchPulseColor;
    private int searchPulseAlpha;

    KeybindMouseRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    Integer render(GuiGraphics g, Font font, int x, int y, int w, int h,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
                   IntPredicate isHiddenKey, IntPredicate isSearchMatch,
                   int mouseX, int mouseY, float animTick, long nowMs) {
        this.panelX = x;
        this.panelY = y;
        this.panelW = w;
        this.panelH = h;

        KeybindViewerScreen.paintPanelBase(g, font, x, y, w, h,
                Component.translatable("screen.newvisualkeybing.viewer.mouse").getString());
        ensureLabelWidths(font);
        layoutBody();
        refreshInputData();

        float dt = lastFrameMs > 0 ? Math.min((nowMs - lastFrameMs) / 1000f, 0.05f) : 0.016f;
        lastFrameMs = nowMs;
        pulseAccent = KeybindViewerScreen.pulseAccent(animTick);
        searchPulseColor = KeybindViewerScreen.searchPulseColor(animTick);
        searchPulseAlpha = KeybindViewerScreen.searchPulseAlpha(animTick);
        Integer hovered = updateStates(selectedVirtualKey, isVisibleKey, isHiddenKey, isSearchMatch,
                mouseX, mouseY, dt);

        if (UITheme.flat()) {
            renderFlat(g, font);
        } else {
            renderModern(g, font);
        }
        return hovered;
    }

    // ------------------------------------------------------------------ state ---------------------

    private Integer updateStates(Integer selectedVirtualKey, IntPredicate isVisibleKey,
                                 IntPredicate isHiddenKey, IntPredicate isSearchMatch,
                                 int mouseX, int mouseY, float dt) {
        Set<Integer> comboKeys = comboParticipantKeys();
        Integer hovered = null;
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            int key = KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
            matched[i] = isVisibleKey.test(key);
            hidden[i] = isHiddenKey.test(key);
            hover[i] = !hidden[i] && hits(i, mouseX, mouseY);
            selected[i] = selectedVirtualKey != null && selectedVirtualKey == key;
            searchMatch[i] = matched[i] && !hidden[i] && isSearchMatch.test(key);
            combo[i] = !hidden[i] && comboKeys.contains(key);
            if (hover[i]) hovered = key;
            hoverProgress[i] = advanceProgress(hoverProgress[i], hover[i] && matched[i] ? 1f : 0f, dt, 16f);
            selectProgress[i] = advanceProgress(selectProgress[i], selected[i] ? 1f : 0f, dt, 18f);
        }
        return hovered;
    }

    private boolean hits(int index, double mx, double my) {
        Rect b = bounds[index];
        if (b == null || !KeybindViewerScreen.inside(mx, my, b.x, b.y, b.w, b.h)) return false;
        if ((index == IDX_LMB || index == IDX_RMB) && silhouette != null && !UITheme.flat()) {
            return silhouette.insideBody(mx - bodyX, my - bodyY);
        }
        return true;
    }

    private Set<Integer> comboParticipantKeys() {
        KeybindComboStore store = KeybindComboStore.global();
        long v = store.version();
        if (v != cachedComboVersion) {
            cachedComboVersion = v;
            cachedComboKeys = store.participantVirtualKeys();
        }
        return cachedComboKeys;
    }

    private void refreshInputData() {
        long version = scanner.version();
        if (cachedDataVersion == version) return;
        cachedDataVersion = version;
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            KeyboardLayoutData.KeyDef key = KeyboardLayoutData.MOUSE_KEYS.get(i);
            if (KeyboardLayoutData.isWheel(key.glfwKey())) {
                cachedStatuses[i] = KeyBindingScanner.KeyStatus.FREE;
                cachedBindingCounts[i] = 0;
            } else {
                int mouseButton = KeyboardLayoutData.virtualToMouseBtn(key.glfwKey());
                cachedStatuses[i] = scanner.getMouseStatus(mouseButton);
                cachedBindingCounts[i] = scanner.getMouseBindingCount(mouseButton);
            }
        }
    }

    // ------------------------------------------------------------------ geometry ------------------

    private float computeMouseScale() {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        float byWidth = (panelW - PANEL_PAD * 2 - SIDE_PROTRUDE * 2 - 4) / (float) MOUSE_BODY_W;
        float byHeight = (availH - 4) / (float) MOUSE_BODY_H;
        return Mth.clamp(Math.min(byWidth, byHeight), 0.72f, 1.0f);
    }

    private void layoutBody() {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        float ms = computeMouseScale();
        int bw = Math.round(MOUSE_BODY_W * ms);
        int bh = Math.round(MOUSE_BODY_H * ms);
        int bx = panelX + (panelW - bw) / 2;
        int by = innerTop + Math.max(0, (availH - bh) / 2);
        if (bx == bodyX && by == bodyY && bw == bodyW && bh == bodyH && silhouette != null) return;
        bodyX = bx;
        bodyY = by;
        if (silhouette == null || bw != bodyW || bh != bodyH) silhouette = new MouseSilhouette(bw, bh);
        bodyW = bw;
        bodyH = bh;
        MouseSilhouette s = silhouette;

        int plateY1 = s.seamY - MouseSilhouette.PLATE_GAP;
        bounds[IDX_LMB] = new Rect(bx, by, s.leftPlateX1, plateY1);
        bounds[IDX_RMB] = new Rect(bx + s.rightPlateX0, by, bw - s.rightPlateX0, plateY1);
        bounds[IDX_MMB] = new Rect(bx + s.wheelX + 1, by + s.mmbY, s.wheelW - 2, s.mmbH);
        bounds[IDX_WHEEL_UP] = new Rect(bx + s.wheelX + 1, by + s.wheelY + 1, s.wheelW - 2, s.tickH - 1);
        bounds[IDX_WHEEL_DOWN] = new Rect(bx + s.wheelX + 1, by + s.wheelY + s.wheelH - s.tickH,
                s.wheelW - 2, s.tickH - 1);

        int leftY = by + Math.round(bh * LEFT_SIDE_T);
        for (int i = 0; i < 2; i++) {
            int yy = leftY + i * (SIDE_H + SIDE_GAP);
            int edge = Math.round(s.leftEdge(Math.min(bh - 1, yy - by + SIDE_H / 2)));
            bounds[IDX_M4 + i] = new Rect(bx + edge - SIDE_PROTRUDE, yy, SIDE_W, SIDE_H);
        }
        int rightY = by + Math.round(bh * RIGHT_SIDE_T);
        for (int i = 0; i < 3; i++) {
            int yy = rightY + i * (SIDE_H + SIDE_GAP);
            int edge = Math.round(s.rightEdge(Math.min(bh - 1, yy - by + SIDE_H / 2)));
            bounds[IDX_M4 + 2 + i] = new Rect(bx + edge - SIDE_W + SIDE_PROTRUDE, yy, SIDE_W, SIDE_H);
        }
    }

    Integer hitTest(double mx, double my) {
        if (bounds[0] == null) return null;
        // Side buttons first: they overlap the body flanks and must win over the plates.
        for (int i = IDX_M4; i <= IDX_M8; i++) {
            if (hits(i, mx, my)) return KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
        }
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            if (i >= IDX_M4 && i <= IDX_M8) continue;
            if (hits(i, mx, my)) return KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
        }
        return null;
    }

    // ------------------------------------------------------------------ modern skin ---------------

    private void renderModern(GuiGraphics g, Font font) {
        var c = UITheme.colors();
        MouseSilhouette s = silhouette;
        int bx = bodyX, by = bodyY;
        int frameFill = UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.42f);

        UITheme.fillShape(g, s.shadow, bx, by + 3, UITheme.withAlpha(0x000000, 0x48));
        UITheme.fillShape(g, s.body, bx, by, frameFill);

        renderPlate(g, IDX_LMB, s.leftPlate, s.leftPlateStroke, s.leftPlateGlow, s.leftPlateGloss,
                s.leftPlateEdge, s.leftPlateCombo);
        renderPlate(g, IDX_RMB, s.rightPlate, s.rightPlateStroke, s.rightPlateGlow, s.rightPlateGloss,
                s.rightPlateEdge, s.rightPlateCombo);

        for (int i = 0; i < MouseSilhouette.PALM_BANDS; i++) {
            UITheme.fillShape(g, s.palmBands[i], bx, by, UITheme.withAlpha(0x000000, 0x07 * (i + 1)));
        }
        UITheme.fillShape(g, s.seam, bx, by, UITheme.withAlpha(0x000000, 0x90));
        UITheme.fillShape(g, s.seamLight, bx, by, UITheme.withAlpha(0xFFFFFF, 0x1C));

        renderWheelSlot(g, frameFill);
        UITheme.fillShape(g, s.bodyStroke, bx, by, UITheme.withAlpha(c.widgetBorder(), 0xE0));

        for (int i = IDX_M4; i <= IDX_M8; i++) renderPill(g, i, SIDE_W / 2);

        renderLabels(g, font);
    }

    private void renderPlate(GuiGraphics g, int idx, int[] fill, int[] stroke, int[] glow,
                             int[] gloss, int[] edge, int[] comboBar) {
        var c = UITheme.colors();
        int bx = bodyX, by = bodyY;
        KeyBindingScanner.KeyStatus status = cachedStatuses[idx];
        boolean active = hover[idx] || selected[idx];
        float hoverEase = UITheme.easeOutCubic(hoverProgress[idx]);
        float selectEase = UITheme.easeOutCubic(selectProgress[idx]);

        int plateFill = hidden[idx] ? UITheme.withAlpha(c.widgetBg(), 0x30)
                : KeybindViewerScreen.keyStatusColor(status, matched[idx]);
        UITheme.fillShape(g, fill, bx, by, plateFill);
        if (!hidden[idx]) {
            UITheme.fillShape(g, gloss, bx, by, UITheme.withAlpha(0xFFFFFF, active ? 0x1E : 0x12));
            if (status != KeyBindingScanner.KeyStatus.FREE) {
                UITheme.fillShape(g, edge, bx, by,
                        UITheme.withAlpha(KeybindViewerScreen.keyStatusColor(status), active ? 0xD8 : 0xA0));
            }
            if (combo[idx]) {
                int comboColor = matched[idx] ? KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR
                        : UITheme.withAlpha(KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR, 0x70);
                UITheme.fillShape(g, comboBar, bx, by, comboColor);
            }
        }

        if (searchMatch[idx] && hoverEase < 0.99f && selectEase < 0.99f) {
            int a = Math.round(searchPulseAlpha * (1f - Math.max(hoverEase, selectEase)));
            if (a > 0) UITheme.fillShape(g, glow, bx, by, UITheme.withAlpha(searchPulseColor, Math.max(0x14, a / 2)));
        }
        if (selectProgress[idx] > 0.005f) {
            UITheme.fillShape(g, glow, bx, by, UITheme.withAlpha(pulseAccent, Math.round(0x60 * selectEase)));
        }
        int baseBorder = matched[idx] && !hidden[idx] ? UITheme.withAlpha(c.widgetBorder(), 0xB0)
                : UITheme.withAlpha(c.widgetBorder(), hidden[idx] ? 0x30 : 0x60);
        int border = selectProgress[idx] > hoverProgress[idx]
                ? UITheme.lerpColor(baseBorder, pulseAccent, selectEase)
                : UITheme.lerpColor(baseBorder, c.accentAlt(), hoverEase);
        UITheme.fillShape(g, stroke, bx, by, border);
    }

    private void renderWheelSlot(GuiGraphics g, int frameFill) {
        var c = UITheme.colors();
        MouseSilhouette s = silhouette;
        int sx = bodyX + s.wheelX;
        int sy = bodyY + s.wheelY;
        int slotR = s.wheelW / 2;
        int slotColor = UITheme.lerpColor(frameFill, 0x000000, 0.58f);
        UITheme.fillRoundedRect(g, sx, sy, s.wheelW, s.wheelH, slotR, slotColor);
        UITheme.fillRoundedRectEx(g, sx + 1, sy + 1, s.wheelW - 2, Math.max(3, s.tickH / 2),
                slotR - 1, slotR - 1, 1, 1, UITheme.withAlpha(0x000000, 0x50));
        UITheme.drawRoundedBorder(g, sx, sy, s.wheelW, s.wheelH, slotR, UITheme.withAlpha(0x000000, 0x70));

        renderTick(g, IDX_WHEEL_UP, true);
        renderTick(g, IDX_WHEEL_DOWN, false);

        // The wheel itself is the middle button.
        int idx = IDX_MMB;
        Rect b = bounds[idx];
        int inset = Math.max(2, s.wheelW / 6);
        int wx = sx + inset;
        int ww = s.wheelW - inset * 2;
        int wy = b.y + 1;
        int wh = b.h - 2;
        int wr = Math.max(2, ww / 2);
        KeyBindingScanner.KeyStatus status = cachedStatuses[idx];
        boolean active = hover[idx] || selected[idx];
        float hoverEase = UITheme.easeOutCubic(hoverProgress[idx]);
        float selectEase = UITheme.easeOutCubic(selectProgress[idx]);
        int wheelFill = hidden[idx] ? UITheme.withAlpha(c.widgetBg(), 0x40)
                : status == KeyBindingScanner.KeyStatus.FREE
                    ? UITheme.lerpColor(c.widgetBg(), 0xFFFFFFFF, matched[idx] ? 0.16f : 0.06f)
                    : KeybindViewerScreen.keyStatusColor(status, matched[idx]);
        if (searchMatch[idx] && hoverEase < 0.99f && selectEase < 0.99f) {
            int a = Math.round(searchPulseAlpha * (1f - Math.max(hoverEase, selectEase)));
            if (a > 0) {
                UITheme.drawRoundedBorder(g, wx - 2, wy - 2, ww + 4, wh + 4, wr + 2,
                        UITheme.withAlpha(searchPulseColor, Math.max(0x14, a / 3)));
            }
        }
        if (selectProgress[idx] > 0.005f) {
            UITheme.drawRoundedBorder(g, wx - 2, wy - 2, ww + 4, wh + 4, wr + 2,
                    UITheme.withAlpha(pulseAccent, Math.round(0x50 * selectEase)));
        }
        UITheme.fillRoundedRect(g, wx, wy, ww, wh, wr, wheelFill);
        if (!hidden[idx]) {
            // Rubber ribs + a soft highlight so the wheel reads as a cylinder.
            int ribColor = UITheme.withAlpha(0x000000, 0x48);
            for (int ry = wy + wr; ry < wy + wh - wr; ry += 3) {
                g.fill(wx + 1, ry, wx + ww - 1, ry + 1, ribColor);
            }
            UITheme.fillRoundedRectEx(g, wx + 1, wy + 1, ww - 2, Math.max(2, wh / 4),
                    wr - 1, wr - 1, 1, 1, UITheme.withAlpha(0xFFFFFF, active ? 0x28 : 0x18));
        }
        int baseBorder = UITheme.withAlpha(0x000000, hidden[idx] ? 0x30 : 0x70);
        int border = selectProgress[idx] > hoverProgress[idx]
                ? UITheme.lerpColor(baseBorder, pulseAccent, selectEase)
                : UITheme.lerpColor(baseBorder, c.accentAlt(), hoverEase);
        UITheme.drawRoundedBorder(g, wx, wy, ww, wh, wr, border);
    }

    /** Scroll-up / scroll-down zone at an end of the wheel slot: a small triangle, lit on hover. */
    private void renderTick(GuiGraphics g, int idx, boolean up) {
        var c = UITheme.colors();
        Rect b = bounds[idx];
        float hoverEase = UITheme.easeOutCubic(hoverProgress[idx]);
        float selectEase = UITheme.easeOutCubic(selectProgress[idx]);
        if (hoverEase > 0.01f || selectEase > 0.01f) {
            int a = Math.round(0x38 * Math.max(hoverEase, selectEase));
            int col = selectEase > hoverEase ? pulseAccent : c.accentAlt();
            UITheme.fillRoundedRect(g, b.x, b.y, b.w, b.h, Math.min(4, b.w / 2), UITheme.withAlpha(col, a));
        }
        if (hidden[idx]) return;
        int size = Math.max(2, Math.min(3, b.w / 4));
        int cx = b.x + b.w / 2;
        int cy = b.y + (b.h - size) / 2;
        int col = !matched[idx] ? UITheme.withAlpha(c.textMuted(), 0x80)
                : hover[idx] || selected[idx] ? c.textPrimary() : c.textSecondary();
        for (int i = 0; i < size; i++) {
            int row = up ? cy + i : cy + size - 1 - i;
            g.fill(cx - i, row, cx + i + 1, row + 1, col);
        }
    }

    /** Side / thumb button: a pill hugging the body flank, with the standard key state chrome. */
    private void renderPill(GuiGraphics g, int idx, int radius) {
        var c = UITheme.colors();
        Rect b = bounds[idx];
        KeyBindingScanner.KeyStatus status = cachedStatuses[idx];
        boolean active = hover[idx] || selected[idx];
        float hoverEase = UITheme.easeOutCubic(hoverProgress[idx]);
        float selectEase = UITheme.easeOutCubic(selectProgress[idx]);

        if (searchMatch[idx] && hoverEase < 0.99f && selectEase < 0.99f) {
            int a = Math.round(searchPulseAlpha * (1f - Math.max(hoverEase, selectEase)));
            if (a > 0) {
                UITheme.drawRoundedBorder(g, b.x - 2, b.y - 2, b.w + 4, b.h + 4, radius + 2,
                        UITheme.withAlpha(searchPulseColor, Math.max(0x14, a / 3)));
            }
        }
        if (selectProgress[idx] > 0.005f) {
            UITheme.drawRoundedBorder(g, b.x - 2, b.y - 2, b.w + 4, b.h + 4, radius + 2,
                    UITheme.withAlpha(pulseAccent, Math.round(0x50 * selectEase)));
        }
        UITheme.fillRoundedRect(g, b.x, b.y + 2, b.w, b.h, radius, UITheme.withAlpha(0x000000, 0x40));
        int fill = hidden[idx] ? UITheme.withAlpha(c.widgetBg(), 0x30)
                : KeybindViewerScreen.keyStatusColor(status, matched[idx]);
        UITheme.fillRoundedRect(g, b.x, b.y, b.w, b.h, radius, fill);
        if (!hidden[idx]) {
            UITheme.fillRoundedRectEx(g, b.x + 1, b.y + 1, b.w - 2, Math.max(2, b.h / 2 - 1),
                    radius - 1, radius - 1, 1, 1, UITheme.withAlpha(0xFFFFFF, active ? 0x1E : 0x12));
            if (status != KeyBindingScanner.KeyStatus.FREE) {
                UITheme.fillRoundedRectEx(g, b.x + 3, b.y + b.h - 3, b.w - 6, 2, 1, 1, 1, 1,
                        UITheme.withAlpha(KeybindViewerScreen.keyStatusColor(status), active ? 0xD8 : 0xA0));
            }
            if (combo[idx]) {
                int comboColor = matched[idx] ? KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR
                        : UITheme.withAlpha(KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR, 0x70);
                UITheme.fillRoundedRect(g, b.x + 3, b.y + 1, b.w - 6, 2, 1, comboColor);
            }
        }
        int baseBorder = matched[idx] && !hidden[idx] ? c.widgetBorder()
                : UITheme.withAlpha(c.widgetBorder(), hidden[idx] ? 0x28 : 0x60);
        int border = selectProgress[idx] > hoverProgress[idx]
                ? UITheme.lerpColor(baseBorder, pulseAccent, selectEase)
                : UITheme.lerpColor(baseBorder, c.accentAlt(), hoverEase);
        UITheme.drawRoundedBorder(g, b.x, b.y, b.w, b.h, radius, border);
    }

    private void renderLabels(GuiGraphics g, Font font) {
        var c = UITheme.colors();
        MouseSilhouette s = silhouette;
        int plateY1 = s.seamY - MouseSilhouette.PLATE_GAP;
        int labelRow = Math.round(plateY1 * 0.58f);
        float leftEdge = s.leftEdge(labelRow);
        float rightEdge = s.rightEdge(labelRow);
        int lmbCx = Math.round((leftEdge + s.leftPlateX1) / 2f);
        int rmbCx = Math.round((s.rightPlateX0 + rightEdge) / 2f);
        int labelY = bodyY + labelRow - font.lineHeight / 2;
        drawPlateLabel(g, font, IDX_LMB, bodyX + lmbCx, labelY);
        drawPlateLabel(g, font, IDX_RMB, bodyX + rmbCx, labelY);

        for (int i = IDX_M4; i <= IDX_M8; i++) {
            Rect b = bounds[i];
            if (hidden[i]) continue;
            KeyBindingScanner.KeyStatus status = cachedStatuses[i];
            int textColor = matched[i] ? KeybindViewerScreen.labelColorForStatus(status)
                    : UITheme.withAlpha(c.textMuted(), 0x80);
            g.drawString(font, KeyboardLayoutData.MOUSE_KEYS.get(i).label(),
                    b.x + (b.w - labelWidths[i]) / 2, b.y + (b.h - font.lineHeight) / 2 + 1, textColor, false);
            if (!hidden[i]) renderBadge(g, font, b.x + b.w - 2, b.y - 2, cachedBindingCounts[i], status, true);
        }

        // Badges sit just above the seam, tucked against the wheel slot.
        int badgeY = bodyY + plateY1 - font.lineHeight - 3;
        if (!hidden[IDX_LMB]) {
            renderBadge(g, font, bodyX + s.leftPlateX1 - 3, badgeY, cachedBindingCounts[IDX_LMB],
                    cachedStatuses[IDX_LMB], true);
        }
        if (!hidden[IDX_RMB]) {
            renderBadge(g, font, bodyX + s.rightPlateX0 + 3, badgeY, cachedBindingCounts[IDX_RMB],
                    cachedStatuses[IDX_RMB], false);
        }
        if (!hidden[IDX_MMB]) {
            Rect b = bounds[IDX_MMB];
            renderBadge(g, font, b.x + b.w + 1, b.y - 1, cachedBindingCounts[IDX_MMB], cachedStatuses[IDX_MMB], false);
        }
    }

    private void drawPlateLabel(GuiGraphics g, Font font, int idx, int centerX, int y) {
        if (hidden[idx]) return;
        var c = UITheme.colors();
        KeyBindingScanner.KeyStatus status = cachedStatuses[idx];
        int textColor = matched[idx] ? KeybindViewerScreen.labelColorForStatus(status)
                : UITheme.withAlpha(c.textMuted(), 0x80);
        g.drawString(font, KeyboardLayoutData.MOUSE_KEYS.get(idx).label(),
                centerX - labelWidths[idx] / 2, y, textColor, false);
    }

    /**
     * Binding-count chip. {@code anchorX} is the chip's right edge when {@code alignRight}, else its
     * left edge; the chip is skipped for counts below two.
     */
    private static void renderBadge(GuiGraphics g, Font font, int anchorX, int y, int count,
                                    KeyBindingScanner.KeyStatus status, boolean alignRight) {
        if (count <= 1) return;
        var c = UITheme.colors();
        String text = String.valueOf(count);
        int bw = font.width(text) + 6;
        int bh = font.lineHeight;
        int bx = alignRight ? anchorX - bw : anchorX;
        int chipColor = status == KeyBindingScanner.KeyStatus.CONFLICT ? c.danger()
                : status == KeyBindingScanner.KeyStatus.COMBO ? c.warning()
                : c.accent();
        UITheme.fillRoundedRect(g, bx, y, bw, bh, bh / 2, chipColor);
        g.drawString(font, text, bx + 3, y + 1, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ flat skins ----------------

    /**
     * Vanilla / custom skins: blocky rectangle body (the theme helpers degrade rounded shapes to
     * flat fills + bevels), rectangular buttons, and optional user textures for body and buttons.
     */
    private void renderFlat(GuiGraphics g, Font font) {
        var c = UITheme.colors();
        MouseSilhouette s = silhouette;
        boolean customBody = UITheme.custom() && UITextureStore.global().has(UITextureSlot.MOUSE_BODY);
        if (customBody) {
            UITextureStore.global().draw(UITextureSlot.MOUSE_BODY, g, bodyX, bodyY, bodyW, bodyH);
        } else {
            int frameFill = UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.42f);
            UITheme.fillRoundedRect(g, bodyX, bodyY, bodyW, bodyH, 0, frameFill);
            int splitY = bodyY + s.seamY;
            g.fill(bodyX + 4, splitY, bodyX + bodyW - 4, splitY + 1, UITheme.withAlpha(c.divider(), 0xC0));
            UITheme.fillRoundedRect(g, bodyX + s.wheelX, bodyY + s.wheelY, s.wheelW, s.wheelH, 0,
                    UITheme.lerpColor(frameFill, 0x000000, 0.45f));
            UITheme.drawRoundedBorder(g, bodyX, bodyY, bodyW, bodyH, 0, UITheme.withAlpha(c.widgetBorder(), 0xD0));
        }
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect b = bounds[i];
            boolean wheel = i == IDX_WHEEL_UP || i == IDX_WHEEL_DOWN;
            KeyBindingScanner.KeyStatus status = cachedStatuses[i];
            int radius = Math.max(2, Math.min(4, Math.min(b.w, b.h) / 4));
            float hoverEase = UITheme.easeOutCubic(hoverProgress[i]);
            float selectEase = UITheme.easeOutCubic(selectProgress[i]);
            int fill = hidden[i] ? UITheme.withAlpha(c.widgetBg(), 0x24)
                    : KeybindViewerScreen.keyStatusColor(status, matched[i]);
            boolean textured = false;
            if (UITheme.custom() && !wheel) {
                UITextureStore store = UITextureStore.global();
                textured = store.drawTinted(UITextureSlot.MOUSE_BUTTON, g, b.x, b.y, b.w, b.h, fill)
                        || store.drawTinted(UITextureSlot.KEY, g, b.x, b.y, b.w, b.h, fill);
            }
            if (!textured) {
                UITheme.fillRoundedRect(g, b.x, b.y, b.w, b.h, radius, fill);
                if (!hidden[i] && status != KeyBindingScanner.KeyStatus.FREE) {
                    g.fill(b.x + 2, b.y + b.h - 3, b.x + b.w - 2, b.y + b.h - 1,
                            UITheme.withAlpha(KeybindViewerScreen.keyStatusColor(status), 0xA0));
                }
                int baseBorder = matched[i] && !hidden[i] ? c.widgetBorder()
                        : UITheme.withAlpha(c.widgetBorder(), hidden[i] ? 0x28 : 0x60);
                int border = selectProgress[i] > hoverProgress[i]
                        ? UITheme.lerpColor(baseBorder, pulseAccent, selectEase)
                        : UITheme.lerpColor(baseBorder, c.accentAlt(), hoverEase);
                UITheme.drawRoundedBorder(g, b.x, b.y, b.w, b.h, radius, border);
            } else if (selected[i]) {
                UITheme.drawRoundedBorder(g, b.x - 1, b.y - 1, b.w + 2, b.h + 2, 2, 0xFFFFFFFF);
            } else if (hover[i] && matched[i]) {
                UITheme.drawRoundedBorder(g, b.x, b.y, b.w, b.h, 2, c.accentLight());
            }
            if (combo[i] && b.w >= 10 && b.h >= 8) {
                int comboColor = matched[i] ? KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR
                        : UITheme.withAlpha(KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR, 0x70);
                g.fill(b.x + 3, b.y + 1, b.x + b.w - 3, b.y + 3, comboColor);
            }
            if (!hidden[i] && b.w >= labelWidths[i] + 2 && b.h >= font.lineHeight) {
                int textColor = matched[i] ? KeybindViewerScreen.labelColorForStatus(status)
                        : UITheme.withAlpha(c.textMuted(), 0x80);
                g.drawString(font, KeyboardLayoutData.MOUSE_KEYS.get(i).label(),
                        b.x + (b.w - labelWidths[i]) / 2, b.y + (b.h - font.lineHeight) / 2, textColor, false);
            }
            if (!hidden[i] && b.w >= 14 && b.h >= 12) {
                renderBadge(g, font, b.x + b.w - 2, b.y + (combo[i] ? 4 : 2), cachedBindingCounts[i], status, true);
            }
        }
    }

    // ------------------------------------------------------------------ helpers -------------------

    private static float advanceProgress(float current, float target, float dt, float speed) {
        float updated = current + (target - current) * Math.min(1f, dt * speed);
        if (Math.abs(updated - target) < 0.003f) return target;
        return updated;
    }

    private void ensureLabelWidths(Font font) {
        if (font == cachedLabelFont) return;
        cachedLabelFont = font;
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            labelWidths[i] = font.width(KeyboardLayoutData.MOUSE_KEYS.get(i).label());
        }
    }

    private record Rect(int x, int y, int w, int h) {}
}
