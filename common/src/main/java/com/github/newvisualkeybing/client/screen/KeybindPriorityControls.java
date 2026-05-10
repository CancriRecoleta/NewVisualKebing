package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindProfileStore;
import com.github.newvisualkeybing.client.ui.UITheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import com.github.newvisualkeybing.client.ui.GuiGraphics;

final class KeybindPriorityControls {

    static final int WIDTH = 64;
    private static final int BUTTON_W = 18;

    private final KeybindProfileStore profileStore;

    KeybindPriorityControls(KeybindProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    void render(GuiGraphics graphics, Font font, KeyMapping mapping, int x, int y,
                int entryHeight, int mouseX, int mouseY, boolean rowHovered) {
        var colors = UITheme.colors();
        int h = entryHeight - 4;
        boolean upHover = rowHovered && inside(mouseX, mouseY, x, y + 1, BUTTON_W, h);
        boolean downHover = rowHovered && inside(mouseX, mouseY, x + WIDTH - BUTTON_W, y + 1, BUTTON_W, h);
        UITheme.fillRoundedRect(graphics, x, y + 1, BUTTON_W, h, 4,
                UITheme.lerpColor(colors.widgetBg(), colors.accent(), upHover ? 0.42f : 0.18f));
        UITheme.drawRoundedBorder(graphics, x, y + 1, BUTTON_W, h, 4, UITheme.withAlpha(colors.accent(), 0x88));
        UITheme.fillRoundedRect(graphics, x + WIDTH - BUTTON_W, y + 1, BUTTON_W, h, 4,
                UITheme.lerpColor(colors.widgetBg(), colors.warningColor(), downHover ? 0.42f : 0.18f));
        UITheme.drawRoundedBorder(graphics, x + WIDTH - BUTTON_W, y + 1, BUTTON_W, h, 4, UITheme.withAlpha(colors.warningColor(), 0x88));
        graphics.drawString(font, "+", x + 6, y + (entryHeight - font.lineHeight) / 2, colors.textPrimary(), false);
        graphics.drawString(font, "-", x + WIDTH - BUTTON_W + 7, y + (entryHeight - font.lineHeight) / 2, colors.textPrimary(), false);
        String value = String.valueOf(profileStore.priorityOf(mapping));
        graphics.drawString(font, value, x + (WIDTH - font.width(value)) / 2,
                y + (entryHeight - font.lineHeight) / 2, colors.textMuted(), false);
    }

    int hitDelta(double mouseX, int x) {
        if (mouseX >= x && mouseX < x + BUTTON_W) return 1;
        if (mouseX >= x + WIDTH - BUTTON_W && mouseX < x + WIDTH) return -1;
        return 0;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
