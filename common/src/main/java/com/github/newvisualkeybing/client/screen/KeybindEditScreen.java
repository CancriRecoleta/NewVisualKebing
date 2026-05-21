package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeyBindingScanner;
import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.github.newvisualkeybing.client.keyboard.KeybindPriorityEnforcer;
import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.keyboard.KeyboardLayoutData;
import com.github.newvisualkeybing.client.ui.MCButton;
import com.github.newvisualkeybing.client.ui.MCEditBox;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
















public class KeybindEditScreen extends FixedScaleScreen {

    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 24;
    private static final int ENTRY_H = 24;
    private static final int CATEGORY_H = 20;
    private static final int CHANGE_BTN_W = 132;
    private static final int RESET_BTN_W = 72;
    private static final int COL_GAP = 4;

    private final Screen parent;
    private final Integer focusVirtualKey; 

    private MCEditBox searchBox;
    private MCButton resetAllButton;
    private MCButton viewerButton;
    private MCButton comboButton;
    private MCButton backButton;
    private final KeybindProfileStore profileStore = KeybindProfileStore.global();
    private final KeybindProfilePanel profilePanel = new KeybindProfilePanel(profileStore, this::rebuildEntries, this::showNotice);
    private final KeybindPriorityControls priorityControls = new KeybindPriorityControls(profileStore);

    private final List<Object> entries = new ArrayList<>();
    private int scrollOffset;
    private int totalListH;

    private KeyMapping waitingMapping;
    private InputConstants.Key captureFirstKey;
    private String noticeMessage;
    private long noticeUntil;
    private int searchX;
    private int searchW;

    public KeybindEditScreen(Screen parent) {
        this(parent, null);
    }

    public KeybindEditScreen(Screen parent, Integer focusVirtualKey) {
        super(Component.translatable("screen.newvisualkeybing.viewer.edit_title"));
        this.parent = parent;
        this.focusVirtualKey = focusVirtualKey;
    }

    @Override
    protected void init() {
        super.init();
        applyFixedScaleMetrics();

        searchX = KeybindProfilePanel.WIDTH + 22;
        int btnGap = 6;
        int backW = 60;
        int viewerW = 96;
        int comboW = 96;
        int resetW = 110;
        int xReset = width - 12 - resetW;
        int xViewer = xReset - btnGap - viewerW;
        int xCombo = xViewer - btnGap - comboW;
        int xBack = xCombo - btnGap - backW;
        searchW = Mth.clamp(xBack - searchX - 14, 130, 330);
        int searchH = 22;
        int editY = 7;
        searchBox = new MCEditBox(font, searchX, editY, searchW, searchH,
                Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withPlaceholder(Component.translatable("screen.newvisualkeybing.viewer.search"))
                .withClearAffordance(true);
        searchBox.setResponder(value -> rebuildEntries());
        addRenderableWidget(searchBox);

        backButton = MCButton.create(xBack, 8, backW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.back"), b -> onClose());
        addRenderableWidget(backButton);

        comboButton = MCButton.create(xCombo, 8, comboW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.combo.open"),
                b -> minecraft.setScreen(new KeybindComboManageScreen(this)));
        addRenderableWidget(comboButton);

        viewerButton = MCButton.create(xViewer, 8, viewerW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.open_visual"),
                b -> minecraft.setScreen(new KeybindViewerScreen(parent)));
        addRenderableWidget(viewerButton);

        resetAllButton = MCButton.create(xReset, 8, resetW, 20,
                Component.translatable("screen.newvisualkeybing.viewer.reset_all"), b -> resetAllMappings());
        addRenderableWidget(resetAllButton);

        rebuildEntries();
    }

    private void rebuildEntries() {
        entries.clear();
        Map<String, List<KeyMapping>> grouped = new LinkedHashMap<>();
        for (KeyMapping km : profileStore.sortedMappings(Minecraft.getInstance().options.keyMappings)) {
            String cat = Component.translatable(km.getCategory()).getString();
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(km);
        }

        String q = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        grouped.forEach((cat, mappings) -> {
            List<KeyMapping> filtered = q.isBlank() ? mappings :
                    mappings.stream().filter(km ->
                            Component.translatable(km.getName()).getString().toLowerCase().contains(q)
                                    || cat.toLowerCase().contains(q)).toList();
            if (!filtered.isEmpty()) {
                entries.add(new CategoryEntry(cat));
                for (KeyMapping km : filtered) entries.add(new KeyEntry(km));
            }
        });

        int h = 0;
        for (Object e : entries) h += entryHeight(e);
        totalListH = h;
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, totalListH - listHeight()));
    }

