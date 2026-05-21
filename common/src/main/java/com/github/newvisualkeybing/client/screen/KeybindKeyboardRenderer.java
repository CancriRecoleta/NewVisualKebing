package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;





final class KeybindKeyboardRenderer {

    static final int COMBO_HIGHLIGHT_COLOR = 0xFFFFD500;

    private final KeyBindingScanner scanner;
    private KeyboardLayoutData.Style cachedStyle;
    private int cachedKeyboardX = Integer.MIN_VALUE;
    private int cachedKeyboardY = Integer.MIN_VALUE;
    private float cachedKeyScale = Float.NaN;
    private KeyDrawState[] drawStates = new KeyDrawState[0];
    private long lastFrameMs;
    private long cachedComboVersion = Long.MIN_VALUE;
    private Set<Integer> cachedComboKeys = java.util.Collections.emptySet();

    KeybindKeyboardRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    


    Integer render(GuiGraphics g, Font font, KeyboardLayoutData.Style style,
                   int keyboardX, int keyboardY, float keyScale,
                   Integer selectedVirtualKey, IntPredicate isVisibleKey,
                   IntPredicate isHiddenKey, IntPredicate isSearchMatch,
                   int mouseX, int mouseY, float animTick, long nowMs) {
        var c = UITheme.colors();
        renderChassis(g, style, keyboardX, keyboardY, keyScale);
        refreshDrawStates(font, style, keyboardX, keyboardY, keyScale);

        long scannerVersion = scanner.version();
        float dt = lastFrameMs > 0 ? Math.min((nowMs - lastFrameMs) / 1000f, 0.05f) : 0.016f;
        lastFrameMs = nowMs;
        Set<Integer> comboKeys = comboParticipantKeys();

        int pulseAccent = KeybindViewerScreen.pulseAccent(animTick);
        int searchPulseColor = KeybindViewerScreen.searchPulseColor(animTick);
        int searchPulseAlpha = KeybindViewerScreen.searchPulseAlpha(animTick);
        int accentAlt = c.accentAlt();
        int conflictBorder = UITheme.lerpColor(c.danger(), c.widgetBorder(), 0.30f);
        int widgetBorder = c.widgetBorder();
        int hiddenGhost = UITheme.withAlpha(c.widgetBg(), 0x24);
        int hiddenBorder = UITheme.withAlpha(widgetBorder, 0x28);
        int normalBorder = UITheme.withAlpha(widgetBorder, 0x60);

        Integer hovered = null;
        for (KeyDrawState state : drawStates) {
            state.matched = isVisibleKey.test(state.glfwKey);
            state.hidden = isHiddenKey.test(state.glfwKey);
            state.searchMatch = isSearchMatch.test(state.glfwKey);
            state.hover = !state.hidden && KeybindViewerScreen.inside(mouseX, mouseY, state.x, state.y, state.w, state.h);
            state.selected = selectedVirtualKey != null && selectedVirtualKey == state.glfwKey;
            state.comboParticipant = comboKeys.contains(state.glfwKey);
            if (state.cachedDataVersion != scannerVersion) {
                state.cachedDataVersion = scannerVersion;
                state.status = scanner.getStatus(state.glfwKey);
                state.bindingCount = scanner.getBindingCount(state.glfwKey);
                state.cachedBindings = scanner.getBindings(state.glfwKey);
                state.cachedInlineMaxW = Integer.MIN_VALUE;
                refreshCachedColors(state);
            }
            state.hoverProgress = advanceProgress(state.hoverProgress,
                    state.hover && state.matched ? 1f : 0f, dt, 16f);
            state.selectProgress = advanceProgress(state.selectProgress,
                    state.selected ? 1f : 0f, dt, 18f);
            if (state.hover) hovered = state.glfwKey;
            renderKeyShape(g, state, pulseAccent, searchPulseColor, searchPulseAlpha,
                    accentAlt, conflictBorder, widgetBorder,
                    hiddenGhost, hiddenBorder, normalBorder);
        }

        for (KeyDrawState state : drawStates) {
            if (state.matched && !state.hidden) {
                renderKeyLabels(g, font, state, KeybindViewerScreen.labelColorForStatus(state.status), true);
                renderInlineBindings(g, font, state);
                renderBindingBadge(g, font, state.x, state.y, state.w, state.h,
                        state.bindingCount, state.status, state.comboParticipant);
            } else if (!state.hidden) {
                renderKeyLabels(g, font, state, UITheme.withAlpha(c.textMuted(), 0x80), false);
            }
        }
        return hovered;
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

    private static void renderComboTopBar(GuiGraphics g, KeyDrawState state, boolean dim) {
        int x = state.x;
        int y = state.y;
        int w = state.w;
        int faceH = state.h - 1;
        if (w < 10 || faceH < 8) return;
        int barH = Math.min(3, Math.max(2, faceH / 5));
        int color = dim ? UITheme.withAlpha(COMBO_HIGHLIGHT_COLOR, 0x70) : COMBO_HIGHLIGHT_COLOR;
        UITheme.fillRoundedRectFast(g, x + 3, y + 1, w - 6, barH, Math.max(1, barH / 2), color);
    }

    private void refreshDrawStates(Font font, KeyboardLayoutData.Style style,
                                   int keyboardX, int keyboardY, float keyScale) {
        if (style == cachedStyle && keyboardX == cachedKeyboardX && keyboardY == cachedKeyboardY
                && Float.compare(keyScale, cachedKeyScale) == 0) {
            return;
        }
        cachedStyle = style;
        cachedKeyboardX = keyboardX;
        cachedKeyboardY = keyboardY;
        cachedKeyScale = keyScale;
        List<KeyboardLayoutData.KeyDef> keys = KeyboardLayoutData.getKeys(style);
        if (drawStates.length != keys.size()) {
            drawStates = new KeyDrawState[keys.size()];
            for (int i = 0; i < drawStates.length; i++) drawStates[i] = new KeyDrawState();
        }
        for (int i = 0; i < keys.size(); i++) {
            KeyboardLayoutData.KeyDef key = keys.get(i);
            KeyDrawState state = drawStates[i];
            state.key = key;
            state.glfwKey = key.glfwKey();
            state.zone = KeyboardLayoutData.zoneOf(state.glfwKey);
            state.x = key.screenX(keyboardX, keyScale);
            state.y = key.screenY(keyboardY, keyScale);
            state.w = key.screenW(keyScale);
            state.h = key.screenH(keyScale);
            state.radius = state.w >= 34 || state.h >= 34 ? 4 : 3;
            int labelMaxW = Math.max(0, state.w - 4);
            state.mainLabel = KeybindViewerScreen.fitToWidth(font, key.label(), labelMaxW);
            state.mainLabelW = font.width(state.mainLabel);
            state.subLabel = key.subLabel() == null ? null : KeybindViewerScreen.fitToWidth(font, key.subLabel(), labelMaxW);
            state.subLabelW = state.subLabel == null ? 0 : font.width(state.subLabel);
            state.cachedInlineMaxW = Integer.MIN_VALUE;
            state.cachedDataVersion = Long.MIN_VALUE;
        }
    }

    private static float advanceProgress(float current, float target, float dt, float speed) {
        float updated = current + (target - current) * Math.min(1f, dt * speed);
        if (Math.abs(updated - target) < 0.003f) return target;
        return updated;
    }

    private static void renderKeyShape(GuiGraphics g, KeyDrawState state,
                                       int pulseAccent, int searchPulseColor, int searchPulseAlpha,
                                       int accentAlt, int conflictBorder, int widgetBorder,
                                       int hiddenGhost, int hiddenBorder, int normalBorder) {
        int x = state.x;
        int y = state.y;
        int w = state.w;
        int h = state.h;
        int radius = state.radius;
        if (state.matched && !state.hidden) {
            float selectEase = UITheme.easeOutCubic(state.selectProgress);
            float hoverEase = UITheme.easeOutCubic(state.hoverProgress);
            boolean active = state.hover || state.selected;

            if (state.searchMatch && state.selectProgress < 0.6f && state.hoverProgress < 0.6f) {
                int searchInner = Math.round(searchPulseAlpha * (1f - Math.max(selectEase, hoverEase)));
                if (searchInner > 0) {
                    UITheme.drawRoundedBorderFast(g, x - 2, y - 2, w + 4, h + 4, radius + 2,
                            UITheme.withAlpha(searchPulseColor, Math.max(0x14, searchInner / 3)));
                }
            }
            if (state.hoverProgress > 0.005f && state.selectProgress < 0.99f) {
                int alpha = Math.round(0x60 * hoverEase * (1f - selectEase));
                if (alpha > 0) {
                    UITheme.drawRoundedBorderFast(g, x - 1, y - 1, w + 2, h + 2, radius + 1,
                            UITheme.withAlpha(accentAlt, alpha));
                }
            }
            if (state.selectProgress > 0.005f) {
                int outerAlpha = Math.round(0x40 * selectEase);
                int grow = Math.round(2f * selectEase);
                if (outerAlpha > 0 && grow > 0) {
                    UITheme.drawRoundedBorderFast(g, x - grow - 1, y - grow - 1,
                            w + grow * 2 + 2, h + grow * 2 + 2, radius + 2,
                            UITheme.withAlpha(pulseAccent, outerAlpha));
                }
            }

            int faceH = h - 1;
            UITheme.fillRoundedRectFast(g, x, y, w, faceH, radius, state.cachedTopFill);

            int hlAlpha = state.hover ? 0x18 : 0x10;
            int half = faceH / 2;
            UITheme.fillRoundedRectEx(g, x + 1, y + 1, w - 2, Math.max(2, half - 1),
                    Math.max(1, radius - 1), Math.max(1, radius - 1), 1, 1,
                    UITheme.withAlpha(0xFFFFFF, hlAlpha));
            UITheme.fillRoundedRectEx(g, x + 1, y + faceH - 2, w - 2, 2,
                    1, 1, Math.max(1, radius - 1), Math.max(1, radius - 1), FACE_BOTTOM_TINT);

            if (state.comboParticipant) renderComboTopBar(g, state, false);

            if (state.status != KeyBindingScanner.KeyStatus.FREE) {
                int alpha = active ? 0xD0 : 0x86;
                int edgeH = state.status == KeyBindingScanner.KeyStatus.CONFLICT ? 3 : 2;
                if (w >= 10 && faceH >= 8) {
                    renderStatusEdge(g, x, y, w, faceH, edgeH, radius,
                            UITheme.withAlpha(state.cachedStatusEdgeColor, alpha));
                }
            }

            int borderColor = state.selected ? pulseAccent
                    : state.hover ? accentAlt
                    : state.status == KeyBindingScanner.KeyStatus.CONFLICT
                        ? conflictBorder
                        : widgetBorder;
            UITheme.drawRoundedBorderFast(g, x, y, w, faceH, radius, borderColor);
        } else {
            int ghostFill = state.hidden ? hiddenGhost : state.cachedGhostFill;
            int faceH = h - 1;
            UITheme.fillRoundedRectFast(g, x, y, w, faceH, radius, ghostFill);
            if (state.comboParticipant && !state.hidden) renderComboTopBar(g, state, true);
            if (!state.hidden && state.status != KeyBindingScanner.KeyStatus.FREE && w >= 10 && faceH >= 8) {
                int edgeH = state.status == KeyBindingScanner.KeyStatus.CONFLICT ? 3 : 2;
                renderStatusEdge(g, x, y, w, faceH, edgeH, radius,
                        UITheme.withAlpha(state.cachedStatusEdgeColor, 0x86));
            }
            UITheme.drawRoundedBorderFast(g, x, y, w, faceH, radius,
                    state.hidden ? hiddenBorder : normalBorder);
        }
    }

    private static void renderStatusEdge(GuiGraphics g, int x, int y, int w, int faceH,
                                         int edgeH, int radius, int color) {
        UITheme.fillRoundedRectEx(g, x + 3, y + faceH - edgeH - 1, w - 6, edgeH,
                Math.max(1, edgeH / 2), Math.max(1, edgeH / 2),
                Math.max(1, Math.min(radius - 1, edgeH)),
                Math.max(1, Math.min(radius - 1, edgeH)), color);
    }

    private static final int FACE_BOTTOM_TINT = UITheme.withAlpha(0x000000, 0x18);

    private static void refreshCachedColors(KeyDrawState state) {
        int base = KeybindViewerScreen.keyStatusColor(state.status);
        int topFill = applyZoneTint(base, state.zone, state.status);
        state.cachedTopFill = topFill;
        state.cachedShadowFill = UITheme.lerpColor(topFill, 0x000000, 0.50f);
        state.cachedGhostFill = KeybindViewerScreen.keyStatusColor(state.status, false);
        state.cachedStatusEdgeColor = base;
    }

    
    private static int applyZoneTint(int base, KeyboardLayoutData.KeyZone zone, KeyBindingScanner.KeyStatus status) {
        if (status != KeyBindingScanner.KeyStatus.FREE) return base;
        var c = UITheme.colors();
        return switch (zone) {
            case ALPHA -> base;
            case MODIFIER -> UITheme.lerpColor(base, c.panelBg(), 0.22f);
            case FUNCTION -> UITheme.lerpColor(base, c.panelBg(), 0.18f);
            case EDIT -> UITheme.lerpColor(base, c.accentSecondary(), 0.10f);
            case ARROW -> UITheme.lerpColor(base, c.accentSecondary(), 0.14f);
            case NUMPAD -> UITheme.lerpColor(base, c.accentTertiary(), 0.10f);
        };
    }

    private static void renderChassis(GuiGraphics g, KeyboardLayoutData.Style style, int keyboardX, int keyboardY, float keyScale) {
        var c = UITheme.colors();
        int kbW = KeyboardLayoutData.totalWidthPx(style, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(style, keyScale);
        int pad = Math.max(6, Math.round(keyScale * 0.30f));
        int x = keyboardX - pad;
        int y = keyboardY - pad;
        int w = kbW + pad * 2;
        int h = kbH + pad * 2;
        int radius = 8;

        int chassisOuter = UITheme.lerpColor(c.panelBg(), 0x000000, 0.35f);
        UITheme.fillRoundedRectFast(g, x, y, w, h, radius, chassisOuter);

        int trayColor = UITheme.lerpColor(c.headerBg(), 0x000000, 0.20f);
        UITheme.fillRoundedRectFast(g, x + 2, y + 2, w - 4, h - 4, radius - 2, trayColor);

        UITheme.drawRoundedBorderFast(g, x, y, w, h, radius,
                UITheme.withAlpha(c.widgetBorder(), 0xC0));
    }

    
    private static void renderKeyLabels(GuiGraphics g, Font font, KeyDrawState state,
                                        int labelColor, boolean showSub) {
        var c = UITheme.colors();
        int x = state.x;
        int y = state.y;
        int w = state.w;
        int h = state.h;
        String main = state.mainLabel;
        String sub = state.subLabel;
        if (showSub && sub != null && h >= 18) {
            int subColor = UITheme.withAlpha(c.textSecondary(), 0xB0);
            g.drawString(font, sub, x + (w - state.subLabelW) / 2, y + 3, subColor, false);
            g.drawString(font, main, x + (w - state.mainLabelW) / 2,
                    y + h - font.lineHeight - 4, labelColor, false);
        } else {
            g.drawString(font, main,
                    x + (w - state.mainLabelW) / 2,
                    y + (h - font.lineHeight) / 2,
                    labelColor, false);
        }
    }

    private static void renderInlineBindings(GuiGraphics g, Font font, KeyDrawState state) {
        int x = state.x;
        int y = state.y;
        int w = state.w;
        int h = state.h;
        if (w < 34 || h < 24) return;
        List<KeyBindingScanner.KeyBindingInfo> bindings = state.cachedBindings;
        if (bindings == null || bindings.isEmpty()) return;
        int maxW = w - 8;
        if (state.cachedInlineMaxW != maxW) {
            state.cachedInlineMaxW = maxW;
            state.cachedInlineText = KeybindViewerScreen.fitToWidth(font, bindings.get(0).modName(), maxW);
            state.cachedInlineTextW = font.width(state.cachedInlineText);
        }
        var c = UITheme.colors();
        String text = state.cachedInlineText;
        int chipW = state.cachedInlineTextW + 6;
        int chipX = x + (w - chipW) / 2;
        int chipY = y + h - font.lineHeight - 3;
        int fill = UITheme.lerpColor(c.widgetBg(), c.accent(), 0.20f);
        UITheme.fillRoundedRectFast(g, chipX, chipY, chipW, font.lineHeight + 1, 3,
                UITheme.withAlpha(fill, 0xCC));
        g.drawString(font, text, chipX + 3, chipY + 1, c.textPrimary(), false);
    }

    
    private static void renderBindingBadge(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int count, KeyBindingScanner.KeyStatus status,
                                           boolean comboTopBar) {
        if (count <= 0 || h < 14) return;
        var c = UITheme.colors();
        int topPad = comboTopBar ? 5 : 2;
        if (count >= 2) {
            String s = String.valueOf(count);
            int bw = font.width(s) + 6;
            int bh = font.lineHeight;
            int bx = x + w - bw - 2;
            int by = y + topPad;
            int chipColor = status == KeyBindingScanner.KeyStatus.CONFLICT ? c.danger()
                    : status == KeyBindingScanner.KeyStatus.COMBO ? c.warning()
                    : c.accent();
            UITheme.fillRoundedRectFast(g, bx, by, bw, bh, bh / 2, chipColor);
            g.drawString(font, s, bx + 3, by + 1, 0xFFFFFFFF, false);
        } else if (w >= 16) {
            int dotColor = status == KeyBindingScanner.KeyStatus.SELF ? c.accent()
                    : status == KeyBindingScanner.KeyStatus.COMBO ? c.warning()
                    : c.success();
            UITheme.fillRoundedRectFast(g, x + w - 5, y + topPad + 1, 3, 3, 1, dotColor);
        }
    }

    private static final class KeyDrawState {
        KeyboardLayoutData.KeyDef key;
        int glfwKey;
        KeyboardLayoutData.KeyZone zone;
        int x;
        int y;
        int w;
        int h;
        int radius;
        String mainLabel;
        int mainLabelW;
        String subLabel;
        int subLabelW;
        KeyBindingScanner.KeyStatus status;
        int bindingCount;
        boolean matched;
        boolean hidden;
        boolean hover;
        boolean selected;
        boolean searchMatch;
        boolean comboParticipant;
        float hoverProgress;
        float selectProgress;
        long cachedDataVersion = Long.MIN_VALUE;
        List<KeyBindingScanner.KeyBindingInfo> cachedBindings;
        String cachedInlineText;
        int cachedInlineTextW;
        int cachedInlineMaxW = Integer.MIN_VALUE;
        int cachedTopFill;
        int cachedShadowFill;
        int cachedGhostFill;
        int cachedStatusEdgeColor;
    }
}
