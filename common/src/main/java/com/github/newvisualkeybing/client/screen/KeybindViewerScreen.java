package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.FilterTab;
import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class KeybindViewerScreen extends FixedScaleScreen {

    private static final int HEADER_H = 36;
    private static final int TOOLBAR_H = 32;
    private static final int STATUS_H = 24;
    private static final int CHROME_GAP = 8;
    private static final int SEARCH_W_DEFAULT = 220;
    private static final int SEARCH_BH = 20;

    private static final int BODY_PAD = 8;
    private static final int COL_GAP = 8;
    private static final int MOD_PANEL_W = 168;
    private static final int MOUSE_PANEL_W = 136;
    private static final int DETAIL_PANEL_W = 216;
    private static final int COMPACT_WIDTH_THRESHOLD = 760;
    private static final int RIGHT_RAIL_STACK_THRESHOLD = 980;
    private static final int MOUSE_PANEL_STACK_H = 160;
    private static final float FIXED_KEY_UNIT = 30.0f;

    private static final int PANEL_PAD = 12;
    private static final int PANEL_RADIUS = 8;
    private static final int PANEL_TITLE_Y = 10;
    private static final int PANEL_CONTENT_TOP = 28;
    private static final int ACTION_BTN_H = 22;
    private static final int ACTION_BTN_GAP = 8;

    private final Screen parent;
    private final KeyBindingScanner scanner = new KeyBindingScanner();
    private final KeybindTooltipRenderer tooltipRenderer = new KeybindTooltipRenderer(scanner);
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();
    private final KeybindViewerConfig viewerConfig = KeybindViewerConfig.global();
    private final KeybindProfilePanel profilePanel = new KeybindProfilePanel(
            profileStore, this::onProfileMutation, this::showNotice, this::releaseSearchFocus);
    private final KeybindKeyboardRenderer keyboardRenderer = new KeybindKeyboardRenderer(scanner);
    private final KeybindMouseRenderer mouseRenderer = new KeybindMouseRenderer(scanner);
    private final KeybindDetailPanel detailPanel = new KeybindDetailPanel(scanner, profileStore);
    private final KeybindQuickEditPopover quickEdit = new KeybindQuickEditPopover(scanner, this::showNotice, this::onPriorityMutation);

    private EditBox searchBox;
    private MCButton closeButton;
    private MCButton manageButton;
    private MCButton comboButton;
    private MCButton modToggleButton;
    private MCButton profileToggleButton;
    private MCButton layoutButton;

    private KeyboardLayoutData.Style currentStyle = KeybindViewerConfig.global().defaultLayoutStyle();

    private String noticeMsg;
    private long noticeTime;
    private float animTick;
    private Integer tooltipHoverKey;
    private long tooltipHoverStartMs;
    private static final long TOOLTIP_DELAY_MS = 80;
    private static final long TOOLTIP_FADE_MS = 90;

    private FilterTab activeFilter = FilterTab.ALL;
    private Set<Integer> textFilteredKeys;
    private Set<Integer> tabFilteredKeys;
    private Set<Integer> modFilteredKeys;
    private boolean filtersDirty = true;
    private Integer selectedVirtualKey;
    private Integer hoveredVirtualKey;
    private boolean modPanelOpen;
    private boolean profilePanelOpen;
    private String selectedModId;
    private String modSearchQuery = "";
    private int modScrollOffset;

    private float keyScale;
    private int keyboardX;
    private int keyboardY;
    private int cachedLayoutWidth = -1;
    private int cachedLayoutHeight = -1;
    private KeyboardLayoutData.Style cachedLayoutStyle;
    private boolean cachedLayoutModOpen;
    private int mousePanelX;
    private int mousePanelY;
    private int mousePanelH;
    private int detailPanelX;
    private int detailPanelY;
    private int detailPanelH;
    private int detailPanelW = DETAIL_PANEL_W;
    private int mousePanelW = MOUSE_PANEL_W;
    private boolean rightRailStacked;
    private int contentTop;
    private int contentBottom;
    private int keyboardInfoTopY;
    private int keyboardInfoTopH;
    private int keyboardInfoBottomY;
    private int keyboardInfoBottomH;

    private int toolbarTabsX;
    private int toolbarTabsW;
    private int toolbarSearchX;
    private int toolbarSearchW;
    private int toolbarLegendX;
    private long cachedModEntriesVersion = -1L;
    private String cachedModEntriesQuery;
    private List<Map.Entry<String, String>> cachedModEntries = List.of();
    private long cachedModRowsVersion = -1L;
    private String cachedModRowsQuery;
    private int cachedModRowsWidth = -1;
    private List<ModRow> cachedModRows = List.of();
    private final String[] tabLabels = new String[FilterTab.values().length];
    private final int[] tabWidths = new int[FilterTab.values().length];
    private final String[] legendLabels = new String[5];
    private final int[] legendLabelWidths = new int[5];
    private String hintLabel;
    private String modPanelTitle;
    private String modSearchPlaceholder;
    private String clearModLabel;

    public KeybindViewerScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.viewer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        applyFixedScaleMetrics();
        UITheme.setMode(UITheme.Mode.DARK);
        scanner.scan();
        refreshTextCache();

        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        if (compact) {
            modPanelOpen = false;
            profilePanelOpen = false;
        }

        int btnH = 22;
        int btnGap = 4;
        int btnY = (HEADER_H - btnH) / 2;
        int btnCloseW = 50;
        int btnModsW = compact ? 38 : 56;
        int btnProfilesW = compact ? 52 : 68;
        int btnManageW = compact ? 48 : 64;
        int btnComboW = compact ? 56 : 72;
        int btnLayoutW = compact ? 56 : 78;

        int xClose = width - 8 - btnCloseW;
        int xMods = xClose - btnGap - btnModsW;
        int xProfiles = xMods - btnGap - btnProfilesW;
        int xManage = xProfiles - btnGap - btnManageW;
        int xCombo = xManage - btnGap - btnComboW;
        int xLayout = xCombo - btnGap - btnLayoutW;

        computeToolbarGeometry(compact);

        int searchBoxY = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2;
        searchBox = new EditBox(font, toolbarSearchX, searchBoxY, toolbarSearchW, SEARCH_BH,
                Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setHint(Component.translatable("screen.newvisualkeybing.viewer.search"));
        searchBox.setResponder(value -> markFiltersDirty());
        addRenderableWidget(searchBox);

        layoutButton = MCButton.create(xLayout, btnY, btnLayoutW, btnH,
                layoutLabel(currentStyle), button -> {
            if (Screen.hasShiftDown()) {
                viewerConfig.setDefaultLayoutStyle(currentStyle);
                showNotice(Component.translatable("screen.newvisualkeybing.viewer.layout.default_set",
                        layoutLabel(currentStyle).getString()).getString());
                return;
            }
            currentStyle = currentStyle.next();
            button.setMessage(layoutLabel(currentStyle));
        });
        addRenderableWidget(layoutButton);

        manageButton = MCButton.create(xManage, btnY, btnManageW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.manage"),
                button -> minecraft.setScreen(new KeybindEditScreen(this)));
        addRenderableWidget(manageButton);

        comboButton = MCButton.create(xCombo, btnY, btnComboW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.combo.open"),
                button -> minecraft.setScreen(new KeybindComboManageScreen(this)));
        addRenderableWidget(comboButton);

        profileToggleButton = MCButton.create(xProfiles, btnY, btnProfilesW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.profiles"), button -> {
            profilePanelOpen = !profilePanelOpen;
            if (profilePanelOpen) modPanelOpen = false;
            invalidateLayoutCache();
        });
        addRenderableWidget(profileToggleButton);

        modToggleButton = MCButton.create(xMods, btnY, btnModsW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.mods"), button -> {
            modPanelOpen = !modPanelOpen;
            if (modPanelOpen) profilePanelOpen = false;
            invalidateLayoutCache();
        });
        addRenderableWidget(modToggleButton);

        closeButton = MCButton.create(xClose, btnY, btnCloseW, btnH,
                Component.translatable("gui.done"), button -> onClose());
        addRenderableWidget(closeButton);

        refreshFilters();
    }

    private void computeToolbarGeometry(boolean compact) {
        int tabsW = 0;
        for (int w : tabWidths) tabsW += w;
        tabsW += (tabWidths.length - 1) * 4;

        int legendW = compact
                ? 4 * 14 + 3 * 4               
                : measureLegendWidth();
        int searchW = SEARCH_W_DEFAULT;
        int outerPad = 12;
        int innerGap = 12;
        int totalAvail = width - outerPad * 2;

        if (tabsW + searchW + legendW + innerGap * 2 > totalAvail) {
            searchW = Math.max(140, totalAvail - tabsW - legendW - innerGap * 2);
        }

        toolbarTabsX = outerPad;
        toolbarTabsW = tabsW;
        toolbarSearchX = (width - searchW) / 2;
        toolbarSearchW = searchW;
        toolbarLegendX = width - outerPad - legendW;

        int afterTabs = toolbarTabsX + tabsW + innerGap;
        if (toolbarSearchX < afterTabs) toolbarSearchX = afterTabs;
    }

    private int measureLegendWidth() {
        int total = 0;
        for (int i = 0; i < legendLabels.length; i++) {
            total += 8 + 4 + legendLabelWidths[i];
            if (i < legendLabels.length - 1) total += 10;
        }
        return total;
    }

    private void refreshTextCache() {
        TextFitCache.clear();
        FilterTab[] tabs = FilterTab.values();
        for (int i = 0; i < tabs.length; i++) {
            tabLabels[i] = tabs[i].getLabel();
            tabWidths[i] = font.width(tabLabels[i]) + 14;
        }
        legendLabels[0] = Component.translatable("screen.newvisualkeybing.viewer.legend.free").getString();
        legendLabels[1] = Component.translatable("screen.newvisualkeybing.viewer.legend.self").getString();
        legendLabels[2] = Component.translatable("screen.newvisualkeybing.viewer.legend.other").getString();
        legendLabels[3] = Component.translatable("screen.newvisualkeybing.viewer.legend.combo").getString();
        legendLabels[4] = Component.translatable("screen.newvisualkeybing.viewer.legend.conflict").getString();
        for (int i = 0; i < legendLabels.length; i++) {
            legendLabelWidths[i] = font.width(legendLabels[i]);
        }
        hintLabel = Component.translatable("screen.newvisualkeybing.viewer.hint").getString();
        modPanelTitle = Component.translatable("screen.newvisualkeybing.viewer.mods").getString();
        modSearchPlaceholder = Component.translatable("screen.newvisualkeybing.viewer.mod_search").getString();
        clearModLabel = Component.translatable("screen.newvisualkeybing.viewer.clear_mod").getString();
    }

    @Override
    public void tick() {
        super.tick();
        if (scanner.refreshIfNeeded()) {
            TextFitCache.clear();
            markFiltersDirty();
        }
        if (filtersDirty) refreshFilters();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        int fixedMouseX = fixedMouseX(mouseX);
        int fixedMouseY = fixedMouseY(mouseY);
        long nowMs = System.currentTimeMillis();
        if (filtersDirty) refreshFilters();
        animTick += partialTick;
        layoutPanels();
        hoveredVirtualKey = null;

        pushFixedScale(g);
        try {
        var c = UITheme.colors();
        g.fill(0, 0, width, height, c.panelBg() | 0xFF000000);

        renderHeaderBar(g);
        renderToolbar(g, fixedMouseX, fixedMouseY);

        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            renderModPanel(g, fixedMouseX, fixedMouseY);
        } else if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int x = BODY_PAD;
            int y = contentTop;
            int h = contentBottom - contentTop;
            profilePanel.render(g, font, x, y, h, fixedMouseX, fixedMouseY);
        }

        renderKeyboard(g, fixedMouseX, fixedMouseY, nowMs);
        renderKeyboardInfoBands(g, fixedMouseX, fixedMouseY);
        renderMousePanel(g, fixedMouseX, fixedMouseY, nowMs);
        renderDetailPanel(g, selectedVirtualKey != null ? selectedVirtualKey : hoveredVirtualKey, fixedMouseX, fixedMouseY);
        renderStatusBar(g);

        super.render(g, fixedMouseX, fixedMouseY, partialTick);

        if (hoveredVirtualKey != null
                && (selectedVirtualKey == null || hoveredVirtualKey.intValue() != selectedVirtualKey.intValue())) {
            if (tooltipHoverKey == null || hoveredVirtualKey.intValue() != tooltipHoverKey.intValue()) {
                tooltipHoverKey = hoveredVirtualKey;
                tooltipHoverStartMs = nowMs;
            }
            long elapsed = nowMs - tooltipHoverStartMs;
            if (elapsed >= TOOLTIP_DELAY_MS) {
                float progress = Math.min(1f, (elapsed - TOOLTIP_DELAY_MS) / (float) TOOLTIP_FADE_MS);
                progress = UITheme.easeOutCubic(progress);
                float dy = (1f - progress) * 8f;
                g.pose().pushPose();
                g.pose().translate(0f, dy, 0f);
                tooltipRenderer.render(g, font, width, height, hoveredVirtualKey, fixedMouseX, fixedMouseY);
                g.pose().popPose();
            }
        } else {
            tooltipHoverKey = null;
        }

        renderNotice(g, nowMs);

        quickEdit.render(g, font, width, height, fixedMouseX, fixedMouseY);
        } finally {
            popFixedScale(g);
        }
    }

    private void renderHeaderBar(GuiGraphics g) {
        var c = UITheme.colors();
        g.fill(0, 0, width, HEADER_H, c.headerBg());
        g.fill(0, HEADER_H - 1, width, HEADER_H, c.divider());
        g.fill(0, HEADER_H, width, HEADER_H + 1, UITheme.withAlpha(c.accent(), 0x70));

        g.drawString(font, title, 12, (HEADER_H - font.lineHeight) / 2, c.textPrimary(), true);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = HEADER_H;
        g.fill(0, y, width, y + TOOLBAR_H, UITheme.lerpColor(c.headerBg(), c.panelBg(), 0.30f));
        g.fill(0, y + TOOLBAR_H - 1, width, y + TOOLBAR_H, c.divider());

        renderToolbarTabs(g, mouseX, mouseY);
        renderToolbarSearchFrame(g);
        renderToolbarLegend(g, mouseX, mouseY);
    }

    private void renderToolbarTabs(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = toolbarTabsX;
        int y = HEADER_H + 4;
        int h = TOOLBAR_H - 8;
        FilterTab[] tabs = FilterTab.values();
        for (int i = 0; i < tabs.length; i++) {
            FilterTab tab = tabs[i];
            int w = tabWidths[i];
            boolean active = tab == activeFilter;
            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            int fill = active
                    ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.55f)
                    : hovered ? UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.20f) : c.widgetBg();
            UITheme.fillRoundedRectFast(g, x, y, w, h, h / 2, fill);
            UITheme.drawRoundedBorderFast(g, x, y, w, h, h / 2,
                    active ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xB0));
            g.drawString(font, tabLabels[i], x + 7, y + (h - font.lineHeight) / 2,
                    active ? 0xFFFFFFFF : c.textSecondary(), false);
            x += w + 4;
        }
    }

    private void renderToolbarSearchFrame(GuiGraphics g) {
        var c = UITheme.colors();
        int sx = toolbarSearchX - 3;
        int sy = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2 - 3;
        int sw = toolbarSearchW + 6;
        int sh = SEARCH_BH + 6;
        int focusColor = searchBox != null && searchBox.isFocused() ? c.accent() : UITheme.withAlpha(c.accent(), 0x40);
        UITheme.drawRoundedBorderFast(g, sx, sy, sw, sh, 6, focusColor);

        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            int clearSize = 12;
            int clearXLocal = sx + sw - clearSize - 4;
            int clearYLocal = sy + (sh - clearSize) / 2;
            UITheme.fillRoundedRectFast(g, clearXLocal, clearYLocal, clearSize, clearSize, 6,
                    UITheme.withAlpha(c.widgetBg(), 0xC0));
            int cx = clearXLocal + clearSize / 2;
            int cy = clearYLocal + clearSize / 2;
            g.fill(cx - 3, cy - 1, cx + 4, cy, c.textSecondary());
            g.fill(cx - 1, cy - 3, cx, cy + 4, c.textSecondary());
        }
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY) {
        if (searchBox == null || searchBox.getValue().isEmpty()) return false;
        int sx = toolbarSearchX - 3;
        int sy = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2 - 3;
        int sw = toolbarSearchW + 6;
        int sh = SEARCH_BH + 6;
        int clearSize = 12;
        int clearXLocal = sx + sw - clearSize - 4;
        int clearYLocal = sy + (sh - clearSize) / 2;
        if (mouseX >= clearXLocal && mouseX < clearXLocal + clearSize
                && mouseY >= clearYLocal && mouseY < clearYLocal + clearSize) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            this.setFocused(searchBox);
            return true;
        }
        return false;
    }

    private void renderToolbarLegend(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        int x = toolbarLegendX;
        int y = HEADER_H + (TOOLBAR_H - 12) / 2;

        int[] colors = { c.widgetBorder(), c.accent(), c.success(), c.warning(), c.danger() };

        for (int i = 0; i < legendLabels.length; i++) {
            UITheme.fillRoundedRectFast(g, x, y + 2, 8, 8, 4, colors[i]);
            x += 8;
            if (!compact) {
                x += 4;
                g.drawString(font, legendLabels[i], x, y + 2, c.textSecondary(), false);
                x += legendLabelWidths[i];
            }
            if (i < legendLabels.length - 1) x += 10;
        }
    }

    private void renderStatusBar(GuiGraphics g) {
        var c = UITheme.colors();
        int y = height - STATUS_H;
        g.fill(0, y, width, height, c.headerBg());
        g.fill(0, y, width, y + 1, c.divider());

        KeyBindingScanner.ScanStats stats = scanner.getStats();
        int chipY = y + (STATUS_H - 14) / 2;
        int textY = y + (STATUS_H - font.lineHeight) / 2;
        String scale = Component.translatable("screen.newvisualkeybing.viewer.scale", Math.round(keyScale)).getString();
        String layoutName = layoutLabel(currentStyle).getString();
        String middle = layoutName + "  |  " + scale;
        int middleW = font.width(middle);
        int middleX = (width - middleW) / 2;

        int hintW = font.width(hintLabel);
        int hintX = width - hintW - 10;
        int leftLimit = Math.max(10, Math.min(middleX - 8, hintX - 8));
        int x = 10;

        x = renderStatChip(g, x, chipY, c.widgetBorder(),
                legendLabels[0],
                stats.free(), leftLimit);
        x += 6;
        x = renderStatChip(g, x, chipY, c.accent(),
                legendLabels[1],
                stats.self(), leftLimit);
        x += 6;
        x = renderStatChip(g, x, chipY, c.success(),
                legendLabels[2],
                stats.other(), leftLimit);
        x += 6;
        x = renderStatChip(g, x, chipY, c.warning(),
                legendLabels[3],
                stats.combo(), leftLimit);
        x += 6;
        renderStatChip(g, x, chipY, c.danger(),
                legendLabels[4],
                stats.conflict(), leftLimit);

        if (middleX > x + 8 && middleX + middleW < hintX - 8) {
            g.drawString(font, middle, middleX, textY, c.textMuted(), false);
        }
        if (hintX > Math.max(x, middleX + middleW) + 8) {
            g.drawString(font, hintLabel, hintX, textY, c.textMuted(), false);
        }
    }

    private int renderStatChip(GuiGraphics g, int x, int y, int dotColor, String label, int count, int maxRight) {
        var c = UITheme.colors();
        String text = count + " " + label;
        int chipW = font.width(text) + 18;
        if (x + chipW > maxRight) return x;
        int chipH = 14;
        int fill = UITheme.lerpColor(c.widgetBg(), dotColor, 0.14f);
        UITheme.fillRoundedRectFast(g, x, y, chipW, chipH, chipH / 2, fill);
        UITheme.drawRoundedBorderFast(g, x, y, chipW, chipH, chipH / 2, UITheme.withAlpha(dotColor, 0x90));
        UITheme.fillRoundedRectFast(g, x + 5, y + (chipH - 5) / 2, 5, 5, 2, dotColor);
        g.drawString(font, text, x + 13, y + (chipH - font.lineHeight) / 2 + 1, c.textSecondary(), false);
        return x + chipW;
    }

