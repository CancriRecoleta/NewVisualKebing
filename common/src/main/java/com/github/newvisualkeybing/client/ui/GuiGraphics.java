package com.github.newvisualkeybing.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

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

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (shadow) return font.drawShadow(pose, text, x, y, color);
        return font.draw(pose, text, x, y, color);
    }

    // 5-arg overloads default to shadow=true, matching 1.20.1 GuiGraphics.drawString(...).
    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
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

    /** 1.20.1 GuiGraphics.setColor — global shader tint on 1.19.2. */
    public void setColor(float red, float green, float blue, float alpha) {
        RenderSystem.setShaderColor(red, green, blue, alpha);
    }

    /**
     * 1.20.1 GuiGraphics.blit(ResourceLocation, x, y, width, height, u, v, uWidth, vHeight,
     * texWidth, texHeight): a stretched blit. 1.19.2 has no ResourceLocation-taking blit, so bind
     * the texture then delegate to GuiComponent's stretched static blit (dest width/height ←
     * source uWidth/vHeight).
     */
    public void blit(ResourceLocation texture, int x, int y, int width, int height,
                     float uOffset, float vOffset, int uWidth, int vHeight,
                     int textureWidth, int textureHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, width, height, uOffset, vOffset, uWidth, vHeight,
                textureWidth, textureHeight);
    }

    /**
     * Stretched nine-slice approximating 1.20.1 GuiGraphics.blitNineSliced(rl, x, y, width, height,
     * sliceWidth, sliceHeight, uWidth, vHeight, uOffset, vOffset). Corners are drawn near 1:1; edges
     * and center are stretched (not tiled) — visually fine for widget skins. The 1.20.1 overload
     * assumes a 256×256 atlas (vanilla widgets), so that is hard-coded here.
     */
    public void blitNineSliced(ResourceLocation texture, int x, int y, int width, int height,
                               int sliceWidth, int sliceHeight, int uWidth, int vHeight,
                               int uOffset, int vOffset) {
        final int tex = 256;
        int cw = Math.min(sliceWidth, width / 2);
        int ch = Math.min(sliceHeight, height / 2);
        int innerW = width - 2 * cw;
        int innerH = height - 2 * ch;
        int srcInnerW = uWidth - 2 * sliceWidth;
        int srcInnerH = vHeight - 2 * sliceHeight;
        int rx = x + cw + innerW;
        int by = y + ch + innerH;
        int ru = uOffset + uWidth - sliceWidth;
        int bv = vOffset + vHeight - sliceHeight;
        // corners
        blit(texture, x,  y,  cw, ch, uOffset, vOffset, sliceWidth, sliceHeight, tex, tex);
        blit(texture, rx, y,  cw, ch, ru,      vOffset, sliceWidth, sliceHeight, tex, tex);
        blit(texture, x,  by, cw, ch, uOffset, bv,      sliceWidth, sliceHeight, tex, tex);
        blit(texture, rx, by, cw, ch, ru,      bv,      sliceWidth, sliceHeight, tex, tex);
        // top / bottom edges
        if (innerW > 0) {
            blit(texture, x + cw, y,  innerW, ch, uOffset + sliceWidth, vOffset, srcInnerW, sliceHeight, tex, tex);
            blit(texture, x + cw, by, innerW, ch, uOffset + sliceWidth, bv,      srcInnerW, sliceHeight, tex, tex);
        }
        // left / right edges
        if (innerH > 0) {
            blit(texture, x,  y + ch, cw, innerH, uOffset, vOffset + sliceHeight, sliceWidth, srcInnerH, tex, tex);
            blit(texture, rx, y + ch, cw, innerH, ru,      vOffset + sliceHeight, sliceWidth, srcInnerH, tex, tex);
        }
        // center
        if (innerW > 0 && innerH > 0) {
            blit(texture, x + cw, y + ch, innerW, innerH,
                    uOffset + sliceWidth, vOffset + sliceHeight, srcInnerW, srcInnerH, tex, tex);
        }
    }
}
