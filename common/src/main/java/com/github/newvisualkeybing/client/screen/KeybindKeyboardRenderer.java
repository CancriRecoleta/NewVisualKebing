package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.Font;
import com.github.newvisualkeybing.client.ui.GuiGraphics;

import java.util.List;
import java.util.function.IntPredicate;





final class KeybindKeyboardRenderer {

    private final KeyBindingScanner scanner;
    private KeyboardLayoutData.Style cachedStyle;
    private int cachedKeyboardX = Integer.MIN_VALUE;
    private int cachedKeyboardY = Integer.MIN_VALUE;
    private float cachedKeyScale = Float.NaN;
    private KeyDrawState[] drawStates = new KeyDrawState[0];
    private long lastFrameMs;

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
        Integer hovered = null;
        for (KeyDrawState state : drawStates) {
            state.matched = isVisibleKey.test(state.glfwKey);
            state.hidden = isHiddenKey.test(state.glfwKey);
            state.searchMatch = isSearchMatch.test(state.glfwKey);
            state.hover = !state.hidden && KeybindViewerScreen.inside(mouseX, mouseY, state.x, state.y, state.w, state.h);
            state.selected = selectedVirtualKey != null && selectedVirtualKey == state.glfwKey;
            if (state.cachedDataVersion != scannerVersion) {
                state.cachedDataVersion = scannerVersion;
                state.status = scanner.getStatus(state.glfwKey);
                state.bindingCount = scanner.getBindingCount(state.glfwKey);
                state.cachedBindings = scanner.getBindings(state.glfwKey);
                state.cachedInlineMaxW = Integer.MIN_VALUE;
            }
            state.hoverProgress = advanceProgress(state.hoverProgress,
                    state.hover && state.matched ? 1f : 0f, dt, 16f);
            state.selectProgress = advanceProgress(state.selectProgress,
                    state.selected ? 1f : 0f, dt, 18f);
            if (state.hover) hovered = state.glfwKey;
            renderKeyShape(g, state, c, animTick);
        }

