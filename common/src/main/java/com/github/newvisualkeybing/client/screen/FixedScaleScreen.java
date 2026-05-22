package com.github.newvisualkeybing.client.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

abstract class FixedScaleScreen extends Screen {

    private static final float FIXED_GUI_SCALE = 2.0f;
    private static final float MIN_RENDER_SCALE = 0.001f;

    private float fixedRenderScale = 1.0f;

    protected FixedScaleScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        applyFixedScaleMetrics();
    }

    @Override
    protected void repositionElements() {
        applyFixedScaleMetrics();
        super.repositionElements();
    }

    protected final void applyFixedScaleMetrics() {
        Window window = Minecraft.getInstance().getWindow();
        if (window == null) {
            fixedRenderScale = 1.0f;
            return;
        }

        double vanillaScale = Math.max(1.0d, window.getGuiScale());
        fixedRenderScale = Math.max(MIN_RENDER_SCALE, FIXED_GUI_SCALE / (float) vanillaScale);

        int fixedWidth = Math.max(1, Math.round(window.getGuiScaledWidth() / fixedRenderScale));
        int fixedHeight = Math.max(1, Math.round(window.getGuiScaledHeight() / fixedRenderScale));
        if (width != fixedWidth || height != fixedHeight) {
            width = fixedWidth;
            height = fixedHeight;
            onFixedScaleMetricsChanged();
        }
    }

    protected void onFixedScaleMetricsChanged() {
    }

    protected final void pushFixedScale(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(fixedRenderScale, fixedRenderScale);
    }

    protected final void popFixedScale(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    protected final void enableFixedScissor(GuiGraphicsExtractor graphics, int minX, int minY, int maxX, int maxY) {
        graphics.enableScissor(minX, minY, maxX, maxY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        applyFixedScaleMetrics();
        super.mouseMoved(fixedMouseX(mouseX), fixedMouseY(mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        applyFixedScaleMetrics();
        return super.mouseClicked(fixedMouseEvent(event), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        applyFixedScaleMetrics();
        return super.mouseReleased(fixedMouseEvent(event));
    }

    protected final MouseButtonEvent fixedMouseEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(fixedMouseX(event.x()), fixedMouseY(event.y()), event.buttonInfo());
    }

    protected final int fixedMouseX(int mouseX) {
        return (int) Math.floor(mouseX / fixedRenderScale);
    }

    protected final int fixedMouseY(int mouseY) {
        return (int) Math.floor(mouseY / fixedRenderScale);
    }

    protected final double fixedMouseX(double mouseX) {
        return mouseX / fixedRenderScale;
    }

    protected final double fixedMouseY(double mouseY) {
        return mouseY / fixedRenderScale;
    }
}
