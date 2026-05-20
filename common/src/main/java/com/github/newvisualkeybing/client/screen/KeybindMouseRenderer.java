package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntPredicate;




final class KeybindMouseRenderer {

    static final int MOUSE_BODY_W = 80;
    static final int MOUSE_BODY_H = 116;
    static final int MOUSE_TOP_R = 26;
    static final int MOUSE_BOT_R = 14;
    static final int MOUSE_TOP_AREA_H = 50;
    static final int WHEEL_COL_W = 14;
    static final int WHEEL_TICK_H = 10;
    static final int SIDE_W = 14;
    static final int SIDE_H = 18;
    static final int SIDE_GAP = 3;
    static final int SIDE_OFFSET = 3;

    private static final int PANEL_PAD = 12;
    private static final int PANEL_CONTENT_TOP = 28;

    private final KeyBindingScanner scanner;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final Rect[] bounds = new Rect[KeyboardLayoutData.MOUSE_KEYS.size()];
    private int cachedBodyX = Integer.MIN_VALUE;
    private int cachedBodyY = Integer.MIN_VALUE;
    private int cachedBodyW = Integer.MIN_VALUE;
    private int cachedBodyH = Integer.MIN_VALUE;
    private float cachedMouseScale = Float.NaN;
    private final int[] labelWidths = new int[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final KeyBindingScanner.KeyStatus[] cachedStatuses =
            new KeyBindingScanner.KeyStatus[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final int[] cachedBindingCounts = new int[KeyboardLayoutData.MOUSE_KEYS.size()];
    private Font cachedLabelFont;
    private final float[] hoverProgress = new float[KeyboardLayoutData.MOUSE_KEYS.size()];
    private final float[] selectProgress = new float[KeyboardLayoutData.MOUSE_KEYS.size()];
    private long cachedDataVersion = Long.MIN_VALUE;
    private long lastFrameMs;

    KeybindMouseRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    


    Integer render(GuiGraphics g, Font font, int x, int y, int w, int h,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
                   IntPredicate isHiddenKey, IntPredicate isSearchMatch,
                   int mouseX, int mouseY, float animTick, long nowMs) {
        var c = UITheme.colors();
        this.panelX = x;
        this.panelY = y;
        this.panelW = w;
        this.panelH = h;

        KeybindViewerScreen.paintPanelBase(g, font, x, y, w, h,
                Component.translatable("screen.newvisualkeybing.viewer.mouse").getString());
        ensureLabelWidths(font);

        int innerTop = y + PANEL_CONTENT_TOP;
        int innerBottom = y + h - PANEL_PAD;
        int availH = innerBottom - innerTop;

        float mouseScale = Mth.clamp(Math.min(
                (w - PANEL_PAD * 2 - SIDE_W * 2 - SIDE_OFFSET * 2) / (float) MOUSE_BODY_W,
                availH / (float) MOUSE_BODY_H), 0.78f, 1.0f);
        int bodyW = Math.round(MOUSE_BODY_W * mouseScale);
        int bodyH = Math.round(MOUSE_BODY_H * mouseScale);
        int bodyX = x + (w - bodyW) / 2;
        int bodyY = innerTop + (availH - bodyH) / 2;
        int rTop = Math.round(MOUSE_TOP_R * mouseScale);
        int rBot = Math.round(MOUSE_BOT_R * mouseScale);
        updateBounds(bodyX, bodyY, bodyW, bodyH, mouseScale);
        refreshInputData();

        int shadow = UITheme.withAlpha(0x000000, 0x40);
        UITheme.fillRoundedRectEx(g, bodyX - 1, bodyY + 3, bodyW + 2, bodyH,
                rTop + 1, rTop + 1, rBot + 1, rBot + 1, shadow);

        int frameFill = UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.42f);
        UITheme.fillRoundedRectEx(g, bodyX, bodyY, bodyW, bodyH,
                rTop, rTop, rBot, rBot, frameFill);

        int hlH = Math.max(4, Math.round(MOUSE_TOP_AREA_H * mouseScale * 0.7f));
        UITheme.fillRoundedRectEx(g, bodyX + 2, bodyY + 2, bodyW - 4, hlH,
                rTop - 2, rTop - 2, 2, 2, UITheme.withAlpha(0xFFFFFF, 0x10));

        int splitY = bodyY + Math.round(MOUSE_TOP_AREA_H * mouseScale);
        g.fill(bodyX + 8, splitY, bodyX + bodyW - 8, splitY + 1,
                UITheme.withAlpha(c.divider(), 0xC0));
        g.fill(bodyX + 8, splitY + 1, bodyX + bodyW - 8, splitY + 2,
                UITheme.withAlpha(0x000000, 0x20));

        int leftW = (bodyW - WHEEL_COL_W) / 2;
        int wheelX = bodyX + leftW;
        int wheelDepthColor = UITheme.lerpColor(frameFill, 0x000000, 0.45f);
        UITheme.fillRoundedRectFast(g, wheelX, bodyY + 1, WHEEL_COL_W,
                Math.round(MOUSE_TOP_AREA_H * mouseScale) - 1, 4, wheelDepthColor);

        UITheme.drawRoundedBorderEx(g, bodyX, bodyY, bodyW, bodyH,
                rTop, rTop, rBot, rBot,
                UITheme.withAlpha(c.widgetBorder(), 0xD0));

        float dt = lastFrameMs > 0 ? Math.min((nowMs - lastFrameMs) / 1000f, 0.05f) : 0.016f;
        lastFrameMs = nowMs;
        int pulseAccent = KeybindViewerScreen.pulseAccent(animTick);
        int searchPulseColor = KeybindViewerScreen.searchPulseColor(animTick);
        int searchPulseAlpha = KeybindViewerScreen.searchPulseAlpha(animTick);
        Integer hovered = null;
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect b = boundsAt(i);
            if (b == null) continue;
            KeyboardLayoutData.KeyDef key = KeyboardLayoutData.MOUSE_KEYS.get(i);
            boolean wheel = KeyboardLayoutData.isWheel(key.glfwKey());
            boolean matched = isVisibleKey.test(key.glfwKey());
            boolean hidden = isHiddenKey.test(key.glfwKey());
            boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, b.x, b.y, b.w, b.h);
            boolean selected = selectedVirtualKey != null && selectedVirtualKey == key.glfwKey();
            if (hidden) hover = false;
            if (hover) hovered = key.glfwKey();

            hoverProgress[i] = advanceProgress(hoverProgress[i],
                    hover && matched ? 1f : 0f, dt, 16f);
            selectProgress[i] = advanceProgress(selectProgress[i],
                    selected ? 1f : 0f, dt, 18f);
            float hoverEase = UITheme.easeOutCubic(hoverProgress[i]);
            float selectEase = UITheme.easeOutCubic(selectProgress[i]);

            KeyBindingScanner.KeyStatus status = cachedStatuses[i];
            int bindingCount = cachedBindingCounts[i];
            int radius = Math.max(3, Math.min(6, Math.min(b.w, b.h) / 4));

            boolean searchMatch = matched && !hidden && isSearchMatch.test(key.glfwKey());
            if (searchMatch && hoverEase < 0.99f && selectEase < 0.99f) {
                int searchInner = Math.round(searchPulseAlpha * (1f - Math.max(hoverEase, selectEase)));
                if (searchInner > 0) {
                    UITheme.drawRoundedBorderFast(g, b.x - 2, b.y - 2, b.w + 4, b.h + 4, radius + 2,
                            UITheme.withAlpha(searchPulseColor, Math.max(0x14, searchInner / 3)));
                }
            }
            if (matched && !hidden && hoverProgress[i] > 0.005f && selectProgress[i] < 0.99f) {
                int alpha = Math.round(0x55 * hoverEase * (1f - selectEase));
                if (alpha > 0) {
                    UITheme.drawRoundedBorderFast(g, b.x - 1, b.y - 1, b.w + 2, b.h + 2, radius + 1,
                            UITheme.withAlpha(c.accentAlt(), alpha));
                }
            }
            if (selectProgress[i] > 0.005f) {
                int outerAlpha = Math.round(0x38 * selectEase);
                int grow = Math.round(2f * selectEase);
                if (outerAlpha > 0 && grow > 0) {
                    UITheme.drawRoundedBorderFast(g, b.x - grow - 1, b.y - grow - 1,
                            b.w + grow * 2 + 2, b.h + grow * 2 + 2, radius + 2,
                            UITheme.withAlpha(pulseAccent, outerAlpha));
                }
            }

            int fill = KeybindViewerScreen.keyStatusColor(status, matched);
            if (hidden) fill = UITheme.withAlpha(c.widgetBg(), 0x24);
            UITheme.fillRoundedRectFast(g, b.x, b.y, b.w, b.h, radius, fill);
            renderMouseButtonSurface(g, b, radius, status, hover || selected, wheel, hidden);
            int baseBorder = matched && !hidden ? c.widgetBorder()
                    : UITheme.withAlpha(c.widgetBorder(), hidden ? 0x28 : 0x60);
            int targetBorder = selectProgress[i] > hoverProgress[i]
                    ? UITheme.lerpColor(baseBorder, pulseAccent, selectEase)
                    : UITheme.lerpColor(baseBorder, c.accentAlt(), hoverEase);
            UITheme.drawRoundedBorderFast(g, b.x, b.y, b.w, b.h, radius, targetBorder);

            if (!hidden && b.w >= 14 && b.h >= 10) {
                int textColor = matched ? KeybindViewerScreen.labelColorForStatus(status)
                        : UITheme.withAlpha(c.textMuted(), 0x80);
                String label = key.label();
                g.drawString(font, label,
                        b.x + (b.w - labelWidths[i]) / 2,
                        b.y + (b.h - font.lineHeight) / 2,
                        textColor, false);
            }
            boolean comboParticipant = !hidden && KeybindComboStore.global().isParticipant(key.glfwKey());
            if (comboParticipant && b.w >= 10 && b.h >= 8) {
                int comboColor = matched ? KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR
                        : UITheme.withAlpha(KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR, 0x70);
                g.fill(b.x + 2, b.y + 1, b.x + b.w - 2, b.y + 3, comboColor);
            }
            if (!hidden) renderBindingBadge(g, font, b, bindingCount, status, comboParticipant);
        }
        return hovered;
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

