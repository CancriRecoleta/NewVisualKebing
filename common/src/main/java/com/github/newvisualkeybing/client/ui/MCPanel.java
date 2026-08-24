package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class MCPanel {

    private static final int TITLE_BAR_HEIGHT = 24;
    private static final int INNER_PADDING = 12;

    private int x, y, width, height;
    private String title;
    private boolean collapsible;
    private boolean collapsed = false;
    private int titleColor;
    private final List<RenderEntry> entries = new ArrayList<>();

    private float expandProgress = 1.0f;
    private float titleHoverAnim = 0f;

    public MCPanel(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.title = "";
        this.collapsible = false;
        this.titleColor = UITheme.colors().textPrimary();
    }

    public MCPanel setTitle(String title) { this.title = title; return this; }
    public MCPanel setCollapsible(boolean collapsible) { this.collapsible = collapsible; return this; }
    public MCPanel setTitleColor(int color) { this.titleColor = color; return this; }
    public MCPanel setPosition(int x, int y) { this.x = x; this.y = y; return this; }
    public MCPanel setSize(int w, int h) { this.width = w; this.height = h; return this; }
    public boolean isCollapsed() { return collapsed; }

    public void toggleCollapse() { if (collapsible) collapsed = !collapsed; }

    public MCPanel clearEntries() { entries.clear(); return this; }
    public MCPanel addEntry(RenderEntry entry) { entries.add(entry); return this; }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        updateAnimations(mouseX, mouseY);
        var colors = UITheme.colors();
        boolean hasTitle = title != null && !title.isEmpty();
        int effectiveH = getEffectiveHeight();

        int radius = UITheme.radius(UITheme.Shape.MD, width, effectiveH);
        UITheme.drawCardShadow(graphics, x, y, width, effectiveH, radius);
        UITheme.fillRoundedRect(graphics, x, y, width, effectiveH, radius, colors.surface());
        UITheme.fillRoundedRectEx(graphics, x, y, width, Math.max(4, TITLE_BAR_HEIGHT / 3),
                radius, radius, 2, 2, UITheme.withAlpha(0xFFFFFFFF, 0x08));

        if (hasTitle) renderTitleBar(graphics, colors, mouseX, mouseY, radius);

        int borderColor = UITheme.lerpColor(colors.outlineVariant(),
                UITheme.withAlpha(colors.primary(), 0x40), titleHoverAnim * 0.3f);
        UITheme.drawRoundedBorder(graphics, x, y, width, effectiveH, radius, borderColor);

        if (expandProgress > 0.01f && !entries.isEmpty()) {
            int contentY = y + (hasTitle ? TITLE_BAR_HEIGHT : 0) + INNER_PADDING;
            int contentX = x + INNER_PADDING;
            int contentW = width - INNER_PADDING * 2;
            int fullContentH = height - (hasTitle ? TITLE_BAR_HEIGHT : 0) - INNER_PADDING * 2;
            int visibleContentH = (int) (fullContentH * UITheme.easeInOutQuad(expandProgress));
            if (visibleContentH > 0) {
                graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + visibleContentH);
                for (RenderEntry entry : entries) entry.render(graphics, contentX, contentY, contentW, fullContentH);
                graphics.disableScissor();
            }
        }
    }

    public boolean handleClick(double mouseX, double mouseY) {
        if (!collapsible) return false;
        boolean hasTitle = title != null && !title.isEmpty();
        if (!hasTitle) return false;
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + TITLE_BAR_HEIGHT) {
            toggleCollapse();
            return true;
        }
        return false;
    }

    public int getEffectiveHeight() {
        boolean hasTitle = title != null && !title.isEmpty();
        int titleH = hasTitle ? TITLE_BAR_HEIGHT : 0;
        if (collapsed && expandProgress <= 0.01f) return titleH;
        float eased = UITheme.easeInOutQuad(expandProgress);
        int bodyH = height - titleH;
        return titleH + (int) (bodyH * eased);
    }

    public int getContentStartY() {
        boolean hasTitle = title != null && !title.isEmpty();
        return y + (hasTitle ? TITLE_BAR_HEIGHT : 0) + INNER_PADDING;
    }

    public int getContentWidth() { return width - INNER_PADDING * 2; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getTitleBarHeight() { return TITLE_BAR_HEIGHT; }

    private void updateAnimations(int mouseX, int mouseY) {
        float target = collapsed ? 0f : 1f;
        expandProgress = UITheme.smoothDamp(expandProgress, target, 0.18f);
        if (Math.abs(expandProgress - target) < 0.005f) expandProgress = target;

        if (collapsible) {
            boolean titleHovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + TITLE_BAR_HEIGHT;
            float hoverTarget = titleHovered ? 1f : 0f;
            titleHoverAnim = UITheme.smoothDamp(titleHoverAnim, hoverTarget, 0.15f);
            if (Math.abs(titleHoverAnim - hoverTarget) < 0.005f) titleHoverAnim = hoverTarget;
        }
    }

    private void renderTitleBar(GuiGraphics graphics, UITheme.ColorPalette colors, int mouseX, int mouseY, int radius) {
        UITheme.fillRoundedRectEx(graphics, x, y, width, TITLE_BAR_HEIGHT,
                radius, radius, 0, 0, colors.surfaceContainerLow());
        UITheme.fillRoundedRect(graphics, x + 2, y + 1, width - 4, 2, 2,
                UITheme.withAlpha(0xFFFFFFFF, 0x12));

        if (collapsible && titleHoverAnim > 0.01f) {
            float eased = UITheme.easeOutCubic(titleHoverAnim);
            UITheme.fillStateLayer(graphics, x, y, width, TITLE_BAR_HEIGHT, radius,
                    colors.onSurface(), UITheme.STATE_HOVER * eased);
        }

        int borderColor = collapsible
                ? UITheme.lerpColor(colors.divider(), colors.accent(), titleHoverAnim * 0.6f)
                : colors.divider();
        graphics.fill(x + INNER_PADDING, y + TITLE_BAR_HEIGHT - 1,
                x + width - INNER_PADDING, y + TITLE_BAR_HEIGHT, borderColor);

        Minecraft mc = Minecraft.getInstance();
        int textY = y + (TITLE_BAR_HEIGHT - mc.font.lineHeight) / 2 + 1;

        if (collapsible) {
            String indicator = expandProgress > 0.5f ? "\u25BC" : "\u25B6";
            int indicatorColor = UITheme.lerpColor(colors.textMuted(), colors.accentLight(), titleHoverAnim);
            graphics.drawString(mc.font, indicator, x + INNER_PADDING + 1, textY + 1,
                    UITheme.withAlpha(0xFF000000, 0x40), false);
            graphics.drawString(mc.font, indicator, x + INNER_PADDING, textY, indicatorColor, true);
        }

        int titleX = x + INNER_PADDING + (collapsible ? 18 : 0);
        int displayTitleColor = collapsible
                ? UITheme.lerpColor(titleColor, colors.accentLight(), titleHoverAnim * 0.4f)
                : titleColor;
        graphics.drawString(mc.font, title, titleX + 1, textY + 1,
                UITheme.withAlpha(0xFF000000, 0x50), false);
        graphics.drawString(mc.font, title, titleX, textY, displayTitleColor, true);
    }

    @FunctionalInterface
    public interface RenderEntry {
        void render(GuiGraphics graphics, int x, int y, int width, int height);
    }
}

