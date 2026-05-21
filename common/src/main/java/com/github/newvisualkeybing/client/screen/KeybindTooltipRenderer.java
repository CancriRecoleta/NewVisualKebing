package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

final class KeybindTooltipRenderer {

    private final KeyBindingScanner scanner;

    private final EnumMap<KeyBindingScanner.KeyStatus, String> statusLabels =
            new EnumMap<>(KeyBindingScanner.KeyStatus.class);
    private final EnumMap<ConflictContext, String> contextNames =
            new EnumMap<>(ConflictContext.class);
    private String inputTypeKeyboard;
    private String inputTypeMouse;
    private String inputTypeWheel;
    private String unboundText;
    private String wheelHintText;
    private String conflictWarningText;
    private String clickHintText;
    private String unknownContextText;
    private boolean cacheReady;
    private TooltipLayout cachedLayout;

    KeybindTooltipRenderer(KeyBindingScanner scanner) {
        this.scanner = scanner;
    }

    private void ensureCache() {
        if (cacheReady) return;
        for (KeyBindingScanner.KeyStatus s : KeyBindingScanner.KeyStatus.values()) {
            statusLabels.put(s, Component.translatable(statusTranslation(s)).getString());
        }
        contextNames.put(ConflictContext.UNIVERSAL,
                Component.translatable("screen.newvisualkeybing.viewer.context.short.universal").getString());
        contextNames.put(ConflictContext.IN_GAME,
                Component.translatable("screen.newvisualkeybing.viewer.context.short.in_game").getString());
        contextNames.put(ConflictContext.GUI,
                Component.translatable("screen.newvisualkeybing.viewer.context.short.gui").getString());
        contextNames.put(ConflictContext.UNKNOWN,
                Component.translatable("screen.newvisualkeybing.viewer.context.short.unknown").getString());
        unknownContextText = Component.translatable("screen.newvisualkeybing.viewer.context.unknown").getString();
        inputTypeKeyboard = Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.keyboard").getString();
        inputTypeMouse = Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.mouse").getString();
        inputTypeWheel = Component.translatable("screen.newvisualkeybing.viewer.tooltip.input.wheel").getString();
        unboundText = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
        wheelHintText = Component.translatable("screen.newvisualkeybing.viewer.wheel_hint").getString();
        conflictWarningText = Component.translatable("screen.newvisualkeybing.viewer.tooltip.conflict_warning").getString();
        clickHintText = Component.translatable("screen.newvisualkeybing.viewer.tooltip.click_hint").getString();
        cacheReady = true;
    }

