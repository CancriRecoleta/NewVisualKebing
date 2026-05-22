package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public class KeybindComboManageScreen extends FixedScaleScreen {

    static final int HIGHLIGHT_COLOR = KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR;

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 26;
    private static final int DELETE_BTN_W = 60;
    private static final int RECORD_BTN_W = 96;
    private static final int COL_GAP = 4;

    private final Screen parent;
    private final KeybindComboStore store = KeybindComboStore.global();

    private MCEditBox mappingSearchBox;
    private MCButton backButton;
    private MCButton addButton;
    private MCButton clearAllButton;

    private final List<ComboRow> rows = new ArrayList<>();
    private final List<MappingPickRow> mappingPickRows = new ArrayList<>();
    private int scrollOffset;
    private int totalListH;
    private long mappingPickRowsVersion = -1L;
    private String comboTitleText;
    private String comboEmptyText;
    private String statusActiveText;
    private String statusDormantText;
    private String rerecordText;
    private String deleteText;
    private String footerHintText;
    private String pickerSearchText;
    private String pickerHintText;
    private String pickerTitleText;
    private String captureTitleText;
    private String cancelHintText;

    private CaptureState capture = null;
    private String noticeMessage;
    private long noticeUntil;
    private int headerSearchX;
    private int headerSearchW;

    public KeybindComboManageScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.viewer.combo.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        UITheme.setMode(UITheme.Mode.DARK);

        mappingSearchBox = new MCEditBox(font, 0, 0, 1, 1,
                Component.translatable("screen.newvisualkeybing.viewer.combo.search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.combo.search"))
                .withClearAffordance(true);
        mappingSearchBox.setResponder(value -> rebuildRows());
        addRenderableWidget(mappingSearchBox);

        backButton = MCButton.create(0, 0, 60, 20,
                Component.translatable("screen.newvisualkeybing.viewer.back"), b -> onClose());
        addRenderableWidget(backButton);

        addButton = MCButton.create(0, 0, 110, 20,
                Component.translatable("screen.newvisualkeybing.viewer.combo.add"),
                b -> beginAddCombo());
        addRenderableWidget(addButton);

        clearAllButton = MCButton.create(0, 0, 110, 20,
                Component.translatable("screen.newvisualkeybing.viewer.combo.clear_all"),
                b -> {
                    store.clear();
                    rebuildRows();
                    showNotice(Component.translatable("screen.newvisualkeybing.viewer.combo.cleared").getString());
                });
        addRenderableWidget(clearAllButton);

        layoutHeaderControls();
        rebuildRows();
    }

    @Override
    protected void onFixedScaleMetricsChanged() {
        layoutHeaderControls();
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, totalListH - listHeight()));
    }

    private void layoutHeaderControls() {
        headerSearchX = 12;
        headerSearchW = Mth.clamp(width / 3, 180, 320);
        int searchH = 22;
        int searchY = (HEADER_H - searchH) / 2;
        if (mappingSearchBox != null) {
            mappingSearchBox.setX(headerSearchX);
            mappingSearchBox.setY(searchY);
            mappingSearchBox.setWidth(headerSearchW);
            mappingSearchBox.setHeight(searchH);
        }

        int btnGap = 6;
        int backW = 60;
        int addW = 110;
        int clearW = 110;
        int xClear = width - 12 - clearW;
        int xAdd = xClear - btnGap - addW;
        int xBack = xAdd - btnGap - backW;
        if (backButton != null) {
            backButton.setX(xBack);
            backButton.setY(10);
            backButton.setWidth(backW);
            backButton.setHeight(20);
        }
        if (addButton != null) {
            addButton.setX(xAdd);
            addButton.setY(10);
            addButton.setWidth(addW);
            addButton.setHeight(20);
        }
        if (clearAllButton != null) {
            clearAllButton.setX(xClear);
            clearAllButton.setY(10);
            clearAllButton.setWidth(clearW);
            clearAllButton.setHeight(20);
        }
    }

    private void rebuildRows() {
        refreshTextCache();
        rows.clear();
        String q = mappingSearchBox == null ? "" : mappingSearchBox.getValue().toLowerCase(Locale.ROOT);
        for (KeybindComboStore.ComboBinding combo : store.combos()) {
            ComboRow row = ComboRow.of(combo);
            if (q.isBlank() || row.matches(q)) {
                rows.add(row);
            }
        }
        totalListH = rows.size() * ROW_H;
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, totalListH - listHeight()));
    }

    private void refreshTextCache() {
        if (comboTitleText != null) return;
        comboTitleText = Component.translatable("screen.newvisualkeybing.viewer.combo.title").getString();
        comboEmptyText = Component.translatable("screen.newvisualkeybing.viewer.combo.empty").getString();
        statusActiveText = Component.translatable("screen.newvisualkeybing.viewer.combo.status.active").getString();
        statusDormantText = Component.translatable("screen.newvisualkeybing.viewer.combo.status.dormant").getString();
        rerecordText = Component.translatable("screen.newvisualkeybing.viewer.combo.rerecord").getString();
        deleteText = Component.translatable("screen.newvisualkeybing.viewer.combo.delete").getString();
        footerHintText = Component.translatable("screen.newvisualkeybing.viewer.combo.hint").getString();
        pickerSearchText = Component.translatable("screen.newvisualkeybing.viewer.combo.search").getString();
        pickerHintText = Component.translatable("screen.newvisualkeybing.viewer.combo.picker_hint").getString();
        pickerTitleText = Component.translatable("screen.newvisualkeybing.viewer.combo.pick_mapping").getString();
        captureTitleText = Component.translatable("screen.newvisualkeybing.viewer.combo.capture_title").getString();
        cancelHintText = Component.translatable("screen.newvisualkeybing.viewer.combo.cancel_hint").getString();
    }

    private List<MappingPickRow> mappingPickRows() {
        Minecraft mc = Minecraft.getInstance();
        long version = mc != null && mc.options != null ? mc.options.keyMappings.length : 0L;
        if (version == mappingPickRowsVersion) return mappingPickRows;
        mappingPickRows.clear();
        if (mc != null && mc.options != null) {
            for (KeyMapping km : mc.options.keyMappings) {
                mappingPickRows.add(MappingPickRow.of(km));
            }
            mappingPickRows.sort(Comparator.comparing(MappingPickRow::name, String.CASE_INSENSITIVE_ORDER));
        }
        mappingPickRowsVersion = version;
        return mappingPickRows;
    }

    private void beginAddCombo() {
        capture = new CaptureState();
        if (mappingSearchBox != null && mappingSearchBox.isFocused()) {
            mappingSearchBox.setFocused(false);
            this.setFocused(null);
        }
    }

    private int listTop() { return HEADER_H + 4; }
    private int listHeight() { return height - HEADER_H - FOOTER_H - 8; }
    private int listX() { return 12; }
    private int listW() { return width - listX() - 12; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        int fixedMouseX = fixedMouseX(mouseX);
        int fixedMouseY = fixedMouseY(mouseY);
        pushFixedScale(graphics);
        try {
        var colors = UITheme.colors();
        graphics.fill(0, 0, width, height, UITheme.withAlpha(colors.panelBg(), 0xE6));

        renderHeader(graphics);
        renderList(graphics, fixedMouseX, fixedMouseY);
        renderFooter(graphics);

        super.render(graphics, fixedMouseX, fixedMouseY, partialTick);

        if (capture != null) renderCaptureOverlay(graphics);
        renderNotice(graphics);
        } finally {
            popFixedScale(graphics);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        var colors = UITheme.colors();
        UITheme.drawGlassPanel(graphics, 4, 4, width - 8, HEADER_H - 4, 8);

        String title = comboTitleText;
        String count = Component.translatable("screen.newvisualkeybing.viewer.combo.count", store.size()).getString();
        int titleX = headerSearchX + headerSearchW + 16;
        int textBlockH = font.lineHeight * 2 + 3;
        int titleY = (HEADER_H - textBlockH) / 2;
        int titleRight = backButton == null ? width - 12 : backButton.getX() - 8;
        graphics.drawString(font, KeybindViewerScreen.fitToWidth(font, title, Math.max(40, titleRight - titleX)),
                titleX, titleY, colors.textPrimary(), false);
        graphics.drawString(font, KeybindViewerScreen.fitToWidth(font, count, Math.max(40, titleRight - titleX)),
                titleX, titleY + font.lineHeight + 3, HIGHLIGHT_COLOR, false);
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY) {
        if (mappingSearchBox == null || mappingSearchBox.getValue().isEmpty()) return false;
        if (mappingSearchBox.clearAffordanceClicked(mouseX, mouseY)) {
            mappingSearchBox.setValue("");
            mappingSearchBox.setFocused(true);
            this.setFocused(mappingSearchBox);
            return true;
        }
        return false;
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int listTop = listTop();
        int listH = listHeight();
        int x = listX();
        int w = listW();

        UITheme.fillRoundedRectFast(graphics, x, listTop, w, listH, 8, UITheme.withAlpha(colors.headerBg(), 0xC0));
        UITheme.drawRoundedBorderFast(graphics, x, listTop, w, listH, 8, colors.widgetBorder());

        if (rows.isEmpty()) {
            String empty = comboEmptyText;
            graphics.drawString(font, empty,
                    x + (w - font.width(empty)) / 2,
                    listTop + (listH - font.lineHeight) / 2,
                    colors.textMuted(), false);
            return;
        }

        enableFixedScissor(graphics, x + 1, listTop + 1, x + w - 1, listTop + listH - 1);
        int drawY = listTop + 4 - scrollOffset;
        for (int i = 0; i < rows.size(); i++) {
            if (drawY + ROW_H >= listTop && drawY <= listTop + listH) {
                boolean hovered = mouseX >= x + 8 && mouseX < x + w - 8
                        && mouseY >= drawY && mouseY < drawY + ROW_H;
                renderRow(graphics, rows.get(i), x + 8, drawY, w - 16, mouseX, mouseY, hovered);
            }
            drawY += ROW_H;
        }
        graphics.disableScissor();

        if (totalListH > listH) {
            float ratio = (float) listH / totalListH;
            int sbH = Math.max(20, (int) (listH * ratio));
            int sbY = listTop + (int) ((float) scrollOffset / totalListH * listH);
            UITheme.fillRoundedRectFast(graphics, x + w - 6, listTop, 4, listH, 2, colors.scrollbarTrack());
            UITheme.fillRoundedRectFast(graphics, x + w - 6, sbY, 4, sbH, 2, colors.scrollbarThumb());
        }
    }

    private void renderRow(GuiGraphics graphics, ComboRow combo,
                           int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        var colors = UITheme.colors();
        int rowTop = y + 1;
        int rowH = ROW_H - 3;
        int bg = hovered ? UITheme.lerpColor(colors.widgetBg(), HIGHLIGHT_COLOR, 0.10f)
                : UITheme.withAlpha(colors.widgetBg(), 0xA0);
        UITheme.fillRoundedRectFast(graphics, x, rowTop, w, rowH, 6, bg);
        UITheme.drawRoundedBorderFast(graphics, x, rowTop, w, rowH, 6,
                UITheme.withAlpha(HIGHLIGHT_COLOR, 0xA0));

        UITheme.fillRoundedRectFast(graphics, x + 3, rowTop + 3, 2, rowH - 6, 1, HIGHLIGHT_COLOR);

        int textColX = x + 10;
        int btnAreaW = RECORD_BTN_W + DELETE_BTN_W + COL_GAP * 2;
        int textColMaxW = w - btnAreaW - COL_GAP - 14;

        KeyMapping mapping = KeybindComboStore.findMapping(combo.mappingName);
        boolean active = mapping != null
                && combo.secondKey != null
                && combo.secondKey.equals(KeybindComboStore.currentKey(mapping).getName());
        int statusColor = active ? HIGHLIGHT_COLOR : colors.warningColor();
        String statusText = active ? statusActiveText : statusDormantText;
        int statusW = font.width(statusText);

        String name = combo.name;
        int nameMaxW = textColMaxW;
        if (font.width(name) > nameMaxW) name = font.plainSubstrByWidth(name, nameMaxW - 6) + "..";
        String comboLabel = combo.comboLabel;
        int comboMaxW = Math.max(20, textColMaxW - statusW - 12);
        if (font.width(comboLabel) > comboMaxW) {
            comboLabel = font.plainSubstrByWidth(comboLabel, comboMaxW - 6) + "..";
        }

        int textBlockH = font.lineHeight * 2 + 3;
        int textTop = rowTop + (rowH - textBlockH) / 2;
        graphics.drawString(font, name, textColX, textTop, colors.textPrimary(), false);
        int comboY = textTop + font.lineHeight + 3;
        graphics.drawString(font, comboLabel, textColX, comboY, HIGHLIGHT_COLOR, false);

        int statusX = textColX + font.width(comboLabel) + 10;
        int dotR = 3;
        UITheme.fillRoundedRectFast(graphics, statusX, comboY + (font.lineHeight - dotR) / 2,
                dotR, dotR, 1, statusColor);
        graphics.drawString(font, statusText, statusX + dotR + 4, comboY, statusColor, false);

        int recordX = x + w - RECORD_BTN_W - DELETE_BTN_W - COL_GAP * 2;
        int btnTop = rowTop + (rowH - 16) / 2;
        boolean rcHover = hovered && mouseX >= recordX && mouseX < recordX + RECORD_BTN_W
                && mouseY >= btnTop && mouseY < btnTop + 16;
        int rcFill = rcHover ? UITheme.lerpColor(colors.widgetBg(), HIGHLIGHT_COLOR, 0.40f) : colors.widgetBg();
        UITheme.fillRoundedRectFast(graphics, recordX, btnTop, RECORD_BTN_W, 16, 4, rcFill);
        UITheme.drawRoundedBorderFast(graphics, recordX, btnTop, RECORD_BTN_W, 16, 4,
                UITheme.withAlpha(HIGHLIGHT_COLOR, 0xC0));
        String reLabel = rerecordText;
        graphics.drawString(font, reLabel,
                recordX + (RECORD_BTN_W - font.width(reLabel)) / 2,
                btnTop + (16 - font.lineHeight) / 2 + 1,
                colors.textPrimary(), false);

        int deleteX = recordX + RECORD_BTN_W + COL_GAP;
        boolean delHover = hovered && mouseX >= deleteX && mouseX < deleteX + DELETE_BTN_W
                && mouseY >= btnTop && mouseY < btnTop + 16;
        int delFill = delHover ? UITheme.lerpColor(colors.widgetBg(), colors.dangerColor(), 0.50f)
                : colors.widgetBg();
        UITheme.fillRoundedRectFast(graphics, deleteX, btnTop, DELETE_BTN_W, 16, 4, delFill);
        UITheme.drawRoundedBorderFast(graphics, deleteX, btnTop, DELETE_BTN_W, 16, 4,
                UITheme.withAlpha(colors.dangerColor(), 0xB0));
        String del = deleteText;
        graphics.drawString(font, del,
                deleteX + (DELETE_BTN_W - font.width(del)) / 2,
                btnTop + (16 - font.lineHeight) / 2 + 1,
                delHover ? colors.dangerColor() : colors.textSecondary(), false);
    }

    private void renderFooter(GuiGraphics graphics) {
        var colors = UITheme.colors();
        int y = height - FOOTER_H;
        graphics.fill(0, y, width, y + 1, colors.divider());
        String hint = capture == null ? footerHintText : capture.hintMessage();
        graphics.drawString(font, hint, (width - font.width(hint)) / 2,
                y + (FOOTER_H - font.lineHeight) / 2, colors.textSecondary(), false);
    }

    private void renderCaptureOverlay(GuiGraphics graphics) {
        var colors = UITheme.colors();
        graphics.fill(0, 0, width, height, 0xC0000000);

        int bw = 420;
        int bh = capture.stage == CaptureStage.SELECT_MAPPING ? Math.min(360, height - 80) : 120;
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;
        UITheme.drawGlassPanel(graphics, bx, by, bw, bh, 10);
        UITheme.fillRoundedRectFast(graphics, bx + 8, by, bw - 16, 1, 1, HIGHLIGHT_COLOR);

        String title = capture.titleMessage();
        graphics.drawString(font, title, bx + (bw - font.width(title)) / 2, by + 10,
                HIGHLIGHT_COLOR, true);

        if (capture.stage == CaptureStage.SELECT_MAPPING) {
            renderMappingPicker(graphics, bx, by, bw, bh);
        } else {
            String l2 = KeybindComboStore.describeMapping(capture.mapping.getName());
            String l3 = capture.firstKey == null
                    ? Component.translatable("screen.newvisualkeybing.viewer.combo.press_first").getString()
                    : Component.translatable("screen.newvisualkeybing.viewer.combo.press_second",
                        capture.firstKey.getDisplayName().getString()).getString();
            graphics.drawString(font, l2, bx + (bw - font.width(l2)) / 2, by + 36, colors.textPrimary(), true);
            graphics.drawString(font, l3, bx + (bw - font.width(l3)) / 2, by + 56, HIGHLIGHT_COLOR, false);
            String tip = Component.translatable("screen.newvisualkeybing.viewer.combo.cancel_hint").getString();
            graphics.drawString(font, tip, bx + (bw - font.width(tip)) / 2, by + bh - 16, colors.textMuted(), false);
        }
    }

    private void renderMappingPicker(GuiGraphics graphics, int bx, int by, int bw, int bh) {
        var colors = UITheme.colors();
        String search = capture.search.isEmpty()
                ? pickerSearchText
                : "> " + capture.search;
        graphics.drawString(font, search, bx + 12, by + 22,
                capture.search.isEmpty() ? colors.textMuted() : HIGHLIGHT_COLOR, false);
        int listX = bx + 12;
        int listY = by + 32;
        int listW = bw - 24;
        int listH = bh - 56;
        UITheme.fillRoundedRectFast(graphics, listX, listY, listW, listH, 6,
                UITheme.withAlpha(colors.widgetBg(), 0xC0));
        UITheme.drawRoundedBorderFast(graphics, listX, listY, listW, listH, 6, colors.widgetBorder());

        List<MappingPickRow> mappings = capture.filteredMappings();
        int rowH = 16;
        int visible = Math.max(1, (listH - 6) / rowH);
        capture.scrollOffset = Mth.clamp(capture.scrollOffset, 0, Math.max(0, mappings.size() - visible));

        enableFixedScissor(graphics, listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        int drawY = listY + 4;
        for (int i = capture.scrollOffset; i < mappings.size() && i < capture.scrollOffset + visible; i++) {
            MappingPickRow rowData = mappings.get(i);
            String name = rowData.name;
            String current = rowData.currentKey;
            String row = name + "  (" + current + ")";
            if (font.width(row) > listW - 16) row = font.plainSubstrByWidth(row, listW - 22) + "..";
            graphics.drawString(font, row, listX + 8, drawY, colors.textPrimary(), false);
            drawY += rowH;
        }
        graphics.disableScissor();

        String tip = pickerHintText;
        graphics.drawString(font, tip, bx + (bw - font.width(tip)) / 2, by + bh - 16, colors.textMuted(), false);
    }

    private void renderNotice(GuiGraphics graphics) {
        if (noticeMessage == null) return;
        long now = System.currentTimeMillis();
        if (now > noticeUntil) { noticeMessage = null; return; }
        var colors = UITheme.colors();
        int w = font.width(noticeMessage) + 24;
        int x = (width - w) / 2;
        int y = height - FOOTER_H - 26;
        UITheme.fillRoundedRectFast(graphics, x, y, w, 20, 6, UITheme.withAlpha(colors.headerBg(), 0xE0));
        UITheme.drawRoundedBorderFast(graphics, x, y, w, 20, 6, HIGHLIGHT_COLOR);
        graphics.drawString(font, noticeMessage, x + 12, y + 6, colors.textPrimary(), false);
    }

    private void showNotice(String msg) {
        noticeMessage = msg;
        noticeUntil = System.currentTimeMillis() + 2200;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (capture != null) {
            if (capture.stage == CaptureStage.SELECT_MAPPING) {
                if (handleMappingPickerClick(mouseX, mouseY)) return true;
                return true;
            }
            if (button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
                onKeyCaptured(InputConstants.Type.MOUSE.getOrCreate(button));
            }
            return true;
        }
        if (handleSearchClearClick(mouseX, mouseY)) return true;
        if (mappingSearchBox != null && mappingSearchBox.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (mappingSearchBox != null && mappingSearchBox.isFocused()) {
            mappingSearchBox.setFocused(false);
            if (this.getFocused() == mappingSearchBox) this.setFocused(null);
        }
        if (headerButtonClicked(mouseX, mouseY, button)) return true;

        int x = listX(), w = listW();
        int listTop = listTop();
        int listH = listHeight();
        if (mouseX >= x && mouseX < x + w && mouseY >= listTop && mouseY < listTop + listH) {
            int relY = (int) (mouseY - listTop - 4) + scrollOffset;
            int idx = relY / ROW_H;
            if (idx >= 0 && idx < rows.size() && relY - idx * ROW_H < ROW_H) {
                ComboRow combo = rows.get(idx);
                int rowX = x + 8;
                int rowW = w - 16;
                int recordX = rowX + rowW - RECORD_BTN_W - DELETE_BTN_W - COL_GAP * 2;
                int deleteX = recordX + RECORD_BTN_W + COL_GAP;
                if (mouseX >= recordX && mouseX < recordX + RECORD_BTN_W) {
                    KeyMapping mapping = KeybindComboStore.findMapping(combo.mappingName);
                    if (mapping != null) {
                        capture = new CaptureState();
                        capture.mapping = mapping;
                        capture.stage = CaptureStage.AWAITING_FIRST;
                    } else {
                        showNotice(Component.translatable(
                                "screen.newvisualkeybing.viewer.combo.mapping_missing").getString());
                    }
                    return true;
                }
                if (mouseX >= deleteX && mouseX < deleteX + DELETE_BTN_W) {
                    store.removeCombo(combo.mappingName);
                    rebuildRows();
                    showNotice(Component.translatable(
                            "screen.newvisualkeybing.viewer.combo.deleted",
                            combo.name).getString());
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private boolean headerButtonClicked(double mouseX, double mouseY, int button) {
        return clickHeaderButton(backButton, mouseX, mouseY, button)
                || clickHeaderButton(addButton, mouseX, mouseY, button)
                || clickHeaderButton(clearAllButton, mouseX, mouseY, button);
    }

    private boolean clickHeaderButton(MCButton button, double mouseX, double mouseY, int mouseButton) {
        return button != null && button.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean handleMappingPickerClick(double mouseX, double mouseY) {
        int bw = 420;
        int bh = Math.min(360, height - 80);
        int bx = (width - bw) / 2;
        int by = (height - bh) / 2;
        int listX = bx + 12;
        int listY = by + 32;
        int listW = bw - 24;
        int listH = bh - 56;
        if (mouseX < listX || mouseX >= listX + listW || mouseY < listY || mouseY >= listY + listH) {
            return false;
        }
        List<MappingPickRow> mappings = capture.filteredMappings();
        int rowH = 16;
        int relY = (int) (mouseY - listY - 4);
        int idx = relY / rowH;
        if (idx < 0) return true;
        int real = capture.scrollOffset + idx;
        if (real >= 0 && real < mappings.size()) {
            capture.mapping = mappings.get(real).mapping;
            capture.stage = CaptureStage.AWAITING_FIRST;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (capture != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capture = null;
                return true;
            }
            if (capture.stage == CaptureStage.SELECT_MAPPING) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !capture.search.isEmpty()) {
                    capture.search = capture.search.substring(0, capture.search.length() - 1);
                    capture.scrollOffset = 0;
                }
                return true;
            }
            onKeyCaptured(InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (mappingSearchBox != null && mappingSearchBox.isFocused()) {
                if (!mappingSearchBox.getValue().isEmpty()) {
                    mappingSearchBox.setValue("");
                    return true;
                }
                mappingSearchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        if (mappingSearchBox != null && mappingSearchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        applyFixedScaleMetrics();
        if (capture != null) {
            if (capture.stage == CaptureStage.SELECT_MAPPING && codePoint >= 32) {
                capture.search += codePoint;
                capture.scrollOffset = 0;
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (capture != null && capture.stage == CaptureStage.SELECT_MAPPING) {
            capture.scrollOffset = Math.max(0, capture.scrollOffset - (int) Math.signum(scrollY));
            return true;
        }
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * ROW_H * 2), 0,
                Math.max(0, totalListH - listHeight()));
        return true;
    }

    private void onKeyCaptured(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            capture = null;
            return;
        }
        if (capture.firstKey == null) {
            capture.firstKey = key;
            return;
        }
        if (key.getValue() == capture.firstKey.getValue() && key.getType() == capture.firstKey.getType()) {
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.combo.distinct_required").getString());
            return;
        }
        store.putCombo(capture.mapping, capture.firstKey, key);
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.combo.saved",
                KeybindComboStore.describeMapping(capture.mapping.getName()),
                capture.firstKey.getDisplayName().getString() + " + " + key.getDisplayName().getString()).getString());
        capture = null;
        rebuildRows();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }


    private enum CaptureStage { SELECT_MAPPING, AWAITING_FIRST }

    private final class CaptureState {
        CaptureStage stage = CaptureStage.SELECT_MAPPING;
        KeyMapping mapping;
        InputConstants.Key firstKey;
        String search = "";
        int scrollOffset;

        List<MappingPickRow> filteredMappings() {
            List<MappingPickRow> all = new ArrayList<>();
            String q = search.toLowerCase(Locale.ROOT);
            for (MappingPickRow row : KeybindComboManageScreen.this.mappingPickRows()) {
                if (q.isBlank()
                        || row.nameLower.contains(q)
                        || row.mappingNameLower.contains(q)) {
                    all.add(row);
                }
            }
            return all;
        }

        String titleMessage() {
            if (stage == CaptureStage.SELECT_MAPPING) {
                return pickerTitleText;
            }
            return captureTitleText;
        }

        String hintMessage() {
            if (stage == CaptureStage.SELECT_MAPPING) {
                return pickerHintText;
            }
            return cancelHintText;
        }
    }

    private static final class ComboRow {
        final String mappingName;
        final String secondKey;
        final String name;
        final String comboLabel;
        final String searchable;

        private ComboRow(String mappingName, String secondKey, String name, String comboLabel, String searchable) {
            this.mappingName = mappingName;
            this.secondKey = secondKey;
            this.name = name;
            this.comboLabel = comboLabel;
            this.searchable = searchable;
        }

        static ComboRow of(KeybindComboStore.ComboBinding combo) {
            String mappingName = combo.mappingName;
            String name = KeybindComboStore.describeMapping(mappingName);
            String comboLabel = combo.comboLabel();
            String searchable = (name + "\n" + String.valueOf(mappingName) + "\n" + comboLabel)
                    .toLowerCase(Locale.ROOT);
            return new ComboRow(mappingName, combo.secondKey, name, comboLabel, searchable);
        }

        boolean matches(String query) {
            return searchable.contains(query);
        }
    }

    private record MappingPickRow(KeyMapping mapping, String mappingName, String mappingNameLower,
                                  String name, String nameLower, String currentKey) {
        static MappingPickRow of(KeyMapping mapping) {
            String mappingName = mapping.getName();
            String name = Component.translatable(mappingName).getString();
            return new MappingPickRow(mapping, mappingName, mappingName.toLowerCase(Locale.ROOT),
                    name, name.toLowerCase(Locale.ROOT), mapping.getTranslatedKeyMessage().getString());
        }
    }
}