static int paintPanelBase(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String title) {
        var c = UITheme.colors();
        UITheme.drawGlassPanel(g, x, y, w, h, PANEL_RADIUS);
        g.drawString(font, title, x + PANEL_PAD, y + PANEL_TITLE_Y, c.textPrimary(), false);
        int divY = y + PANEL_TITLE_Y + font.lineHeight + 4;
        UITheme.fillRoundedRectFast(g, x + PANEL_PAD, divY, w - PANEL_PAD * 2, 1, 1,
                UITheme.withAlpha(c.divider(), 0xA0));
        return y + PANEL_CONTENT_TOP;
    }

    private void renderModPanel(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = BODY_PAD;
        int y = contentTop;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        int contentY = paintPanelBase(g, font, x, y, w, h, modPanelTitle);

        int fieldX = x + PANEL_PAD;
        int fieldW = w - PANEL_PAD * 2;
        int searchY = contentY + 4;
        UITheme.fillRoundedRectFast(g, fieldX, searchY, fieldW, 18, 6, c.inputBg());
        UITheme.drawRoundedBorderFast(g, fieldX, searchY, fieldW, 18, 6, c.widgetBorder());
        String display = modSearchQuery.isBlank()
                ? modSearchPlaceholder
                : modSearchQuery;
        g.drawString(font, display, fieldX + 6, searchY + 5,
                modSearchQuery.isBlank() ? c.textMuted() : c.textPrimary(), false);

        int listY = searchY + 26;
        int clearY = y + h - PANEL_PAD - ACTION_BTN_H;
        int hideToggleY = clearY - ACTION_BTN_H - ACTION_BTN_GAP;
        int comboToggleY = hideToggleY - ACTION_BTN_H - ACTION_BTN_GAP;
        int listBottom = comboToggleY - ACTION_BTN_GAP;
        List<ModRow> mods = filteredModRows(fieldW);
        int rowH = 18;
        int visibleRows = Math.max(1, (listBottom - listY) / rowH);
        modScrollOffset = Mth.clamp(modScrollOffset, 0, Math.max(0, mods.size() - visibleRows));

        int rowY = listY;
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            ModRow mod = mods.get(i);
            boolean selected = mod.modId().equals(selectedModId);
            boolean hovered = inside(mouseX, mouseY, fieldX, rowY, fieldW, rowH - 1);
            int fill = selected ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.40f)
                    : hovered ? UITheme.lerpColor(c.widgetBg(), c.accentAlt(), 0.18f) : c.widgetBg();
            UITheme.fillRoundedRectFast(g, fieldX, rowY, fieldW, rowH - 1, 5, fill);
            if (!selected && !hovered && i < mods.size() - 1
                    && i < modScrollOffset + visibleRows - 1) {
                UITheme.fillRoundedRectFast(g, fieldX + 6, rowY + rowH - 2, fieldW - 12, 1, 1,
                        UITheme.withAlpha(c.divider(), 0x30));
            }
            int textY = rowY + (rowH - 1 - font.lineHeight) / 2;
            g.drawString(font, mod.name(), fieldX + 6, textY,
                    selected ? 0xFFFFFFFF : c.textSecondary(), false);
            g.drawString(font, mod.count(), fieldX + fieldW - mod.countW() - 6, textY,
                    mod.conflict() ? c.danger() : c.textMuted(), false);
            rowY += rowH;
        }

        boolean comboToggleHover = inside(mouseX, mouseY, fieldX, comboToggleY, fieldW, ACTION_BTN_H);
        String comboToggleLabel = Component.translatable(viewerConfig.comboKeysNonConflicting()
                ? "screen.newvisualkeybing.viewer.combo_non_conflict.on"
                : "screen.newvisualkeybing.viewer.combo_non_conflict.off").getString();
        renderActionButton(g, font, fieldX, comboToggleY, fieldW, ACTION_BTN_H,
                comboToggleLabel, viewerConfig.comboKeysNonConflicting() ? c.warning() : c.widgetBorder(), comboToggleHover);

        boolean toggleHover = inside(mouseX, mouseY, fieldX, hideToggleY, fieldW, ACTION_BTN_H);
        String toggleLabel = Component.translatable(viewerConfig.hideNonSelectedMod()
                ? "screen.newvisualkeybing.viewer.hide_unselected.on"
                : "screen.newvisualkeybing.viewer.hide_unselected.off").getString();
        renderActionButton(g, font, fieldX, hideToggleY, fieldW, ACTION_BTN_H,
                toggleLabel, viewerConfig.hideNonSelectedMod() ? c.accent() : c.widgetBorder(), toggleHover);

        boolean clearHover = inside(mouseX, mouseY, fieldX, clearY, fieldW, ACTION_BTN_H);
        renderActionButton(g, font, fieldX, clearY, fieldW, ACTION_BTN_H,
                clearModLabel,
                selectedModId == null ? c.widgetBorder() : c.danger(), clearHover);
    }

    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY, long nowMs) {
        Integer hover = keyboardRenderer.render(g, font, currentStyle,
                keyboardX, keyboardY, keyScale,
                selectedVirtualKey, this::isVisibleKey, this::isHiddenBySelectedMod,
                this::isSearchMatch,
                mouseX, mouseY, animTick, nowMs);
        if (hover != null) hoveredVirtualKey = hover;
    }

    private void renderKeyboardInfoBands(GuiGraphics g, int mouseX, int mouseY) {
        int kbW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        if (keyboardInfoTopH > 0) renderKeyboardTopBand(g, keyboardX, keyboardInfoTopY, kbW, keyboardInfoTopH);
        if (keyboardInfoBottomH > 0) {
            Integer key = selectedVirtualKey != null ? selectedVirtualKey : hoveredVirtualKey;
            renderKeyboardBottomBand(g, keyboardX, keyboardInfoBottomY, kbW, keyboardInfoBottomH, key);
        }
    }

    private void renderKeyboardTopBand(GuiGraphics g, int x, int y, int w, int h) {
        var c = UITheme.colors();
        UITheme.fillRoundedRectFast(g, x, y, w, h, 7, UITheme.withAlpha(c.headerBg(), 0xC4));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 7, UITheme.withAlpha(c.widgetBorder(), 0x8E));
        int textY = y + (h - font.lineHeight) / 2;
        if (selectedModId == null) {
            String text = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.no_mod").getString();
            g.drawString(font, fitToWidth(font, text, w - 18), x + 9, textY, c.textMuted(), false);
            return;
        }
        String modName = scanner.getAllRegisteredMods().getOrDefault(selectedModId, selectedModId);
        KeyBindingScanner.ModStats stats = scanner.getModStats(selectedModId);
        String left = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.mod",
                modName, stats.inputs(), stats.bindings()).getString();
        g.drawString(font, fitToWidth(font, left, Math.max(80, w / 2)), x + 9, textY, c.textPrimary(), false);
        String right = Component.translatable(viewerConfig.hideNonSelectedMod()
                ? "screen.newvisualkeybing.viewer.keyboard_band.hidden_on"
                : "screen.newvisualkeybing.viewer.keyboard_band.hidden_off").getString();
        int rightW = font.width(right);
        if (rightW < w / 2 - 8) {
            g.drawString(font, right, x + w - rightW - 9, textY,
                    viewerConfig.hideNonSelectedMod() ? c.accentLight() : c.textMuted(), false);
        }
    }

    private void renderKeyboardBottomBand(GuiGraphics g, int x, int y, int w, int h, Integer virtualKey) {
        var c = UITheme.colors();
        UITheme.fillRoundedRectFast(g, x, y, w, h, 7, UITheme.withAlpha(c.headerBg(), 0xB8));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 7, UITheme.withAlpha(c.widgetBorder(), 0x78));
        int textY = y + (h - font.lineHeight) / 2;
        if (virtualKey == null) {
            String text = Component.translatable("screen.newvisualkeybing.viewer.hover_hint").getString();
            g.drawString(font, fitToWidth(font, text, w - 18), x + 9, textY, c.textMuted(), false);
            return;
        }
        List<KeyBindingScanner.KeyBindingInfo> bindings = scanner.getVirtualBindings(virtualKey);
        String keyLabel = scanner.getVirtualKeyLabel(virtualKey);
        int labelW = Math.min(font.width(keyLabel) + 16, Math.max(46, w / 5));
        UITheme.fillRoundedRectFast(g, x + 7, y + 4, labelW, h - 8, 5,
                UITheme.lerpColor(c.widgetBg(), statusAccentColor(scanner.getVirtualStatus(virtualKey)), 0.18f));
        g.drawString(font, fitToWidth(font, keyLabel, labelW - 8), x + 11, textY, c.textPrimary(), false);
        int curX = x + labelW + 14;
        int right = x + w - 8;
        if (bindings.isEmpty()) {
            String empty = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
            g.drawString(font, fitToWidth(font, empty, right - curX), curX, textY, c.textMuted(), false);
            return;
        }
        int max = Math.min(3, bindings.size());
        for (int i = 0; i < max && curX < right - 20; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            int chunkW = Math.min(Math.max(78, w / 4), right - curX);
            UITheme.fillRoundedRectFast(g, curX, y + 4, chunkW, h - 8, 5, UITheme.withAlpha(c.widgetBg(), 0x90));
            String text = info.modName() + " / " + info.actionName();
            g.drawString(font, fitToWidth(font, text, chunkW - 10), curX + 5, textY, c.textSecondary(), false);
            curX += chunkW + 5;
        }
        if (bindings.size() > max && curX < right - 16) {
            String more = "+" + (bindings.size() - max);
            g.drawString(font, more, curX, textY, c.textMuted(), false);
        }
    }


    private Component layoutLabel(KeyboardLayoutData.Style style) {
        return switch (style) {
            case ANSI_104    -> Component.translatable("screen.newvisualkeybing.viewer.layout.ansi_104");
            case KEYS_98     -> Component.translatable("screen.newvisualkeybing.viewer.layout.keys_98");
            case TKL_87      -> Component.translatable("screen.newvisualkeybing.viewer.layout.tkl_87");
            case COMPACT_60  -> Component.translatable("screen.newvisualkeybing.viewer.layout.compact_60");
            case MAC_FULL    -> Component.translatable("screen.newvisualkeybing.viewer.layout.mac_full");
            case MAC_COMPACT -> Component.translatable("screen.newvisualkeybing.viewer.layout.mac_compact");
        };
    }

    private void renderMousePanel(GuiGraphics g, int mouseX, int mouseY, long nowMs) {
        Integer hover = mouseRenderer.render(g, font, mousePanelX, mousePanelY, mousePanelW, mousePanelH,
                selectedVirtualKey, this::isVisibleKey, this::isHiddenBySelectedMod,
                this::isSearchMatch,
                mouseX, mouseY, animTick, nowMs);
        if (hover != null) hoveredVirtualKey = hover;
    }


    private void renderDetailPanel(GuiGraphics g, Integer virtualKey, int mouseX, int mouseY) {
        detailPanel.render(g, font, detailPanelX, detailPanelY, detailPanelW, detailPanelH,
                virtualKey, mouseX, mouseY);
    }

    static String fitToWidth(net.minecraft.client.gui.Font font, String text, int maxW) {
        return TextFitCache.fitByChars(font, text, maxW);
    }

    static void renderActionButton(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   int x, int y, int w, int h, String label, int accent, boolean hovered) {
        var c = UITheme.colors();
        String fitted = fitToWidth(font, label, w - 10);
        int fill = UITheme.lerpColor(c.widgetBg(), accent, hovered ? 0.50f : 0.26f);
        UITheme.fillRoundedRectFast(g, x, y, w, h, h / 3, fill);
        UITheme.drawRoundedBorderFast(g, x, y, w, h, h / 3, UITheme.withAlpha(accent, 0xC0));
        UITheme.fillRoundedRectFast(g, x + 1, y + 1, w - 2, 1, h / 3, UITheme.withAlpha(0xFFFFFF, hovered ? 0x18 : 0x10));
        g.drawString(font, fitted,
                x + (w - font.width(fitted)) / 2,
                y + (h - font.lineHeight) / 2,
                c.textPrimary(), false);
    }

    private void renderHoverTooltip(GuiGraphics g, int virtualKey, int mouseX, int mouseY) {
        tooltipRenderer.render(g, font, width, height, virtualKey, mouseX, mouseY);
    }


    private void renderNotice(GuiGraphics g, long nowMs) {
        if (noticeMsg == null) return;
        long elapsed = nowMs - noticeTime;
        if (elapsed > 2500) { noticeMsg = null; return; }
        float alpha = elapsed < 300 ? elapsed / 300f
                : elapsed > 2000 ? (2500f - elapsed) / 500f
                : 1f;
        var c = UITheme.colors();
        int alpha255 = Math.round(alpha * 0xE6);
        int textColor = UITheme.withAlpha(c.textPrimary(), alpha255);
        int bgColor = UITheme.withAlpha(c.headerBg(), Math.round(alpha * 0xCC));
        int accent = UITheme.withAlpha(c.accent(), Math.round(alpha * 0xC0));

        int textW = font.width(noticeMsg);
        int padX = 12;
        int padY = 6;
        int boxW = textW + padX * 2;
        int boxH = font.lineHeight + padY * 2;
        int boxX = (width - boxW) / 2;
        int boxY = height - STATUS_H - boxH - 8;

        UITheme.fillRoundedRectFast(g, boxX, boxY, boxW, boxH, 8, bgColor);
        UITheme.drawRoundedBorderFast(g, boxX, boxY, boxW, boxH, 8, accent);
        UITheme.fillRoundedRectFast(g, boxX, boxY, boxW, 2, 2, accent);
        g.drawString(font, noticeMsg, boxX + padX, boxY + padY, textColor, false);
    }

    private void showNotice(String msg) {
        this.noticeMsg = msg;
        this.noticeTime = System.currentTimeMillis();
    }

    private void onProfileMutation() {
        scanner.scan();
        refreshFilters();
    }

    private void releaseSearchFocus() {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.setFocused(false);
        }
        if (this.getFocused() == searchBox) {
            this.setFocused(null);
        }
    }

    private void onPriorityMutation() {
        scanner.scan();
        refreshFilters();
    }

    private void unbindSingleMapping(KeyBindingScanner.KeyBindingInfo info) {
        net.minecraft.client.KeyMapping target = null;
        for (net.minecraft.client.KeyMapping km : minecraft.options.keyMappings) {
            if (km.getName().equals(info.translationKey())) { target = km; break; }
        }
        if (target == null) return;
        KeybindComboStore.global().removeCombo(target.getName());
        minecraft.options.setKey(target, com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
        com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer.resetAndEnforce();
        minecraft.options.save();
        scanner.scan();
        refreshFilters();
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.notice.unbind_one",
                info.actionName()).getString());
    }

    private void changeMappingPriority(KeyBindingScanner.KeyBindingInfo info, int delta) {
        net.minecraft.client.KeyMapping target = null;
        for (net.minecraft.client.KeyMapping km : minecraft.options.keyMappings) {
            if (km.getName().equals(info.translationKey())) { target = km; break; }
        }
        if (target == null) return;
        profileStore.changePriority(target, delta);
        scanner.scan();
        refreshFilters();
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.priority.changed",
                info.actionName(), profileStore.priorityOf(target)).getString());
    }

    private void invalidateLayoutCache() {
        cachedLayoutWidth = -1;
        cachedLayoutHeight = -1;
    }

    private void layoutPanels() {
        boolean leftRailOpen = modPanelOpen || profilePanelOpen;
        if (width == cachedLayoutWidth && height == cachedLayoutHeight
                && currentStyle == cachedLayoutStyle && leftRailOpen == cachedLayoutModOpen) {
            return;
        }
        cachedLayoutWidth = width;
        cachedLayoutHeight = height;
        cachedLayoutStyle = currentStyle;
        cachedLayoutModOpen = leftRailOpen;

        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        contentTop = HEADER_H + TOOLBAR_H + CHROME_GAP;
        contentBottom = height - STATUS_H - 6;
        int bodyH = contentBottom - contentTop;

        float uiScale = Mth.clamp(Math.min(width / 980.0f, height / 560.0f), 0.78f, 1.10f);
        int detailW = Mth.clamp(Math.round(DETAIL_PANEL_W * uiScale), 188, 250);
        int mouseW = Mth.clamp(Math.round(MOUSE_PANEL_W * uiScale), 124, 154);

        rightRailStacked = width < RIGHT_RAIL_STACK_THRESHOLD || bodyH > width * 0.46f;

        if (rightRailStacked) {
            int railW = Math.max(detailW, mouseW);
            detailPanelW = railW;
            mousePanelW = railW;
            mousePanelX = width - BODY_PAD - railW;
            mousePanelY = contentTop;
            mousePanelH = Math.min(MOUSE_PANEL_STACK_H, Math.max(128, bodyH / 3));
            detailPanelX = mousePanelX;
            detailPanelY = mousePanelY + mousePanelH + COL_GAP;
            detailPanelH = Math.max(96, contentBottom - detailPanelY);
        } else {
            detailPanelW = detailW;
            mousePanelW = mouseW;
            detailPanelX = width - BODY_PAD - detailPanelW;
            detailPanelY = contentTop;
            detailPanelH = bodyH;
            mousePanelX = detailPanelX - COL_GAP - mousePanelW;
            mousePanelY = contentTop;
            mousePanelH = bodyH;
        }

        int leftRailW = profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W;
        int leftMargin = (leftRailOpen && !compact) ? BODY_PAD + leftRailW + COL_GAP : BODY_PAD;
        int keyboardLeft = leftMargin;
        int keyboardRight = (rightRailStacked ? detailPanelX : mousePanelX) - COL_GAP;
        int keyboardSpaceW = keyboardRight - keyboardLeft;
        int infoH = bodyH >= 300 && keyboardSpaceW >= 340 ? 24 : 0;
        int infoGap = infoH > 0 ? 6 : 0;
        int keyboardSpaceH = Math.max(90, bodyH - infoH * 2 - infoGap * 2);
        float widthU = currentStyle.widthU();
        float heightU = currentStyle.heightU();
        float gapW = (widthU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float gapH = (heightU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        keyScale = FIXED_KEY_UNIT;

        int kbW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        keyboardX = keyboardLeft + Math.max(0, (keyboardSpaceW - kbW) / 2);
        keyboardY = contentTop + infoH + infoGap + Math.max(0, (keyboardSpaceH - kbH) / 2);
        keyboardInfoTopY = contentTop;
        keyboardInfoTopH = infoH;
        keyboardInfoBottomY = contentBottom - infoH;
        keyboardInfoBottomH = infoH;
    }

    private void refreshFilters() {
        textFilteredKeys = scanner.filterKeys(searchBox != null ? searchBox.getValue() : "");
        tabFilteredKeys = scanner.filterByStatus(activeFilter);
        modFilteredKeys = scanner.filterByMod(selectedModId);
        filtersDirty = false;
    }

    private void markFiltersDirty() {
        filtersDirty = true;
    }

    static int pulseAccent(float animTick) {
        var c = UITheme.colors();
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.18f);
        return UITheme.lerpColor(c.accent(), c.accentLight(), pulse);
    }

    static int searchPulseColor(float animTick) {
        var c = UITheme.colors();
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.22f);
        return UITheme.lerpColor(c.success(), c.accentLight(), pulse);
    }

    static int searchPulseAlpha(float animTick) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.22f);
        return 0x50 + Math.round(pulse * 0x40);
    }

    private int statusAccentColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case COMBO -> c.warning();
            case CONFLICT -> c.danger();
        };
    }

    
    static int labelColorForStatus(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case CONFLICT -> 0xFFFFFFFF;
            case SELF -> 0xFFFFFFFF;
            case COMBO -> c.textPrimary();
            case OTHER_SINGLE, BOUND -> c.textPrimary();
            case FREE -> c.textSecondary();
        };
    }

    static int keyStatusColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBg();
            case SELF -> UITheme.lerpColor(c.widgetBg(), c.accent(), 0.68f);
            case OTHER_SINGLE, BOUND -> UITheme.lerpColor(c.widgetBg(), c.success(), 0.62f);
            case COMBO -> UITheme.lerpColor(c.widgetBg(), c.warning(), 0.68f);
            case CONFLICT -> UITheme.lerpColor(c.widgetBg(), c.danger(), 0.82f);
        };
    }

    
    static int keyStatusColor(KeyBindingScanner.KeyStatus status, boolean matched) {
        int base = keyStatusColor(status);
        if (matched) return base;
        return UITheme.withAlpha(base, 0x55);
    }

    private boolean isVisibleKey(int virtualKey) {
        return matchesFilter(textFilteredKeys, virtualKey)
                && matchesFilter(tabFilteredKeys, virtualKey)
                && matchesFilter(modFilteredKeys, virtualKey);
    }

    private boolean isSearchMatch(int virtualKey) {
        return textFilteredKeys != null && textFilteredKeys.contains(virtualKey);
    }

    private boolean isHiddenBySelectedMod(int virtualKey) {
        return selectedModId != null
                && viewerConfig.hideNonSelectedMod()
                && !scanner.hasBindingForMod(virtualKey, selectedModId);
    }

    private static boolean matchesFilter(Set<Integer> filter, int virtualKey) {
        return filter == null || filter.contains(virtualKey);
    }

    private List<Map.Entry<String, String>> filteredModEntries() {
        String query = modSearchQuery.toLowerCase(Locale.ROOT);
        long version = scanner.version();
        if (version == cachedModEntriesVersion && query.equals(cachedModEntriesQuery)) {
            return cachedModEntries;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : scanner.getAllRegisteredMods().entrySet()) {
            if (query.isBlank()
                    || entry.getValue().toLowerCase(Locale.ROOT).contains(query)
                    || entry.getKey().toLowerCase(Locale.ROOT).contains(query)) {
                entries.add(entry);
            }
        }
        cachedModEntriesVersion = version;
        cachedModEntriesQuery = query;
        cachedModEntries = entries;
        return cachedModEntries;
    }

    private List<ModRow> filteredModRows(int fieldW) {
        String query = modSearchQuery.toLowerCase(Locale.ROOT);
        long version = scanner.version();
        if (version == cachedModRowsVersion
                && query.equals(cachedModRowsQuery)
                && fieldW == cachedModRowsWidth) {
            return cachedModRows;
        }
        List<Map.Entry<String, String>> entries = filteredModEntries();
        List<ModRow> rows = new ArrayList<>(entries.size());
        int countMaxW = Math.max(30, fieldW / 2);
        for (Map.Entry<String, String> entry : entries) {
            KeyBindingScanner.ModStats stats = scanner.getModStats(entry.getKey());
            boolean conflict = stats.conflicts() > 0;
            String count = conflict
                    ? Component.translatable("screen.newvisualkeybing.viewer.mod_count_conflict",
                            stats.bindings(), stats.conflicts()).getString()
                    : Component.translatable("screen.newvisualkeybing.viewer.mod_count",
                            stats.bindings()).getString();
            count = fitToWidth(font, count, countMaxW);
            int countW = font.width(count);
            String name = fitToWidth(font, entry.getValue(), Math.max(24, fieldW - countW - 16));
            rows.add(new ModRow(entry.getKey(), name, count, countW, conflict));
        }
        cachedModRowsVersion = version;
        cachedModRowsQuery = query;
        cachedModRowsWidth = fieldW;
        cachedModRows = rows;
        return cachedModRows;
    }

    private record ModRow(String modId, String name, String count, int countW, boolean conflict) {}

    private static String trim(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 2) + "..";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (quickEdit.isOpen()) return quickEdit.mouseClicked(mouseX, mouseY, button);
        if (handleSearchClearClick(mouseX, mouseY)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        FilterTab[] tabs = FilterTab.values();
        int x = toolbarTabsX;
        int y = HEADER_H + 4;
        int h = TOOLBAR_H - 8;
        for (int i = 0; i < tabs.length; i++) {
            FilterTab tab = tabs[i];
            int w = tabWidths[i];
            if (inside(mouseX, mouseY, x, y, w, h)) {
                activeFilter = tab;
                refreshFilters();
                return true;
            }
            x += w + 4;
        }

        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            if (handleModPanelClick(mouseX, mouseY)) return true;
        } else if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int px = BODY_PAD;
            int py = contentTop;
            int ph = contentBottom - contentTop;
            if (profilePanel.mouseClicked(mouseX, mouseY, px, py, ph)) return true;
        }

        boolean wheelSelected = selectedVirtualKey != null && KeyboardLayoutData.isWheel(selectedVirtualKey);
        if (selectedVirtualKey != null && !wheelSelected) {
            KeybindDetailPanel.PriorityHit priorityHit = detailPanel.getRowPriorityHit(mouseX, mouseY);
            if (priorityHit != null) {
                changeMappingPriority(priorityHit.info(), priorityHit.delta());
                return true;
            }
            KeyBindingScanner.KeyBindingInfo rowInfo = detailPanel.getRowUnbindHit(mouseX, mouseY);
            if (rowInfo != null) {
                unbindSingleMapping(rowInfo);
                return true;
            }
        }
        if (selectedVirtualKey != null && !wheelSelected && detailPanel.isModifyHit(mouseX, mouseY)) {
            quickEdit.open(selectedVirtualKey);
            return true;
        }
        if (selectedVirtualKey != null && !wheelSelected && detailPanel.isUnbindHit(mouseX, mouseY)) {
            int virtualKey = selectedVirtualKey;
            int countBefore = (KeyboardLayoutData.isMouse(virtualKey)
                    ? scanner.getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey))
                    : scanner.getBindings(virtualKey)).size();
            KeybindEditScreen.unbindAllForVirtualKey(virtualKey);
            scanner.scan();
            refreshFilters();
            String label = scanner.getVirtualKeyLabel(virtualKey);
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.notice.unbind",
                    label, countBefore).getString());
            return true;
        }

        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            if (isHiddenBySelectedMod(key.glfwKey())) continue;
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            if (inside(mouseX, mouseY, kx, ky, kw, kh)) {
                selectedVirtualKey = key.glfwKey();
                detailPanel.resetScroll();
                return true;
            }
        }

        Integer mouseHit = mouseRenderer.hitTest(mouseX, mouseY);
        if (mouseHit != null) {
            if (isHiddenBySelectedMod(mouseHit)) return false;
            selectedVirtualKey = mouseHit;
            detailPanel.resetScroll();
            return true;
        }

        selectedVirtualKey = null;
        return false;
    }

    private boolean handleModPanelClick(double mouseX, double mouseY) {
        int x = BODY_PAD;
        int y = contentTop;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        int contentY = y + PANEL_CONTENT_TOP;
        int fieldX = x + PANEL_PAD;
        int fieldW = w - PANEL_PAD * 2;
        int searchY = contentY + 4;
        int listY = searchY + 26;
        int clearY = y + h - PANEL_PAD - ACTION_BTN_H;
        int hideToggleY = clearY - ACTION_BTN_H - ACTION_BTN_GAP;
        int comboToggleY = hideToggleY - ACTION_BTN_H - ACTION_BTN_GAP;
        int rowH = 18;
        int visibleRows = Math.max(1, (comboToggleY - ACTION_BTN_GAP - listY) / rowH);

        if (inside(mouseX, mouseY, fieldX, searchY, fieldW, 18)) {
            modSearchQuery = "";
            return true;
        }

        List<Map.Entry<String, String>> mods = filteredModEntries();
        int rowY = listY;
        for (int i = modScrollOffset; i < mods.size() && i < modScrollOffset + visibleRows; i++) {
            if (inside(mouseX, mouseY, fieldX, rowY, fieldW, rowH - 1)) {
                String modId = mods.get(i).getKey();
                selectedModId = modId.equals(selectedModId) ? null : modId;
                refreshFilters();
                return true;
            }
            rowY += rowH;
        }

        if (inside(mouseX, mouseY, fieldX, comboToggleY, fieldW, ACTION_BTN_H)) {
            boolean enabled = viewerConfig.toggleComboKeysNonConflicting();
            scanner.scan();
            refreshFilters();
            showNotice(Component.translatable(enabled
                    ? "screen.newvisualkeybing.viewer.combo_non_conflict.enabled"
                    : "screen.newvisualkeybing.viewer.combo_non_conflict.disabled").getString());
            return true;
        }

        if (inside(mouseX, mouseY, fieldX, hideToggleY, fieldW, ACTION_BTN_H)) {
            boolean enabled = viewerConfig.toggleHideNonSelectedMod();
            showNotice(Component.translatable(enabled
                    ? "screen.newvisualkeybing.viewer.hide_unselected.enabled"
                    : "screen.newvisualkeybing.viewer.hide_unselected.disabled").getString());
            return true;
        }

        if (inside(mouseX, mouseY, fieldX, clearY, fieldW, ACTION_BTN_H)) {
            selectedModId = null;
            refreshFilters();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        applyFixedScaleMetrics();
        if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD && profilePanel.charTyped(codePoint, modifiers)) return true;
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD && !searchBox.isFocused() && codePoint >= 32) {
            modSearchQuery += codePoint;
            modScrollOffset = 0;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (quickEdit.isOpen()) return quickEdit.keyPressed(keyCode, scanCode, modifiers);
        if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD && profilePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD && !searchBox.isFocused()) {
            if (keyCode == 259 && !modSearchQuery.isEmpty()) {
                modSearchQuery = modSearchQuery.substring(0, modSearchQuery.length() - 1);
                return true;
            }
            if (keyCode == 256) modSearchQuery = "";
        }
        if (keyCode == 256 && searchBox != null && searchBox.isFocused()) {
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            searchBox.setFocused(false);
            this.setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (quickEdit.isOpen()) return quickEdit.mouseScrolled(mouseX, mouseY, scrollY);
        if (selectedVirtualKey != null
                && mouseX >= detailPanelX && mouseX <= detailPanelX + detailPanelW
                && mouseY >= detailPanelY && mouseY <= detailPanelY + detailPanelH) {
            detailPanel.scroll((int) Math.signum(scrollY));
            return true;
        }
        if (mouseX >= mousePanelX && mouseX <= mousePanelX + mousePanelW
                && mouseY >= mousePanelY && mouseY <= mousePanelY + mousePanelH) {
            selectedVirtualKey = scrollY > 0 ? KeyboardLayoutData.WHEEL_UP_VIRTUAL : KeyboardLayoutData.WHEEL_DOWN_VIRTUAL;
            return true;
        }
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int x = BODY_PAD;
            if (mouseX >= x && mouseX <= x + MOD_PANEL_W) {
                int clearY = contentTop + (contentBottom - contentTop) - PANEL_PAD - ACTION_BTN_H;
                int hideToggleY = clearY - ACTION_BTN_H - ACTION_BTN_GAP;
                int comboToggleY = hideToggleY - ACTION_BTN_H - ACTION_BTN_GAP;
                int searchY = contentTop + PANEL_CONTENT_TOP + 4;
                int listY = searchY + 26;
                int visibleRows = Math.max(1, (comboToggleY - ACTION_BTN_GAP - listY) / 18);
                modScrollOffset = Mth.clamp(modScrollOffset - (int) Math.signum(scrollY), 0,
                        Math.max(0, filteredModEntries().size() - visibleRows));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
