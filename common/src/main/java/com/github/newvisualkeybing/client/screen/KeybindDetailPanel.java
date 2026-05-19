package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;





final class KeybindDetailPanel {

    private static final int PANEL_PAD = 12;
    private static final int ACTION_BTN_H = 22;
    private static final int ACTION_BTN_GAP = 8;
    private static final int ROW_UNBIND_W = 14;
    private static final int ROW_UNBIND_GAP = 4;
    private static final int ROW_PRIORITY_W = 48;
    private static final int ROW_PRIORITY_BTN_W = 13;
    private static final int ROW_PRIORITY_GAP = 5;

    private final KeyBindingScanner scanner;
    private final KeybindProfileStore profileStore;

    private int scrollOffset;
    private int modifyX = -1, modifyY = -1;
    private int unbindX = -1, unbindY = -1;
    private int actionBtnW;
    private final List<RowHit> rowHits = new ArrayList<>();
    private final List<PriorityHit> priorityHits = new ArrayList<>();
    private final EnumMap<KeyBindingScanner.KeyStatus, String> statusLabels =
            new EnumMap<>(KeyBindingScanner.KeyStatus.class);
    private int[] rowHeights = new int[0];
    private String detailsTitle;
    private String hoverHint;
    private String wheelHint;
    private String unboundText;
    private String modifyLabel;
    private String unbindLabel;
    private boolean textCacheReady;

    record RowHit(int x, int y, int w, int h, KeyBindingScanner.KeyBindingInfo info) {}
    record PriorityHit(int x, int y, int w, int h, int delta, KeyBindingScanner.KeyBindingInfo info) {}

    KeybindDetailPanel(KeyBindingScanner scanner, KeybindProfileStore profileStore) {
        this.scanner = scanner;
        this.profileStore = profileStore;
    }

    void resetScroll() {
        scrollOffset = 0;
    }

    void scroll(int direction) {
        scrollOffset = Math.max(0, scrollOffset - direction);
    }

    boolean isModifyHit(double mx, double my) {
        return modifyX >= 0 && KeybindViewerScreen.inside(mx, my, modifyX, modifyY, actionBtnW, ACTION_BTN_H);
    }

    boolean isUnbindHit(double mx, double my) {
        return unbindX >= 0 && KeybindViewerScreen.inside(mx, my, unbindX, unbindY, actionBtnW, ACTION_BTN_H);
    }


    KeyBindingScanner.KeyBindingInfo getRowUnbindHit(double mx, double my) {
        for (RowHit h : rowHits) {
            if (KeybindViewerScreen.inside(mx, my, h.x, h.y, h.w, h.h)) return h.info;
        }
        return null;
    }

    PriorityHit getRowPriorityHit(double mx, double my) {
        for (PriorityHit h : priorityHits) {
            if (KeybindViewerScreen.inside(mx, my, h.x, h.y, h.w, h.h)) return h;
        }
        return null;
    }

