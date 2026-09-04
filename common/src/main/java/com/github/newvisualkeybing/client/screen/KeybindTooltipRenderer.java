package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * Hover tooltip for a keyboard key / mouse input, plus a generic multi-line tooltip used by the
 * detail panel and mod list.
 *
 * <p>Layout policy: the card grows to the natural width of its content (within a screen-relative
 * cap) and anything still wider is <em>wrapped</em>, never silently clipped. When the card would
 * not fit the screen height it first drops the translation-key lines, then shows fewer binding
 * rows (with a "+N more" line), so the most important information always stays readable. The card
 * is placed beside the cursor and flips to the other side instead of covering the hovered input.
 */
final class KeybindTooltipRenderer {

    private static final int PAD_X = 10;
    private static final int PAD_Y = 8;
    private static final int ROW_TEXT_INSET = 8;
    private static final int MAX_ROWS = 4;
    private static final int CURSOR_GAP = 12;
    private static final int SCREEN_MARGIN = 4;

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

    // ------------------------------------------------------------------ key tooltip ---------------

    void render(GuiGraphics g, Font font, int screenW, int screenH, int virtualKey, int mouseX, int mouseY) {
        TooltipLayout layout = layout(font, screenW, screenH, virtualKey);
        var c = UITheme.colors();
        KeyBindingScanner.KeyStatus status = layout.status();
        int innerW = layout.innerW();
        int totalW = layout.totalW();
        int totalH = layout.totalH();
        int lineH = font.lineHeight + 2;
        int tx = placeX(mouseX, totalW, screenW);
        int ty = placeY(mouseY, totalH, screenH);

        int accent = statusAccentColor(status);
        UITheme.renderTooltipBackground(g, tx, ty, totalW, totalH);
        if (!UITheme.flat()) {
            UITheme.fillRoundedRect(g, tx + UITheme.TOOLTIP_RADIUS, ty, totalW - UITheme.TOOLTIP_RADIUS * 2, 2, 1,
                    UITheme.withAlpha(accent, 0xE0));
        }

        int curX = tx + PAD_X;
        int curY = ty + PAD_Y;

        // Title: key name (wrapped) with the status chip riding the first line.
        int chipW = layout.chipW();
        renderStatusChip(g, font, tx + totalW - PAD_X - chipW, curY, status);
        for (int i = 0; i < layout.keyNameLines().size(); i++) {
            g.drawString(font, layout.keyNameLines().get(i), curX, curY + 1, c.textPrimary(), true);
            curY += i == 0 ? layout.titleH() : lineH;
        }
        curY += 4;

        UITheme.fillRoundedRect(g, curX, curY, innerW, 1, 1, UITheme.withAlpha(c.divider(), 0x90));
        curY += 4;
        curY = drawLines(g, font, layout.statusLines(), curX, curY, c.textSecondary(), lineH) + 3;

        if (layout.isWheel()) {
            curY = drawLines(g, font, layout.hintLines(), curX, curY, c.textMuted(), lineH) + 2;
        } else if (layout.bindings().isEmpty()) {
            curY = drawLines(g, font, layout.hintLines(), curX, curY, c.textMuted(), lineH) + 2;
        } else {
            curY = drawLines(g, font, layout.summaryLines(), curX, curY, c.textSecondary(), lineH) + 3;
            for (BindingRowLayout row : layout.rows()) {
                KeyBindingScanner.KeyBindingInfo info = row.info();
                int rowH = row.rowH();
                int sideColor = info.self() ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xC0);
                UITheme.fillRoundedRect(g, curX, curY, innerW, rowH - 2, 6,
                        UITheme.withAlpha(c.widgetBg(), info.self() ? 0xB8 : 0x88));
                UITheme.drawRoundedBorder(g, curX, curY, innerW, rowH - 2, 6,
                        UITheme.withAlpha(sideColor, info.self() ? 0x70 : 0x40));
                UITheme.fillRoundedRect(g, curX + 3, curY + 4, 3, rowH - 10, 2, sideColor);

                int textX = curX + ROW_TEXT_INSET;
                int ly = curY + 3;
                int actionColor = info.self() ? c.accent() : c.textPrimary();
                List<String> actionLines = row.actionLines();
                for (int i = 0; i < actionLines.size(); i++) {
                    g.drawString(font, actionLines.get(i), textX, ly, actionColor, true);
                    if (i == 0 && !row.modOnOwnLine()) {
                        drawSourceBlock(g, font, row, curX + innerW - 6, ly);
                    }
                    ly += lineH;
                }
                if (row.modOnOwnLine()) {
                    drawSourceBlock(g, font, row, curX + innerW - 6, ly);
                    ly += lineH;
                }
                ly = drawLines(g, font, row.metaLines(), textX, ly, c.textMuted(), lineH);
                ly = drawLines(g, font, row.idLines(), textX, ly, UITheme.withAlpha(c.textMuted(), 0xBE), lineH);
                drawLines(g, font, row.keyInfoLines(), textX, ly, UITheme.withAlpha(c.textMuted(), 0xD8), lineH);
                curY += rowH;
            }
            if (layout.moreText() != null) {
                g.drawString(font, layout.moreText(), curX, curY, c.textMuted(), true);
                curY += lineH;
            }
        }

        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            int warnH = layout.conflictLines().size() * lineH + 5;
            UITheme.fillRoundedRect(g, curX, curY - 2, innerW, warnH, 6, UITheme.withAlpha(c.dangerColor(), 0x24));
            UITheme.drawRoundedBorder(g, curX, curY - 2, innerW, warnH, 6, UITheme.withAlpha(c.dangerColor(), 0x88));
            drawLines(g, font, layout.conflictLines(), curX + 7, curY + 1, c.dangerColor(), lineH);
            curY += warnH + 3;
        }

        if (!layout.comboLines().isEmpty()) {
            int yellow = KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR;
            for (List<String> combo : layout.comboLines()) {
                int boxH = combo.size() * lineH + 4;
                UITheme.fillRoundedRect(g, curX, curY - 1, innerW, boxH, 6, UITheme.withAlpha(yellow, 0x20));
                UITheme.drawRoundedBorder(g, curX, curY - 1, innerW, boxH, 6, UITheme.withAlpha(yellow, 0x70));
                drawLines(g, font, combo, curX + 7, curY + 2, yellow, lineH);
                curY += boxH + 2;
            }
            curY += 2;
        }

        drawLines(g, font, layout.clickLines(), curX, curY, c.textMuted(), lineH);
    }

    /** Right-aligned "[ctx] mod" block whose right edge sits at {@code rightX}. */
    private void drawSourceBlock(GuiGraphics g, Font font, BindingRowLayout row, int rightX, int y) {
        var c = UITheme.colors();
        int modX = rightX - row.modW();
        g.drawString(font, row.modText(), modX, y, c.textSecondary(), true);
        if (!row.ctxTag().isEmpty()) {
            int tagW = row.ctxTagW() + 6;
            int tagX = modX - tagW - 4;
            UITheme.fillRoundedRect(g, tagX - 3, y - 1, tagW, font.lineHeight + 2, 4,
                    UITheme.withAlpha(c.accentAlt(), 0x28));
            UITheme.drawRoundedBorder(g, tagX - 3, y - 1, tagW, font.lineHeight + 2, 4,
                    UITheme.withAlpha(c.accentAlt(), 0x70));
            g.drawString(font, row.ctxTag(), tagX, y, c.accentAlt(), true);
        }
    }

    private static int drawLines(GuiGraphics g, Font font, List<String> lines, int x, int y, int color, int lineH) {
        for (String line : lines) {
            g.drawString(font, line, x, y, color, true);
            y += lineH;
        }
        return y;
    }

    private TooltipLayout layout(Font font, int screenW, int screenH, int virtualKey) {
        ensureCache();
        long version = scanner.version();
        long comboVersion = KeybindComboStore.global().version();
        if (cachedLayout != null
                && cachedLayout.virtualKey() == virtualKey
                && cachedLayout.scannerVersion() == version
                && cachedLayout.comboVersion() == comboVersion
                && cachedLayout.screenW() == screenW
                && cachedLayout.screenH() == screenH
                && cachedLayout.lineHeight() == font.lineHeight) {
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
        int maxInnerW = maxInnerWidth(screenW);
        int minInnerW = Math.min(200, maxInnerW);

        String statusLine = Component.translatable("screen.newvisualkeybing.viewer.tooltip.status_line",
                inputTypeName(virtualKey), contextCountText(bindings)).getString();
        String hint = isWheel ? wheelHintText : unboundText;
        String summary = Component.translatable("screen.newvisualkeybing.viewer.tooltip.summary",
                bindings.size(), countSources(bindings), countCategories(bindings), countContexts(bindings)).getString();
        List<KeybindComboStore.ComboBinding> combos = KeybindComboStore.global().combosForVirtualKey(virtualKey);
        List<String> comboTexts = new ArrayList<>(combos.size());
        for (KeybindComboStore.ComboBinding cb : combos) {
            comboTexts.add("\u25cf " + cb.comboLabel() + " \u2014 " + KeybindComboStore.describeMapping(cb.mappingName));
        }

        // Natural width: the widest single line of anything we will show. Row header lines count
        // action + tag + mod; the detail lines are allowed to wrap, so they only widen the card up
        // to a comfortable reading width rather than dictating it.
        int natural = font.width(keyName) + 8 + chipW;
        natural = Math.max(natural, font.width(statusLine));
        natural = Math.max(natural, font.width(clickHintText));
        if (isWheel || bindings.isEmpty()) {
            natural = Math.max(natural, font.width(hint));
        } else {
            natural = Math.max(natural, font.width(summary));
            int visibleRows = Math.min(bindings.size(), MAX_ROWS);
            for (int i = 0; i < visibleRows; i++) {
                KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
                String ctxTag = contextTag(info.conflictContext());
                int headW = ROW_TEXT_INSET + 6 + font.width(info.actionName()) + 12 + font.width(info.modName())
                        + (ctxTag.isEmpty() ? 0 : font.width(ctxTag) + 10);
                natural = Math.max(natural, headW);
                natural = Math.max(natural, Math.min(ROW_TEXT_INSET + 6 + font.width(keyInfoText(info)), 300));
                natural = Math.max(natural, Math.min(ROW_TEXT_INSET + 6 + font.width(metaText(info)), 300));
            }
        }
        if (status == KeyBindingScanner.KeyStatus.CONFLICT) {
            natural = Math.max(natural, Math.min(font.width(conflictWarningText) + 14, 320));
        }
        for (String comboText : comboTexts) natural = Math.max(natural, Math.min(font.width(comboText) + 14, 320));
        int innerW = Math.max(minInnerW, Math.min(maxInnerW, natural));

        int lineH = font.lineHeight + 2;
        int titleH = Math.max(font.lineHeight, 12);
        List<String> keyNameLines = wrapLines(font, keyName, innerW - chipW - 6, 2);
        List<String> statusLines = wrapLines(font, statusLine, innerW, 2);
        List<String> hintLines = wrapLines(font, hint, innerW, 3);
        List<String> summaryLines = wrapLines(font, summary, innerW, 2);
        List<String> conflictLines = status == KeyBindingScanner.KeyStatus.CONFLICT
                ? wrapLines(font, conflictWarningText, innerW - 14, 3) : List.of();
        List<List<String>> comboLines = new ArrayList<>(comboTexts.size());
        for (String comboText : comboTexts) comboLines.add(wrapLines(font, comboText, innerW - 14, 2));
        List<String> clickLines = wrapLines(font, clickHintText, innerW, 2);

        // Fit to the screen height: drop translation keys first, then rows.
        int availH = screenH - SCREEN_MARGIN * 2;
        int maxRows = Math.min(bindings.size(), MAX_ROWS);
        boolean compact = false;
        BindingRowLayout[] rows;
        String moreText;
        int totalH;
        while (true) {
            rows = buildRows(font, bindings, maxRows, innerW, compact, lineH);
            moreText = bindings.size() > maxRows
                    ? Component.translatable("screen.newvisualkeybing.viewer.tooltip.more",
                            bindings.size() - maxRows).getString()
                    : null;
            int contentH = titleH + (keyNameLines.size() - 1) * lineH + 4 + 4;
            contentH += statusLines.size() * lineH + 3;
            if (isWheel || bindings.isEmpty()) {
                contentH += hintLines.size() * lineH + 2;
            } else {
                contentH += summaryLines.size() * lineH + 3;
                for (BindingRowLayout r : rows) contentH += r.rowH();
                if (moreText != null) contentH += lineH;
            }
            if (status == KeyBindingScanner.KeyStatus.CONFLICT) contentH += conflictLines.size() * lineH + 5 + 3;
            if (!comboLines.isEmpty()) {
                for (List<String> combo : comboLines) contentH += combo.size() * lineH + 4 + 2;
                contentH += 2;
            }
            contentH += clickLines.size() * lineH;
            totalH = contentH + PAD_Y * 2;
            if (totalH <= availH) break;
            if (!compact && !bindings.isEmpty()) {
                compact = true;
            } else if (maxRows > 1) {
                maxRows--;
            } else {
                break;
            }
        }

        cachedLayout = new TooltipLayout(virtualKey, version, comboVersion, screenW, screenH, font.lineHeight,
                isWheel, bindings, status, keyNameLines, statusLines, hintLines, summaryLines, moreText,
                conflictLines, clickLines, innerW, innerW + PAD_X * 2, totalH, chipW, titleH, rows, comboLines);
        return cachedLayout;
    }

    private BindingRowLayout[] buildRows(Font font, List<KeyBindingScanner.KeyBindingInfo> bindings,
                                         int maxRows, int innerW, boolean compact, int lineH) {
        BindingRowLayout[] rows = new BindingRowLayout[maxRows];
        int textW = innerW - ROW_TEXT_INSET - 6;
        for (int i = 0; i < maxRows; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            String ctxTag = contextTag(info.conflictContext());
            int ctxTagW = ctxTag.isEmpty() ? 0 : font.width(ctxTag);
            int tagBlockW = ctxTag.isEmpty() ? 0 : ctxTagW + 6 + 4;
            int modNaturalW = font.width(info.modName());
            // The source block shares the header line while it leaves the action at least half the
            // width; otherwise it moves to its own right-aligned line so neither gets squeezed.
            boolean modOnOwnLine = tagBlockW + modNaturalW > textW / 2;
            String modText = modOnOwnLine ? fitToWidth(font, info.modName(), textW - tagBlockW) : info.modName();
            int modW = font.width(modText);
            int actionW = modOnOwnLine ? textW : textW - tagBlockW - modW - 8;
            List<String> actionLines = wrapLines(font, info.actionName(), Math.max(40, actionW), 3);
            List<String> metaLines = wrapLines(font, metaText(info), textW, 2);
            List<String> idLines = compact ? List.of() : wrapLines(font, info.translationKey(), textW, 2);
            List<String> keyInfoLines = wrapLines(font, keyInfoText(info), textW, 2);
            int nLines = actionLines.size() + (modOnOwnLine ? 1 : 0) + metaLines.size()
                    + idLines.size() + keyInfoLines.size();
            rows[i] = new BindingRowLayout(info, ctxTag, ctxTagW, actionLines, modText, modW, modOnOwnLine,
                    metaLines, idLines, keyInfoLines, 6 + nLines * lineH);
        }
        return rows;
    }

    private String metaText(KeyBindingScanner.KeyBindingInfo info) {
        return info.categoryName() + "  \u00b7  " + contextName(info.conflictContext());
    }

    private static String keyInfoText(KeyBindingScanner.KeyBindingInfo info) {
        return Component.translatable("screen.newvisualkeybing.viewer.tooltip.current_key",
                info.currentKeyName()).getString() + "   \u00b7   "
                + Component.translatable("screen.newvisualkeybing.viewer.tooltip.default_key",
                info.defaultKeyName()).getString();
    }

    private void renderStatusChip(GuiGraphics g, Font font, int x, int y, KeyBindingScanner.KeyStatus status) {
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
        int chipFill = UITheme.lerpColor(c.widgetBg(), dot, 0.22f);
        UITheme.fillRoundedRect(g, x, y, chipW, chipH, 6, UITheme.withAlpha(chipFill, 0xEA));
        UITheme.drawRoundedBorder(g, x, y, chipW, chipH, 6, UITheme.withAlpha(dot, 0xD8));
        UITheme.fillRoundedRect(g, x + 4, y + (chipH - 4) / 2, 4, 4, 2, dot);
        g.drawString(font, label, x + 10, y + (chipH - font.lineHeight) / 2 + 1, textColor, true);
    }

    private int statusChipWidth(Font font, KeyBindingScanner.KeyStatus status) {
        return font.width(statusLabels.get(status)) + 14;
    }

    // ------------------------------------------------------------------ generic tooltip -----------

    /** One line of a generic tooltip. A {@code null} text draws a thin divider instead. */
    record TipLine(String text, int color) {
        static TipLine divider() {
            return new TipLine(null, 0);
        }
    }

    /**
     * Draws a themed tooltip made of plain text lines next to the cursor. Lines wider than the
     * screen-relative cap wrap (up to four visual lines each); the card flips to the other side of
     * the cursor when it would run off the screen.
     */
    static void renderLines(GuiGraphics g, Font font, int screenW, int screenH,
                            List<TipLine> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) return;
        int maxInnerW = maxInnerWidth(screenW);
        int natural = 0;
        for (TipLine line : lines) {
            if (line.text() != null) natural = Math.max(natural, font.width(line.text()));
        }
        int innerW = Math.max(Math.min(60, maxInnerW), Math.min(maxInnerW, natural));
        int lineH = font.lineHeight + 2;
        List<List<String>> wrapped = new ArrayList<>(lines.size());
        int contentH = 0;
        for (TipLine line : lines) {
            if (line.text() == null) {
                wrapped.add(null);
                contentH += 5;
            } else {
                List<String> w = wrapLines(font, line.text(), innerW, 4);
                wrapped.add(w);
                contentH += w.size() * lineH;
            }
        }
        int totalW = innerW + PAD_X * 2;
        int totalH = contentH + PAD_Y * 2 - 2;
        int tx = placeX(mouseX, totalW, screenW);
        int ty = placeY(mouseY, totalH, screenH);
        UITheme.renderTooltipBackground(g, tx, ty, totalW, totalH);
        var c = UITheme.colors();
        int curY = ty + PAD_Y;
        for (int i = 0; i < lines.size(); i++) {
            List<String> w = wrapped.get(i);
            if (w == null) {
                UITheme.fillRoundedRect(g, tx + PAD_X, curY + 2, innerW, 1, 1, UITheme.withAlpha(c.divider(), 0x90));
                curY += 5;
                continue;
            }
            curY = drawLines(g, font, w, tx + PAD_X, curY, lines.get(i).color(), lineH);
        }
    }

    // ------------------------------------------------------------------ placement / measuring -----

    /** Widest content the card may have on this screen: roughly half the width, within sane bounds. */
    private static int maxInnerWidth(int screenW) {
        int cap = Math.round(screenW * 0.5f);
        return Math.max(120, Math.min(Math.min(440, screenW - 32), Math.max(240, cap)));
    }

    /** Prefers the right of the cursor, flips to the left when that would overflow, then clamps. */
    private static int placeX(int mouseX, int totalW, int screenW) {
        int x = mouseX + CURSOR_GAP;
        if (x + totalW > screenW - SCREEN_MARGIN) {
            int flipped = mouseX - CURSOR_GAP - totalW;
            x = flipped >= SCREEN_MARGIN ? flipped : Math.max(SCREEN_MARGIN, screenW - SCREEN_MARGIN - totalW);
        }
        return Math.max(SCREEN_MARGIN, x);
    }

    /** Prefers below the cursor, flips above when that would overflow, then clamps. */
    private static int placeY(int mouseY, int totalH, int screenH) {
        int y = mouseY + CURSOR_GAP;
        if (y + totalH > screenH - SCREEN_MARGIN) {
            int flipped = mouseY - CURSOR_GAP - totalH;
            y = flipped >= SCREEN_MARGIN ? flipped : Math.max(SCREEN_MARGIN, screenH - SCREEN_MARGIN - totalH);
        }
        return Math.max(SCREEN_MARGIN, y);
    }

    private static String fitToWidth(Font font, String text, int maxW) {
        return TextFitCache.fitPlain(font, text, maxW);
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

    /**
     * Greedy word/character wrap (via Minecraft's line splitter) capped at {@code maxLines}; the last
     * kept line is ellipsized when the text overflows the cap so nothing is silently dropped.
     */
    static List<String> wrapLines(Font font, String text, int maxW, int maxLines) {
        if (text == null || text.isEmpty()) return List.of("");
        if (maxW <= 0) return List.of(fitToWidth(font, text, Math.max(1, maxW)));
        if (font.width(text) <= maxW) return List.of(text);
        List<FormattedText> split = font.getSplitter().splitLines(text, maxW, Style.EMPTY);
        if (split.isEmpty()) return List.of(fitToWidth(font, text, maxW));
        List<String> lines = new ArrayList<>(Math.min(split.size(), maxLines));
        for (int i = 0; i < split.size() && i < maxLines; i++) {
            lines.add(split.get(i).getString());
        }
        if (split.size() > maxLines && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, fitToWidth(font, lines.get(last) + " " + split.get(maxLines).getString(), maxW));
        }
        return lines;
    }

    private record TooltipLayout(int virtualKey, long scannerVersion, long comboVersion,
                                 int screenW, int screenH, int lineHeight, boolean isWheel,
                                 List<KeyBindingScanner.KeyBindingInfo> bindings,
                                 KeyBindingScanner.KeyStatus status,
                                 List<String> keyNameLines, List<String> statusLines,
                                 List<String> hintLines, List<String> summaryLines, String moreText,
                                 List<String> conflictLines, List<String> clickLines,
                                 int innerW, int totalW, int totalH, int chipW,
                                 int titleH, BindingRowLayout[] rows,
                                 List<List<String>> comboLines) {}

    private record BindingRowLayout(KeyBindingScanner.KeyBindingInfo info, String ctxTag, int ctxTagW,
                                    List<String> actionLines, String modText, int modW, boolean modOnOwnLine,
                                    List<String> metaLines, List<String> idLines, List<String> keyInfoLines,
                                    int rowH) {}
}
