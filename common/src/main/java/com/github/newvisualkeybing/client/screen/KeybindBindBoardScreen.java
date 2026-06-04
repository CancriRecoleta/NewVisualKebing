package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.keyboard.Pinyin;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.client.ui.UITextureSlot;
import com.github.newvisualkeybing.client.ui.UITextureStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A second visual keyboard screen, distinct from {@link KeybindViewerScreen}: it lists every
 * keybind <em>function</em> in a side palette and lets you bind one by dragging it onto a key. The
 * drop assigns the function as that key's single-key binding (clearing any chord on it).
 */
public class KeybindBindBoardScreen extends FixedScaleScreen {

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 22;
    private static final int PANEL_W = 224;
    // Width the functions panel shrinks to when folded shut — just a rail with an expand chevron.
    private static final int PANEL_COLLAPSED_W = 22;
    private static final int PANEL_PAD = 10;
    private static final int SEARCH_H = 20;
    private static final int ROW_H = 22;
    private static final int CAT_H = 18;
    private static final int BODY_PAD = 12;
    private static final int COL_GAP = 10;
    private static final int PANEL_TOP = HEADER_H + 4;
    private static final int PANEL_CONTENT_TOP = 28;
    private static final int SEARCH_Y = PANEL_TOP + PANEL_CONTENT_TOP + 2;
    private static final int FILTER_H = 16;
    private static final int FILTER_Y = SEARCH_Y + SEARCH_H + 4;
    private static final int MOD_ROW_H = 16;
    private static final float FIXED_KEY_UNIT = 30.0f;

    private final Screen parent;
    private final KeyBindingScanner scanner = new KeyBindingScanner();
    private final KeybindKeyboardRenderer keyboardRenderer = new KeybindKeyboardRenderer(scanner);
    private final KeybindTooltipRenderer tooltipRenderer = new KeybindTooltipRenderer(scanner);
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();

    private KeyboardLayoutData.Style currentStyle = KeybindViewerConfig.global().defaultLayoutStyle();
    private MCEditBox searchBox;
    private MCButton backButton;
    private MCButton layoutButton;
    private MCButton scopeButton;

    // Annotation map scope: label every bound key, only the mod-filter selection, or nothing.
    private enum AnnotateScope {
        ALL, SELECTED, OFF;
        AnnotateScope next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    private AnnotateScope annotateScope = AnnotateScope.ALL;

    private final List<Object> entries = new ArrayList<>();
    // Names of the mappings currently shown in the palette list (mod + search filtered); the
    // SELECTED annotation scope mirrors exactly this set. Collapsing a category hides its rows but
    // keeps its mappings in this set, so folding the list never changes what the keyboard annotates.
    private final Set<String> listedMappingNames = new HashSet<>();
    // Categories the user has folded shut in the palette (remembered by name across rebuilds).
    private final Set<String> collapsedCategories = new HashSet<>();
    private int listScroll;
    private int totalListH;

    private String selectedModId;
    private boolean modDropdownOpen;
    private int modDropdownScroll;
    // When folded, the side palette collapses to a rail and the keyboard reclaims the freed width.
    private boolean functionsCollapsed;

    private float keyScale;
    private int keyboardX;
    private int keyboardY;
    private int keyboardW;
    private int keyboardH;
    private int boardX;
    private int boardTop;
    private int boardW;
    private int boardH;
    private boolean calloutsEnabled;
    private Integer hoveredCalloutKey;
    // Per-frame callout chip rects so a single function's label can be grabbed and dragged.
    private final List<CalloutHit> calloutHits = new ArrayList<>();

    private float animTick;

    // Drag-bind / drag-unbind: a drag begins only once the cursor moves past a small threshold,
    // so a press-then-release in place is treated as a click (used for click-to-select binding).
    private static final double DRAG_THRESHOLD_SQ = 16.0;
    private KeyMapping dragging;
    private String dragLabel;
    private int dragMouseX;
    private int dragMouseY;
    private Integer dropTargetKey;
    private boolean overUnbindZone;

    // Pending press, armed on mouse-down, promoted to a drag on movement or to a click on release.
    private boolean pressArmed;
    private KeyMapping pressMapping;
    private Integer pressFromKey;
    private double pressX;
    private double pressY;

    // Click-to-bind: a function selected in the palette, waiting for a key click to land on.
    private KeyMapping selectedMapping;
    private String selectedLabel;

    private String noticeMessage;
    private long noticeUntil;