    void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                Integer virtualKey, int mouseX, int mouseY) {
        ensureTextCache();
        modifyX = modifyY = unbindX = unbindY = -1;
        rowHits.clear();
        priorityHits.clear();

        var c = UITheme.colors();
        int contentY = KeybindViewerScreen.paintPanelBase(g, font, x, y, w, h, detailsTitle);

        int innerX = x + PANEL_PAD;
        int innerW = w - PANEL_PAD * 2;
        int innerBottom = y + h - PANEL_PAD;

        if (virtualKey == null) {
            int boxH = 54;
            int boxY = contentY + Math.max(8, (innerBottom - contentY - boxH) / 2);
            UITheme.fillRoundedRectFast(g, innerX, boxY, innerW, boxH, 6,
                    UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.45f));
            UITheme.drawRoundedBorderFast(g, innerX, boxY, innerW, boxH, 6,
                    UITheme.withAlpha(c.widgetBorder(), 0x90));
            String hint = KeybindViewerScreen.fitToWidth(font, hoverHint, innerW - 16);
            g.drawString(font, hint, innerX + 8, textY(font, boxY, boxH), c.textMuted(), false);
            return;
        }

        boolean isWheel = KeyboardLayoutData.isWheel(virtualKey);
        boolean isMouse = KeyboardLayoutData.isMouse(virtualKey);
        List<KeyBindingScanner.KeyBindingInfo> bindings = isWheel
                ? Collections.emptyList()
                : (isMouse
                    ? scanner.getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getBindings(virtualKey));
        KeyBindingScanner.KeyStatus status = isWheel ? KeyBindingScanner.KeyStatus.FREE
                : (isMouse
                    ? scanner.getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getStatus(virtualKey));

        String keyName = scanner.getVirtualKeyLabel(virtualKey);
        int chipW = renderStatusChip(g, font, 0, 0, status, true);
        int keyNameW = font.width(keyName);
        boolean stackHead = keyNameW + 8 + chipW > innerW && innerW < 160 || keyNameW > innerW - chipW - 12;
        int lineY;
        if (stackHead) {
            String displayKeyName = KeybindViewerScreen.fitToWidth(font, keyName, innerW);
            g.drawString(font, displayKeyName, innerX, textY(font, contentY, 12), c.textPrimary(), false);
            renderStatusChip(g, font, innerX, contentY + font.lineHeight + 4, status, false);
            lineY = contentY + font.lineHeight + 4 + 12 + 4;
        } else {
            int chipX = innerX + innerW - chipW;
            renderStatusChip(g, font, chipX, contentY, status, false);
            int keyNameMaxW = innerW - chipW - 6;
            String displayKeyName = KeybindViewerScreen.fitToWidth(font, keyName, keyNameMaxW);
            g.drawString(font, displayKeyName, innerX, textY(font, contentY, 12), c.textPrimary(), false);
            lineY = contentY + Math.max(font.lineHeight, 12) + 4;
        }

        if (!bindings.isEmpty()) {
            String catText = bindings.get(0).categoryName();
            if (bindings.size() > 1) {
                int distinct = countDistinctCategories(bindings);
                if (distinct > 1) catText = catText + "  +" + (distinct - 1);
            }
            String catFit = KeybindViewerScreen.fitToWidth(font, catText, innerW);
            g.drawString(font, catFit, innerX, lineY, c.textMuted(), false);
            lineY += font.lineHeight + 2;
        }

        if (!bindings.isEmpty()) {
            lineY = renderInfoChips(g, font, innerX, lineY, innerW, bindings);
        }

        lineY = renderComboSection(g, font, innerX, lineY, innerW, virtualKey);

        g.fill(innerX, lineY, innerX + innerW, lineY + 1, UITheme.withAlpha(c.divider(), 0x80));
        lineY += 4;

        boolean canEdit = !isWheel;
        int actionY = canEdit ? innerBottom - ACTION_BTN_H : innerBottom;
        int listBottom = actionY - 4;

        if (isWheel) {
            renderInfoBox(g, font, innerX, lineY, innerW, wheelHint, c.textMuted());
        } else if (bindings.isEmpty()) {
            renderInfoBox(g, font, innerX, lineY, innerW, unboundText, c.textMuted());
        } else {
            renderBindingList(g, font, innerX, lineY, innerW, listBottom - lineY, bindings, mouseX, mouseY);
        }

        if (canEdit) {
            int btnW = (innerW - ACTION_BTN_GAP) / 2;
            actionBtnW = btnW;
            modifyX = innerX;
            modifyY = actionY;
            KeybindViewerScreen.renderActionButton(g, font, modifyX, modifyY, btnW, ACTION_BTN_H,
                    modifyLabel, c.accent(), KeybindViewerScreen.inside(mouseX, mouseY, modifyX, modifyY, btnW, ACTION_BTN_H));

            unbindX = innerX + btnW + ACTION_BTN_GAP;
            unbindY = actionY;
            KeybindViewerScreen.renderActionButton(g, font, unbindX, unbindY, btnW, ACTION_BTN_H,
                    unbindLabel, c.danger(), KeybindViewerScreen.inside(mouseX, mouseY, unbindX, unbindY, btnW, ACTION_BTN_H));
        }
    }

    private void ensureTextCache() {
        if (textCacheReady) return;
        detailsTitle = Component.translatable("screen.newvisualkeybing.viewer.details").getString();
        hoverHint = Component.translatable("screen.newvisualkeybing.viewer.hover_hint").getString();
        wheelHint = Component.translatable("screen.newvisualkeybing.viewer.wheel_hint").getString();
        unboundText = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
        modifyLabel = Component.translatable("screen.newvisualkeybing.viewer.modify").getString();
        unbindLabel = Component.translatable("screen.newvisualkeybing.viewer.unbind").getString();
        for (KeyBindingScanner.KeyStatus status : KeyBindingScanner.KeyStatus.values()) {
            statusLabels.put(status, Component.translatable(statusTranslation(status)).getString());
        }
        textCacheReady = true;
    }

    private static int renderComboSection(GuiGraphics g, Font font, int x, int y, int w, int virtualKey) {
        List<KeybindComboStore.ComboBinding> combos =
                KeybindComboStore.global().combosForVirtualKey(virtualKey);
        if (combos.isEmpty()) return y;
        int yellow = KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR;
        var c = UITheme.colors();
        for (KeybindComboStore.ComboBinding combo : combos) {
            String action = KeybindComboStore.describeMapping(combo.mappingName);
            String combination = combo.comboLabel();
            String row = action + " · " + combination;
            int chipH = font.lineHeight + 4;
            UITheme.fillRoundedRectFast(g, x, y, w, chipH, 4,
                    UITheme.withAlpha(c.widgetBg(), 0xA0));
            UITheme.drawRoundedBorderFast(g, x, y, w, chipH, 4,
                    UITheme.withAlpha(yellow, 0xB0));
            g.fill(x, y, x + 2, y + chipH, yellow);
            String fit = KeybindViewerScreen.fitToWidth(font, row, w - 12);
            g.drawString(font, fit, x + 7, y + (chipH - font.lineHeight) / 2 + 1,
                    yellow, false);
            y += chipH + 3;
        }
        return y;
    }

    private static void renderInfoBox(GuiGraphics g, Font font, int x, int y, int w, String text, int textColor) {
        var c = UITheme.colors();
        int h = font.lineHeight + 12;
        UITheme.fillRoundedRectFast(g, x, y, w, h, 6, UITheme.lerpColor(c.widgetBg(), c.panelBg(), 0.45f));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 6, UITheme.withAlpha(c.widgetBorder(), 0x80));
        String fit = KeybindViewerScreen.fitToWidth(font, text, w - 16);
        g.drawString(font, fit, x + 8, textY(font, y, h), textColor, false);
    }

    private int renderStatusChip(GuiGraphics g, Font font, int x, int y, KeyBindingScanner.KeyStatus status, boolean measureOnly) {
        var c = UITheme.colors();
        int dot;
        int textColor;
        switch (status) {
            case FREE -> { dot = c.widgetBorder(); textColor = c.textSecondary(); }
            case SELF -> { dot = c.accent(); textColor = c.accent(); }
            case OTHER_SINGLE, BOUND -> { dot = c.success(); textColor = c.success(); }
            case COMBO -> { dot = c.warning(); textColor = c.warning(); }
            case CONFLICT -> { dot = c.danger(); textColor = c.danger(); }
            default -> { dot = c.widgetBorder(); textColor = c.textSecondary(); }
        }
        String label = statusLabels.get(status);
        int chipH = 12;
        int chipW = font.width(label) + 16;
        if (measureOnly) return chipW;
        int chipFill = UITheme.lerpColor(c.widgetBg(), dot, 0.18f);
        UITheme.fillRoundedRectFast(g, x, y, chipW, chipH, chipH / 2, chipFill);
        UITheme.drawRoundedBorderFast(g, x, y, chipW, chipH, chipH / 2, UITheme.withAlpha(dot, 0xC0));
        UITheme.fillRoundedRectFast(g, x + 5, y + (chipH - 4) / 2, 4, 4, 2, dot);
        g.drawString(font, label, x + 11, textY(font, y, chipH), textColor, false);
        return chipW;
    }

    private static int renderInfoChips(GuiGraphics g, Font font, int x, int y, int w,
                                       List<KeyBindingScanner.KeyBindingInfo> bindings) {
        var c = UITheme.colors();
        int gap = 4;
        int colW = (w - gap) / 2;
        int chipH = 14;
        renderInfoChip(g, font, c, x, y, colW, chipH,
                Component.translatable("screen.newvisualkeybing.viewer.info.bindings", bindings.size()).getString());
        renderInfoChip(g, font, c, x + colW + gap, y, w - colW - gap, chipH,
                Component.translatable("screen.newvisualkeybing.viewer.info.sources", countDistinctMods(bindings)).getString());
        renderInfoChip(g, font, c, x, y + chipH + 3, colW, chipH,
                Component.translatable("screen.newvisualkeybing.viewer.info.categories", countDistinctCategories(bindings)).getString());
        renderInfoChip(g, font, c, x + colW + gap, y + chipH + 3, w - colW - gap, chipH,
                Component.translatable("screen.newvisualkeybing.viewer.info.contexts", countDistinctContexts(bindings)).getString());
        return y + chipH * 2 + 6;
    }

    private static void renderInfoChip(GuiGraphics g, Font font, UITheme.ColorPalette c,
                                       int x, int y, int w, int h, String label) {
        UITheme.fillRoundedRectFast(g, x, y, w, h, 5,
                UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.12f));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 5,
                UITheme.withAlpha(c.widgetBorder(), 0x70));
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, label, w - 8),
                x + 5, textY(font, y, h), c.textMuted(), false);
    }

    private void renderBindingList(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   List<KeyBindingScanner.KeyBindingInfo> bindings,
                                   int mouseX, int mouseY) {
        var c = UITheme.colors();
        int singleRowH = font.lineHeight + 4;

        boolean showPriority = w >= 156;
        int reservedRight = ROW_UNBIND_W + ROW_UNBIND_GAP
                + (showPriority ? ROW_PRIORITY_W + ROW_PRIORITY_GAP : 0);
        int textW = w - reservedRight;

        ensureRowCapacity(bindings.size());
        for (int i = 0; i < bindings.size(); i++) {
            rowHeights[i] = singleRowH;
        }

        int totalContentH = 0;
        for (int rh : rowHeights) totalContentH += rh + 2;
        boolean overflow = totalContentH > h;
        if (overflow) {
            int maxOffset = 0;
            int accH = 0;
            for (int i = bindings.size() - 1; i >= 0; i--) {
                accH += rowHeights[i] + 2;
                if (accH > h - (font.lineHeight + 2)) {
                    maxOffset = i + 1;
                    break;
                }
            }
            scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
        } else {
            scrollOffset = 0;
        }

        int rowY = y;
        int reservedForOverflow = overflow ? font.lineHeight + 4 : 0;
        int budgetH = h - reservedForOverflow;
        int end = bindings.size();
        int usedH = 0;
        for (int i = scrollOffset; i < bindings.size(); i++) {
            int rh = rowHeights[i];
            if (usedH + rh > budgetH) { end = i; break; }

            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            renderBindingRow(g, font, x, rowY, w, rh, info, showPriority, mouseX, mouseY);
            rowY += rh + 2;
            usedH += rh + 2;
        }

        if (overflow) {
            int more = bindings.size() - end + scrollOffset;
            if (more > 0) {
                String s = "+" + more;
                g.drawString(font, s, x + w - font.width(s), y + h - font.lineHeight, c.textMuted(), false);
            }
        }
    }

    private void ensureRowCapacity(int size) {
        if (rowHeights.length >= size) return;
        int newSize = Math.max(size, rowHeights.length * 2 + 4);
        rowHeights = new int[newSize];
    }

    private static int countDistinctMods(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            String value = bindings.get(i).modName();
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value.equals(bindings.get(j).modName())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    private static int countDistinctCategories(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            String value = bindings.get(i).categoryName();
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value.equals(bindings.get(j).categoryName())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    private static int countDistinctContexts(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            ConflictContext value = bindings.get(i).conflictContext();
            if (value == null) continue;
            boolean seen = false;
            for (int j = 0; j < i; j++) {
                if (value == bindings.get(j).conflictContext()) {
                    seen = true;
                    break;
                }
            }
            if (!seen) count++;
        }
        return count;
    }

    
    private void renderBindingRow(GuiGraphics g, Font font, int x, int y, int w, int rowH,
                                  KeyBindingScanner.KeyBindingInfo info, boolean showPriority,
                                  int mouseX, int mouseY) {
        var c = UITheme.colors();
        int reservedRight = ROW_UNBIND_W + ROW_UNBIND_GAP
                + (showPriority ? ROW_PRIORITY_W + ROW_PRIORITY_GAP : 0);
        int textW = w - reservedRight;
        boolean rowHovered = KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, rowH);

        if (info.self()) {
            UITheme.fillRoundedRectFast(g, x, y, textW, rowH, 4,
                    UITheme.lerpColor(c.widgetBg(), c.accent(), 0.10f));
        }
        int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xC0);
        g.fill(x, y + 2, x + 2, y + rowH - 2, sideColor);

        int actionColor = info.self() ? c.accent() : c.textPrimary();
        String ctxTag = bindingTag(info);
        String modText = info.modName();

        int ctxW = ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 6;
        int modMaxW = Math.max(32, Math.min(textW / 3, textW - 44 - ctxW));
        String modFit = KeybindViewerScreen.fitToWidth(font, modText, modMaxW);
        int modW = font.width(modFit);
        int rightBlockW = modW + ctxW;
        int actionMaxW = Math.max(24, textW - 8 - rightBlockW - 4);
        String actionText = KeybindViewerScreen.fitToWidth(font, info.actionName(), actionMaxW);
        int rowTextY = textY(font, y, rowH);
        g.drawString(font, actionText, x + 6, rowTextY, actionColor, false);
        int rightX = x + textW - modW;
        g.drawString(font, modFit, rightX, rowTextY, c.textMuted(), false);
        if (!ctxTag.isEmpty()) {
            int tagX = rightX - font.width(ctxTag) - 6;
            int tagBgW = font.width(ctxTag) + 4;
            int tagBgH = font.lineHeight + 1;
            UITheme.fillRoundedRectFast(g, tagX - 2, rowTextY - 1, tagBgW, tagBgH, 3,
                    UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.20f));
            g.drawString(font, ctxTag, tagX, rowTextY, c.accentAlt(), false);
        }

        int xButtonX = x + w - ROW_UNBIND_W;
        int xButtonY = y + (rowH - ROW_UNBIND_W) / 2;
        if (showPriority) {
            int priorityX = xButtonX - ROW_PRIORITY_GAP - ROW_PRIORITY_W;
            renderPriorityControls(g, font, info, priorityX, xButtonY, mouseX, mouseY);
        }
        rowHits.add(new RowHit(xButtonX, xButtonY, ROW_UNBIND_W, ROW_UNBIND_W, info));
        boolean xHovered = KeybindViewerScreen.inside(mouseX, mouseY, xButtonX, xButtonY, ROW_UNBIND_W, ROW_UNBIND_W);
        int fill = xHovered ? UITheme.lerpColor(c.widgetBg(), c.danger(), 0.55f)
                            : UITheme.lerpColor(c.widgetBg(), c.danger(), rowHovered ? 0.18f : 0.10f);
        UITheme.fillRoundedRectFast(g, xButtonX, xButtonY, ROW_UNBIND_W, ROW_UNBIND_W, 3, fill);
        UITheme.drawRoundedBorderFast(g, xButtonX, xButtonY, ROW_UNBIND_W, ROW_UNBIND_W, 3,
                UITheme.withAlpha(c.danger(), xHovered ? 0xC0 : 0x78));
        int cx = xButtonX + ROW_UNBIND_W / 2;
        int cy = xButtonY + ROW_UNBIND_W / 2;
        int markColor = xHovered ? 0xFFFFFFFF : c.danger();
        for (int d = -3; d <= 3; d++) {
            g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, markColor);
            g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, markColor);
        }
    }

    private void renderPriorityControls(GuiGraphics g, Font font, KeyBindingScanner.KeyBindingInfo info,
                                        int x, int y, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int h = ROW_UNBIND_W;
        int plusX = x;
        int minusX = x + ROW_PRIORITY_W - ROW_PRIORITY_BTN_W;
        boolean plusHover = KeybindViewerScreen.inside(mouseX, mouseY, plusX, y, ROW_PRIORITY_BTN_W, h);
        boolean minusHover = KeybindViewerScreen.inside(mouseX, mouseY, minusX, y, ROW_PRIORITY_BTN_W, h);
        priorityHits.add(new PriorityHit(plusX, y, ROW_PRIORITY_BTN_W, h, 1, info));
        priorityHits.add(new PriorityHit(minusX, y, ROW_PRIORITY_BTN_W, h, -1, info));

        UITheme.fillRoundedRectFast(g, plusX, y, ROW_PRIORITY_BTN_W, h, 3,
                UITheme.lerpColor(c.widgetBg(), c.accent(), plusHover ? 0.48f : 0.18f));
        UITheme.drawRoundedBorderFast(g, plusX, y, ROW_PRIORITY_BTN_W, h, 3,
                UITheme.withAlpha(c.accent(), plusHover ? 0xC0 : 0x80));
        UITheme.fillRoundedRectFast(g, minusX, y, ROW_PRIORITY_BTN_W, h, 3,
                UITheme.lerpColor(c.widgetBg(), c.warningColor(), minusHover ? 0.48f : 0.18f));
        UITheme.drawRoundedBorderFast(g, minusX, y, ROW_PRIORITY_BTN_W, h, 3,
                UITheme.withAlpha(c.warningColor(), minusHover ? 0xC0 : 0x80));

        int buttonTextY = textY(font, y, h);
        g.drawString(font, "+", plusX + 4, buttonTextY, c.textPrimary(), false);
        g.drawString(font, "-", minusX + 5, buttonTextY, c.textPrimary(), false);
        String value = String.valueOf(profileStore.priorityOf(info.translationKey()));
        int valueX = x + ROW_PRIORITY_BTN_W;
        int valueW = ROW_PRIORITY_W - ROW_PRIORITY_BTN_W * 2;
        String fitted = KeybindViewerScreen.fitToWidth(font, value, valueW);
        g.drawString(font, fitted,
                valueX + (valueW - font.width(fitted)) / 2,
                buttonTextY, c.textMuted(), false);
    }

    private static int textY(Font font, int y, int h) {
        return y + (h - font.lineHeight) / 2;
    }

    private static String contextTag(ConflictContext ctx) {
        if (ctx == null) return "";
        return switch (ctx) {
            case UNIVERSAL -> "U";
            case IN_GAME   -> "I";
            case GUI       -> "G";
            case UNKNOWN   -> "";
        };
    }

    private static String bindingTag(KeyBindingScanner.KeyBindingInfo info) {
        String modifier = info.modifier() != null && info.modifier().isCombination() ? info.modifier().displayName() : "";
        String context = contextTag(info.conflictContext());
        if (modifier.isEmpty()) return context;
        if (context.isEmpty()) return modifier;
        return modifier + "/" + context;
    }

    private static String statusTranslation(KeyBindingScanner.KeyStatus status) {
        return switch (status) {
            case FREE -> "screen.newvisualkeybing.viewer.legend.free";
            case SELF -> "screen.newvisualkeybing.viewer.legend.self";
            case OTHER_SINGLE, BOUND -> "screen.newvisualkeybing.viewer.legend.other";
            case COMBO -> "screen.newvisualkeybing.viewer.legend.combo";
            case CONFLICT -> "screen.newvisualkeybing.viewer.legend.conflict";
        };
    }
}
