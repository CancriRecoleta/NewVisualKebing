package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.FilterTab;
import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.client.ui.UITextureSlot;
import com.github.newvisualkeybing.client.ui.UITextureStore;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int PANEL_TITLE_Y = 10;
    private static final int PANEL_CONTENT_TOP = 28;
    private static final int ACTION_BTN_H = 22;
    private static final int ACTION_BTN_GAP = 8;
    // Collapsed right-rail panels shrink to a header-only strip with a toggle chevron.
    private static final int COLLAPSED_PANEL_H = 24;
    private static final int PANEL_TOGGLE_SIZE = 14;

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

    private MCEditBox searchBox;
    private MCButton closeButton;
    private MCButton manageButton;
    private MCButton boardButton;
    private MCButton comboButton;
    private MCButton modToggleButton;
    private MCButton profileToggleButton;
    private MCButton layoutButton;
    private MCButton skinButton;

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
    private MCEditBox modSearchBox;
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
    private boolean mousePanelCollapsed;
    private boolean detailPanelCollapsed;
    private int mouseToggleX = -1, mouseToggleY = -1;
    private int detailToggleX = -1, detailToggleY = -1;
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
    private String clearModLabel;
    // Static band labels cached once (they only change with language/font, i.e. a screen re-init),
    // so the per-frame band rendering no longer rebuilds them via Component.translatable().getString().
    private String bandNoModLabel;
    private String bandHoverHint;
    private String bandUnboundLabel;
    private String bandHiddenOnLabel;
    private String bandHiddenOffLabel;
    private String legendTitle;
    // Status-bar middle text (layout | scale) rebuilt only when the layout changes, not every frame.
    private String statusMiddleText = "";

    // ---- Layout-edit mode: drag top-level panels to reposition them (offsets persisted in config) --
    private static final String PANEL_MOUSE = "mouse";
    private static final String PANEL_DETAIL = "detail";
    private static final String PANEL_LEFT = "left";
    // Keep at least this much of a dragged panel on-screen so it can never be lost off an edge.
    private static final int PANEL_DRAG_MARGIN = 28;
    private static final float KEYBOARD_ZOOM_STEP = 0.1f;
    private boolean layoutEditMode;
    private String draggingPanel;
    private double dragGrabX;
    private double dragGrabY;
    private int dragBaseOffsetX;
    private int dragBaseOffsetY;
    // Left-rail (mod/profile) panel origin: auto-layout base plus the persisted drag offset.
    private int leftPanelX;
    private int leftPanelY;
    // Pre-offset auto-layout origins, captured each relayout so drags can clamp the stored offset
    // against the live base (keeps panels on-screen without a "drag past the edge" dead zone).
    private int mousePanelBaseX, mousePanelBaseY;
    private int detailPanelBaseX, detailPanelBaseY;
    private int leftPanelBaseX, leftPanelBaseY;
    // Status-bar interactive chips (layout-edit toggle + reset); hit rects cached during render.
    private int editToggleX = -1, editToggleY = -1, editToggleW;
    private int resetChipX = -1, resetChipY = -1, resetChipW;
    private int statusChipH;

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
        mousePanelCollapsed = viewerConfig.mousePanelCollapsed();
        detailPanelCollapsed = viewerConfig.detailPanelCollapsed();

        boolean compact = width < COMPACT_WIDTH_THRESHOLD;
        if (compact) {
            modPanelOpen = false;
            profilePanelOpen = false;
        }

        int btnH = 22;
        int btnGap = 4;
        int btnY = (HEADER_H - btnH) / 2;
        int btnCloseW = fitButtonWidth(Component.translatable("gui.done"), compact ? 44 : 50, compact ? 56 : 68);
        int btnModsW = fitButtonWidth(Component.translatable("screen.newvisualkeybing.viewer.mods"), compact ? 38 : 56, compact ? 64 : 78);
        int btnProfilesW = fitButtonWidth(Component.translatable("screen.newvisualkeybing.viewer.profiles"), compact ? 52 : 68, compact ? 78 : 94);
        int btnManageW = fitButtonWidth(Component.translatable("screen.newvisualkeybing.viewer.manage"), compact ? 48 : 64, compact ? 74 : 86);
        int btnBoardW = fitButtonWidth(Component.translatable("screen.newvisualkeybing.viewer.board.open"), compact ? 48 : 64, compact ? 74 : 90);
        int btnComboW = fitButtonWidth(Component.translatable("screen.newvisualkeybing.viewer.combo.open"), compact ? 56 : 72, compact ? 82 : 96);
        int btnLayoutW = fitButtonWidth(layoutLabel(currentStyle), compact ? 56 : 78, compact ? 86 : 104);

        int xClose = width - 8 - btnCloseW;
        int xMods = xClose - btnGap - btnModsW;
        int xProfiles = xMods - btnGap - btnProfilesW;
        int xManage = xProfiles - btnGap - btnManageW;
        int xBoard = xManage - btnGap - btnBoardW;
        int xCombo = xBoard - btnGap - btnComboW;
        int xLayout = xCombo - btnGap - btnLayoutW;
        int skinLabelW = Math.max(
                font.width(Component.translatable("screen.newvisualkeybing.viewer.skin.modern").getString()),
                Math.max(font.width(Component.translatable("screen.newvisualkeybing.viewer.skin.vanilla").getString()),
                        font.width(Component.translatable("screen.newvisualkeybing.viewer.skin.custom").getString())));
        int btnSkinW = Mth.clamp(skinLabelW + 18, compact ? 44 : 56, compact ? 72 : 94);
        int xSkin = xLayout - btnGap - btnSkinW;

        computeToolbarGeometry(compact, xSkin);

        int searchBoxY = HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2;
        searchBox = new MCEditBox(font, toolbarSearchX, searchBoxY, toolbarSearchW, SEARCH_BH,
                Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withClearAffordance(true);
        searchBox.setResponder(value -> markFiltersDirty());
        addRenderableWidget(searchBox);

        // Mod-panel search uses the same MCEditBox paradigm as the toolbar search and the profile
        // name box, so all three share one focus model. Positioned/shown during mod-panel render.
        modSearchBox = new MCEditBox(font, BODY_PAD + PANEL_PAD, contentTop + PANEL_CONTENT_TOP + 4,
                MOD_PANEL_W - PANEL_PAD * 2, 18,
                Component.translatable("screen.newvisualkeybing.viewer.mod_search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.mod_search"))
                .withClearAffordance(true);
        modSearchBox.setResponder(value -> modScrollOffset = 0);
        modSearchBox.setVisible(false);
        addRenderableWidget(modSearchBox);

        // The profile name box is also an MCEditBox child, sharing the same focus model.
        addRenderableWidget(profilePanel.createNameBox(font));

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

        skinButton = MCButton.create(xSkin, btnY, btnSkinW, btnH,
                skinLabel(), button -> {
            UITextureStore store = UITextureStore.global();
            if (Screen.hasShiftDown()) {
                // Shift+click: enter Custom and cycle to the next discovered texture pack (folder/zip).
                UITheme.setSkin(UITheme.Skin.CUSTOM);
                viewerConfig.setUiSkin(UITheme.Skin.CUSTOM);
                button.setMessage(skinLabel());
                store.ensureLoaded(viewerConfig.uiTexturePack());
                String nextPack = store.nextPackId();
                if (nextPack != null) viewerConfig.setUiTexturePack(nextPack);
                store.reload(nextPack != null ? nextPack : viewerConfig.uiTexturePack());
                showSkinPackNotice(store);
                return;
            }
            UITheme.Skin next = switch (UITheme.getSkin()) {
                case MODERN -> UITheme.Skin.VANILLA;
                case VANILLA -> UITheme.Skin.CUSTOM;
                case CUSTOM -> UITheme.Skin.MODERN;
            };
            UITheme.setSkin(next);
            viewerConfig.setUiSkin(next);
            button.setMessage(skinLabel());
            if (next == UITheme.Skin.CUSTOM) {
                store.reload(viewerConfig.uiTexturePack());
                showSkinPackNotice(store);
            } else {
                showNotice(skinLabel().getString());
            }
        });
        addRenderableWidget(skinButton);

        manageButton = MCButton.create(xManage, btnY, btnManageW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.manage"),
                button -> minecraft.setScreen(new KeybindEditScreen(this)));
        addRenderableWidget(manageButton);

        boardButton = MCButton.create(xBoard, btnY, btnBoardW, btnH,
                Component.translatable("screen.newvisualkeybing.viewer.board.open"),
                button -> minecraft.setScreen(new KeybindBindBoardScreen(this)));
        addRenderableWidget(boardButton);

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

    private void computeToolbarGeometry(boolean compact, int headerButtonLeft) {
        int tabsW = 0;
        for (int w : tabWidths) tabsW += w;
        tabsW += (tabWidths.length - 1) * 4;

        int outerPad = 12;
        int innerGap = 12;
        int totalAvail = width - outerPad * 2;
        int titleReservedRight = Math.max(170, Math.min(headerButtonLeft - 12, width / 3));
        int searchW = Math.min(SEARCH_W_DEFAULT, Math.max(140, width - titleReservedRight - outerPad * 2));

        if (tabsW + searchW + innerGap > totalAvail) {
            searchW = Math.max(140, totalAvail - tabsW - innerGap);
        }

        toolbarTabsX = outerPad;
        toolbarTabsW = tabsW;
        int afterTabs = toolbarTabsX + tabsW + innerGap;
        toolbarSearchX = Math.max(afterTabs, (width - searchW) / 2);
        toolbarSearchW = searchW;
        int maxRight = width - outerPad;
        if (toolbarSearchX + toolbarSearchW > maxRight) {
            toolbarSearchW = Math.max(120, maxRight - toolbarSearchX);
        }
    }

    private int fitButtonWidth(Component text, int minW, int maxW) {
        return Mth.clamp(font.width(text) + 18, minW, maxW);
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
        clearModLabel = Component.translatable("screen.newvisualkeybing.viewer.clear_mod").getString();
        bandNoModLabel = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.no_mod").getString();
        bandHoverHint = Component.translatable("screen.newvisualkeybing.viewer.hover_hint").getString();
        bandUnboundLabel = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
        bandHiddenOnLabel = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.hidden_on").getString();
        bandHiddenOffLabel = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.hidden_off").getString();
        legendTitle = Component.translatable("screen.newvisualkeybing.viewer.legend.title").getString();
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
    public void renderBackground(GuiGraphics g) {
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
        if (!(UITheme.custom() && UITextureStore.global().draw(UITextureSlot.BACKGROUND, g, 0, 0, width, height))) {
            g.fill(0, 0, width, height, c.panelBg() | 0xFF000000);
        }

        renderHeaderBar(g);
        renderToolbar(g, fixedMouseX, fixedMouseY);

        boolean modSearchVisible = modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD;
        boolean profileVisible = profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD;
        blurIfHidden(modSearchBox, modSearchVisible);
        blurIfHidden(profilePanel.nameBox(), profileVisible);
        if (modSearchBox != null) modSearchBox.setVisible(modSearchVisible);
        if (profilePanel.nameBox() != null) profilePanel.nameBox().setVisible(profileVisible);

        if (modSearchVisible) {
            renderModPanel(g, fixedMouseX, fixedMouseY);
        } else if (profileVisible) {
            int x = leftPanelX;
            int y = leftPanelY;
            int h = contentBottom - contentTop;
            profilePanel.render(g, font, x, y, h, fixedMouseX, fixedMouseY);
        }

        renderKeyboard(g, fixedMouseX, fixedMouseY, nowMs);
        renderKeyboardInfoBands(g, fixedMouseX, fixedMouseY);
        renderMousePanel(g, fixedMouseX, fixedMouseY, nowMs);
        renderDetailPanel(g, selectedVirtualKey != null ? selectedVirtualKey : hoveredVirtualKey, fixedMouseX, fixedMouseY);
        renderStatusBar(g, fixedMouseX, fixedMouseY);
        renderLayoutEditOverlay(g, fixedMouseX, fixedMouseY);

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

        int titleRight = skinButton != null ? skinButton.getX() - 10
                : layoutButton == null ? width - 10 : layoutButton.getX() - 10;
        String fittedTitle = fitToWidth(font, title.getString(), Math.max(40, titleRight - 12));
        g.drawString(font, fittedTitle, 12, (HEADER_H - font.lineHeight) / 2, c.textPrimary(), true);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = HEADER_H;
        g.fill(0, y, width, y + TOOLBAR_H, UITheme.lerpColor(c.headerBg(), c.panelBg(), 0.30f));
        g.fill(0, y + TOOLBAR_H - 1, width, y + TOOLBAR_H, c.divider());

        renderToolbarTabs(g, mouseX, mouseY);
        renderToolbarSearchFrame(g);
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
            int tabR = UITheme.pill(w, h);
            UITheme.fillRoundedRectFast(g, x, y, w, h, tabR, fill);
            UITheme.drawRoundedBorderFast(g, x, y, w, h, tabR,
                    active ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0xB0));
            g.drawString(font, tabLabels[i], x + 7, y + (h - font.lineHeight) / 2,
                    active ? 0xFFFFFFFF : c.textSecondary(), false);
            x += w + 4;
        }
    }

    private void renderToolbarSearchFrame(GuiGraphics g) {
        if (searchBox != null) {
            searchBox.setX(toolbarSearchX);
            searchBox.setY(HEADER_H + (TOOLBAR_H - SEARCH_BH) / 2);
            searchBox.setWidth(toolbarSearchW);
            searchBox.setHeight(SEARCH_BH);
        }
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY) {
        if (searchBox == null || searchBox.getValue().isEmpty()) return false;
        if (searchBox.clearAffordanceClicked(mouseX, mouseY)) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            this.setFocused(searchBox);
            return true;
        }
        return false;
    }

    private void renderStatusBar(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = height - STATUS_H;
        g.fill(0, y, width, height, c.headerBg());
        g.fill(0, y, width, y + 1, c.divider());

        int textY = y + (STATUS_H - font.lineHeight) / 2;

        // Right-aligned interactive chips: layout-edit toggle, plus a reset chip while editing.
        statusChipH = STATUS_H - 8;
        int chipY = y + (STATUS_H - statusChipH) / 2;
        int rightEdge = width - 8;

        String editLabel = Component.translatable("screen.newvisualkeybing.viewer.layout_edit.toggle").getString();
        editToggleW = font.width(editLabel) + 16;
        editToggleX = rightEdge - editToggleW;
        editToggleY = chipY;
        renderStatusChip(g, editToggleX, chipY, editToggleW, statusChipH, editLabel, layoutEditMode,
                inside(mouseX, mouseY, editToggleX, chipY, editToggleW, statusChipH));
        int chipsLeft = editToggleX;

        if (layoutEditMode) {
            String resetLabel = Component.translatable("screen.newvisualkeybing.viewer.layout_edit.reset_chip").getString();
            resetChipW = font.width(resetLabel) + 16;
            resetChipX = editToggleX - 6 - resetChipW;
            resetChipY = chipY;
            boolean canReset = viewerConfig.hasPanelOffsets();
            renderStatusChip(g, resetChipX, chipY, resetChipW, statusChipH, resetLabel, false,
                    canReset && inside(mouseX, mouseY, resetChipX, chipY, resetChipW, statusChipH));
            chipsLeft = resetChipX;
        } else {
            resetChipX = -1;
        }

        // Hint text sits to the left of the chips when there is room.
        int hintW = font.width(hintLabel);
        int hintX = chipsLeft - 8 - hintW;
        boolean hintVisible = hintX > 10;
        if (hintVisible) {
            g.drawString(font, hintLabel, hintX, textY, c.textMuted(), false);
        }

        // Centered status text (layout | key px | UI scale | keyboard zoom) when it clears the sides.
        String middle = statusMiddleText;
        int middleW = font.width(middle);
        int middleX = (width - middleW) / 2;
        int rightBound = hintVisible ? hintX - 8 : chipsLeft - 8;
        if (middleX > 10 && middleX + middleW < rightBound) {
            g.drawString(font, middle, middleX, textY, c.textMuted(), false);
        }
    }

    /** A small pill used for the status-bar layout-edit and reset affordances. */
    private void renderStatusChip(GuiGraphics g, int x, int y, int w, int h,
                                  String label, boolean active, boolean hover) {
        var c = UITheme.colors();
        int accent = active ? c.accent() : c.widgetBorder();
        float blend = active ? 0.55f : hover ? 0.42f : 0.24f;
        int chipR = UITheme.pill(w, h);
        UITheme.fillRoundedRectFast(g, x, y, w, h, chipR, UITheme.lerpColor(c.widgetBg(), accent, blend));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, chipR,
                UITheme.withAlpha(accent, hover || active ? 0xE0 : 0xA0));
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2,
                active ? 0xFFFFFFFF : c.textSecondary(), false);
    }

    /** Highlights the draggable panels and draws grip handles while layout-edit mode is active. */
    private void renderLayoutEditOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (!layoutEditMode) return;
        var c = UITheme.colors();
        for (PanelRect rect : draggablePanelRects()) {
            boolean active = rect.id().equals(draggingPanel);
            boolean hover = inside(mouseX, mouseY, rect.x(), rect.y(), rect.w(), rect.h());
            int border = active ? c.accentLight()
                    : hover ? c.accent() : UITheme.withAlpha(c.accent(), 0xB0);
            int panelR = UITheme.radius(UITheme.Shape.MD, rect.w(), rect.h());
            UITheme.drawRoundedBorder(g, rect.x() - 1, rect.y() - 1, rect.w() + 2, rect.h() + 2,
                    panelR, border);
            int gripW = 18;
            int gripH = 8;
            int gx = rect.x() + (rect.w() - gripW) / 2;
            int gy = rect.y() + 2;
            UITheme.fillRoundedRectFast(g, gx, gy, gripW, gripH,
                    UITheme.radius(UITheme.Shape.XS, gripW, gripH), UITheme.withAlpha(c.accent(), 0xCC));
            for (int i = 0; i < 2; i++) {
                g.fill(gx + 4, gy + 3 + i * 2, gx + gripW - 4, gy + 4 + i * 2,
                        UITheme.withAlpha(0xFFFFFF, 0x90));
            }
        }
    }

