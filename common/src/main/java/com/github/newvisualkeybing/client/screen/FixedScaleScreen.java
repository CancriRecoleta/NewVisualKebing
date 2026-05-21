package com.github.newvisualkeybing.client.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class FixedScaleScreen extends Screen {

    private static final float FIXED_GUI_SCALE = 2.0f;
    private static final float MIN_RENDER_SCALE = 0.001f;

    private float fixedRenderScale = 1.0f;

    protected FixedScaleScreen(Component title) {
        super(title);
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

    protected final void pushFixedScale(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().scale(fixedRenderScale, fixedRenderScale, 1.0f);
    }

    protected final void popFixedScale(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    protected final void enableFixedScissor(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        graphics.enableScissor(
                (int) Math.floor(minX * fixedRenderScale),
                (int) Math.floor(minY * fixedRenderScale),
                (int) Math.ceil(maxX * fixedRenderScale),
                (int) Math.ceil(maxY * fixedRenderScale));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        applyFixedScaleMetrics();
        super.mouseMoved(fixedMouseX(mouseX), fixedMouseY(mouseY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        applyFixedScaleMetrics();
        return releaseLogicalMouse(fixedMouseX(mouseX), fixedMouseY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        applyFixedScaleMetrics();
        return dragLogicalMouse(
                fixedMouseX(mouseX),
                fixedMouseY(mouseY),
                button,
                dragX / fixedRenderScale,
                dragY / fixedRenderScale);
    }

    protected final boolean releaseLogicalMouse(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected final boolean dragLogicalMouse(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