        for (KeyDrawState state : drawStates) {
            if (state.matched && !state.hidden) {
                renderKeyLabels(g, font, state, KeybindViewerScreen.labelColorForStatus(state.status), true);
                renderInlineBindings(g, font, state);
                renderBindingBadge(g, font, state.x, state.y, state.w, state.h, state.bindingCount, state.status);
            } else if (!state.hidden) {
                renderKeyLabels(g, font, state, UITheme.withAlpha(c.textMuted(), 0x80), false);
            }
        }
        return hovered;
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
        }
    }

    private static float advanceProgress(float current, float target, float dt, float speed) {
        float updated = current + (target - current) * Math.min(1f, dt * speed);
        if (Math.abs(updated - target) < 0.003f) return target;
        return updated;
    }

    private static void renderKeyShape(GuiGraphics g, KeyDrawState state,
                                       UITheme.ColorPalette c, float animTick) {
        int x = state.x;
        int y = state.y;
        int w = state.w;
        int h = state.h;
        int radius = state.radius;
        if (state.matched && !state.hidden) {
            float selectEase = UITheme.easeOutCubic(state.selectProgress);
            float hoverEase = UITheme.easeOutCubic(state.hoverProgress);
            if (state.searchMatch && state.selectProgress < 0.6f && state.hoverProgress < 0.6f) {
                int basePulse = KeybindViewerScreen.searchPulseAlpha(animTick);
                int searchInner = Math.round(basePulse * (1f - Math.max(selectEase, hoverEase)));
                if (searchInner > 0) {
                    int outerAlpha = Math.max(0x14, searchInner / 3);
                    UITheme.fillRoundedRect(g, x - 2, y - 2, w + 4, h + 3, radius + 2,
                            UITheme.withAlpha(KeybindViewerScreen.searchPulseColor(animTick), outerAlpha));
                    UITheme.fillRoundedRect(g, x - 1, y - 1, w + 2, h + 1, radius + 1,
                            UITheme.withAlpha(KeybindViewerScreen.searchPulseColor(animTick), searchInner));
                }
            }
            if (state.hoverProgress > 0.005f && state.selectProgress < 0.99f) {
                int alpha = Math.round(0x60 * hoverEase * (1f - selectEase));
                if (alpha > 0) {
                    UITheme.fillRoundedRect(g, x - 1, y - 1, w + 2, h + 1, radius + 1,
                            UITheme.withAlpha(c.accentAlt(), alpha));
                }
            }
            if (state.selectProgress > 0.005f) {
                int pulseColor = KeybindViewerScreen.pulseAccent(animTick);
                int innerAlpha = Math.round(0x90 * selectEase);
                int outerAlpha = Math.round(0x40 * selectEase);
                int growW = Math.round(2f * selectEase);
                int growH = Math.round(2f * selectEase);
                UITheme.fillRoundedRect(g, x - growW - 1, y - growH - 1,
                        w + growW * 2 + 2, h + growH * 2 + 1, radius + 2,
                        UITheme.withAlpha(pulseColor, outerAlpha));
                UITheme.fillRoundedRect(g, x - 1, y - 1, w + 2, h + 1, radius + 1,
                        UITheme.withAlpha(pulseColor, innerAlpha));
            }

            int topFill = applyZoneTint(KeybindViewerScreen.keyStatusColor(state.status), state.zone, state.status);
            int faceH = h - 1;

            int shadow = UITheme.lerpColor(topFill, 0x000000, 0.50f);
            UITheme.fillRoundedRect(g, x, y + 1, w, h, radius, shadow);
            UITheme.fillRoundedRect(g, x, y, w, faceH, radius, topFill);

            int half = faceH / 2;
            int hlAlpha = state.hover ? 0x18 : 0x10;
            g.fill(x + 1, y + 1, x + w - 1, y + half, UITheme.withAlpha(0xFFFFFF, hlAlpha));
            g.fill(x + 1, y + faceH - 1, x + w - 1, y + faceH, UITheme.withAlpha(0x000000, 0x18));
            g.fill(x + radius, y, x + w - radius, y + 1, UITheme.withAlpha(0xFFFFFF, 0x30));
            renderStatusEdge(g, x, y, w, faceH, radius, state.status, state.hover || state.selected);

            int borderColor = state.selected ? KeybindViewerScreen.pulseAccent(animTick)
                    : state.hover ? c.accentAlt()
                    : state.status == KeyBindingScanner.KeyStatus.CONFLICT
                        ? UITheme.lerpColor(c.danger(), c.widgetBorder(), 0.30f)
                        : c.widgetBorder();
            UITheme.drawRoundedBorder(g, x, y, w, faceH, radius, borderColor);
        } else {
            int ghostFill = KeybindViewerScreen.keyStatusColor(state.status, false);
            if (state.hidden) ghostFill = UITheme.withAlpha(c.widgetBg(), 0x24);
            UITheme.fillRoundedRect(g, x, y, w, h - 1, radius, ghostFill);
            if (!state.hidden && state.status != KeyBindingScanner.KeyStatus.FREE) {
                renderStatusEdge(g, x, y, w, h - 1, radius, state.status, false);
            }
            UITheme.drawRoundedBorder(g, x, y, w, h - 1, radius,
                    UITheme.withAlpha(c.widgetBorder(), state.hidden ? 0x28 : 0x60));
        }
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

    private static void renderStatusEdge(GuiGraphics g, int x, int y, int w, int h, int radius,
                                         KeyBindingScanner.KeyStatus status, boolean active) {
        if (w < 10 || h < 8) return;
        int color = KeybindViewerScreen.keyStatusColor(status);
        int alpha = active ? 0xD0 : 0x86;
        int edgeH = status == KeyBindingScanner.KeyStatus.CONFLICT ? 3 : 2;
        UITheme.fillRoundedRect(g, x + 2, y + h - edgeH - 1, w - 4, edgeH, Math.max(1, radius - 1),
                UITheme.withAlpha(color, alpha));
        if (status == KeyBindingScanner.KeyStatus.CONFLICT && w >= 18) {
            int sx = x + 4;
            while (sx < x + w - 4) {
                g.fill(sx, y + 2, Math.min(sx + 3, x + w - 4), y + 3, UITheme.withAlpha(color, 0xB0));
                sx += 7;
            }
        }
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

        UITheme.fillRoundedRect(g, x + 2, y + 3, w, h, radius + 1,
                UITheme.withAlpha(0x000000, 0x40));

        int chassisOuter = UITheme.lerpColor(c.panelBg(), 0x000000, 0.35f);
        UITheme.fillRoundedRect(g, x, y, w, h, radius, chassisOuter);

        int trayColor = UITheme.lerpColor(c.headerBg(), 0x000000, 0.20f);
        UITheme.fillRoundedRect(g, x + 2, y + 2, w - 4, h - 4, radius - 2, trayColor);

        g.fill(x + 4, y + 2, x + w - 4, y + 3, UITheme.withAlpha(0x000000, 0x60));
        g.fill(x + 2, y + 4, x + 3, y + h - 4, UITheme.withAlpha(0x000000, 0x40));

        g.fill(x + radius, y, x + w - radius, y + 1, UITheme.withAlpha(0xFFFFFF, 0x18));

        UITheme.drawRoundedBorder(g, x, y, w, h, radius,
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
        UITheme.fillRoundedRect(g, chipX, chipY, chipW, font.lineHeight + 1, 4, UITheme.withAlpha(fill, 0xCC));
        g.drawString(font, text, chipX + 3, chipY + 1, c.textPrimary(), false);
    }

    
    private static void renderBindingBadge(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int count, KeyBindingScanner.KeyStatus status) {
        if (count <= 0 || h < 14) return;
        var c = UITheme.colors();
        if (count >= 2) {
            String s = String.valueOf(count);
            int bw = font.width(s) + 6;
            int bh = font.lineHeight;
            int bx = x + w - bw - 2;
            int by = y + 2;
            int chipColor = status == KeyBindingScanner.KeyStatus.CONFLICT ? c.danger() : c.accent();
            UITheme.fillRoundedRect(g, bx, by, bw, bh, bh / 2, chipColor);
            UITheme.drawRoundedBorder(g, bx, by, bw, bh, bh / 2, UITheme.withAlpha(0xFFFFFF, 0x40));
            g.drawString(font, s, bx + 3, by + 1, 0xFFFFFFFF, false);
        } else if (w >= 16) {
            int dotColor = status == KeyBindingScanner.KeyStatus.SELF ? c.accent() : c.success();
            UITheme.fillRoundedRect(g, x + w - 5, y + 3, 3, 3, 1, dotColor);
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
        float hoverProgress;
        float selectProgress;
        long cachedDataVersion = Long.MIN_VALUE;
        List<KeyBindingScanner.KeyBindingInfo> cachedBindings;
        String cachedInlineText;
        int cachedInlineTextW;
        int cachedInlineMaxW = Integer.MIN_VALUE;
    }
}