    private static void renderMouseButtonSurface(GuiGraphics g, Rect b, int radius,
                                                 KeyBindingScanner.KeyStatus status, boolean active,
                                                 boolean wheel, boolean hidden) {
        var c = UITheme.colors();
        if (hidden) return;
        g.fill(b.x + 1, b.y + 1, b.x + b.w - 1, Math.max(b.y + 2, b.y + b.h / 2),
                UITheme.withAlpha(0xFFFFFF, active ? 0x18 : 0x0E));
        if (wheel) {
            int midX = b.x + b.w / 2;
            g.fill(midX - 1, b.y + 2, midX, b.y + b.h - 2, UITheme.withAlpha(c.divider(), 0x80));
            return;
        }
        if (status != KeyBindingScanner.KeyStatus.FREE) {
            int edgeColor = KeybindViewerScreen.keyStatusColor(status);
            UITheme.fillRoundedRectFast(g, b.x + 2, b.y + b.h - 3, b.w - 4, 2, Math.max(1, radius - 1),
                    UITheme.withAlpha(edgeColor, active ? 0xD0 : 0x9A));
        }
    }

    private static void renderBindingBadge(GuiGraphics g, Font font, Rect b, int count,
                                           KeyBindingScanner.KeyStatus status,
                                           boolean comboTopBar) {
        if (count <= 1 || b.w < 16 || b.h < 14) return;
        var c = UITheme.colors();
        String text = String.valueOf(count);
        int bw = font.width(text) + 6;
        int bh = font.lineHeight;
        int bx = b.x + b.w - bw - 2;
        int by = b.y + (comboTopBar ? 5 : 2);
        int chipColor = status == KeyBindingScanner.KeyStatus.CONFLICT ? c.danger()
                : status == KeyBindingScanner.KeyStatus.COMBO ? c.warning()
                : c.accent();
        UITheme.fillRoundedRectFast(g, bx, by, bw, bh, bh / 2, chipColor);
        g.drawString(font, text, bx + 3, by + 1, 0xFFFFFFFF, false);
    }

    


