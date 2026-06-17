package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.keyboard.KeybindViewerConfig;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.github.newvisualkeybing.client.ui.UITextureStore;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

abstract class FixedScaleScreen extends Screen {

    private static final float MIN_RENDER_SCALE = 0.001f;
    /** Step applied to the global UI scale per Ctrl+wheel notch or Ctrl +/- press. */
    private static final float UI_SCALE_STEP = 0.25f;

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
        float uiScale = KeybindViewerConfig.global().uiScale();
        fixedRenderScale = Math.max(MIN_RENDER_SCALE, uiScale / (float) vanillaScale);

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

    // ---- Global UI scale control (Ctrl + wheel, Ctrl +/-, Ctrl + 0 reset) -------------------------
    // Subclasses each own their mouseScrolled (some never fall through to super), so they call
    // consumeUiScaleScroll() at the very top to give Ctrl+wheel priority over panel/list scrolling.
    // Zoom keys are handled centrally here and reached via each subclass's super.keyPressed() tail.

    protected final boolean consumeUiScaleScroll(double scrollY) {
        if (!Screen.hasControlDown() || scrollY == 0.0d) return false;
        adjustUiScale(scrollY > 0 ? UI_SCALE_STEP : -UI_SCALE_STEP);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown() && handleUiScaleKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleUiScaleKey(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                adjustUiScale(UI_SCALE_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                adjustUiScale(-UI_SCALE_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> {
                setUiScale(KeybindViewerConfig.DEFAULT_UI_SCALE);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    protected final void adjustUiScale(float delta) {
        setUiScale(KeybindViewerConfig.global().uiScale() + delta);
    }

    private void setUiScale(float scale) {
        KeybindViewerConfig config = KeybindViewerConfig.global();
        float clamped = Math.max(KeybindViewerConfig.MIN_UI_SCALE,
                Math.min(KeybindViewerConfig.MAX_UI_SCALE, scale));
        if (clamped == config.uiScale()) return;
        config.setUiScale(clamped);
        onUiScaleChanged();
    }

    /**
     * Re-applies the fixed-scale metrics and rebuilds child widgets so a mid-session scale change
     * reflows every layout (header buttons, search boxes, …) at the new logical canvas size.
     */
    protected void onUiScaleChanged() {
        applyFixedScaleMetrics();
        rebuildWidgets();
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
