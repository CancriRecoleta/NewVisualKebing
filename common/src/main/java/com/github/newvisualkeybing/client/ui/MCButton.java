package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class MCButton extends AbstractWidget {

    // Vanilla widget atlas; the button sprite lives at x=0 with v=46/66/86 (disabled/normal/hover).
    private static final ResourceLocation VANILLA_WIDGETS = new ResourceLocation("textures/gui/widgets.png");

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
    private int cachedThemeVersion = -1;

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

        int themeVersion = UITheme.themeVersion();
        if (cachedThemeVersion != themeVersion) {
            cachedThemeVersion = themeVersion;
            cacheDirty = true;
        }

        float easedHover = UITheme.easeOutCubic(hoverProgress);
        float easedPress = UITheme.easeOutCubic(pressAnimation);

        if (cacheDirty || Math.abs(easedHover - lastEasedHover) > 0.01f) {
            updateRenderCache(colors, easedHover);
            cacheDirty = false;
            lastEasedHover = easedHover;
        }

        if (w <= 0 || h <= 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(getMessage());
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - mc.font.lineHeight) / 2 + 1;

        if (UITheme.flat()) {
            if (!(UITheme.custom() && drawCustomButton(graphics, x, y, w, h))) {
                renderVanillaButton(graphics, x, y, w, h);
            }
            // Vanilla/custom button text: white when active, gray when disabled, with the standard shadow.
            int vColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            graphics.drawString(mc.font, getMessage(), textX, textY, vColor, true);
            return;
        }

        int radius = UITheme.radius(UITheme.Shape.SM, w, h);
        renderSurface(graphics, x, y, w, h, radius, easedHover, easedPress);

        if (this.active) {
            graphics.drawString(mc.font, getMessage(), textX + 1, textY + 1,
                    UITheme.withAlpha(0xFF000000, 0x60), false);
        }
        graphics.drawString(mc.font, getMessage(), textX, textY, cachedTextColor, true);
    }

    /** Pixel-perfect vanilla button: nine-sliced from widgets.png, state chosen by active/hover. */
    private void renderVanillaButton(GuiGraphics graphics, int x, int y, int w, int h) {
        boolean hovered = isHoveredOrFocused() && this.active;
        int v = !this.active ? 46 : hovered ? 86 : 66;
        graphics.blitNineSliced(VANILLA_WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, v);
    }

    /** Custom skin: draw the user's button texture for the current state, or false to fall back. */
    private boolean drawCustomButton(GuiGraphics graphics, int x, int y, int w, int h) {
        UITextureStore store = UITextureStore.global();
        boolean hovered = isHoveredOrFocused() && this.active;
        UITextureSlot slot = UITextureSlot.BUTTON;
        if (!this.active && store.has(UITextureSlot.BUTTON_DISABLED)) {
            slot = UITextureSlot.BUTTON_DISABLED;
        } else if (hovered && store.has(UITextureSlot.BUTTON_HOVER)) {
            slot = UITextureSlot.BUTTON_HOVER;
        }
        return store.draw(slot, graphics, x, y, w, h);
    }

    private void renderSurface(GuiGraphics graphics, int x, int y, int w, int h, int radius,
                               float easedHover, float easedPress) {
        UITheme.fillRoundedRectFast(graphics, x, y, w, h, radius, cachedBgBottom);

        int topBandH = Math.min(h, Math.max(3, (int) (h * 0.55f)));
        UITheme.fillRoundedRectEx(graphics, x, y, w, topBandH,
                radius, radius, Math.max(1, radius - 2), Math.max(1, radius - 2), cachedBgTop);

        if (this.active) {
            UITheme.fillStateLayer(graphics, x, y, w, h, radius, 0xFFFFFF,
                    UITheme.STATE_HOVER * easedHover + UITheme.STATE_PRESSED * easedPress);
        }

        if (w > 4 && h > 4) {
            int glossAlpha = this.active ? 0x10 + (int) (0x08 * easedHover) : 0x08;
            int innerRadius = Math.max(1, radius - 1);
            UITheme.fillRoundedRectEx(graphics, x + 1, y + 1, w - 2, Math.min(3, topBandH),
                    innerRadius, innerRadius, 0, 0, UITheme.withAlpha(0xFFFFFF, glossAlpha));
        }

        UITheme.drawRoundedBorderFast(graphics, x, y, w, h, radius, cachedBorderColor);
        if (this.active && w > 4 && h > 4) {
            UITheme.drawRoundedBorderFast(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(1, radius - 1),
                    UITheme.withAlpha(0xFFFFFF, 0x08));
        }
    }

    private void updateRenderCache(UITheme.ColorPalette colors, float easedHover) {
        if (!this.active) {
            cachedBgTop = UITheme.withAlpha(UITheme.lerpColor(colors.widgetBg(), colors.panelBg(), 0.12f), 0x61);
            cachedBgBottom = UITheme.withAlpha(colors.widgetBg(), 0x61);
        } else {
            int normalTop = UITheme.brighten(colors.surfaceContainer(), 0.06f);
            int normalBottom = colors.surfaceContainer();
            int hoverTop = UITheme.lerpColor(normalTop, colors.primary(), 0.22f);
            int hoverBottom = UITheme.lerpColor(normalBottom, colors.primary(), 0.18f);
            cachedBgTop = UITheme.lerpColor(normalTop, hoverTop, easedHover);
            cachedBgBottom = UITheme.lerpColor(normalBottom, hoverBottom, easedHover);
        }
        if (!this.active) {
            cachedBorderColor = UITheme.withAlpha(colors.outlineVariant(), 0x50);
        } else {
            int normalBorder = colors.outlineVariant();
            int hoverBorder = UITheme.lerpColor(colors.primary(), 0xFFFFFFFF, 0.18f);
            cachedBorderColor = UITheme.lerpColor(normalBorder, hoverBorder, easedHover);
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
