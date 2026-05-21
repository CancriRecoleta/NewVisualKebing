package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class MCButton extends AbstractWidget {

    private static final int CORNER_RADIUS = 6;
    private static final float ANIM_SPEED_IN = 0.15f;
    private static final float ANIM_SPEED_OUT = 0.08f;

    private final OnPress onPress;

    private float hoverProgress = 0f;
    private float pressAnimation = 0f;

    private int cachedBgTop = 0;
    private int cachedBgBottom = 0;
    private int cachedBorderColor = 0;
    private int cachedTextColor = 0;
    private boolean cacheDirty = true;
    private float lastEasedHover = 0f;

    @FunctionalInterface
    public interface OnPress {
        void onPress(MCButton button);
    }

    public MCButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public static MCButton create(int x, int y, int w, int h, Component text, OnPress onPress) {
        return new MCButton(x, y, w, h, text, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateAnimations(partialTick);

        var colors = UITheme.colors();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        float easedHover = UITheme.easeOutCubic(hoverProgress);
        float easedPress = UITheme.easeOutCubic(pressAnimation);

        if (cacheDirty || Math.abs(easedHover - lastEasedHover) > 0.01f) {
            updateRenderCache(colors, easedHover);
            cacheDirty = false;
            lastEasedHover = easedHover;
        }

        if (easedPress > 0.01f) {
            int shrink = (int) (2 * easedPress);
            x += shrink; y += shrink; w -= shrink * 2; h -= shrink * 2;
        }

        if (w <= 0 || h <= 0) {
            return;
        }

        int radius = Math.min(CORNER_RADIUS, Math.max(2, Math.min(w, h) / 2));
        renderSurface(graphics, x, y, w, h, radius, easedHover, easedPress);

        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(getMessage());
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - mc.font.lineHeight) / 2 + 1;

        if (this.active) {
            graphics.drawString(mc.font, getMessage(), textX + 1, textY + 1,
                    UITheme.withAlpha(0xFF000000, 0x60), false);
        }
        graphics.drawString(mc.font, getMessage(), textX, textY, cachedTextColor, true);
    }

    private void renderSurface(GuiGraphics graphics, int x, int y, int w, int h, int radius,
                               float easedHover, float easedPress) {
        UITheme.fillRoundedRectFast(graphics, x, y, w, h, radius, cachedBgBottom);

        int topBandH = Math.min(h, Math.max(3, (int) (h * 0.55f)));
        UITheme.fillRoundedRectEx(graphics, x, y, w, topBandH,
                radius, radius, Math.max(1, radius - 2), Math.max(1, radius - 2), cachedBgTop);

        if (w > 4 && h > 4) {
            int glossAlpha = this.active ? 0x12 + (int) (0x0A * easedHover) : 0x08;
            int shadeAlpha = this.active ? 0x0A + (int) (0x08 * easedPress) : 0x06;
            int innerRadius = Math.max(1, radius - 1);
            UITheme.fillRoundedRectEx(graphics, x + 1, y + 1, w - 2, Math.min(3, topBandH),
                    innerRadius, innerRadius, 0, 0, UITheme.withAlpha(0xFFFFFF, glossAlpha));
            UITheme.fillRoundedRectEx(graphics, x + 1, y + h - Math.min(3, h / 3) - 1, w - 2,
                    Math.min(3, h / 3), 0, 0, innerRadius, innerRadius, UITheme.withAlpha(0x000000, shadeAlpha));
        }

        UITheme.drawRoundedBorderFast(graphics, x, y, w, h, radius, cachedBorderColor);
        if (this.active && w > 4 && h > 4) {
            UITheme.drawRoundedBorderFast(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1),
                    UITheme.withAlpha(0xFFFFFF, 0x08));
        }
    }

    private void updateRenderCache(UITheme.ColorPalette colors, float easedHover) {
        if (!this.active) {
            cachedBgTop = UITheme.withAlpha(UITheme.lerpColor(colors.widgetBg(), colors.panelBg(), 0.12f), 0x60);
            cachedBgBottom = UITheme.withAlpha(colors.widgetBg(), 0x60);
        } else {
            int normalTop = UITheme.brighten(colors.widgetBg(), 0.05f);
            int normalBottom = colors.widgetBg();
            int hoverTop = UITheme.brighten(colors.accent(), 0.15f);
            int hoverBottom = colors.accent();
            cachedBgTop = UITheme.lerpColor(normalTop, hoverTop, easedHover);
            cachedBgBottom = UITheme.lerpColor(normalBottom, hoverBottom, easedHover);
        }
        if (!this.active) {
            cachedBorderColor = UITheme.withAlpha(colors.widgetBorder(), 0x50);
        } else {
            int normalBorder = colors.widgetBorder();
            int hoverBorder = UITheme.lerpColor(colors.accent(), 0xFFFFFFFF, 0.3f);
            cachedBorderColor = UITheme.lerpColor(normalBorder, hoverBorder, easedHover * 0.7f);
        }
        int baseTextColor = this.active ? colors.textPrimary() : colors.textMuted();
        cachedTextColor = this.active
                ? UITheme.lerpColor(baseTextColor, 0xFFFFFFFF, easedHover * 0.2f)
                : baseTextColor;
    }

    private void updateAnimations(float partialTick) {
        boolean hovered = isHoveredOrFocused() && this.active;
        float targetHover = hovered ? 1.0f : 0f;
        float speed = hovered ? ANIM_SPEED_IN : ANIM_SPEED_OUT;
        hoverProgress = UITheme.smoothDamp(hoverProgress, targetHover, speed * partialTick * 3f);
        if (Math.abs(hoverProgress - targetHover) < 0.005f) hoverProgress = targetHover;

        if (pressAnimation > 0.01f) {
            pressAnimation = UITheme.smoothDamp(pressAnimation, 0f, 0.15f * partialTick * 3f);
        } else {
            pressAnimation = 0f;
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active) {
            pressAnimation = 1.0f;
            onPress.onPress(this);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
