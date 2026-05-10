package com.github.newvisualkeybing.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuiGraphics {
    private final PoseStack pose;

    public GuiGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() {
        return pose;
    }

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        Screen.fill(pose, minX, minY, maxX, maxY, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) return font.drawShadow(pose, text, x, y, color);
        return font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (shadow) return font.drawShadow(pose, text, x, y, color);
        return font.draw(pose, text, x, y, color);
    }

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int x = (int) Math.round(minX * scale);
        int y = (int) Math.round(window.getHeight() - maxY * scale);
        int w = (int) Math.round((maxX - minX) * scale);
        int h = (int) Math.round((maxY - minY) * scale);
        RenderSystem.enableScissor(x, y, w, h);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }
}