    private boolean matchesFocus(KeyMapping km) {
        InputConstants.Key key = ((KeyMappingAccessor) (Object) km).newvisualkeybing$getKey();
        if (key == InputConstants.UNKNOWN) return false;
        if (KeyboardLayoutData.isMouse(focusVirtualKey)) {
            return key.getType() == InputConstants.Type.MOUSE
                    && key.getValue() == KeyboardLayoutData.virtualToMouseBtn(focusVirtualKey);
        }
        return key.getType() != InputConstants.Type.MOUSE && key.getValue() == focusVirtualKey;
    }

    private int entryHeight(Object e) {
        if (e instanceof CategoryEntry) return CATEGORY_H;
        if (e instanceof KeyEntry) return ENTRY_H;
        return 0;
    }

    private int listTop() { return HEADER_H + 4; }
    private int listHeight() { return height - HEADER_H - FOOTER_H - 8; }
    private int listX() { return KeybindProfilePanel.WIDTH + 14; }
    private int listW() { return width - listX() - 8; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyFixedScaleMetrics();
        int fixedMouseX = fixedMouseX(mouseX);
        int fixedMouseY = fixedMouseY(mouseY);
        pushFixedScale(graphics);
        try {
        var colors = UITheme.colors();
        graphics.fill(0, 0, width, height, UITheme.withAlpha(colors.panelBg(), 0xE4));

        renderHeader(graphics);
        profilePanel.render(graphics, font, 8, listTop(), listHeight(), fixedMouseX, fixedMouseY);
        renderEntries(graphics, fixedMouseX, fixedMouseY);
        renderFooter(graphics);
        super.render(graphics, fixedMouseX, fixedMouseY, partialTick);

        if (waitingMapping != null) renderWaitingOverlay(graphics);
        renderNotice(graphics);
        } finally {
            popFixedScale(graphics);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        var colors = UITheme.colors();
        UITheme.drawGlassPanel(graphics, 4, 4, width - 8, HEADER_H - 4, 8);


        String title = focusVirtualKey != null
                ? Component.translatable("screen.newvisualkeybing.viewer.edit_title_focused",
                    targetKeyName()).getString()
                : Component.translatable("screen.newvisualkeybing.viewer.edit_title").getString();
        int titleY = 8 + (20 - font.lineHeight) / 2 + 1;
        int titleX = searchX + searchW + 12;
        int titleRight = backButton == null ? width - 12 : backButton.getX() - 8;
        graphics.drawString(font, KeybindViewerScreen.fitToWidth(font, title, Math.max(40, titleRight - titleX)),
                titleX, titleY, colors.textPrimary(), false);
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

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int listTop = listTop();
        int listH = listHeight();
        int x = listX();
        int w = listW();

        UITheme.fillRoundedRectFast(graphics, x, listTop, w, listH, 8, UITheme.withAlpha(colors.headerBg(), 0xC0));
        UITheme.drawRoundedBorderFast(graphics, x, listTop, w, listH, 8, colors.widgetBorder());

        enableFixedScissor(graphics, x + 1, listTop + 1, x + w - 1, listTop + listH - 1);
        int drawY = listTop + 4 - scrollOffset;
        KeyMapping[] all = Minecraft.getInstance().options.keyMappings;

        for (Object entry : entries) {
            int eh = entryHeight(entry);
            if (drawY + eh >= listTop && drawY <= listTop + listH) {
                if (entry instanceof CategoryEntry ce) {
                    renderCategory(graphics, ce, x + 8, drawY, w - 16);
                } else if (entry instanceof KeyEntry ke) {
                    boolean hovered = mouseX >= x + 8 && mouseX < x + w - 8 && mouseY >= drawY && mouseY < drawY + ENTRY_H;
                    renderKeyEntry(graphics, ke, x + 8, drawY, w - 16, mouseX, mouseY, hovered, all);
                }
            }
            drawY += eh;
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

    private void renderCategory(GuiGraphics graphics, CategoryEntry ce, int x, int y, int w) {
        var colors = UITheme.colors();
        UITheme.fillRoundedRectFast(graphics, x, y, w, CATEGORY_H, 4, UITheme.withAlpha(colors.accent(), 0x22));
        UITheme.fillRoundedRectFast(graphics, x, y + 2, 3, CATEGORY_H - 4, 1, colors.accent());
        graphics.drawString(font, ce.name, x + 10, y + (CATEGORY_H - font.lineHeight) / 2, colors.accentLight(), false);
    }

    private void renderKeyEntry(GuiGraphics graphics, KeyEntry ke, int x, int y, int w,
                                int mouseX, int mouseY, boolean hovered, KeyMapping[] all) {
        var colors = UITheme.colors();
        boolean isWaiting = waitingMapping == ke.mapping;
        boolean isUnbound = ke.mapping.isUnbound();
        boolean conflict = isConflicting(ke.mapping, all);
        boolean focusedTarget = focusVirtualKey != null && matchesFocus(ke.mapping);
        boolean comboAware = KeybindViewerConfig.global().comboKeysNonConflicting();
        KeybindComboStore.ComboBinding combo = KeybindComboStore.global().findByMapping(ke.mapping.getName());
        boolean hasCombo = comboAware && KeybindComboStore.global().matchesCurrentCombo(ke.mapping);
        int yellow = KeybindKeyboardRenderer.COMBO_HIGHLIGHT_COLOR;

        if (focusedTarget) {
            UITheme.fillRoundedRectFast(graphics, x, y, w, ENTRY_H - 2, 5, UITheme.withAlpha(colors.accent(), 0x36));
            UITheme.fillRoundedRectFast(graphics, x, y + 2, 3, ENTRY_H - 6, 1, colors.accent());
        } else if (isWaiting) {
            UITheme.fillRoundedRectFast(graphics, x, y, w, ENTRY_H - 2, 5, UITheme.withAlpha(colors.accent(), 0x55));
        } else if (hovered) {
            UITheme.fillRoundedRectFast(graphics, x, y, w, ENTRY_H - 2, 5, UITheme.withAlpha(colors.widgetBg(), 0xA0));
        }

        String name = Component.translatable(ke.mapping.getName()).getString();
        int nameMaxW = w - CHANGE_BTN_W - RESET_BTN_W
                - KeybindPriorityControls.WIDTH - COL_GAP * 5 - 12;
        if (font.width(name) > nameMaxW) name = font.plainSubstrByWidth(name, nameMaxW - 6) + "..";
        graphics.drawString(font, name, x + 8, y + (ENTRY_H - font.lineHeight) / 2, colors.textPrimary(), false);

        int priorityX = x + w - KeybindPriorityControls.WIDTH - CHANGE_BTN_W - RESET_BTN_W - COL_GAP * 3;
        priorityControls.render(graphics, font, ke.mapping, priorityX, y, ENTRY_H, mouseX, mouseY, hovered);

        int changeX = priorityX + KeybindPriorityControls.WIDTH + COL_GAP;
        String changeLabel;
        if (isWaiting) {
            changeLabel = "> ... <";
        } else if (isUnbound) {
            changeLabel = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
        } else if (hasCombo) {
            changeLabel = combo.displayFirst() + " + " + ke.mapping.getTranslatedKeyMessage().getString();
        } else {
            changeLabel = ke.mapping.getTranslatedKeyMessage().getString();
        }
        if (font.width(changeLabel) > CHANGE_BTN_W - 8) {
            changeLabel = font.plainSubstrByWidth(changeLabel, CHANGE_BTN_W - 14) + "..";
        }
        boolean chHover = !isWaiting && hovered && mouseX >= changeX && mouseX < changeX + CHANGE_BTN_W
                && mouseY >= y + 1 && mouseY < y + ENTRY_H - 1;
        int chBg = isWaiting ? UITheme.withAlpha(colors.accent(), 0xC0)
                : chHover ? UITheme.lerpColor(colors.widgetBg(), hasCombo ? yellow : colors.accent(), 0.45f)
                : colors.widgetBg();

        int statusColor = focusedTarget ? colors.accent()
                : conflict ? colors.dangerColor()
                : focusVirtualKey != null && isUnbound ? colors.accentLight()
                : isUnbound ? colors.textMuted()
                : hasCombo ? yellow
                : colors.accentLight();
        UITheme.fillRoundedRectFast(graphics, changeX, y + 1, CHANGE_BTN_W, ENTRY_H - 4, 4, chBg);
        UITheme.drawRoundedBorderFast(graphics, changeX, y + 1, CHANGE_BTN_W, ENTRY_H - 4, 4, UITheme.withAlpha(statusColor, 0xB0));
        UITheme.fillRoundedRectFast(graphics, changeX + 4, y + 1, CHANGE_BTN_W - 8, 2, 1, statusColor);
        int chTextX = changeX + (CHANGE_BTN_W - font.width(changeLabel)) / 2;
        int chTextY = y + (ENTRY_H - font.lineHeight) / 2;
        int chTextColor = focusedTarget ? colors.accentLight()
                : isUnbound ? colors.textMuted() : conflict ? colors.dangerColor()
                : hasCombo ? yellow
                : colors.textPrimary();
        graphics.drawString(font, changeLabel, chTextX, chTextY, chTextColor, false);

        int resetX = changeX + CHANGE_BTN_W + COL_GAP;
        boolean isDefault = ke.mapping.isDefault();
        boolean rsHover = hovered && !isDefault && mouseX >= resetX && mouseX < resetX + RESET_BTN_W
                && mouseY >= y + 1 && mouseY < y + ENTRY_H - 1;
        int rsBg = isDefault ? UITheme.withAlpha(colors.widgetBg(), 0x60)
                : rsHover ? UITheme.lerpColor(colors.widgetBg(), colors.successColor(), 0.40f)
                : colors.widgetBg();
        UITheme.fillRoundedRectFast(graphics, resetX, y + 1, RESET_BTN_W, ENTRY_H - 4, 4, rsBg);
        UITheme.drawRoundedBorderFast(graphics, resetX, y + 1, RESET_BTN_W, ENTRY_H - 4, 4,
                isDefault ? UITheme.withAlpha(colors.widgetBorder(), 0x60) : UITheme.withAlpha(colors.successColor(), 0x88));
        String defLabel = ke.mapping.getDefaultKey().getDisplayName().getString();
        if (defLabel.length() > 8) defLabel = defLabel.substring(0, 8) + "..";
        defLabel = "(" + defLabel + ")";
        int rsTextColor = isDefault ? colors.textMuted() : rsHover ? colors.successColor() : colors.textSecondary();
        graphics.drawString(font, defLabel, resetX + (RESET_BTN_W - font.width(defLabel)) / 2,
                y + (ENTRY_H - font.lineHeight) / 2, rsTextColor, false);
    }

    private void renderFooter(GuiGraphics graphics) {
        var colors = UITheme.colors();
        int y = height - FOOTER_H;
        graphics.fill(0, y, width, y + 1, colors.divider());
        String hint;
        if (waitingMapping != null) {
            hint = Component.translatable("screen.newvisualkeybing.viewer.waiting_combo").getString();
        } else {
            hint = Component.translatable("screen.newvisualkeybing.viewer.edit_hint_combo").getString();
        }
        graphics.drawString(font, hint, (width - font.width(hint)) / 2,
                y + (FOOTER_H - font.lineHeight) / 2, colors.textSecondary(), false);
    }

    private void renderWaitingOverlay(GuiGraphics graphics) {
        var colors = UITheme.colors();
        graphics.fill(0, 0, width, height, 0xC0000000);
        int bw = 320, bh = 80;
        int bx = (width - bw) / 2, by = (height - bh) / 2;
        UITheme.drawGlassPanel(graphics, bx, by, bw, bh, 10);
        UITheme.fillRoundedRectFast(graphics, bx + 8, by, bw - 16, 1, 1, colors.accent());
        String l1 = Component.translatable("screen.newvisualkeybing.viewer.waiting").getString();
        String l2 = Component.translatable(waitingMapping.getName()).getString();
        String l3 = Component.translatable("screen.newvisualkeybing.viewer.waiting_hint").getString();
        graphics.drawString(font, l1, bx + (bw - font.width(l1)) / 2, by + 12, colors.accentLight(), true);
        graphics.drawString(font, l2, bx + (bw - font.width(l2)) / 2, by + 30, colors.textPrimary(), true);
        graphics.drawString(font, l3, bx + (bw - font.width(l3)) / 2, by + bh - 16, colors.textMuted(), false);
    }

    private void renderNotice(GuiGraphics graphics) {
        if (noticeMessage == null) return;
        long now = System.currentTimeMillis();
        if (now > noticeUntil) { noticeMessage = null; return; }
        var colors = UITheme.colors();
        int w = font.width(noticeMessage) + 24;
        int x = (width - w) / 2;
        int y = height - FOOTER_H - 26;
        UITheme.fillRoundedRectFast(graphics, x, y, w, 20, 6, UITheme.withAlpha(colors.successBg(), 0xE0));
        UITheme.drawRoundedBorderFast(graphics, x, y, w, 20, 6, colors.successColor());
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
        if (waitingMapping != null) {
            if (button >= GLFW.GLFW_MOUSE_BUTTON_1 && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
                handleCapturePress(InputConstants.Type.MOUSE.getOrCreate(button));
            }
            return true;
        }
        if (handleSearchClearClick(mouseX, mouseY)) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (profilePanel.mouseClicked(mouseX, mouseY, 8, listTop(), listHeight())) return true;

        int x = listX(), w = listW();
        int listTop = listTop();
        int listH = listHeight();
        if (mouseX >= x && mouseX < x + w && mouseY >= listTop && mouseY < listTop + listH) {
            int relY = (int) (mouseY - listTop - 4) + scrollOffset;
            int drawY = 0;
            for (Object entry : entries) {
                int eh = entryHeight(entry);
                if (entry instanceof KeyEntry ke && relY >= drawY && relY < drawY + ENTRY_H) {
                    int rowX = x + 8;
                    int rowW = w - 16;
                    int priorityX = rowX + rowW - KeybindPriorityControls.WIDTH
                            - CHANGE_BTN_W - RESET_BTN_W - COL_GAP * 3;
                    int priorityDelta = priorityControls.hitDelta(mouseX, priorityX);
                    if (priorityDelta != 0) {
                        profileStore.changePriority(ke.mapping, priorityDelta);
                        rebuildEntries();
                        showNotice(Component.translatable("screen.newvisualkeybing.viewer.priority.changed",
                                Component.translatable(ke.mapping.getName()).getString(),
                                profileStore.priorityOf(ke.mapping)).getString());
                        return true;
                    }
                    int changeX = priorityX + KeybindPriorityControls.WIDTH + COL_GAP;
                    int resetX = changeX + CHANGE_BTN_W + COL_GAP;
                    if (mouseX >= changeX && mouseX < changeX + CHANGE_BTN_W) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            KeybindComboStore.ComboBinding existing =
                                    KeybindComboStore.global().findByMapping(ke.mapping.getName());
                            if (existing != null) {
                                KeybindComboStore.global().removeCombo(ke.mapping.getName());
                                rebuildEntries();
                                showNotice(Component.translatable(
                                        "screen.newvisualkeybing.viewer.combo.modifier_cleared",
                                        Component.translatable(ke.mapping.getName()).getString()).getString());
                                return true;
                            }
                        }
                        if (focusVirtualKey != null) {
                            applyFocusedTarget(ke.mapping);
                        } else {
                            waitingMapping = ke.mapping;
                        }
                        return true;
                    }
                    if (mouseX >= resetX && mouseX < resetX + RESET_BTN_W) {
                        Minecraft.getInstance().options.setKey(ke.mapping, ke.mapping.getDefaultKey());
                        KeybindComboStore.global().removeCombo(ke.mapping.getName());
                        KeybindPriorityEnforcer.resetAndEnforce();
                        Minecraft.getInstance().options.save();
                        rebuildEntries();
                        showNotice(Component.translatable("screen.newvisualkeybing.viewer.reset_one",
                                Component.translatable(ke.mapping.getName()).getString()).getString());
                        return true;
                    }
                    return true;
                }
                drawY += eh;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (waitingMapping != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                handleCapturePress(InputConstants.UNKNOWN);
                return true;
            }
            handleCapturePress(InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        if (profilePanel.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchBox != null && searchBox.isFocused()) {
                if (!searchBox.getValue().isEmpty()) {
                    searchBox.setValue("");
                    return true;
                }
                searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        applyFixedScaleMetrics();
        if (waitingMapping != null && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            handleCaptureRelease(InputConstants.getKey(keyCode, scanCode));
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (waitingMapping != null && button >= GLFW.GLFW_MOUSE_BUTTON_1
                && button <= GLFW.GLFW_MOUSE_BUTTON_LAST) {
            handleCaptureRelease(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return releaseLogicalMouse(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        applyFixedScaleMetrics();
        if (profilePanel.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        applyFixedScaleMetrics();
        mouseX = fixedMouseX(mouseX);
        mouseY = fixedMouseY(mouseY);
        if (waitingMapping != null) {

            showNotice(Component.translatable("screen.newvisualkeybing.viewer.wheel_unsupported").getString());
            return true;
        }
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * ENTRY_H * 2), 0, Math.max(0, totalListH - listHeight()));
        return true;
    }

    private void handleCapturePress(InputConstants.Key key) {
        if (waitingMapping == null || key == null) return;
        if (key == InputConstants.UNKNOWN) {
            commitBinding(null, InputConstants.UNKNOWN);
            return;
        }
        if (captureFirstKey == null) {
            captureFirstKey = key;
            return;
        }
        if (sameKey(captureFirstKey, key)) return;
        commitBinding(captureFirstKey, key);
    }

    private void handleCaptureRelease(InputConstants.Key key) {
        if (waitingMapping == null || captureFirstKey == null || key == null) return;
        if (!sameKey(captureFirstKey, key)) return;
        commitBinding(null, captureFirstKey);
    }

    private void commitBinding(InputConstants.Key modifier, InputConstants.Key trigger) {
        KeyMapping km = waitingMapping;
        if (km == null) return;
        KeybindComboStore store = KeybindComboStore.global();
        Minecraft.getInstance().options.setKey(km, trigger);
        if (modifier != null && trigger != InputConstants.UNKNOWN) {
            store.putCombo(km, modifier, trigger);
        } else {
            store.removeCombo(km.getName());
        }
        KeybindPriorityEnforcer.resetAndEnforce();
        Minecraft.getInstance().options.save();
        waitingMapping = null;
        captureFirstKey = null;
        rebuildEntries();
        String label;
        if (trigger == InputConstants.UNKNOWN) {
            label = Component.translatable("screen.newvisualkeybing.viewer.unbound").getString();
        } else if (modifier != null) {
            label = modifier.getDisplayName().getString() + " + " + trigger.getDisplayName().getString();
        } else {
            label = trigger.getDisplayName().getString();
        }
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.rebound",
                Component.translatable(km.getName()).getString(), label).getString());
    }

    private static boolean sameKey(InputConstants.Key a, InputConstants.Key b) {
        if (a == null || b == null) return a == b;
        return a.getType() == b.getType() && a.getValue() == b.getValue();
    }

    private void applyFocusedTarget(KeyMapping km) {
        if (focusVirtualKey == null) {
            return;
        }
        if (KeyboardLayoutData.isWheel(focusVirtualKey)) {
            showNotice(Component.translatable("screen.newvisualkeybing.viewer.wheel_unsupported").getString());
            return;
        }
        Minecraft.getInstance().options.setKey(km, targetInputKey());
        KeybindComboStore.global().removeCombo(km.getName());
        KeybindPriorityEnforcer.resetAndEnforce();
        Minecraft.getInstance().options.save();
        rebuildEntries();
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.rebound",
                Component.translatable(km.getName()).getString(),
                targetKeyName()).getString());
    }

    private InputConstants.Key targetInputKey() {
        if (focusVirtualKey == null) {
            return InputConstants.UNKNOWN;
        }
        if (KeyboardLayoutData.isMouse(focusVirtualKey)) {
            return InputConstants.Type.MOUSE.getOrCreate(KeyboardLayoutData.virtualToMouseBtn(focusVirtualKey));
        }
        return InputConstants.getKey(focusVirtualKey, 0);
    }

    private String targetKeyName() {
        if (focusVirtualKey == null) {
            return "";
        }
        if (KeyboardLayoutData.isMouse(focusVirtualKey)) {
            return KeyBindingScanner.getMouseButtonLabel(KeyboardLayoutData.virtualToMouseBtn(focusVirtualKey));
        }
        return targetInputKey().getDisplayName().getString();
    }

    private void resetAllMappings() {
        for (KeyMapping km : Minecraft.getInstance().options.keyMappings) {
            Minecraft.getInstance().options.setKey(km, km.getDefaultKey());
        }
        KeybindComboStore.global().clear();
        KeybindPriorityEnforcer.resetAndEnforce();
        Minecraft.getInstance().options.save();
        rebuildEntries();
        showNotice(Component.translatable("screen.newvisualkeybing.viewer.reset_all_done").getString());
    }

    private static boolean isConflicting(KeyMapping km, KeyMapping[] all) {
        if (km.isUnbound()) return false;
        boolean comboAware = KeybindViewerConfig.global().comboKeysNonConflicting();
        if (!comboAware) {
            for (KeyMapping other : all) {
                if (other == km) continue;
                if (other.isUnbound()) continue;
                if (other.same(km)) return true;
            }
            return false;
        }
        KeybindComboStore store = KeybindComboStore.global();
        String currentActivator = store.activatorSignature(km, Services.PLATFORM.getKeyModifier(km));
        for (KeyMapping other : all) {
            if (other == km) continue;
            if (other.isUnbound()) continue;
            if (!sameInputKey(km, other)) continue;
            String otherActivator = store.activatorSignature(other, Services.PLATFORM.getKeyModifier(other));
            if (!Objects.equals(currentActivator, otherActivator)) continue;
            var currentContext = Services.PLATFORM.getConflictContext(km);
            var otherContext = Services.PLATFORM.getConflictContext(other);
            if (currentContext != null && otherContext != null && currentContext.conflicts(otherContext)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameInputKey(KeyMapping a, KeyMapping b) {
        InputConstants.Key ak = KeybindComboStore.currentKey(a);
        InputConstants.Key bk = KeybindComboStore.currentKey(b);
        return ak != null && bk != null && ak.getType() == bk.getType() && ak.getValue() == bk.getValue();
    }

    
    public static void unbindAllForVirtualKey(int virtualKey) {
        Minecraft minecraft = Minecraft.getInstance();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (KeyboardLayoutData.isMouse(virtualKey)) {
                int btn = KeyboardLayoutData.virtualToMouseBtn(virtualKey);
                if (key.getType() == InputConstants.Type.MOUSE && key.getValue() == btn) {
                    minecraft.options.setKey(mapping, InputConstants.UNKNOWN);
                }
            } else if (key.getType() != InputConstants.Type.MOUSE && key.getValue() == virtualKey) {
                minecraft.options.setKey(mapping, InputConstants.UNKNOWN);
            }
        }
        KeybindPriorityEnforcer.resetAndEnforce();
        minecraft.options.save();
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private record CategoryEntry(String name) {}
    private record KeyEntry(KeyMapping mapping) {}
}