    Integer hitTest(double mx, double my) {
        for (int i = 0; i < KeyboardLayoutData.MOUSE_KEYS.size(); i++) {
            Rect b = boundsAt(i);
            if (b != null && KeybindViewerScreen.inside(mx, my, b.x, b.y, b.w, b.h)) {
                return KeyboardLayoutData.MOUSE_KEYS.get(i).glfwKey();
            }
        }
        return null;
    }

    private float computeMouseScale() {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        return Mth.clamp(Math.min(
                (panelW - PANEL_PAD * 2 - SIDE_W * 2 - SIDE_OFFSET * 2) / (float) MOUSE_BODY_W,
                availH / (float) MOUSE_BODY_H), 0.78f, 1.0f);
    }

    private Rect boundsAt(int index) {
        if (index < 0 || index >= bounds.length) return null;
        if (bounds[index] == null) updateBoundsFromPanel();
        return bounds[index];
    }

    private void updateBoundsFromPanel() {
        int innerTop = panelY + PANEL_CONTENT_TOP;
        int innerBottom = panelY + panelH - PANEL_PAD;
        int availH = innerBottom - innerTop;
        float ms = computeMouseScale();
        int bodyW = Math.round(MOUSE_BODY_W * ms);
        int bodyH = Math.round(MOUSE_BODY_H * ms);
        int bodyX = panelX + (panelW - bodyW) / 2;
        int bodyY = innerTop + (availH - bodyH) / 2;
        updateBounds(bodyX, bodyY, bodyW, bodyH, ms);
    }