static int paintPanelBase(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h, String title) {
        var c = UITheme.colors();
        UITheme.drawGlassPanel(g, x, y, w, h, UITheme.radius(UITheme.Shape.MD, w, h));
        g.drawString(font, title, x + PANEL_PAD, y + PANEL_TITLE_Y, c.textPrimary(), false);
        int divY = y + PANEL_TITLE_Y + font.lineHeight + 4;
        UITheme.fillRoundedRectFast(g, x + PANEL_PAD, divY, w - PANEL_PAD * 2, 1, 1,
                UITheme.withAlpha(c.divider(), 0xA0));
        return y + PANEL_CONTENT_TOP;
    }

    private void renderModPanel(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = leftPanelX;
        int y = leftPanelY;
        int w = MOD_PANEL_W;
        int h = contentBottom - contentTop;
        int contentY = paintPanelBase(g, font, x, y, w, h, modPanelTitle);

        int fieldX = x + PANEL_PAD;
        int fieldW = w - PANEL_PAD * 2;
        int searchY = contentY + 4;
        // The search field is a real MCEditBox (rendered by super.render); just position it here.
        if (modSearchBox != null) {
            modSearchBox.setX(fieldX);
            modSearchBox.setY(searchY);
            modSearchBox.setWidth(fieldW);
            modSearchBox.setHeight(18);
        }

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
            UITheme.fillRoundedRectFast(g, fieldX, rowY, fieldW, rowH - 1,
                    UITheme.radius(UITheme.Shape.SM, fieldW, rowH - 1), fill);
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
        int bandR = UITheme.radius(UITheme.Shape.SM, w, h);
        UITheme.fillRoundedRectFast(g, x, y, w, h, bandR, UITheme.withAlpha(c.headerBg(), 0xC4));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, bandR, UITheme.withAlpha(c.widgetBorder(), 0x8E));
        int textY = y + (h - font.lineHeight) / 2;
        if (selectedModId == null) {
            // No mod selected: use the band as a persistent color legend so the key colors are
            // always decodable, instead of a bland "no mod" line.
            renderInlineLegend(g, x + 9, y, w - 18, h);
            return;
        }
        String modName = scanner.getAllRegisteredMods().getOrDefault(selectedModId, selectedModId);
        KeyBindingScanner.ModStats stats = scanner.getModStats(selectedModId);
        String left = Component.translatable("screen.newvisualkeybing.viewer.keyboard_band.mod",
                modName, stats.inputs(), stats.bindings()).getString();
        g.drawString(font, fitToWidth(font, left, Math.max(80, w / 2)), x + 9, textY, c.textPrimary(), false);
        String right = viewerConfig.hideNonSelectedMod() ? bandHiddenOnLabel : bandHiddenOffLabel;
        int rightW = font.width(right);
        if (rightW < w / 2 - 8) {
            g.drawString(font, right, x + w - rightW - 9, textY,
                    viewerConfig.hideNonSelectedMod() ? c.accentLight() : c.textMuted(), false);
        }
    }

    /**
     * Draws the key-status color legend inline (swatch + label per entry), stopping once it would
     * overflow {@code w}. Used in the top band when no mod is selected so the colors stay decodable.
     */
    private void renderInlineLegend(GuiGraphics g, int x, int y, int w, int h) {
        var c = UITheme.colors();
        final int sw = 8;          // swatch edge
        final int gap = 4;         // swatch -> label
        final int itemGap = 12;    // item -> item
        int cy = y + (h - font.lineHeight) / 2;
        int swatchY = y + (h - sw) / 2;
        int right = x + w;
        int cx = x;
        int[] cols = { c.widgetBorderHover(), c.accent(), c.success(), c.warning(), c.danger() };
        if (legendTitle != null && !legendTitle.isEmpty()) {
            int tw = font.width(legendTitle);
            if (cx + tw + 10 < right) {
                g.drawString(font, legendTitle, cx, cy, c.textMuted(), false);
                cx += tw + 10;
            }
        }
        for (int i = 0; i < legendLabels.length; i++) {
            int itemW = sw + gap + legendLabelWidths[i];
            if (cx + itemW > right) break;
            int swR = UITheme.radius(UITheme.Shape.XS, sw, sw);
            UITheme.fillRoundedRectFast(g, cx, swatchY, sw, sw, swR, cols[i]);
            UITheme.drawRoundedBorderFast(g, cx, swatchY, sw, sw, swR, UITheme.withAlpha(c.widgetBorder(), 0xC0));
            g.drawString(font, legendLabels[i], cx + sw + gap, cy, c.textSecondary(), false);
            cx += itemW + itemGap;
        }
    }

    private void renderKeyboardBottomBand(GuiGraphics g, int x, int y, int w, int h, Integer virtualKey) {
        var c = UITheme.colors();
        int bandR = UITheme.radius(UITheme.Shape.SM, w, h);
        UITheme.fillRoundedRectFast(g, x, y, w, h, bandR, UITheme.withAlpha(c.headerBg(), 0xB8));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, bandR, UITheme.withAlpha(c.widgetBorder(), 0x78));
        int textY = y + (h - font.lineHeight) / 2;
        if (virtualKey == null) {
            g.drawString(font, fitToWidth(font, bandHoverHint, w - 18), x + 9, textY, c.textMuted(), false);
            return;
        }
        List<KeyBindingScanner.KeyBindingInfo> bindings = scanner.getVirtualBindings(virtualKey);
        String keyLabel = scanner.getVirtualKeyLabel(virtualKey);
        int labelW = Math.min(font.width(keyLabel) + 16, Math.max(46, w / 5));
        int chipR = UITheme.radius(UITheme.Shape.SM, labelW, h - 8);
        UITheme.fillRoundedRectFast(g, x + 7, y + 4, labelW, h - 8, chipR,
                UITheme.lerpColor(c.widgetBg(), statusAccentColor(scanner.getVirtualStatus(virtualKey)), 0.18f));
        g.drawString(font, fitToWidth(font, keyLabel, labelW - 8), x + 11, textY, c.textPrimary(), false);
        int curX = x + labelW + 14;
        int right = x + w - 8;
        if (bindings.isEmpty()) {
            g.drawString(font, fitToWidth(font, bandUnboundLabel, right - curX), curX, textY, c.textMuted(), false);
            return;
        }
        int max = Math.min(3, bindings.size());
        for (int i = 0; i < max && curX < right - 20; i++) {
            KeyBindingScanner.KeyBindingInfo info = bindings.get(i);
            int chunkW = Math.min(Math.max(78, w / 4), right - curX);
            UITheme.fillRoundedRectFast(g, curX, y + 4, chunkW, h - 8,
                    UITheme.radius(UITheme.Shape.SM, chunkW, h - 8), UITheme.withAlpha(c.widgetBg(), 0x90));
            String text = info.modName() + " / " + info.actionName();
            g.drawString(font, fitToWidth(font, text, chunkW - 10), curX + 5, textY, c.textSecondary(), false);
            curX += chunkW + 5;
        }
        if (bindings.size() > max && curX < right - 16) {
            String more = "+" + (bindings.size() - max);
            g.drawString(font, more, curX, textY, c.textMuted(), false);
        }
    }


    private Component skinLabel() {
        return Component.translatable(switch (UITheme.getSkin()) {
            case VANILLA -> "screen.newvisualkeybing.viewer.skin.vanilla";
            case CUSTOM -> "screen.newvisualkeybing.viewer.skin.custom";
            default -> "screen.newvisualkeybing.viewer.skin.modern";
        });
    }

    /** Notice after entering/cycling the custom skin: active pack + texture count, or the empty hint. */
    private void showSkinPackNotice(UITextureStore store) {
        if (store.loadedCount() == 0) {
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.skin.custom_empty",
                    store.directory().toString()).getString());
        } else {
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.skin.pack",
                    store.activePackName(), store.loadedCount()).getString());
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
        if (mousePanelCollapsed) {
            renderCollapsedPanel(g, mousePanelX, mousePanelY, mousePanelW,
                    Component.translatable("screen.newvisualkeybing.viewer.mouse").getString(),
                    mouseX, mouseY, true);
            return;
        }
        Integer hover = mouseRenderer.render(g, font, mousePanelX, mousePanelY, mousePanelW, mousePanelH,
                selectedVirtualKey, this::isVisibleKey, this::isHiddenBySelectedMod,
                this::isSearchMatch,
                mouseX, mouseY, animTick, nowMs);
        if (hover != null) hoveredVirtualKey = hover;
        drawPanelToggle(g, mousePanelX, mousePanelY, mousePanelW, PANEL_CONTENT_TOP, mouseX, mouseY, true, false);
    }


    private void renderDetailPanel(GuiGraphics g, Integer virtualKey, int mouseX, int mouseY) {
        if (detailPanelCollapsed) {
            renderCollapsedPanel(g, detailPanelX, detailPanelY, detailPanelW,
                    Component.translatable("screen.newvisualkeybing.viewer.details").getString(),
                    mouseX, mouseY, false);
            return;
        }
        detailPanel.render(g, font, detailPanelX, detailPanelY, detailPanelW, detailPanelH,
                virtualKey, mouseX, mouseY);
        drawPanelToggle(g, detailPanelX, detailPanelY, detailPanelW, PANEL_CONTENT_TOP, mouseX, mouseY, false, false);
    }

    /** Draws a header-only strip standing in for a collapsed right-rail panel. */
    private void renderCollapsedPanel(GuiGraphics g, int x, int y, int w, String title,
                                      int mouseX, int mouseY, boolean isMouse) {
        var c = UITheme.colors();
        UITheme.drawGlassPanel(g, x, y, w, COLLAPSED_PANEL_H,
                UITheme.radius(UITheme.Shape.MD, w, COLLAPSED_PANEL_H));
        g.drawString(font, fitToWidth(font, title, w - PANEL_PAD * 2 - PANEL_TOGGLE_SIZE - 4),
                x + PANEL_PAD, y + (COLLAPSED_PANEL_H - font.lineHeight) / 2, c.textPrimary(), false);
        drawPanelToggle(g, x, y, w, COLLAPSED_PANEL_H, mouseX, mouseY, isMouse, true);
    }

    /**
     * Top-right chevron toggle for a right-rail panel. The chevron points up when expanded
     * (click to fold away) and down when collapsed (click to unfold). The hit rect is cached so
     * {@link #mouseClicked} can route the click without re-deriving the geometry.
     */
    private void drawPanelToggle(GuiGraphics g, int x, int y, int w, int headerH,
                                 int mouseX, int mouseY, boolean isMouse, boolean collapsed) {
        var c = UITheme.colors();
        int size = PANEL_TOGGLE_SIZE;
        int tx = x + w - PANEL_PAD - size;
        int ty = y + (headerH - size) / 2;
        if (isMouse) { mouseToggleX = tx; mouseToggleY = ty; }
        else { detailToggleX = tx; detailToggleY = ty; }
        boolean hover = inside(mouseX, mouseY, tx, ty, size, size);
        int fill = hover ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.45f)
                : UITheme.withAlpha(c.widgetBg(), 0xB0);
        int toggleR = UITheme.radius(UITheme.Shape.XS, size, size);
        UITheme.fillRoundedRectFast(g, tx, ty, size, size, toggleR, fill);
        UITheme.drawRoundedBorderFast(g, tx, ty, size, size, toggleR,
                UITheme.withAlpha(c.widgetBorder(), hover ? 0xC0 : 0x80));
        drawChevron(g, tx + size / 2, ty + size / 2, collapsed, hover ? 0xFFFFFFFF : c.textSecondary());
    }

    /** Draws a small chevron centred at (cx, cy): pointing down when {@code down}, else up. */
    private static void drawChevron(GuiGraphics g, int cx, int cy, boolean down, int color) {
        int oy = down ? -1 : 1;
        for (int i = 0; i <= 3; i++) {
            int dy = down ? i : -i;
            g.fill(cx - 3 + i, cy + dy + oy, cx - 3 + i + 1, cy + dy + oy + 1, color);
            g.fill(cx + 3 - i, cy + dy + oy, cx + 3 - i + 1, cy + dy + oy + 1, color);
        }
    }

    static String fitToWidth(net.minecraft.client.gui.Font font, String text, int maxW) {
        return TextFitCache.fitByChars(font, text, maxW);
    }

    /**
     * Width for a header/toolbar button sized to its label (7px padding each side) and clamped to
     * [min, max]. Shared by the secondary screens so buttons hug their text instead of using
     * oversized fixed widths.
     */
    static int buttonWidth(net.minecraft.client.gui.Font font, Component label, int min, int max) {
        return Mth.clamp(font.width(label.getString()) + 14, min, max);
    }

    static void renderActionButton(GuiGraphics g, net.minecraft.client.gui.Font font,
                                   int x, int y, int w, int h, String label, int accent, boolean hovered) {
        var c = UITheme.colors();
        String fitted = fitToWidth(font, label, w - 10);
        int fill = UITheme.lerpColor(c.widgetBg(), accent, hovered ? 0.50f : 0.26f);
        int btnR = UITheme.radius(UITheme.Shape.SM, w, h);
        UITheme.fillRoundedRectFast(g, x, y, w, h, btnR, fill);
        UITheme.drawRoundedBorderFast(g, x, y, w, h, btnR, UITheme.withAlpha(accent, 0xC0));
        UITheme.fillRoundedRectFast(g, x + 1, y + 1, w - 2, 1, Math.max(1, btnR - 1),
                UITheme.withAlpha(0xFFFFFF, hovered ? 0x18 : 0x10));
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

        int noticeR = UITheme.radius(UITheme.Shape.SM, boxW, boxH);
        UITheme.fillRoundedRectFast(g, boxX, boxY, boxW, boxH, noticeR, bgColor);
        UITheme.drawRoundedBorderFast(g, boxX, boxY, boxW, boxH, noticeR, accent);
        UITheme.fillRoundedRectFast(g, boxX, boxY, boxW, 2, 1, accent);
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

    private void toggleConflictIgnore(KeyBindingScanner.KeyBindingInfo info) {
        net.minecraft.client.KeyMapping target = null;
        for (net.minecraft.client.KeyMapping km : minecraft.options.keyMappings) {
            if (km.getName().equals(info.translationKey())) { target = km; break; }
        }
        if (target == null) return;
        boolean ignored = profileStore.toggleConflictIgnored(target);
        scanner.scan();
        refreshFilters();
        showNotice(Component.translatable(ignored
                ? "screen.newvisualkeybing.viewer.conflict_ignore.on"
                : "screen.newvisualkeybing.viewer.conflict_ignore.off",
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
            detailPanelX = mousePanelX;
            // A collapsed panel keeps only its header strip; the freed vertical space flows to its
            // expanded sibling so the remaining panel can show more (e.g. a longer binding list).
            if (mousePanelCollapsed && detailPanelCollapsed) {
                mousePanelH = COLLAPSED_PANEL_H;
                detailPanelY = mousePanelY + COLLAPSED_PANEL_H + COL_GAP;
                detailPanelH = COLLAPSED_PANEL_H;
            } else if (mousePanelCollapsed) {
                mousePanelH = COLLAPSED_PANEL_H;
                detailPanelY = mousePanelY + COLLAPSED_PANEL_H + COL_GAP;
                detailPanelH = Math.max(60, contentBottom - detailPanelY);
            } else if (detailPanelCollapsed) {
                mousePanelH = Math.max(80, bodyH - COLLAPSED_PANEL_H - COL_GAP);
                detailPanelY = mousePanelY + mousePanelH + COL_GAP;
                detailPanelH = COLLAPSED_PANEL_H;
            } else {
                // Give the mouse diagram up to ~40% of the column (capped), but never let it push the
                // info-dense detail panel below a usable minimum or off the bottom. The old fixed-128
                // minimum could overflow the detail panel past the status bar on small canvases.
                int mouseDesired = Math.min(MOUSE_PANEL_STACK_H, Math.max(110, bodyH * 2 / 5));
                int mouseMax = Math.max(60, bodyH - COL_GAP - 80);
                mousePanelH = Math.min(mouseDesired, mouseMax);
                detailPanelY = mousePanelY + mousePanelH + COL_GAP;
                detailPanelH = Math.max(60, contentBottom - detailPanelY);
            }
        } else {
            detailPanelW = detailW;
            mousePanelW = mouseW;
            detailPanelX = width - BODY_PAD - detailPanelW;
            detailPanelY = contentTop;
            detailPanelH = detailPanelCollapsed ? COLLAPSED_PANEL_H : bodyH;
            mousePanelX = detailPanelX - COL_GAP - mousePanelW;
            mousePanelY = contentTop;
            mousePanelH = mousePanelCollapsed ? COLLAPSED_PANEL_H : bodyH;
        }

        int leftRailW = profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W;
        int leftMargin = (leftRailOpen && !compact) ? BODY_PAD + leftRailW + COL_GAP : BODY_PAD;
        int keyboardLeft = leftMargin;
        int keyboardRight = (rightRailStacked ? detailPanelX : mousePanelX) - COL_GAP;
        int keyboardSpaceW = keyboardRight - keyboardLeft;
        // Keep the info bands on whenever there is any reasonable room (the fixed 2x scale makes the
        // logical canvas small, so the old bodyH>=300 gate hid them on most non-1080p windows). Fall
        // back to a compact height when vertical space is tight, and only drop them when truly cramped.
        int infoH = keyboardSpaceW < 240 ? 0
                : bodyH >= 260 ? 24
                : bodyH >= 180 ? 18
                : 0;
        int infoGap = infoH > 0 ? 6 : 0;
        int keyboardSpaceH = Math.max(90, bodyH - infoH * 2 - infoGap * 2);
        float widthU = currentStyle.widthU();
        float heightU = currentStyle.heightU();
        float gapW = (widthU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        float gapH = (heightU - 1.0f) * KeyboardLayoutData.BASE_GAP;
        keyScale = fitKeyboardScale(keyboardSpaceW, keyboardSpaceH, widthU, heightU, gapW, gapH);

        int kbW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        keyboardX = keyboardLeft + Math.max(0, (keyboardSpaceW - kbW) / 2);
        keyboardY = contentTop + infoH + infoGap + Math.max(0, (keyboardSpaceH - kbH) / 2);
        keyboardInfoTopY = contentTop;
        keyboardInfoTopH = infoH;
        keyboardInfoBottomY = contentBottom - infoH;
        keyboardInfoBottomH = infoH;

        // Layer the user's dragged offsets on top of the responsive auto-layout. They are applied
        // AFTER the keyboard geometry above, so moving a panel never reflows the keyboard — dragged
        // panels simply float over the canvas. Each panel is then clamped so a grabbable strip always
        // stays on-screen. With no offsets (the default) every panelOffset* is 0 and clampPanel() is a
        // no-op on the in-bounds base positions, so behaviour is unchanged.
        mousePanelBaseX = mousePanelX;
        mousePanelBaseY = mousePanelY;
        detailPanelBaseX = detailPanelX;
        detailPanelBaseY = detailPanelY;
        leftPanelBaseX = BODY_PAD;
        leftPanelBaseY = contentTop;
        mousePanelX += viewerConfig.panelOffsetX(PANEL_MOUSE);
        mousePanelY += viewerConfig.panelOffsetY(PANEL_MOUSE);
        detailPanelX += viewerConfig.panelOffsetX(PANEL_DETAIL);
        detailPanelY += viewerConfig.panelOffsetY(PANEL_DETAIL);
        leftPanelX = leftPanelBaseX + viewerConfig.panelOffsetX(PANEL_LEFT);
        leftPanelY = leftPanelBaseY + viewerConfig.panelOffsetY(PANEL_LEFT);

        int mouseEffH = mousePanelCollapsed ? COLLAPSED_PANEL_H : mousePanelH;
        int detailEffH = detailPanelCollapsed ? COLLAPSED_PANEL_H : detailPanelH;
        int[] clampedMouse = clampPanel(mousePanelX, mousePanelY, mousePanelW, mouseEffH);
        mousePanelX = clampedMouse[0];
        mousePanelY = clampedMouse[1];
        int[] clampedDetail = clampPanel(detailPanelX, detailPanelY, detailPanelW, detailEffH);
        detailPanelX = clampedDetail[0];
        detailPanelY = clampedDetail[1];
        int leftRailWidth = profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W;
        int[] clampedLeft = clampPanel(leftPanelX, leftPanelY, leftRailWidth, contentBottom - contentTop);
        leftPanelX = clampedLeft[0];
        leftPanelY = clampedLeft[1];

        // Layout|scale string for the status bar; rebuilt only here (on layout change), not per frame.
        statusMiddleText = layoutLabel(currentStyle).getString() + "  |  "
                + Component.translatable("screen.newvisualkeybing.viewer.scale", Math.round(keyScale)).getString()
                + "  |  "
                + Component.translatable("screen.newvisualkeybing.viewer.ui_scale",
                        String.format(Locale.ROOT, "%.2f", viewerConfig.uiScale())).getString()
                + "  |  "
                + Component.translatable("screen.newvisualkeybing.viewer.kb_zoom",
                        Math.round(viewerConfig.keyboardZoom() * 100)).getString();
    }

    /** Clamps a panel's top-left so at least {@link #PANEL_DRAG_MARGIN}px stays inside the canvas. */
    private int[] clampPanel(int x, int y, int w, int h) {
        int aX = PANEL_DRAG_MARGIN - w;
        int bX = width - PANEL_DRAG_MARGIN;
        int aY = HEADER_H + TOOLBAR_H;
        int bY = height - STATUS_H - PANEL_DRAG_MARGIN;
        // Order both axes' bounds so a tiny canvas (where the max can dip below the min) never inverts.
        return new int[] {
                Mth.clamp(x, Math.min(aX, bX), Math.max(aX, bX)),
                Mth.clamp(y, Math.min(aY, bY), Math.max(aY, bY)),
        };
    }

    private boolean overKeyboard(double mouseX, double mouseY) {
        int kbW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        int kbH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        return inside(mouseX, mouseY, keyboardX, keyboardY, kbW, kbH);
    }

    private void adjustKeyboardZoom(float delta) {
        float clamped = Mth.clamp(viewerConfig.keyboardZoom() + delta,
                KeybindViewerConfig.MIN_KEYBOARD_ZOOM, KeybindViewerConfig.MAX_KEYBOARD_ZOOM);
        if (clamped == viewerConfig.keyboardZoom()) return;
        viewerConfig.setKeyboardZoom(clamped);
        invalidateLayoutCache();
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.kb_zoom",
                Math.round(clamped * 100)).getString());
    }

    private float fitKeyboardScale(int keyboardSpaceW, int keyboardSpaceH,
                                   float widthU, float heightU, float gapW, float gapH) {
        float widthScale = (keyboardSpaceW - gapW) / widthU;
        float heightScale = (keyboardSpaceH - gapH) / heightU;
        // The user keyboard-zoom scales the per-key cap rather than the fitted result, so zooming in
        // only grows the diagram until it fills the available space (never overflowing onto panels),
        // while zooming out always shrinks it. Width/height remain the hard upper bounds.
        float cap = FIXED_KEY_UNIT * viewerConfig.keyboardZoom();
        float fittedScale = Math.min(cap, Math.min(widthScale, heightScale));
        return Math.max(1.0f, fittedScale);
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

    private String modSearchText() {
        return modSearchBox == null ? "" : modSearchBox.getValue();
    }

    private void blurSearchBoxIfOutside(MCEditBox box, double mouseX, double mouseY) {
        if (box == null || !box.isVisible() || !box.isFocused()) return;
        if (!inside(mouseX, mouseY, box.getX(), box.getY(), box.getWidth(), box.getHeight())) {
            box.setFocused(false);
            if (getFocused() == box) setFocused(null);
        }
    }

    /** Blur a box that is about to be hidden, so it cannot keep focus/keyboard input off-screen. */
    private void blurIfHidden(MCEditBox box, boolean willBeVisible) {
        if (box != null && box.isVisible() && !willBeVisible && box.isFocused()) {
            box.setFocused(false);
            if (getFocused() == box) setFocused(null);
        }
    }

    private List<Map.Entry<String, String>> filteredModEntries() {
        String query = modSearchText().toLowerCase(Locale.ROOT);
        long version = scanner.version();
        if (version == cachedModEntriesVersion && query.equals(cachedModEntriesQuery)) {
            return cachedModEntries;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : scanner.getAllRegisteredMods().entrySet()) {
            if (query.isBlank()
                    || entry.getValue().toLowerCase(Locale.ROOT).contains(query)
                    || entry.getKey().toLowerCase(Locale.ROOT).contains(query)
                    || com.github.newvisualkeybing.client.keyboard.Pinyin.matches(entry.getValue(), query)) {
                entries.add(entry);
            }
        }
        cachedModEntriesVersion = version;
        cachedModEntriesQuery = query;
        cachedModEntries = entries;
        return cachedModEntries;
    }

    private List<ModRow> filteredModRows(int fieldW) {
        String query = modSearchText().toLowerCase(Locale.ROOT);
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
        // Status-bar chips (layout-edit toggle + reset) stay clickable in every mode.
        if (button == 0 && handleStatusChipClick(mouseX, mouseY)) return true;
        // In layout-edit mode a left-drag on a panel repositions it; the keyboard/panel-content hit-tests
        // below are skipped so clicks can't accidentally select keys or fold panels while rearranging.
        // Header/toolbar widgets (Done, Mods, …) still receive the click via super so the user is never
        // trapped in edit mode.
        if (layoutEditMode) {
            if (button == 0 && beginPanelDrag(mouseX, mouseY)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // Keep exactly one text box focused: blur any whose bounds this click is outside of. The
        // container's click loop stops at the first handling child, so it cannot be relied on to
        // blur a box that sits after the clicked one in the children list. Covers the toolbar
        // search, the mod-panel search, and the profile name box (all MCEditBox children now).
        blurSearchBoxIfOutside(searchBox, mouseX, mouseY);
        blurSearchBoxIfOutside(modSearchBox, mouseX, mouseY);
        blurSearchBoxIfOutside(profilePanel.nameBox(), mouseX, mouseY);
        if (handleSearchClearClick(mouseX, mouseY)) return true;
        if (modSearchBox != null && modSearchBox.isVisible()
                && modSearchBox.clearAffordanceClicked(mouseX, mouseY)) {
            modSearchBox.setValue("");
            modSearchBox.setFocused(true);
            this.setFocused(modSearchBox);
            modScrollOffset = 0;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        if (inside(mouseX, mouseY, mouseToggleX, mouseToggleY, PANEL_TOGGLE_SIZE, PANEL_TOGGLE_SIZE)) {
            mousePanelCollapsed = !mousePanelCollapsed;
            viewerConfig.setMousePanelCollapsed(mousePanelCollapsed);
            invalidateLayoutCache();
            return true;
        }
        if (inside(mouseX, mouseY, detailToggleX, detailToggleY, PANEL_TOGGLE_SIZE, PANEL_TOGGLE_SIZE)) {
            detailPanelCollapsed = !detailPanelCollapsed;
            viewerConfig.setDetailPanelCollapsed(detailPanelCollapsed);
            invalidateLayoutCache();
            return true;
        }

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
            int px = leftPanelX;
            int py = leftPanelY;
            int ph = contentBottom - contentTop;
            if (profilePanel.mouseClicked(mouseX, mouseY, px, py, ph)) return true;
        }

        boolean wheelSelected = selectedVirtualKey != null && KeyboardLayoutData.isWheel(selectedVirtualKey);
        boolean detailActive = !detailPanelCollapsed && selectedVirtualKey != null && !wheelSelected;
        if (detailActive) {
            KeybindDetailPanel.PriorityHit priorityHit = detailPanel.getRowPriorityHit(mouseX, mouseY);
            if (priorityHit != null) {
                changeMappingPriority(priorityHit.info(), priorityHit.delta());
                return true;
            }
            KeyBindingScanner.KeyBindingInfo ignoreInfo = detailPanel.getRowIgnoreHit(mouseX, mouseY);
            if (ignoreInfo != null) {
                toggleConflictIgnore(ignoreInfo);
                return true;
            }
            KeyBindingScanner.KeyBindingInfo rowInfo = detailPanel.getRowUnbindHit(mouseX, mouseY);
            if (rowInfo != null) {
                unbindSingleMapping(rowInfo);
                return true;
            }
        }
        if (detailActive && detailPanel.isModifyHit(mouseX, mouseY)) {
            quickEdit.open(selectedVirtualKey);
            return true;
        }
        if (detailActive && detailPanel.isUnbindHit(mouseX, mouseY)) {
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

        if (!mousePanelCollapsed) {
            Integer mouseHit = mouseRenderer.hitTest(mouseX, mouseY);
            if (mouseHit != null) {
                if (isHiddenBySelectedMod(mouseHit)) return false;
                selectedVirtualKey = mouseHit;
                detailPanel.resetScroll();
                return true;
            }
        }

        selectedVirtualKey = null;
        return false;
    }

    private boolean handleModPanelClick(double mouseX, double mouseY) {
        int x = leftPanelX;
        int y = leftPanelY;
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

        // The search field itself is an MCEditBox handled by super.mouseClicked; nothing to do here.

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
        // All text boxes (toolbar search, mod search, profile name) are focusable child widgets now,
        // so super.charTyped routes to whichever is focused.
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        // Consume only when the popover actually handles the key; otherwise fall through so global
        // shortcuts (Ctrl +/- /0 UI scale on FixedScaleScreen via super.keyPressed) still work while
        // the popover is open in browse mode.
        if (quickEdit.isOpen() && quickEdit.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (profilePanelOpen && width >= COMPACT_WIDTH_THRESHOLD && profilePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 256) {
            // Escape clears, then blurs, whichever search box is focused.
            MCEditBox focused = searchBox != null && searchBox.isFocused() ? searchBox
                    : modSearchBox != null && modSearchBox.isFocused() ? modSearchBox : null;
            if (focused != null) {
                if (!focused.getValue().isEmpty()) {
                    focused.setValue("");
                    return true;
                }
                focused.setFocused(false);
                this.setFocused(null);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        applyFixedScaleMetrics();
        if (consumeUiScaleScroll(scrollY)) return true;
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        // Alt + wheel over the keyboard zooms the keyboard diagram alone (independent of the UI scale).
        if (Screen.hasAltDown() && overKeyboard(mouseX, mouseY)) {
            adjustKeyboardZoom(scrollY > 0 ? KEYBOARD_ZOOM_STEP : -KEYBOARD_ZOOM_STEP);
            return true;
        }
        if (quickEdit.isOpen()) return quickEdit.mouseScrolled(mouseX, mouseY, scrollY);
        if (!detailPanelCollapsed && selectedVirtualKey != null
                && mouseX >= detailPanelX && mouseX <= detailPanelX + detailPanelW
                && mouseY >= detailPanelY && mouseY <= detailPanelY + detailPanelH) {
            detailPanel.scroll((int) Math.signum(scrollY));
            return true;
        }
        if (!mousePanelCollapsed
                && mouseX >= mousePanelX && mouseX <= mousePanelX + mousePanelW
                && mouseY >= mousePanelY && mouseY <= mousePanelY + mousePanelH) {
            selectedVirtualKey = scrollY > 0 ? KeyboardLayoutData.WHEEL_UP_VIRTUAL : KeyboardLayoutData.WHEEL_DOWN_VIRTUAL;
            return true;
        }
        if (modPanelOpen && width >= COMPACT_WIDTH_THRESHOLD) {
            int x = leftPanelX;
            if (mouseX >= x && mouseX <= x + MOD_PANEL_W) {
                int clearY = leftPanelY + (contentBottom - contentTop) - PANEL_PAD - ACTION_BTN_H;
                int hideToggleY = clearY - ACTION_BTN_H - ACTION_BTN_GAP;
                int comboToggleY = hideToggleY - ACTION_BTN_H - ACTION_BTN_GAP;
                int searchY = leftPanelY + PANEL_CONTENT_TOP + 4;
                int listY = searchY + 26;
                int visibleRows = Math.max(1, (comboToggleY - ACTION_BTN_GAP - listY) / 18);
                modScrollOffset = Mth.clamp(modScrollOffset - (int) Math.signum(scrollY), 0,
                        Math.max(0, filteredModEntries().size() - visibleRows));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        applyFixedScaleMetrics();
        if (draggingPanel != null) {
            double fx = fixedMouseX(mouseX);
            double fy = fixedMouseY(mouseY);
            int rawOffsetX = dragBaseOffsetX + (int) Math.round(fx - dragGrabX);
            int rawOffsetY = dragBaseOffsetY + (int) Math.round(fy - dragGrabY);
            int[] base = panelBase(draggingPanel);
            int[] size = panelSize(draggingPanel);
            // Clamp the offset (not just the rendered position) so dragging past an edge and back has
            // no dead zone: the stored offset can never push the panel out of the visible bounds.
            int offsetX = clampOffsetX(base[0], rawOffsetX, size[0]);
            int offsetY = clampOffsetY(base[1], rawOffsetY, size[1]);
            viewerConfig.setPanelOffset(draggingPanel, offsetX, offsetY);
            invalidateLayoutCache();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPanel != null) {
            draggingPanel = null;
            viewerConfig.save();   // persist the final offset once the drag ends
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void onUiScaleChanged() {
        // A scale change rebuilds the whole layout; abandon any in-progress drag so its stale base
        // position can't be applied against the new metrics.
        draggingPanel = null;
        super.onUiScaleChanged();
    }

    private int clampOffsetX(int baseX, int rawOffsetX, int w) {
        int minX = PANEL_DRAG_MARGIN - w;
        int maxX = width - PANEL_DRAG_MARGIN;
        return Mth.clamp(baseX + rawOffsetX, minX, maxX) - baseX;
    }

    private int clampOffsetY(int baseY, int rawOffsetY, int h) {
        int a = HEADER_H + TOOLBAR_H;
        int b = height - STATUS_H - PANEL_DRAG_MARGIN;
        // On an absurdly short canvas b can fall below a; order the bounds so the clamp range never inverts.
        return Mth.clamp(baseY + rawOffsetY, Math.min(a, b), Math.max(a, b)) - baseY;
    }

    private int[] panelBase(String id) {
        return switch (id) {
            case PANEL_MOUSE -> new int[] {mousePanelBaseX, mousePanelBaseY};
            case PANEL_DETAIL -> new int[] {detailPanelBaseX, detailPanelBaseY};
            default -> new int[] {leftPanelBaseX, leftPanelBaseY};
        };
    }

    private int[] panelSize(String id) {
        return switch (id) {
            case PANEL_MOUSE -> new int[] {mousePanelW, mousePanelCollapsed ? COLLAPSED_PANEL_H : mousePanelH};
            case PANEL_DETAIL -> new int[] {detailPanelW, detailPanelCollapsed ? COLLAPSED_PANEL_H : detailPanelH};
            default -> new int[] {profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W,
                    contentBottom - contentTop};
        };
    }

    /** Starts dragging whichever draggable panel sits under the cursor (topmost first). */
    private boolean beginPanelDrag(double mouseX, double mouseY) {
        for (PanelRect rect : draggablePanelRects()) {
            if (inside(mouseX, mouseY, rect.x(), rect.y(), rect.w(), rect.h())) {
                draggingPanel = rect.id();
                dragGrabX = mouseX;
                dragGrabY = mouseY;
                dragBaseOffsetX = viewerConfig.panelOffsetX(rect.id());
                dragBaseOffsetY = viewerConfig.panelOffsetY(rect.id());
                return true;
            }
        }
        return false;
    }

    /** On-screen rects of the draggable panels, topmost (right rail) first for hit priority. */
    private List<PanelRect> draggablePanelRects() {
        List<PanelRect> rects = new ArrayList<>(3);
        int mouseH = mousePanelCollapsed ? COLLAPSED_PANEL_H : mousePanelH;
        int detailH = detailPanelCollapsed ? COLLAPSED_PANEL_H : detailPanelH;
        rects.add(new PanelRect(PANEL_DETAIL, detailPanelX, detailPanelY, detailPanelW, detailH));
        rects.add(new PanelRect(PANEL_MOUSE, mousePanelX, mousePanelY, mousePanelW, mouseH));
        if ((modPanelOpen || profilePanelOpen) && width >= COMPACT_WIDTH_THRESHOLD) {
            int leftW = profilePanelOpen ? KeybindProfilePanel.WIDTH : MOD_PANEL_W;
            rects.add(new PanelRect(PANEL_LEFT, leftPanelX, leftPanelY, leftW, contentBottom - contentTop));
        }
        return rects;
    }

    private record PanelRect(String id, int x, int y, int w, int h) {}

    /** Handles clicks on the status-bar layout-edit toggle and (while editing) the reset chip. */
    private boolean handleStatusChipClick(double mouseX, double mouseY) {
        if (editToggleX >= 0 && inside(mouseX, mouseY, editToggleX, editToggleY, editToggleW, statusChipH)) {
            layoutEditMode = !layoutEditMode;
            draggingPanel = null;
            showNotice(Component.translatable(layoutEditMode
                    ? "screen.newvisualkeybing.viewer.layout_edit.on"
                    : "screen.newvisualkeybing.viewer.layout_edit.off").getString());
            return true;
        }
        if (layoutEditMode && resetChipX >= 0
                && inside(mouseX, mouseY, resetChipX, resetChipY, resetChipW, statusChipH)
                && viewerConfig.hasPanelOffsets()) {
            viewerConfig.resetPanelOffsets();
            invalidateLayoutCache();
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.layout_edit.reset").getString());
            return true;
        }
        return false;
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
