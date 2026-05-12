package com.github.newvisualkeybing.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class MCLabel {

    private int x, y, maxWidth;
    private int color;
    private boolean shadow;
    private float scale;
    private final List<String> lines = new ArrayList<>();

    public MCLabel(int x, int y, int maxWidth) {
        this.x = x; this.y = y; this.maxWidth = maxWidth;
        this.color = UITheme.colors().textPrimary();
        this.shadow = true;
        this.scale = 1.0f;
    }

    public MCLabel setColor(int color) { this.color = color; return this; }
    public MCLabel setShadow(boolean shadow) { this.shadow = shadow; return this; }
    public MCLabel setScale(float scale) { this.scale = scale; return this; }
    public MCLabel setPosition(int x, int y) { this.x = x; this.y = y; return this; }
    public MCLabel setMaxWidth(int maxWidth) { this.maxWidth = maxWidth; return this; }

    public MCLabel setText(String text) {
        lines.clear();
        if (text != null && !text.isEmpty()) wrapText(text);
        return this;
    }

    public MCLabel addLine(String line) { if (line != null) wrapText(line); return this; }
    public MCLabel clear() { lines.clear(); return this; }

    public void render(GuiGraphicsExtractor graphics) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean needsScale = scale != 1.0f;
        if (needsScale) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
        }
        int drawX = needsScale ? (int) (x / scale) : x;
        int drawY = needsScale ? (int) (y / scale) : y;
        int lineHeight = 11;
        for (String line : lines) {
            if (shadow) {
                int shadowColor = UITheme.withAlpha(darkenForShadow(color), 0xAA);
                graphics.text(font, line, drawX + 1, drawY + 1, shadowColor, false);
            }
            graphics.text(font, line, drawX, drawY, color, false);
            drawY += lineHeight;
        }
        if (needsScale) graphics.pose().popMatrix();
    }

    public int getHeight() { return (int) (lines.size() * 11 * scale); }
    public int getLineCount() { return lines.size(); }

    private void wrapText(String text) {
        if (maxWidth <= 0 || text.isEmpty()) { lines.add(text); return; }
        Font font = Minecraft.getInstance().font;
        int effectiveWidth = (int) (maxWidth / scale);
        if (font.width(text) <= effectiveWidth) { lines.add(text); return; }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() == 0) { current.append(word); }
            else {
                String test = current + " " + word;
                if (font.width(test) > effectiveWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else current.append(" ").append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
    }

    private static int darkenForShadow(int color) {
        int r = Math.max(0, ((color >> 16) & 0xFF) / 4);
        int g = Math.max(0, ((color >> 8) & 0xFF) / 4);
        int b = Math.max(0, (color & 0xFF) / 4);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
