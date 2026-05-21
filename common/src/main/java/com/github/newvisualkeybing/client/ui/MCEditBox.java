package com.github.newvisualkeybing.client.ui;

import com.github.newvisualkeybing.mixin.EditBoxAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class MCEditBox extends EditBox {

    private static final int FRAME_RADIUS = 7;
    private static final int INNER_PAD_X = 7;

    private Component placeholder;
    private boolean clearAffordance;
    private int clearX;
    private int clearY;
    private int clearSize;
    private int renderFrame;

    public MCEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setBordered(false);
        setTextColor(0x00000000);
        setTextColorUneditable(0x00000000);
    }

    public MCEditBox withPlaceholder(Component placeholder) {
        this.placeholder = placeholder;
        setHint(Component.empty());
        setSuggestion(null);
        return this;
    }

    public MCEditBox withClearAffordance(boolean enabled) {
        clearAffordance = enabled;
        return this;
    }

    public boolean clearAffordanceClicked(double mouseX, double mouseY) {
        return clearAffordance && !getValue().isEmpty()
                && mouseX >= clearX && mouseX < clearX + clearSize
                && mouseY >= clearY && mouseY < clearY + clearSize;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderFrame++;
        renderFrame(graphics, mouseX, mouseY);
        renderText(graphics);
    }

    private void renderFrame(GuiGraphics graphics, int mouseX, int mouseY) {
        var colors = UITheme.colors();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean focused = isFocused();
        boolean hovered = isMouseOver(mouseX, mouseY);
        int accent = focused ? colors.accent() : hovered ? colors.accentAlt() : colors.widgetBorder();
        int fill = focused
                ? UITheme.lerpColor(colors.inputBg(), colors.accent(), 0.12f)
                : hovered ? UITheme.lerpColor(colors.inputBg(), colors.widgetBg(), 0.28f) : colors.inputBg();

        if (focused) {
            UITheme.drawSoftGlow(graphics, x, y, w, h, FRAME_RADIUS, colors.accent(), 0x34);
        }
        UITheme.fillSoftRoundedRect(graphics, x, y, w, h, FRAME_RADIUS, fill);
        UITheme.fillRoundedRectEx(graphics, x + 1, y + 1, w - 2, Math.max(2, h / 3),
                FRAME_RADIUS - 1, FRAME_RADIUS - 1, 0, 0, UITheme.withAlpha(0xFFFFFF, focused ? 0x18 : 0x0C));
        UITheme.drawSoftRoundedBorder(graphics, x, y, w, h, FRAME_RADIUS, UITheme.withAlpha(accent, focused ? 0xF0 : 0xA0));
    }

    private void renderText(GuiGraphics graphics) {
        var colors = UITheme.colors();
        Font font = font();
        String value = getValue();
        int x = getX();
        int y = getY();
        int h = getHeight();
        int textX = x + INNER_PAD_X;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        int rightPad = clearAffordance && !value.isEmpty() ? h : INNER_PAD_X;
        int innerW = Math.max(1, getWidth() - INNER_PAD_X - rightPad);
        EditBoxAccessor access = (EditBoxAccessor) (Object) this;
        int displayPos = Mth.clamp(access.newvisualkeybing$getDisplayPos(), 0, value.length());
        String visible = font.plainSubstrByWidth(value.substring(displayPos), innerW);
        int cursorPos = Mth.clamp(getCursorPosition(), 0, value.length());
        int highlightPos = Mth.clamp(access.newvisualkeybing$getHighlightPos(), 0, value.length());
        int localCursor = Mth.clamp(cursorPos - displayPos, 0, visible.length());
        int localHighlight = Mth.clamp(highlightPos - displayPos, 0, visible.length());

        if (!value.isEmpty() && localCursor != localHighlight) {
            int selStart = Math.min(localCursor, localHighlight);
            int selEnd = Math.max(localCursor, localHighlight);
            int selX = textX + font.width(visible.substring(0, selStart));
            int selW = Math.max(1, font.width(visible.substring(selStart, selEnd)));
            UITheme.fillSoftRoundedRect(graphics, selX - 1, textY - 2, selW + 2, font.lineHeight + 4, 3,
                    UITheme.withAlpha(colors.accent(), 0x68));
        }

        if (value.isEmpty()) {
            Component hint = placeholder == null ? getMessage() : placeholder;
            graphics.drawString(font, hint, textX, textY, UITheme.withAlpha(colors.textMuted(), 0xB8), false);
        } else {
            graphics.drawString(font, FormattedCharSequence.forward(visible, net.minecraft.network.chat.Style.EMPTY),
                    textX, textY, colors.textPrimary());
        }

        if (isFocused()) {
            boolean cursorVisible = renderFrame / 12 % 2 == 0;
            if (cursorVisible) {
                int cursorX = textX + font.width(visible.substring(0, localCursor));
                UITheme.fillSoftRoundedRect(graphics, cursorX, textY - 2, 1, font.lineHeight + 4, 1, colors.accentLight());
            }
        }

        renderClearAffordance(graphics);
    }

    private void renderClearAffordance(GuiGraphics graphics) {
        if (!clearAffordance || getValue().isEmpty()) return;
        var colors = UITheme.colors();
        clearSize = Math.min(12, Math.max(10, getHeight() - 8));
        clearX = getX() + getWidth() - clearSize - 5;
        clearY = getY() + (getHeight() - clearSize) / 2;
        UITheme.fillSoftRoundedRect(graphics, clearX, clearY, clearSize, clearSize, clearSize / 2,
                UITheme.lerpColor(colors.widgetBg(), colors.accent(), isFocused() ? 0.24f : 0.12f));
        UITheme.drawSoftRoundedBorder(graphics, clearX, clearY, clearSize, clearSize, clearSize / 2,
                UITheme.withAlpha(colors.widgetBorder(), 0x80));
        int cx = clearX + clearSize / 2;
        int cy = clearY + clearSize / 2;
        int mark = isFocused() ? colors.textPrimary() : colors.textSecondary();
        graphics.fill(cx - 3, cy - 1, cx + 4, cy, mark);
        graphics.fill(cx - 1, cy - 3, cx, cy + 4, mark);
    }

    private Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }
}