    public KeybindBindBoardScreen(Screen parent) {
        super(Component.translatable("screen.newvisualkeybing.board.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        applyFixedScaleMetrics();
        UITheme.setMode(UITheme.Mode.DARK);
        scanner.scan();

        int searchX = BODY_PAD + PANEL_PAD;
        int searchW = PANEL_W - PANEL_PAD * 2;
        searchBox = new MCEditBox(font, searchX, SEARCH_Y, searchW, SEARCH_H,
                Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withClearAffordance(true);
        searchBox.setResponder(value -> rebuildEntries());
        addRenderableWidget(searchBox);

        int btnH = 20;
        int btnY = (HEADER_H - btnH) / 2;
        int backW = KeybindViewerScreen.buttonWidth(font, Component.translatable("gui.done"), 40, 56);
        // Layout/scope cycle through labels, so size to the widest the button will ever show.
        int layoutLabelW = 0;
        for (KeyboardLayoutData.Style s : KeyboardLayoutData.Style.values()) {
            layoutLabelW = Math.max(layoutLabelW, font.width(s.label()));
        }
        int layoutW = Mth.clamp(layoutLabelW + 14, 56, 92);
        int scopeLabelW = Math.max(font.width(Component.translatable("screen.newvisualkeybing.board.annotate.all").getString()),
                Math.max(font.width(Component.translatable("screen.newvisualkeybing.board.annotate.filter").getString()),
                        font.width(Component.translatable("screen.newvisualkeybing.board.annotate.off").getString())));
        int scopeW = Mth.clamp(scopeLabelW + 14, 64, 104);
        int xBack = width - 8 - backW;
        int xLayout = xBack - 6 - layoutW;
        int xScope = xLayout - 6 - scopeW;
        backButton = MCButton.create(xBack, btnY, backW, btnH,
                Component.translatable("gui.done"), b -> onClose());
        addRenderableWidget(backButton);
        layoutButton = MCButton.create(xLayout, btnY, layoutW, btnH,
                Component.literal(currentStyle.label()),
                b -> {
                    currentStyle = currentStyle.next();
                    b.setMessage(Component.literal(currentStyle.label()));
                });
        addRenderableWidget(layoutButton);
        scopeButton = MCButton.create(xScope, btnY, scopeW, btnH,
                annotateLabel(),
                b -> {
                    annotateScope = annotateScope.next();
                    b.setMessage(annotateLabel());
                });
        addRenderableWidget(scopeButton);

        rebuildEntries();
    }

    private void rebuildEntries() {
        entries.clear();
        listedMappingNames.clear();
        String q = searchBox == null ? "" : searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        Map<String, List<KeyMapping>> grouped = new LinkedHashMap<>();
        for (KeyMapping km : profileStore.sortedMappings(Minecraft.getInstance().options.keyMappings)) {
            if (selectedModId != null && !selectedModId.equals(KeyBindingScanner.modIdOf(km))) continue;
            String cat = Component.translatable(km.getCategory()).getString();
            String name = Component.translatable(km.getName()).getString();
            if (!q.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(q)
                    && !cat.toLowerCase(Locale.ROOT).contains(q)
                    && !Pinyin.matches(name, q)
                    && !Pinyin.matches(cat, q)) {
                continue;
            }
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(km);
        }
        int h = 0;
        for (Map.Entry<String, List<KeyMapping>> e : grouped.entrySet()) {
            boolean collapsed = collapsedCategories.contains(e.getKey());
            entries.add(new CategoryEntry(e.getKey(), e.getValue().size(), collapsed));
            h += CAT_H;
            for (KeyMapping km : e.getValue()) {
                // Annotation set tracks every filtered mapping; collapse only hides the palette rows.
                listedMappingNames.add(km.getName());
                if (collapsed) continue;
                entries.add(new FuncEntry(km));
                h += ROW_H;
            }
        }
        totalListH = h;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, totalListH - listHeight()));
    }

    private int listTop() { return FILTER_Y + FILTER_H + 6; }
    private int listHeight() { return height - listTop() - FOOTER_H - 8; }

    private static final int SUMMARY_H = 18;
    private static final int CALLOUT_LANE_GAP = 3;
    // Standoff between the keyboard and the nearest label tier, so chips never hug the keys.
    private static final int CALLOUT_KB_MARGIN = 18;
    private static final int MIN_CALLOUT_GUTTER = 46;

    private int panelWidth() {
        return functionsCollapsed ? PANEL_COLLAPSED_W : PANEL_W;
    }

    private void layoutBoard() {
        boardX = BODY_PAD + panelWidth() + COL_GAP;
        boardTop = HEADER_H + 8;
        boardW = width - boardX - BODY_PAD;
        boardH = height - boardTop - FOOTER_H - 8;

        float widthU = currentStyle.widthU();
        float heightU = currentStyle.heightU();
        float gapW = (widthU - 1) * KeyboardLayoutData.BASE_GAP;
        float gapH = (heightU - 1) * KeyboardLayoutData.BASE_GAP;
        float ws = (boardW - gapW) / widthU;
        float hs = (boardH - gapH) / heightU;
        keyScale = Math.max(1.0f, Math.min(FIXED_KEY_UNIT, Math.min(ws, hs)));

        keyboardW = KeyboardLayoutData.totalWidthPx(currentStyle, keyScale);
        keyboardH = KeyboardLayoutData.totalHeightPx(currentStyle, keyScale);
        keyboardX = boardX + Math.max(0, (boardW - keyboardW) / 2);
        keyboardY = boardTop + Math.max(0, (boardH - keyboardH) / 2);
        calloutsEnabled = keyboardX - boardX >= MIN_CALLOUT_GUTTER
                || boardX + boardW - (keyboardX + keyboardW) >= MIN_CALLOUT_GUTTER
                || horizontalBandAvailable(true)
                || horizontalBandAvailable(false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        animTick += partialTick;
        int mx = fixedMouseX(mouseX);
        int my = fixedMouseY(mouseY);
        dragMouseX = mx;
        dragMouseY = my;
        layoutBoard();
        overUnbindZone = dragging != null && insideUnbindZone(mx, my);
        // Callouts are drawn after the keyboard, so reuse last frame's hovered key to light up the
        // matching key while the cursor is over its label chip (a one-frame lag, imperceptible).
        Integer prevHoverKey = hoveredCalloutKey;
        hoveredCalloutKey = null;
        // While dragging or while a function is selected, the key under the cursor is the live target;
        // the unbind zone overrides any key it overlaps so a drop there always means "remove".
        dropTargetKey = (dragging != null || selectedMapping != null) && !overUnbindZone
                ? keyAt(mx, my) : null;

        pushFixedScale(graphics);
        try {
            var c = UITheme.colors();
            if (!(UITheme.custom() && UITextureStore.global().draw(UITextureSlot.BACKGROUND, graphics, 0, 0, width, height))) {
                graphics.fill(0, 0, width, height, c.panelBg() | 0xFF000000);
            }
            renderHeader(graphics);
            renderPalette(graphics, mx, my);

            long nowMs = System.currentTimeMillis();
            // The keyboard is always drawn solid (never ghosted): every key renders fully, with
            // status colour distinguishing bound keys from free ones. Callouts carry the labels.
            Integer highlightKey = dropTargetKey != null ? dropTargetKey : prevHoverKey;
            keyboardRenderer.render(graphics, font, currentStyle, keyboardX, keyboardY, keyScale,
                    highlightKey, k -> true, k -> false, k -> false, mx, my, animTick, nowMs);

            renderCallouts(graphics, mx, my);
            renderLegend(graphics);

            if (dragging != null) renderUnbindZone(graphics, mx, my);
            renderFooter(graphics);
            super.render(graphics, mx, my, partialTick);
            if (dragging == null && selectedMapping == null) renderHoverDetails(graphics, mx, my);
            if (dragging != null) renderDragChip(graphics);
            renderNotice(graphics);
        } finally {
            popFixedScale(graphics);
        }
    }

    private void renderHeader(GuiGraphics g) {
        var c = UITheme.colors();
        g.fill(0, 0, width, HEADER_H, c.headerBg());
        g.fill(0, HEADER_H - 1, width, HEADER_H, c.divider());
        g.fill(0, HEADER_H, width, HEADER_H + 1, UITheme.withAlpha(c.accent(), 0x70));
        g.drawString(font, title.getString(), 12, (HEADER_H - font.lineHeight) / 2, c.textPrimary(), true);
    }

    private void renderPalette(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int x = BODY_PAD;
        int top = HEADER_H + 4;
        int h = height - top - FOOTER_H - 4;
        if (functionsCollapsed) {
            renderCollapsedRail(g, x, top, h, mouseX, mouseY);
            return;
        }
        String title = Component.translatable("screen.newvisualkeybing.board.functions").getString();
        KeybindViewerScreen.paintPanelBase(g, font, x, top, PANEL_W, h, title);
        renderPanelToggle(g, mouseX, mouseY);

        int listTop = listTop();
        int listH = listHeight();
        int innerX = x + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;

        renderFilterBar(g, innerX, innerW, mouseX, mouseY);

        enableFixedScissor(g, innerX, listTop, innerX + innerW, listTop + listH);
        int drawY = listTop - listScroll;
        for (Object entry : entries) {
            int eh = entry instanceof CategoryEntry ? CAT_H : ROW_H;
            if (drawY + eh >= listTop && drawY <= listTop + listH) {
                if (entry instanceof CategoryEntry ce) {
                    renderCategoryRow(g, ce, innerX, drawY, innerW, mouseX, mouseY);
                } else if (entry instanceof FuncEntry fe) {
                    boolean hovered = dragging == null
                            && KeybindViewerScreen.inside(mouseX, mouseY, innerX, drawY, innerW, ROW_H - 2);
                    boolean selected = selectedMapping == fe.mapping;
                    renderFuncRow(g, fe, innerX, drawY, innerW, hovered, selected);
                }
            }
            drawY += eh;
        }
        g.disableScissor();

        if (totalListH > listH) {
            int sbH = Math.max(20, (int) ((float) listH / totalListH * listH));
            int sbY = listTop + (int) ((float) listScroll / totalListH * listH);
            UITheme.fillRoundedRectFast(g, x + PANEL_W - 6, listTop, 4, listH, 2, c.scrollbarTrack());
            UITheme.fillRoundedRectFast(g, x + PANEL_W - 6, sbY, 4, sbH, 2, c.scrollbarThumb());
        }

        if (modDropdownOpen) renderModDropdown(g, innerX, innerW, mouseX, mouseY);
    }

    /** Folded state: a slim rail standing in for the panel, clickable anywhere to expand. */
    private void renderCollapsedRail(GuiGraphics g, int x, int top, int h, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int w = PANEL_COLLAPSED_W;
        boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, x, top, w, h);
        UITheme.fillRoundedRectFast(g, x, top, w, h, 6,
                UITheme.withAlpha(c.headerBg(), hovered ? 0xF0 : 0xDC));
        UITheme.drawRoundedBorderFast(g, x, top, w, h, 6,
                UITheme.withAlpha(hovered ? c.accent() : c.widgetBorder(), 0xA0));
        int[] r = panelToggleRect();
        if (hovered) {
            UITheme.fillRoundedRectFast(g, r[0], r[1], r[2], r[3], 3, UITheme.withAlpha(c.widgetBg(), 0x88));
        }
        String chev = "▸";
        g.drawString(font, chev, r[0] + (r[2] - font.width(chev)) / 2,
                r[1] + (r[3] - font.lineHeight) / 2, hovered ? c.textPrimary() : c.textSecondary(), false);
        // Vertical "FUNCTIONS" hint so the rail reads as the collapsed functions panel.
        String hint = Component.translatable("screen.newvisualkeybing.board.functions").getString().toUpperCase(Locale.ROOT);
        int gy = r[1] + r[3] + 8;
        int cx = x + w / 2;
        for (int i = 0; i < hint.length() && gy + font.lineHeight < top + h - 4; i++) {
            String ch = String.valueOf(hint.charAt(i));
            if (ch.equals(" ")) { gy += font.lineHeight / 2; continue; }
            g.drawString(font, ch, cx - font.width(ch) / 2, gy, UITheme.withAlpha(c.textMuted(), 0xC0), false);
            gy += font.lineHeight;
        }
    }

    /** Collapse/expand chevron drawn in the expanded panel's header corner. */
    private void renderPanelToggle(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int[] r = panelToggleRect();
        boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        if (hovered) {
            UITheme.fillRoundedRectFast(g, r[0], r[1], r[2], r[3], 3, UITheme.withAlpha(c.widgetBg(), 0x88));
        }
        String chev = "◂";
        g.drawString(font, chev, r[0] + (r[2] - font.width(chev)) / 2,
                r[1] + (r[3] - font.lineHeight) / 2, hovered ? c.textPrimary() : c.textSecondary(), false);
    }

    /** {x, y, w, h} of the panel collapse/expand chevron, valid in both states. */
    private int[] panelToggleRect() {
        int s = 14;
        int y = PANEL_TOP + 6;
        int x = functionsCollapsed
                ? BODY_PAD + (PANEL_COLLAPSED_W - s) / 2
                : BODY_PAD + PANEL_W - s - 6;
        return new int[]{x, y, s, s};
    }

    private void toggleFunctionsCollapsed() {
        functionsCollapsed = !functionsCollapsed;
        modDropdownOpen = false;
        if (searchBox != null) {
            searchBox.setVisible(!functionsCollapsed);
            if (functionsCollapsed) {
                searchBox.setFocused(false);
                if (getFocused() == searchBox) setFocused(null);
            }
        }
    }

    private void renderFilterBar(GuiGraphics g, int x, int w, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean active = selectedModId != null;
        boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, x, FILTER_Y, w, FILTER_H);
        int fill = UITheme.lerpColor(c.widgetBg(), active ? c.accent() : c.accentAlt(), hovered ? 0.40f : 0.18f);
        UITheme.fillRoundedRectFast(g, x, FILTER_Y, w, FILTER_H, 4, fill);
        UITheme.drawRoundedBorderFast(g, x, FILTER_Y, w, FILTER_H, 4,
                UITheme.withAlpha(active ? c.accent() : c.widgetBorder(), 0xB0));
        String label = Component.translatable("screen.newvisualkeybing.board.filter",
                selectedModName()).getString();
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, label, w - 16),
                x + 5, FILTER_Y + (FILTER_H - font.lineHeight) / 2, c.textPrimary(), false);
        // caret
        int cx = x + w - 9;
        int cy = FILTER_Y + FILTER_H / 2 - (modDropdownOpen ? 1 : 2);
        for (int i = 0; i < 3; i++) {
            int yy = modDropdownOpen ? cy + i : cy + (2 - i);
            g.fill(cx + i, yy, cx + i + 1, yy + 1, c.textSecondary());
            g.fill(cx + 4 - i, yy, cx + 5 - i, yy + 1, c.textSecondary());
        }
    }

    private void renderModDropdown(GuiGraphics g, int x, int w, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int y = FILTER_Y + FILTER_H + 2;
        int h = height - y - FOOTER_H - 8;
        UITheme.fillRoundedRectFast(g, x, y, w, h, 5, UITheme.withAlpha(c.headerBg(), 0xF4));
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 5, c.accent());

        List<String> ids = modIds();
        int visible = Math.max(1, (h - 6) / MOD_ROW_H);
        modDropdownScroll = Mth.clamp(modDropdownScroll, 0, Math.max(0, ids.size() - visible));
        enableFixedScissor(g, x + 1, y + 1, x + w - 1, y + h - 1);
        int rowY = y + 3;
        for (int i = modDropdownScroll; i < ids.size() && i < modDropdownScroll + visible; i++) {
            String id = ids.get(i);
            boolean sel = Objects.equals(id, selectedModId);
            boolean hovered = KeybindViewerScreen.inside(mouseX, mouseY, x + 3, rowY, w - 6, MOD_ROW_H - 1);
            if (sel || hovered) {
                UITheme.fillRoundedRectFast(g, x + 3, rowY, w - 6, MOD_ROW_H - 1, 4,
                        UITheme.lerpColor(c.widgetBg(), c.accent(), sel ? 0.45f : 0.22f));
            }
            String name = id == null
                    ? Component.translatable("screen.newvisualkeybing.board.filter_all").getString()
                    : scanner.getAllRegisteredMods().getOrDefault(id, id);
            g.drawString(font, KeybindViewerScreen.fitToWidth(font, name, w - 14),
                    x + 7, rowY + (MOD_ROW_H - 1 - font.lineHeight) / 2,
                    sel ? c.textPrimary() : c.textSecondary(), false);
            rowY += MOD_ROW_H;
        }
        g.disableScissor();
    }

    private String selectedModName() {
        if (selectedModId == null) {
            return Component.translatable("screen.newvisualkeybing.board.filter_all").getString();
        }
        return scanner.getAllRegisteredMods().getOrDefault(selectedModId, selectedModId);
    }

    private List<String> modIds() {
        List<String> ids = new ArrayList<>();
        ids.add(null); // "All"
        ids.addAll(scanner.getAllRegisteredMods().keySet());
        return ids;
    }

    /** Category header: fold chevron, name, and a right-aligned function count. Click toggles fold. */
    private void renderCategoryRow(GuiGraphics g, CategoryEntry ce, int x, int y, int w, int mouseX, int mouseY) {
        var c = UITheme.colors();
        boolean hovered = dragging == null && KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, CAT_H);
        if (hovered) {
            UITheme.fillRoundedRectFast(g, x, y, w, CAT_H - 1, 4, UITheme.withAlpha(c.widgetBg(), 0x66));
        }
        int textY = y + (CAT_H - font.lineHeight) / 2;
        String chev = ce.collapsed() ? "▸" : "▾";
        g.drawString(font, chev, x + 2, textY, hovered ? c.textPrimary() : c.textSecondary(), false);
        int labelX = x + 2 + font.width(chev) + 4;
        String count = Integer.toString(ce.count());
        int countW = font.width(count);
        String name = KeybindViewerScreen.fitToWidth(font, ce.name(), w - (labelX - x) - countW - 8);
        g.drawString(font, name, labelX, textY, c.accentLight(), false);
        g.drawString(font, count, x + w - countW - 2, textY, UITheme.withAlpha(c.textMuted(), 0xC8), false);
    }

    private void renderFuncRow(GuiGraphics g, FuncEntry fe, int x, int y, int w, boolean hovered, boolean selected) {
        var c = UITheme.colors();
        int rowH = ROW_H - 2;
        int fill = selected ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.50f)
                : hovered ? UITheme.lerpColor(c.widgetBg(), c.accent(), 0.30f)
                : UITheme.withAlpha(c.widgetBg(), 0x88);
        UITheme.fillRoundedRectFast(g, x, y, w, rowH, 5, fill);
        UITheme.drawRoundedBorderFast(g, x, y, w, rowH, 5,
                selected ? c.accent() : UITheme.withAlpha(c.widgetBorder(), 0x80));
        // grip dots to signal draggability
        int gx = x + 5;
        int gy = y + rowH / 2;
        for (int i = -1; i <= 1; i++) {
            g.fill(gx, gy + i * 3 - 1, gx + 2, gy + i * 3 + 1, UITheme.withAlpha(c.textMuted(), 0xC0));
            g.fill(gx + 3, gy + i * 3 - 1, gx + 5, gy + i * 3 + 1, UITheme.withAlpha(c.textMuted(), 0xC0));
        }
        String key = fe.mapping.getTranslatedKeyMessage().getString();
        int keyW = Math.min(font.width(key) + 8, w / 2);
        String name = KeybindViewerScreen.fitToWidth(font, Component.translatable(fe.mapping.getName()).getString(),
                w - 14 - keyW - 6);
        int textY = y + (rowH - font.lineHeight) / 2;
        g.drawString(font, name, x + 14, textY, c.textPrimary(), false);
        boolean unbound = fe.mapping.isUnbound();
        String keyFit = KeybindViewerScreen.fitToWidth(font, key, keyW);
        g.drawString(font, keyFit, x + w - font.width(keyFit) - 4, textY,
                unbound ? c.textMuted() : c.accentLight(), false);
    }

    private void renderFooter(GuiGraphics g) {
        var c = UITheme.colors();
        int y = height - FOOTER_H;
        g.fill(0, y, width, y + 1, c.divider());
        String hint;
        if (dragging != null) {
            hint = Component.translatable(
                    overUnbindZone ? "screen.newvisualkeybing.board.unbind_hint"
                            : "screen.newvisualkeybing.board.drop_hint",
                    Component.translatable(dragging.getName()).getString()).getString();
        } else if (selectedMapping != null) {
            hint = Component.translatable("screen.newvisualkeybing.board.select_hint", selectedLabel).getString();
        } else {
            hint = Component.translatable("screen.newvisualkeybing.board.hint").getString();
        }
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, hint, width - 24),
                12, y + (FOOTER_H - font.lineHeight) / 2, c.textSecondary(), false);
    }

    private void renderDragChip(GuiGraphics g) {
        var c = UITheme.colors();
        int pad = 6;
        int w = font.width(dragLabel) + pad * 2;
        int h = font.lineHeight + pad;
        int x = dragMouseX + 8;
        int y = dragMouseY + 8;
        x = Math.min(x, width - w - 2);
        y = Math.min(y, height - h - 2);
        UITheme.fillSoftRoundedRect(g, x, y, w, h, 5, UITheme.withAlpha(c.headerBg(), 0xF0));
        int border = overUnbindZone ? c.danger() : dropTargetKey != null ? c.success() : c.accent();
        UITheme.drawSoftRoundedBorder(g, x, y, w, h, 5, border);
        g.drawString(font, dragLabel, x + pad, y + (h - font.lineHeight) / 2, c.textPrimary(), false);
    }

    /** Drop target shown while dragging — releasing a function here clears its binding. */
    private void renderUnbindZone(GuiGraphics g, int mouseX, int mouseY) {
        var c = UITheme.colors();
        int[] r = unbindZoneRect();
        boolean over = KeybindViewerScreen.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        int fill = UITheme.withAlpha(over ? c.danger() : c.headerBg(), over ? 0xC8 : 0xE6);
        UITheme.fillRoundedRectFast(g, r[0], r[1], r[2], r[3], 6, fill);
        UITheme.drawRoundedBorderFast(g, r[0], r[1], r[2], r[3], 6,
                over ? c.danger() : UITheme.withAlpha(c.danger(), 0x90));
        String label = Component.translatable("screen.newvisualkeybing.board.unbind_zone").getString();
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, label, r[2] - 10),
                r[0] + (r[2] - font.width(label)) / 2, r[1] + (r[3] - font.lineHeight) / 2,
                over ? c.textPrimary() : c.textSecondary(), false);
    }

    /** {x, y, w, h} of the unbind drop zone, centered along the bottom of the keyboard column. */
    private int[] unbindZoneRect() {
        int w = Math.min(220, Math.max(120, boardW - 20));
        int h = 28;
        int x = boardX + (boardW - w) / 2;
        int y = height - FOOTER_H - h - 6;
        return new int[]{x, y, w, h};
    }

    private boolean insideUnbindZone(double mouseX, double mouseY) {
        int[] r = unbindZoneRect();
        return KeybindViewerScreen.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
    }

    private void renderNotice(GuiGraphics g) {
        if (noticeMessage == null) return;
        if (System.currentTimeMillis() > noticeUntil) { noticeMessage = null; return; }
        var c = UITheme.colors();
        int w = font.width(noticeMessage) + 24;
        int x = (width - w) / 2;
        int y = height - FOOTER_H - 26;
        UITheme.fillRoundedRectFast(g, x, y, w, 20, 6, UITheme.withAlpha(c.headerBg(), 0xE0));
        UITheme.drawRoundedBorderFast(g, x, y, w, 20, 6, c.accent());
        g.drawString(font, noticeMessage, x + 12, y + 6, c.textPrimary(), false);
    }

    private void showNotice(String msg) {
        noticeMessage = msg;
        noticeUntil = System.currentTimeMillis() + 2200;
    }

    private Component annotateLabel() {
        return Component.translatable(switch (annotateScope) {
            case ALL -> "screen.newvisualkeybing.board.annotate.all";
            case SELECTED -> "screen.newvisualkeybing.board.annotate.filter";
            case OFF -> "screen.newvisualkeybing.board.annotate.off";
        });
    }

    /** Every binding on a key that the current scope labels (one chip each), in display order. */
    private List<KeyBindingScanner.KeyBindingInfo> shownBindingsFor(int glfwKey) {
        if (annotateScope == AnnotateScope.OFF) return List.of();
        List<KeyBindingScanner.KeyBindingInfo> binds = scanner.getBindings(glfwKey);
        if (binds.isEmpty()) return List.of();
        if (annotateScope == AnnotateScope.ALL) return binds;
        List<KeyBindingScanner.KeyBindingInfo> out = new ArrayList<>();
        for (KeyBindingScanner.KeyBindingInfo info : binds) {
            if (listedMappingNames.contains(info.translationKey())) out.add(info);
        }
        return out;
    }

    private int statusColor(KeyBindingScanner.KeyStatus status) {
        var c = UITheme.colors();
        return switch (status) {
            case FREE -> c.widgetBorder();
            case SELF -> c.accent();
            case OTHER_SINGLE, BOUND -> c.success();
            case COMBO -> c.warning();
            case CONFLICT -> c.danger();
        };
    }

    /** Leader-line callouts arranged in tiered lanes around the keyboard. */
    private void renderCallouts(GuiGraphics g, int mouseX, int mouseY) {
        calloutHits.clear();
        if (!calloutsEnabled || annotateScope == AnnotateScope.OFF) return;
        List<CalloutItem> top = new ArrayList<>();
        List<CalloutItem> bottom = new ArrayList<>();
        List<CalloutItem> left = new ArrayList<>();
        List<CalloutItem> right = new ArrayList<>();
        // Capacity (≈ chips that fit) per usable edge, plus a running load count, so each key's
        // labels land on its less-crowded adjacent edge instead of all piling onto the nearest one.
        EnumMap<CalloutSide, Integer> capacity = edgeCapacities();
        EnumMap<CalloutSide, Integer> load = new EnumMap<>(CalloutSide.class);
        int hidden = 0;
        int conflicts = 0;
        int labelledKeys = 0;
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            int glfw = key.glfwKey();
            if (scanner.getBindings(glfw).isEmpty()) continue;
            List<KeyBindingScanner.KeyBindingInfo> shown = shownBindingsFor(glfw);
            if (shown.isEmpty()) {
                hidden++;
                continue;
            }
            labelledKeys++;
            if (scanner.getStatus(glfw) == KeyBindingScanner.KeyStatus.CONFLICT) conflicts++;
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            int cx = kx + kw / 2;
            int cy = ky + kh / 2;
            // Keep all of one key's functions on the same side, one chip per function.
            CalloutSide chosen = chooseSide(cx, cy, capacity, load);
            List<CalloutItem> side = switch (chosen) {
                case TOP -> top;
                case BOTTOM -> bottom;
                case LEFT -> left;
                case RIGHT -> right;
            };
            for (KeyBindingScanner.KeyBindingInfo info : shown) {
                side.add(new CalloutItem(glfw, kx, ky, kw, kh, cx, cy,
                        info.actionName(), info.translationKey()));
            }
            load.merge(chosen, shown.size(), Integer::sum);
        }
        int total = top.size() + bottom.size() + left.size() + right.size();
        renderAnnotationSummary(g, labelledKeys, conflicts, hidden);
        if (total == 0) return;

        int chipH = font.lineHeight + 4;
        placeBand(g, top, true, chipH, mouseX, mouseY);
        placeBand(g, bottom, false, chipH, mouseX, mouseY);
        placeColumn(g, left, true, chipH, mouseX, mouseY);
        placeColumn(g, right, false, chipH, mouseX, mouseY);
    }

    /**
     * Pick which edge a key's labels go to. The candidates are the key's two <em>adjacent</em> edges
     * — its horizontal one ({@code h}) and vertical one ({@code v}) — since a leader to either stays
     * on the key's own side and never crosses the keyboard. Between those two we take whichever has
     * the lower load ratio (used/capacity), nudging toward the proximity-dominant edge so nearby keys
     * still prefer their natural side. This rings the labels around every free gutter instead of
     * letting a cluster of keys (e.g. WASD on a compact board) all pile onto a single nearest edge.
     */
    private CalloutSide chooseSide(int cx, int cy,
                                   EnumMap<CalloutSide, Integer> capacity, EnumMap<CalloutSide, Integer> load) {
        float cxC = keyboardX + keyboardW / 2f;
        float cyC = keyboardY + keyboardH / 2f;
        float nx = (cx - cxC) / Math.max(1f, keyboardW / 2f);
        float ny = (cy - cyC) / Math.max(1f, keyboardH / 2f);
        CalloutSide h = nx < 0 ? CalloutSide.LEFT : CalloutSide.RIGHT;
        CalloutSide v = ny < 0 ? CalloutSide.TOP : CalloutSide.BOTTOM;
        CalloutSide nearer = Math.abs(nx) > Math.abs(ny) ? h : v;

        CalloutSide best = null;
        float bestScore = Float.MAX_VALUE;
        for (CalloutSide s : new CalloutSide[]{h, v}) {
            int cap = capacity.getOrDefault(s, 0);
            if (cap <= 0) continue;
            float ratio = load.getOrDefault(s, 0) / (float) cap;
            // Bias the proximity-dominant edge so ties and near-ties keep the shorter leader.
            float score = ratio + (s == nearer ? -0.10f : 0f);
            if (score < bestScore) {
                bestScore = score;
                best = s;
            }
        }
        if (best != null) return best;
        // Both adjacent edges are unusable (no gutter): spill to whichever opposite edge has room.
        for (CalloutSide s : new CalloutSide[]{opposite(v), opposite(h)}) {
            if (capacity.getOrDefault(s, 0) > 0) return s;
        }
        return nearer;
    }

    /** Approximate label capacity (chips that fit before force-overlap) of each usable edge. */
    private EnumMap<CalloutSide, Integer> edgeCapacities() {
        EnumMap<CalloutSide, Integer> cap = new EnumMap<>(CalloutSide.class);
        int chipH = font.lineHeight + 4;
        cap.put(CalloutSide.TOP, edgeAvailable(CalloutSide.TOP) ? bandCapacity(true, chipH) : 0);
        cap.put(CalloutSide.BOTTOM, edgeAvailable(CalloutSide.BOTTOM) ? bandCapacity(false, chipH) : 0);
        cap.put(CalloutSide.LEFT, edgeAvailable(CalloutSide.LEFT) ? columnCapacity(true) : 0);
        cap.put(CalloutSide.RIGHT, edgeAvailable(CalloutSide.RIGHT) ? columnCapacity(false) : 0);
        return cap;
    }

    /** Mirrors {@link #placeBand}'s lane math: lanes × (band span / a typical chip width). */
    private int bandCapacity(boolean topSide, int chipH) {
        int near = topSide ? keyboardY - CALLOUT_KB_MARGIN : keyboardY + keyboardH + CALLOUT_KB_MARGIN;
        int far = topSide ? boardTop + SUMMARY_H + 5 : boardTop + boardH - 4;
        int depth = Math.abs(far - near);
        int laneStep = chipH + CALLOUT_LANE_GAP;
        int maxLanes = Mth.clamp((depth + CALLOUT_LANE_GAP) / laneStep, 1, 4);
        int axisSpan = Math.max(1, boardW - 4);
        return Math.max(1, maxLanes * axisSpan / 96);
    }

    /** Mirrors {@link #placeColumn}'s lane math: lanes × (column span / chip height). */
    private int columnCapacity(boolean leftSide) {
        int near = leftSide ? keyboardX - CALLOUT_KB_MARGIN : keyboardX + keyboardW + CALLOUT_KB_MARGIN;
        int far = leftSide ? boardX + 2 : boardX + boardW - 2;
        int depth = Math.abs(near - far);
        int laneGapX = 6;
        int maxLanes = 1;
        for (int l = 3; l >= 1; l--) {
            int w = (depth - (l - 1) * laneGapX) / l;
            if (l == 1 || w >= 64) {
                maxLanes = l;
                break;
            }
        }
        int chipH = font.lineHeight + 4;
        int axisSpan = Math.max(1, boardH - SUMMARY_H - 6);
        return Math.max(1, maxLanes * axisSpan / (chipH + CALLOUT_LANE_GAP));
    }

    private static CalloutSide opposite(CalloutSide s) {
        return switch (s) {
            case TOP -> CalloutSide.BOTTOM;
            case BOTTOM -> CalloutSide.TOP;
            case LEFT -> CalloutSide.RIGHT;
            case RIGHT -> CalloutSide.LEFT;
        };
    }

    private boolean edgeAvailable(CalloutSide side) {
        return switch (side) {
            case TOP -> horizontalBandAvailable(true);
            case BOTTOM -> horizontalBandAvailable(false);
            case LEFT -> keyboardX - boardX >= MIN_CALLOUT_GUTTER;
            case RIGHT -> boardX + boardW - (keyboardX + keyboardW) >= MIN_CALLOUT_GUTTER;
        };
    }

    /**
     * Tiered packing along a 1D axis (items pre-sorted by anchor position). Each item is placed in
     * the lane that requires the <em>smallest forward shift</em> from its anchor-aligned desired
     * position, rather than the first lane that merely fits. Hugging the anchor keeps every leader
     * almost straight (a near-vertical/near-horizontal stub), which both removes line crossings and
     * yields a clean tiered "comb": items sit right next to their key, only stepping to an outer
     * lane when a nearer one is occupied. Order is preserved within a lane because {@code laneEnd}
     * is monotonic and the inputs are sorted, so leaders never reorder and cross.
     */
    private static void shelfPack(int n, int[] size, int[] desired, int axisMin, int axisMax,
                                  int gap, int maxLanes, int[] outLane, int[] outStart) {
        int[] laneEnd = new int[maxLanes];
        Arrays.fill(laneEnd, axisMin - gap);
        for (int i = 0; i < n; i++) {
            int bestLane = -1;
            int bestStart = 0;
            int bestShift = Integer.MAX_VALUE;
            for (int l = 0; l < maxLanes; l++) {
                int s = Math.max(desired[i], laneEnd[l] + gap);
                if (s + size[i] > axisMax) continue;        // no room left in this lane
                int shift = s - desired[i];                 // >= 0: how far past the anchor we pushed
                if (shift < bestShift) {                    // prefer the least-shifted (lower lane on ties)
                    bestShift = shift;
                    bestLane = l;
                    bestStart = s;
                }
                if (shift == 0) break;                      // can't do better than no shift
            }
            if (bestLane < 0) {
                // Every lane is packed to the right edge: drop into the emptiest one and clamp.
                bestLane = 0;
                for (int l = 1; l < maxLanes; l++) if (laneEnd[l] < laneEnd[bestLane]) bestLane = l;
                bestStart = Mth.clamp(Math.max(desired[i], laneEnd[bestLane] + gap),
                        axisMin, axisMax - size[i]);
            }
            laneEnd[bestLane] = bestStart + size[i];
            outLane[i] = bestLane;
            outStart[i] = bestStart;
        }
    }

    private boolean horizontalBandAvailable(boolean topSide) {
        int chipH = font.lineHeight + 4;
        int bandTop = topSide ? boardTop + SUMMARY_H + 5 : keyboardY + keyboardH + CALLOUT_KB_MARGIN;
        int bandBottom = topSide ? keyboardY - CALLOUT_KB_MARGIN : boardTop + boardH - 4;
        return bandBottom - bandTop >= chipH;
    }

    private void renderAnnotationSummary(GuiGraphics g, int total, int conflicts, int hidden) {
        var c = UITheme.colors();
        int x = boardX + 2;
        int y = boardTop + 2;
        int w = boardW - 4;
        UITheme.fillRoundedRectFast(g, x, y, w, SUMMARY_H, 5, UITheme.withAlpha(c.headerBg(), 0xC8));
        UITheme.drawRoundedBorderFast(g, x, y, w, SUMMARY_H, 5, UITheme.withAlpha(c.widgetBorder(), 0x80));

        String scope = switch (annotateScope) {
            case ALL -> Component.translatable("screen.newvisualkeybing.board.filter_all").getString();
            case SELECTED -> selectedModId != null ? selectedModName()
                    : Component.translatable("screen.newvisualkeybing.board.annotate.list").getString();
            case OFF -> "";
        };
        String summary = hidden > 0
                ? Component.translatable("screen.newvisualkeybing.board.summary_hidden",
                        scope, total, conflicts, hidden).getString()
                : Component.translatable("screen.newvisualkeybing.board.summary",
                        scope, total, conflicts).getString();
        // Reserve room on the right for the (now four-item) colour legend drawn over this same strip.
        int maxTextW = Math.max(80, w - 260);
        g.drawString(font, KeybindViewerScreen.fitToWidth(font, summary, maxTextW),
                x + 7, y + (SUMMARY_H - font.lineHeight) / 2, c.textSecondary(), false);
    }

    /** Top / bottom band: one chip per function, packed into horizontal lanes stepping outward. */
    private void placeBand(GuiGraphics g, List<CalloutItem> items, boolean topSide,
                           int chipH, int mouseX, int mouseY) {
        int n = items.size();
        if (n == 0) return;
        items.sort(Comparator.comparingInt(CalloutItem::cx));
        int near = topSide ? keyboardY - CALLOUT_KB_MARGIN : keyboardY + keyboardH + CALLOUT_KB_MARGIN;
        int far = topSide ? boardTop + SUMMARY_H + 5 : boardTop + boardH - 4;
        int depth = Math.abs(far - near);
        int laneStep = chipH + CALLOUT_LANE_GAP;
        int maxLanes = Mth.clamp((depth + CALLOUT_LANE_GAP) / laneStep, 1, 4);
        int axisMin = boardX + 2;
        int axisMax = boardX + boardW - 2;

        // Cap chip width to the per-item share of the band's total lane area, so chips shrink as the
        // band fills and pack tighter instead of hitting shelfPack's overlapping force-place path.
        // Long labels truncate (full text stays on the hover tooltip); short ones keep their size.
        int bandGap = 4;
        int perChipBudget = (axisMax - axisMin) * maxLanes / n - bandGap;
        int chipMaxW = Mth.clamp(perChipBudget, 64, 118);
        int[] size = new int[n];
        int[] desired = new int[n];
        for (int i = 0; i < n; i++) {
            int cw = preferredChipWidth(items.get(i), chipMaxW);
            size[i] = cw;
            desired[i] = Mth.clamp(items.get(i).cx() - cw / 2, axisMin, axisMax - cw);
        }
        int[] lane = new int[n];
        int[] startX = new int[n];
        shelfPack(n, size, desired, axisMin, axisMax, bandGap, maxLanes, lane, startX);

        int laneLo = Math.min(near, far);
        int laneHi = Math.max(near, far) - chipH;
        int[] yTop = new int[n];
        boolean[] hover = new boolean[n];
        int[] accent = new int[n];
        for (int i = 0; i < n; i++) {
            yTop[i] = Mth.clamp(topSide ? near - chipH - lane[i] * laneStep : near + lane[i] * laneStep,
                    laneLo, laneHi);
            hover[i] = calloutHovered(items.get(i), startX[i], yTop[i], size[i], chipH, mouseX, mouseY);
            accent[i] = statusColor(scanner.getStatus(items.get(i).glfw()));
        }
        // Pass 1: leader lines first, so chips painted afterwards cover any line beneath them.
        for (int i = 0; i < n; i++) {
            CalloutItem item = items.get(i);
            int lineColor = UITheme.withAlpha(accent[i], calloutAlpha(hover[i], lane[i]));
            int keyAnchorY = topSide ? item.ky() : item.ky() + item.kh();
            int railY = topSide ? yTop[i] + chipH : yTop[i];
            fillVSeg(g, item.cx(), keyAnchorY, railY, lineColor);
            fillSeg(g, item.cx(), startX[i] + size[i] / 2, railY, lineColor);
            g.fill(item.cx() - 1, keyAnchorY - 1, item.cx() + 1, keyAnchorY + 1, lineColor);
        }
        // Pass 2: chips.
        for (int i = 0; i < n; i++) {
            renderCalloutChip(g, items.get(i), startX[i], yTop[i], size[i], chipH,
                    topSide ? CalloutSide.TOP : CalloutSide.BOTTOM, hover[i], accent[i], lane[i]);
        }
    }

    /** Left / right gutter: one chip per function, packed into vertical lanes stepping outward. */
    private void placeColumn(GuiGraphics g, List<CalloutItem> items, boolean leftSide,
                             int chipH, int mouseX, int mouseY) {
        int n = items.size();
        if (n == 0) return;
        items.sort(Comparator.comparingInt(CalloutItem::cy));
        int near = leftSide ? keyboardX - CALLOUT_KB_MARGIN : keyboardX + keyboardW + CALLOUT_KB_MARGIN;
        int far = leftSide ? boardX + 2 : boardX + boardW - 2;
        int depth = Math.abs(near - far);
        // Fit as many non-overlapping tiers as the gutter allows, each at least 64px wide.
        int laneGapX = 6;
        int maxLanes = 1;
        int laneW = Mth.clamp(depth, 40, 128);
        for (int l = 3; l >= 1; l--) {
            int w = (depth - (l - 1) * laneGapX) / l;
            if (l == 1 || w >= 64) {
                maxLanes = l;
                laneW = Mth.clamp(w, 40, 128);
                break;
            }
        }
        int laneStep = laneW + laneGapX;
        int axisMin = boardTop + SUMMARY_H + 4;
        int axisMax = boardTop + boardH - 2;

        int[] size = new int[n];
        int[] desired = new int[n];
        for (int i = 0; i < n; i++) {
            size[i] = chipH;
            desired[i] = Mth.clamp(items.get(i).cy() - chipH / 2, axisMin, axisMax - chipH);
        }
        int[] lane = new int[n];
        int[] startY = new int[n];
        shelfPack(n, size, desired, axisMin, axisMax, CALLOUT_LANE_GAP, maxLanes, lane, startY);

        int[] chipX = new int[n];
        int[] chipW = new int[n];
        boolean[] hover = new boolean[n];
        int[] accent = new int[n];
        for (int i = 0; i < n; i++) {
            int laneEdge = leftSide ? near - lane[i] * laneStep : near + lane[i] * laneStep;
            int avail = leftSide ? laneEdge - far : far - laneEdge;
            chipW[i] = preferredChipWidth(items.get(i), Mth.clamp(avail, 24, laneW));
            chipX[i] = leftSide ? laneEdge - chipW[i] : laneEdge;
            hover[i] = calloutHovered(items.get(i), chipX[i], startY[i], chipW[i], chipH, mouseX, mouseY);
            accent[i] = statusColor(scanner.getStatus(items.get(i).glfw()));
        }
        // Pass 1: leader lines.
        for (int i = 0; i < n; i++) {
            CalloutItem item = items.get(i);
            int lineColor = UITheme.withAlpha(accent[i], calloutAlpha(hover[i], lane[i]));
            int keyAnchorX = leftSide ? item.kx() : item.kx() + item.kw();
            int railX = leftSide ? chipX[i] + chipW[i] : chipX[i];
            fillSeg(g, keyAnchorX, railX, item.cy(), lineColor);
            fillVSeg(g, railX, item.cy(), startY[i] + chipH / 2, lineColor);
            g.fill(keyAnchorX - 1, item.cy() - 1, keyAnchorX + 1, item.cy() + 1, lineColor);
        }
        // Pass 2: chips.
        for (int i = 0; i < n; i++) {
            renderCalloutChip(g, items.get(i), chipX[i], startY[i], chipW[i], chipH,
                    leftSide ? CalloutSide.LEFT : CalloutSide.RIGHT, hover[i], accent[i], lane[i]);
        }
    }

    /** Outer lanes draw fainter leader lines, reinforcing the tiered depth. */
    private static int calloutAlpha(boolean hover, int lane) {
        return hover ? 0xFF : Math.max(0x70, 0xB4 - lane * 0x1C);
    }

    private int preferredChipWidth(CalloutItem item, int maxW) {
        int safeMax = Math.max(24, maxW);
        String fitted = KeybindViewerScreen.fitToWidth(font, item.text(), safeMax - 12);
        return Math.min(safeMax, Math.max(24, font.width(fitted) + 14));
    }

    private boolean calloutHovered(CalloutItem item, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hover = KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h)
                || KeybindViewerScreen.inside(mouseX, mouseY, item.kx(), item.ky(), item.kw(), item.kh());
        if (hover) hoveredCalloutKey = item.glfw();
        return hover;
    }

    private void renderCalloutChip(GuiGraphics g, CalloutItem item, int x, int y, int w, int h,
                                   CalloutSide side, boolean hover, int accent, int lane) {
        var c = UITheme.colors();
        // Recede outer tiers a little (lower opacity the farther from the keyboard) so the lanes read
        // as depth layers — the hovered chip always snaps back to full strength and front.
        int depth = hover ? 0 : Math.min(lane, 3);
        int bgAlpha = 0xF0 - depth * 0x1A;
        int borderAlpha = (hover ? 0xE0 : 0x96) - depth * 0x12;
        int bg = UITheme.withAlpha(hover ? UITheme.lerpColor(c.headerBg(), accent, 0.30f) : c.headerBg(), bgAlpha);
        UITheme.fillRoundedRectFast(g, x, y, w, h, 3, bg);
        UITheme.drawRoundedBorderFast(g, x, y, w, h, 3, UITheme.withAlpha(accent, borderAlpha));
        switch (side) {
            case LEFT -> g.fill(x + w - 2, y + 1, x + w, y + h - 1, accent);
            case RIGHT -> g.fill(x, y + 1, x + 2, y + h - 1, accent);
            case TOP -> g.fill(x + 1, y + h - 2, x + w - 1, y + h, accent);
            case BOTTOM -> g.fill(x + 1, y, x + w - 1, y + 2, accent);
        }
        String fitted = KeybindViewerScreen.fitToWidth(font, item.text(), w - 12);
        g.drawString(font, fitted, x + 5, y + (h - font.lineHeight) / 2 + 1, c.textPrimary(), false);
        calloutHits.add(new CalloutHit(x, y, w, h, item.mappingName(), item.glfw()));
    }

    private static void fillSeg(GuiGraphics g, int x1, int x2, int y, int color) {
        g.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, color);
    }

    private static void fillVSeg(GuiGraphics g, int x, int y1, int y2, int color) {
        g.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, color);
    }

    private void renderHoverDetails(GuiGraphics g, int mouseX, int mouseY) {
        // Hovering any bound key shows its full binding info, regardless of the annotation scope.
        Integer key = hoveredCalloutKey != null ? hoveredCalloutKey : keyAt(mouseX, mouseY);
        if (key != null && !scanner.getBindings(key).isEmpty()) {
            tooltipRenderer.render(g, font, width, height, key, mouseX, mouseY);
        }
    }

    private void renderLegend(GuiGraphics g) {
        var c = UITheme.colors();
        int y = boardTop + 5;
        // Mirror the key status palette the keyboard actually paints (see statusColor / keyStatusColor):
        // bound keys are green, combos yellow, conflicts red, free keys neutral. The legend used to
        // show "assigned" as the blue accent, which never matched the green keys on screen.
        int[] swatches = { c.success(), c.warning(), c.danger(), c.widgetBorder() };
        String[] keys = {
                "screen.newvisualkeybing.board.legend.assigned",
                "screen.newvisualkeybing.board.legend.combo",
                "screen.newvisualkeybing.board.legend.conflict",
                "screen.newvisualkeybing.board.legend.unused",
        };
        int w = 0;
        for (int i = 0; i < keys.length; i++) {
            w += 12 + font.width(Component.translatable(keys[i]).getString());
            if (i > 0) w += 10;
        }
        int x = Math.max(boardX + 2, boardX + boardW - w - 12);
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) x += 10;
            x = legendItem(g, x, y, swatches[i], keys[i]);
        }
    }

    private int legendItem(GuiGraphics g, int x, int y, int swatch, String key) {
        var c = UITheme.colors();
        UITheme.fillRoundedRectFast(g, x, y + 1, 8, 8, 2, swatch);
        UITheme.drawRoundedBorderFast(g, x, y + 1, 8, 8, 2, UITheme.withAlpha(c.widgetBorder(), 0xB0));
        String label = Component.translatable(key).getString();
        g.drawString(font, label, x + 12, y, c.textSecondary(), false);
        return x + 12 + font.width(label);
    }

    /** Keyboard key (glfw code) whose on-screen rect contains the point, or null. */
    private Integer keyAt(double mouseX, double mouseY) {
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.getKeys(currentStyle)) {
            int kx = key.screenX(keyboardX, keyScale);
            int ky = key.screenY(keyboardY, keyScale);
            int kw = key.screenW(keyScale);
            int kh = key.screenH(keyScale);
            if (KeybindViewerScreen.inside(mouseX, mouseY, kx, ky, kw, kh)) return key.glfwKey();
        }
        return null;
    }

    private FuncEntry funcRowAt(double mouseX, double mouseY) {
        if (functionsCollapsed) return null;
        int listTop = listTop();
        int listH = listHeight();
        int innerX = BODY_PAD + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;
        if (!KeybindViewerScreen.inside(mouseX, mouseY, innerX, listTop, innerW, listH)) return null;
        int drawY = listTop - listScroll;
        for (Object entry : entries) {
            int eh = entry instanceof CategoryEntry ? CAT_H : ROW_H;
            if (entry instanceof FuncEntry fe
                    && KeybindViewerScreen.inside(mouseX, mouseY, innerX, drawY, innerW, ROW_H - 2)) {
                return fe;
            }
            drawY += eh;
        }
        return null;
    }

    /** Category header at the point (for fold toggling), or null. */
    private String categoryAt(double mouseX, double mouseY) {
        if (functionsCollapsed) return null;
        int listTop = listTop();
        int listH = listHeight();
        int innerX = BODY_PAD + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;
        if (!KeybindViewerScreen.inside(mouseX, mouseY, innerX, listTop, innerW, listH)) return null;
        int drawY = listTop - listScroll;
        for (Object entry : entries) {
            int eh = entry instanceof CategoryEntry ? CAT_H : ROW_H;
            if (entry instanceof CategoryEntry ce
                    && KeybindViewerScreen.inside(mouseX, mouseY, innerX, drawY, innerW, CAT_H)) {
                return ce.name();
            }
            drawY += eh;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        int innerX = BODY_PAD + PANEL_PAD;
        int innerW = PANEL_W - PANEL_PAD * 2;
        // Collapse/expand the functions panel. When folded, a click anywhere on the rail expands it.
        int[] toggle = panelToggleRect();
        if (KeybindViewerScreen.inside(mx, my, toggle[0], toggle[1], toggle[2], toggle[3])) {
            toggleFunctionsCollapsed();
            return true;
        }
        if (functionsCollapsed) {
            int railH = height - PANEL_TOP - FOOTER_H - 4;
            if (KeybindViewerScreen.inside(mx, my, BODY_PAD, PANEL_TOP, PANEL_COLLAPSED_W, railH)) {
                toggleFunctionsCollapsed();
                return true;
            }
        }
        if (!functionsCollapsed) {
            if (KeybindViewerScreen.inside(mx, my, innerX, FILTER_Y, innerW, FILTER_H)) {
                modDropdownOpen = !modDropdownOpen;
                modDropdownScroll = 0;
                return true;
            }
            if (modDropdownOpen) {
                // Selecting a row applies the filter; clicking anywhere else just closes the dropdown.
                handleModDropdownClick(mx, my, innerX, innerW);
                modDropdownOpen = false;
                return true;
            }
            if (handleSearchClearClick(mx, my)) return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            Integer key = keyAt(mx, my);
            // Click-to-bind: a function is armed in the palette and the user clicks a key for it.
            if (selectedMapping != null && key != null) {
                KeyMapping mapping = selectedMapping;
                clearSelection();
                bindToKey(mapping, key);
                return true;
            }
            // Press on a callout chip: grab that one function so it can be dragged onto another key
            // (rebind/move) or onto the unbind zone (clear).
            for (CalloutHit hit : calloutHits) {
                if (KeybindViewerScreen.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    KeyMapping m = mappingByName(hit.mappingName());
                    if (m != null) {
                        beginPress(m, hit.glfwKey(), mx, my);
                        return true;
                    }
                }
            }
            // Click a category header to fold / unfold its function rows.
            String cat = categoryAt(mx, my);
            if (cat != null) {
                if (!collapsedCategories.remove(cat)) collapsedCategories.add(cat);
                rebuildEntries();
                return true;
            }
            // Press on a palette row: arm for either a click (select) or a drag (drag-bind).
            FuncEntry fe = funcRowAt(mx, my);
            if (fe != null) {
                beginPress(fe.mapping, null, mx, my);
                if (searchBox != null) searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            // Press on a bound key: arm a drag that can move it to another key or drop it to unbind.
            if (key != null) {
                KeyMapping onKey = primaryMappingOn(key);
                if (onKey != null) {
                    beginPress(onKey, key, mx, my);
                    return true;
                }
            }
            // Click on empty space cancels a pending palette selection.
            if (selectedMapping != null) {
                clearSelection();
                return true;
            }
        }
        return false;
    }

    private void beginPress(KeyMapping mapping, Integer fromKey, double mx, double my) {
        pressArmed = true;
        pressMapping = mapping;
        pressFromKey = fromKey;
        pressX = mx;
        pressY = my;
    }

    private void clearPress() {
        pressArmed = false;
        pressMapping = null;
        pressFromKey = null;
    }

    private void endDrag() {
        dragging = null;
        dragLabel = null;
        overUnbindZone = false;
        clearPress();
    }

    private void clearSelection() {
        selectedMapping = null;
        selectedLabel = null;
    }

    /** The top (highest-priority) mapping currently bound to a keyboard key, or null if none. */
    private KeyMapping primaryMappingOn(int glfwKey) {
        List<KeyBindingScanner.KeyBindingInfo> binds = scanner.getBindings(glfwKey);
        if (binds.isEmpty()) return null;
        return mappingByName(binds.get(0).translationKey());
    }

    private KeyMapping mappingByName(String name) {
        if (name == null) return null;
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            if (km.getName().equals(name)) return km;
        }
        return null;
    }

    private void handleModDropdownClick(double mouseX, double mouseY, int x, int w) {
        int y = FILTER_Y + FILTER_H + 2;
        int h = height - y - FOOTER_H - 8;
        if (!KeybindViewerScreen.inside(mouseX, mouseY, x, y, w, h)) return;
        List<String> ids = modIds();
        int visible = Math.max(1, (h - 6) / MOD_ROW_H);
        int rowY = y + 3;
        for (int i = modDropdownScroll; i < ids.size() && i < modDropdownScroll + visible; i++) {
            if (KeybindViewerScreen.inside(mouseX, mouseY, x + 3, rowY, w - 6, MOD_ROW_H - 1)) {
                selectedModId = ids.get(i);
                modDropdownOpen = false;
                listScroll = 0;
                rebuildEntries();
                return;
            }
            rowY += MOD_ROW_H;
        }
    }

    private boolean handleSearchClearClick(double mouseX, double mouseY) {
        if (searchBox == null || searchBox.getValue().isEmpty()) return false;
        if (searchBox.clearAffordanceClicked(mouseX, mouseY)) {
            searchBox.setValue("");
            searchBox.setFocused(true);
            setFocused(searchBox);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        if (button == 0 && pressArmed && dragging == null) {
            double ddx = mx - pressX;
            double ddy = my - pressY;
            if (ddx * ddx + ddy * ddy >= DRAG_THRESHOLD_SQ) {
                dragging = pressMapping;
                dragLabel = Component.translatable(pressMapping.getName()).getString();
                clearSelection();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        double my = fixedMouseY(mouseY);
        if (button == 0 && dragging != null) {
            KeyMapping mapping = dragging;
            boolean toUnbind = insideUnbindZone(mx, my);
            Integer target = keyAt(mx, my);
            endDrag();
            if (toUnbind) {
                if (!mapping.isUnbound()) unbind(mapping);
            } else if (target != null) {
                bindToKey(mapping, target);
            }
            return true;
        }
        if (button == 0 && pressArmed) {
            // A press that never crossed the drag threshold counts as a click. Only palette rows
            // toggle the click-to-bind selection; clicking a bound key in place does nothing (drag
            // it onto another key to move, or onto the unbind zone to clear).
            KeyMapping mapping = pressMapping;
            Integer fromKey = pressFromKey;
            clearPress();
            if (fromKey == null) {
                if (selectedMapping == mapping) {
                    clearSelection();
                } else {
                    selectedMapping = mapping;
                    selectedLabel = Component.translatable(mapping.getName()).getString();
                }
            }
            return true;
        }
        return releaseLogicalMouse(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        applyFixedScaleMetrics();
        double mx = fixedMouseX(mouseX);
        if (modDropdownOpen) {
            modDropdownScroll = Math.max(0, modDropdownScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (!functionsCollapsed && mx < BODY_PAD + PANEL_W) {
            listScroll = Mth.clamp(listScroll - (int) (scrollY * ROW_H * 2), 0,
                    Math.max(0, totalListH - listHeight()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && modDropdownOpen) {
            modDropdownOpen = false;
            return true;
        }
        if (dragging != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            endDrag();
            return true;
        }
        if (selectedMapping != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searchBox != null && searchBox.isFocused()) {
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            searchBox.setFocused(false);
            setFocused(null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void bindToKey(KeyMapping mapping, int glfwKey) {
        if (mapping == null || glfwKey == GLFW.GLFW_KEY_UNKNOWN) return;
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        Minecraft mc = Minecraft.getInstance();
        KeybindPriorityEnforcer.rebind(mapping, key);
        KeybindComboStore.global().removeCombo(mapping.getName());
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        scanner.scan();
        showNotice(Component.translatable("screen.newvisualkeybing.board.bound",
                Component.translatable(mapping.getName()).getString(),
                key.getDisplayName().getString()).getString());
    }

    private void unbind(KeyMapping mapping) {
        if (mapping == null) return;
        Minecraft mc = Minecraft.getInstance();
        KeybindPriorityEnforcer.rebind(mapping, InputConstants.UNKNOWN);
        KeybindComboStore.global().removeCombo(mapping.getName());
        KeybindPriorityEnforcer.resetAndEnforce();
        mc.options.save();
        scanner.scan();
        showNotice(Component.translatable("screen.newvisualkeybing.board.unbound",
                Component.translatable(mapping.getName()).getString()).getString());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum CalloutSide { TOP, RIGHT, BOTTOM, LEFT }
    /** One label = one bound function on a key. {@code text} is the action name, {@code mappingName}
     *  the binding's translation key (used to grab it for a drag). */
    private record CalloutItem(int glfw, int kx, int ky, int kw, int kh, int cx, int cy,
                               String text, String mappingName) {}
    private record CalloutHit(int x, int y, int w, int h, String mappingName, int glfwKey) {}
    private record CategoryEntry(String name, int count, boolean collapsed) {}
    private record FuncEntry(KeyMapping mapping) {}
}
