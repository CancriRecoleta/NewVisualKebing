package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.client.ui.UITextureStore;
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

    @Override
    protected void init() {
        super.init();
        // Load the persisted skin before any widget builds its colour cache, so every screen in the
        // mod (viewer, board, edit, …) honours the choice the user made on the main screen.
        UITheme.setSkin(KeybindViewerConfig.global().uiSkin());
        // When the custom skin is active, make sure the active pack's textures are loaded (render thread).
        if (UITheme.custom()) UITextureStore.global().ensureLoaded(KeybindViewerConfig.global().uiTexturePack());
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
        // 1.21.6: GuiGraphics.pose() is a Matrix3x2fStack (2D) — pushPose/scale(x,y,z)/popPose became
        // pushMatrix/scale(x,y)/popMatrix.
        graphics.pose().pushMatrix();
        graphics.pose().scale(fixedRenderScale, fixedRenderScale);
    }

    protected final void popFixedScale(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    protected final void enableFixedScissor(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        // 1.21.2+ GuiGraphics.enableScissor transforms the scissor rect by the current pose matrix
        // (ScreenRectangle.transformAxisAligned(pose.last().pose())). enableFixedScissor is always
        // called inside pushFixedScale, so the active fixed-scale pose already scales these
        // coordinates — pass them through as logical coordinates. Pre-1.21.2 enableScissor ignored the
        // pose, so the coords had to be pre-multiplied by fixedRenderScale; doing that here on 1.21.2+
        // double-scales and mis-clips the scissored panels (e.g. the bind-board function list).
        graphics.enableScissor(minX, minY, maxX, maxY);
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