    void render(GuiGraphics g, Font font, int screenW, int screenH, int virtualKey, int mouseX, int mouseY) {
        TooltipLayout layout = layout(font, screenW, virtualKey);
        var c = UITheme.colors();
        List<KeyBindingScanner.KeyBindingInfo> bindings = layout.bindings();
        KeyBindingScanner.KeyStatus status = layout.status();
        int innerW = layout.innerW();
        int totalW = layout.totalW();
        int totalH = layout.totalH();
        int chipW = layout.chipW();
        int titleH = layout.titleH();
        int rowH = layout.rowH();
        int padX = 10;
        int padY = 8;
        int tx = clamp(mouseX + 12, 4, screenW - totalW - 4);
        int ty = clamp(mouseY + 12, 4, screenH - totalH - 4);

        int accent = statusAccentColor(status);
        UITheme.renderTooltipBackground(g, tx, ty, totalW, totalH);
        UITheme.fillSoftRoundedRect(g, tx + 8, ty, totalW - 16, 2, 1, UITheme.withAlpha(accent, 0xE0));

        int curX = tx + padX;
        int curY = ty + padY;

        int chipX = tx + totalW - padX - chipW;
        renderStatusChip(g, font, chipX, curY, status, false);
        g.drawString(font, layout.keyNameFit(), curX, curY + 1, c.textPrimary(), true);
        curY += titleH + 4;

        UITheme.fillSoftRoundedRect(g, curX, curY, innerW, 1, 1, UITheme.withAlpha(c.divider(), 0x90));
        curY += 4;
        g.drawString(font, layout.statusLineFit(), curX, curY, c.textSecondary(), true);
        curY += font.lineHeight + 5;

        if (layout.isWheel()) {
            g.drawString(font, wheelHintText, curX, curY, c.textMuted(), true);
            curY += font.lineHeight + 4;
        } else if (bindings.isEmpty()) {
            g.drawString(font, unboundText, curX, curY, c.textMuted(), true);
            curY += font.lineHeight + 4;
        } else {
            g.drawString(font, layout.summaryFit(), curX, curY, c.textSecondary(), true);
            curY += font.lineHeight + 5;
            for (BindingRowLayout row : layout.rows()) {
                KeyBindingScanner.KeyBindingInfo info = row.info();
                int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xC0);
                UITheme.fillSoftRoundedRect(g, curX, curY, innerW, rowH - 1, 6,
                        UITheme.withAlpha(c.widgetBg(), info.self() ? 0xB8 : 0x88));
                UITheme.drawSoftRoundedBorder(g, curX, curY, innerW, rowH - 1, 6,
                        UITheme.withAlpha(sideColor, info.self() ? 0x70 : 0x40));
                UITheme.fillSoftRoundedRect(g, curX + 3, curY + 4, 3, rowH - 9, 2, sideColor);

                int textY = curY + 2;
                g.drawString(font, row.actionFit(), curX + 6, textY,
                        info.self() ? c.accent() : c.textPrimary(), true);
                int rightX = curX + innerW - row.modW();
                g.drawString(font, row.modText(), rightX, textY, c.textSecondary(), true);
                if (!row.ctxTag().isEmpty()) {
                    int tagX = rightX - row.ctxTagW() - 6;
                    int tagW = row.ctxTagW() + 6;
                    UITheme.fillSoftRoundedRect(g, tagX - 3, textY - 1, tagW, font.lineHeight + 2, 4,
                            UITheme.withAlpha(c.accentAlt(), 0x28));
                    UITheme.drawSoftRoundedBorder(g, tagX - 3, textY - 1, tagW, font.lineHeight + 2, 4,
                            UITheme.withAlpha(c.accentAlt(), 0x70));
                    g.drawString(font, row.ctxTag(), tagX, textY, c.accentAlt(), true);
                }
                g.drawString(font, row.metaFit(),
                        curX + 6, curY + font.lineHeight + 4, c.textMuted(), true);
                g.drawString(font, row.keyMetaFit(),
                        curX + 6, curY + font.lineHeight * 2 + 6, UITheme.withAlpha(c.textMuted(), 0xD0), true);
                curY += rowH;
            }
            if (layout.moreText() != null) {
                g.drawString(font, layout.moreText(), curX, curY, c.textMuted(), true);
                curY += font.lineHeight + 2;
            }
        }

        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            int warnH = font.lineHeight + 7;
            UITheme.fillSoftRoundedRect(g, curX, curY - 2, innerW, warnH, 6,
                    UITheme.withAlpha(c.dangerColor(), 0x24));
            UITheme.drawSoftRoundedBorder(g, curX, curY - 2, innerW, warnH, 6,
                    UITheme.withAlpha(c.dangerColor(), 0x88));
            g.drawString(font, layout.conflictFit(), curX + 7, curY + 1, c.dangerColor(), true);
            curY += warnH + 3;
        }

        String[] comboLines = layout.comboLines();
        if (comboLines.length > 0) {
            int yellow = KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR;
            for (String line : comboLines) {
                int lineH = font.lineHeight + 6;
                UITheme.fillSoftRoundedRect(g, curX, curY - 1, innerW, lineH, 6,
                        UITheme.withAlpha(yellow, 0x20));
                UITheme.drawSoftRoundedBorder(g, curX, curY - 1, innerW, lineH, 6,
                        UITheme.withAlpha(yellow, 0x70));
                g.drawString(font, line, curX + 7, curY + 2, yellow, true);
                curY += lineH + 2;
            }
            curY += 2;
        }

        g.drawString(font, layout.clickFit(), curX, curY, c.textMuted(), true);
    }

    private TooltipLayout layout(Font font, int screenW, int virtualKey) {
        ensureCache();
        long version = scanner.version();
        long comboVersion = KeybindComboStore.global().version();
        if (cachedLayout != null
                && cachedLayout.virtualKey() == virtualKey
                && cachedLayout.scannerVersion() == version
                && cachedLayout.comboVersion() == comboVersion
                && cachedLayout.screenW() == screenW) {
            return cachedLayout;
        }
        boolean isWheel = KeyboardLayoutData.isWheel(virtualKey);
        boolean isMouseKey = KeyboardLayoutData.isMouse(virtualKey);
        String keyName = scanner.getVirtualKeyLabel(virtualKey);

        List<KeyBindingScanner.KeyBindingInfo> bindings;
        KeyBindingScanner.KeyStatus status;
        if (isWheel) {
            bindings = Collections.emptyList();
            status = KeyBindingScanner.KeyStatus.FREE;
        } else if (isMouseKey) {
            int btn = KeyboardLayoutData.virtualToMouseBtn(virtualKey);
            bindings = scanner.getMouseBindings(btn);
            status = scanner.getMouseStatus(btn);
        } else {
            bindings = scanner.getBindings(virtualKey);
            status = scanner.getStatus(virtualKey);
        }

        int chipW = statusChipWidth(font, status);
        int padX = 10;
        int padY = 8;
        int maxInnerW = Math.max(140, Math.min(340, screenW - 32));
        int innerW = Math.min(maxInnerW, Math.max(190, font.width(keyName) + 8 + chipW));
        String inputType = inputTypeName(virtualKey);
        String statusLine = Component.translatable("screen.newvisualkeybing.viewer.tooltip.status_line",
                inputType, contextCountText(bindings)).getString();
        innerW = Math.min(maxInnerW, Math.max(innerW, font.width(statusLine)));
        int maxRows = Math.min(bindings.size(), 4);
        for (int i = 0; i < maxRows; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            String ctxTag = contextTag(info.conflictContext());
            int rowW = 8 + font.width(info.actionName()) + 8 + font.width(info.modName())
                    + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 8);
            rowW = Math.max(rowW, 8 + font.width(info.categoryName() + " | " + contextName(info.conflictContext())));
            rowW = Math.max(rowW, 8 + font.width(info.translationKey() + " | "
                    + Component.translatable("screen.newvisualkeybing.viewer.tooltip.current_key",
                    info.currentKeyName()).getString() + " | "
                    + Component.translatable("screen.newvisualkeybing.viewer.tooltip.default_key",
                    info.defaultKeyName()).getString()));
            innerW = Math.min(maxInnerW, Math.max(innerW, rowW));
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            innerW = Math.min(maxInnerW, Math.max(innerW, font.width(conflictWarningText)));
        }
        innerW = Math.min(maxInnerW, Math.max(innerW, font.width(clickHintText)));

        List<KeybindComboStore.ComboBinding> combos = KeybindComboStore.global().combosForVirtualKey(virtualKey);
        String[] comboLines = new String[combos.size()];
        for (int i = 0; i < combos.size(); i++) {
            KeybindComboStore.ComboBinding cb = combos.get(i);
            String line = "\u25cf " + cb.comboLabel() + " \u2014 " + KeybindComboStore.describeMapping(cb.mappingName);
            innerW = Math.min(maxInnerW, Math.max(innerW, font.width(line)));
            comboLines[i] = line;
        }

        int titleH = Math.max(font.lineHeight, 12);
        int rowH = font.lineHeight * 3 + 7;
        int contentH = titleH + 6;
        contentH += font.lineHeight + 5;
        if (isWheel || bindings.isEmpty()) {
            contentH += font.lineHeight + 4;
        } else {
            contentH += font.lineHeight + 5;
            contentH += maxRows * rowH;
            if (bindings.size() > maxRows) contentH += font.lineHeight + 2;
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) contentH += font.lineHeight + 4;
        if (comboLines.length > 0) contentH += comboLines.length * (font.lineHeight + 2) + 2;
        contentH += font.lineHeight + 6;

        int totalW = innerW + padX * 2;
        int totalH = contentH + padY * 2;
        String keyNameFit = fitToWidth(font, keyName, innerW - chipW - 6);
        String statusLineFit = fitToWidth(font, statusLine, innerW);
        String summaryFit = null;
        String moreText = null;
        BindingRowLayout[] rows = new BindingRowLayout[maxRows];
        if (!isWheel && !bindings.isEmpty()) {
            String summary = Component.translatable("screen.newvisualkeybing.viewer.tooltip.summary",
                    bindings.size(), countSources(bindings), countCategories(bindings), countContexts(bindings)).getString();
            summaryFit = fitToWidth(font, summary, innerW);
            for (int i = 0; i < maxRows; i++) {
                KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
                String ctxTag = contextTag(info.conflictContext());
                int ctxTagW = ctxTag.isEmpty() ? 0 : font.width(ctxTag);
                int tagW = ctxTag.isEmpty() ? 0 : ctxTagW + 6;
                int modMaxW = Math.max(44, Math.min(innerW / 3, innerW - 74));
                String modText = fitToWidth(font, info.modName(), modMaxW);
                int modW = font.width(modText);
                int rightBlockW = tagW + modW + 4;
                String actionFit = fitToWidth(font, info.actionName(), Math.max(30, innerW - 10 - rightBlockW));
                String meta = info.categoryName() + " | " + contextName(info.conflictContext());
                String keyMeta = info.translationKey() + " | "
                        + Component.translatable("screen.newvisualkeybing.viewer.tooltip.current_key",
                        info.currentKeyName()).getString() + " | "
                        + Component.translatable("screen.newvisualkeybing.viewer.tooltip.default_key",
                        info.defaultKeyName()).getString();
                rows[i] = new BindingRowLayout(info, ctxTag, ctxTagW, actionFit, modText, modW,
                        fitToWidth(font, meta, innerW - 6), fitToWidth(font, keyMeta, innerW - 6));
            }
            if (bindings.size() > maxRows) {
                moreText = Component.translatable("screen.newvisualkeybing.viewer.tooltip.more",
                        bindings.size() - maxRows).getString();
            }
        }

        String[] comboLinesFit = new String[comboLines.length];
        for (int i = 0; i < comboLines.length; i++) {
            comboLinesFit[i] = fitToWidth(font, comboLines[i], innerW);
        }
        cachedLayout = new TooltipLayout(virtualKey, version, comboVersion, screenW, isWheel, bindings, status,
                keyNameFit, statusLineFit, summaryFit, moreText,
                fitToWidth(font, conflictWarningText, innerW), fitToWidth(font, clickHintText, innerW),
                innerW, totalW, totalH, chipW, titleH, rowH, rows, comboLinesFit);
        return cachedLayout;
    }

    private int renderStatusChip(GuiGraphics g, Font font, int x, int y,
                                 KeyBindingScanner.KeyStatus status, boolean measureOnly) {
        var c = UITheme.colors();
        int dot = statusAccentColor(status);
        int textColor = switch (status) {
            case FREE -> c.textSecondary();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case COMBO -> c.warning();
            case CONFLICT -> c.danger();
        };
        String label = statusLabels.get(status);
        int chipH = 12;
        int chipW = statusChipWidth(font, status);
        if (measureOnly) return chipW;
        int chipFill = UITheme.lerpColor(c.widgetBg(), dot, 0.22f);
        UITheme.fillSoftRoundedRect(g, x, y, chipW, chipH, 6, UITheme.withAlpha(chipFill, 0xEA));
        UITheme.drawSoftRoundedBorder(g, x, y, chipW, chipH, 6, UITheme.withAlpha(dot, 0xD8));
        UITheme.fillSoftRoundedRect(g, x + 4, y + (chipH - 4) / 2, 4, 4, 2, dot);
        g.drawString(font, label, x + 10, y + (chipH - font.lineHeight) / 2 + 1, textColor, true);
        return chipW;
    }

    private int statusChipWidth(Font font, KeyBindingScanner.KeyStatus status) {
        return font.width(statusLabels.get(status)) + 14;
    }

    private static String fitToWidth(Font font, String text, int maxW) {
        return TextFitCache.fitPlain(font, text, maxW);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(value, max));
    }

    private static int countSources(List<KeyBindingScanner.KeyBindingInfo> bindings) {
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

    private static int countCategories(List<KeyBindingScanner.KeyBindingInfo> bindings) {
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

    private static int countContexts(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = 0;
        for (int i = 0; i < bindings.size(); i++) {
            ConflictContext value = bindings.get(i).conflictContext();
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

    private static String contextCountText(List<KeyBindingScanner.KeyBindingInfo> bindings) {
        int count = bindings.isEmpty() ? 0 : countContexts(bindings);
        return Component.translatable("screen.newvisualkeybing.viewer.info.contexts", count).getString();
    }

    private String inputTypeName(int virtualKey) {
        if (KeyboardLayoutData.isWheel(virtualKey)) return inputTypeWheel;
        if (KeyboardLayoutData.isMouse(virtualKey)) return inputTypeMouse;
        return inputTypeKeyboard;
    }

    private static int statusAccentColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case COMBO -> c.warning();
            case CONFLICT -> c.danger();
        };
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

    private static String contextTag(ConflictContext ctx) {
        if (ctx == null) return "";
        return switch (ctx) {
            case UNIVERSAL -> "U";
            case IN_GAME -> "G";
            case GUI -> "UI";
            case UNKNOWN -> "?";
        };
    }

    private String contextName(ConflictContext ctx) {
        if (ctx == null) return unknownContextText;
        String name = contextNames.get(ctx);
        return name != null ? name : unknownContextText;
    }

    private record TooltipLayout(int virtualKey, long scannerVersion, long comboVersion,
                                 int screenW, boolean isWheel,
                                 List<KeyBindingScanner.KeyBindingInfo> bindings,
                                 KeyBindingScanner.KeyStatus status,
                                 String keyNameFit, String statusLineFit,
                                 String summaryFit, String moreText,
                                 String conflictFit, String clickFit,
                                 int innerW, int totalW, int totalH, int chipW,
                                 int titleH, int rowH, BindingRowLayout[] rows,
                                 String[] comboLines) {}

    private record BindingRowLayout(KeyBindingScanner.KeyBindingInfo info, String ctxTag, int ctxTagW,
                                    String actionFit, String modText, int modW,
                                    String metaFit, String keyMetaFit) {}
}