    private void updateBounds(int bodyX, int bodyY, int bodyW, int bodyH, float ms) {
        if (bodyX == cachedBodyX && bodyY == cachedBodyY && bodyW == cachedBodyW
                && bodyH == cachedBodyH && Float.compare(ms, cachedMouseScale) == 0) {
            return;
        }
        cachedBodyX = bodyX;
        cachedBodyY = bodyY;
        cachedBodyW = bodyW;
        cachedBodyH = bodyH;
        cachedMouseScale = ms;

        int leftW = (bodyW - WHEEL_COL_W) / 2;
        int rightW = bodyW - leftW - WHEEL_COL_W;
        int topH = Math.round(MOUSE_TOP_AREA_H * ms);
        int wheelTickH = WHEEL_TICK_H;
        int mmbH = topH - wheelTickH * 2;

        int wheelX = bodyX + leftW;
        int wheelY = bodyY;

        int leftSideX = bodyX - SIDE_W - SIDE_OFFSET;
        int leftStackH = SIDE_H * 2 + SIDE_GAP;
        int leftSideY = bodyY + (bodyH - leftStackH) / 2;

        int rightSideX = bodyX + bodyW + SIDE_OFFSET;
        int rightStackH = SIDE_H * 3 + SIDE_GAP * 2;
        int rightSideY = bodyY + (bodyH - rightStackH) / 2;

        int rTop = Math.round(MOUSE_TOP_R * ms);
        int topInset = Math.max(2, rTop / 4);

        bounds[0] = new Rect(bodyX + topInset, bodyY + 2, leftW - topInset - 1, topH - 4);
        bounds[1] = new Rect(wheelX + 1, wheelY + wheelTickH + 1, WHEEL_COL_W - 2, mmbH - 2);
        bounds[2] = new Rect(bodyX + leftW + WHEEL_COL_W + 1, bodyY + 2, rightW - topInset - 1, topH - 4);
        bounds[3] = new Rect(leftSideX, leftSideY, SIDE_W, SIDE_H);
        bounds[4] = new Rect(leftSideX, leftSideY + SIDE_H + SIDE_GAP, SIDE_W, SIDE_H);
        bounds[5] = new Rect(rightSideX, rightSideY, SIDE_W, SIDE_H);
        bounds[6] = new Rect(rightSideX, rightSideY + SIDE_H + SIDE_GAP, SIDE_W, SIDE_H);
        bounds[7] = new Rect(rightSideX, rightSideY + (SIDE_H + SIDE_GAP) * 2, SIDE_W, SIDE_H);
        bounds[8] = new Rect(wheelX + 1, wheelY + 1, WHEEL_COL_W - 2, wheelTickH - 2);
        bounds[9] = new Rect(wheelX + 1, wheelY + wheelTickH + mmbH + 1, WHEEL_COL_W - 2, wheelTickH - 2);
    }

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
