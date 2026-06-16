package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.ui.UITheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class KeybindViewerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile KeybindViewerConfig INSTANCE;

    /** Bounds for the user-adjustable global UI scale (replaces the old fixed 2.0x). */
    public static final float MIN_UI_SCALE = 1.0f;
    public static final float MAX_UI_SCALE = 4.0f;
    public static final float DEFAULT_UI_SCALE = 2.0f;

    /** Bounds for the keyboard-only zoom factor layered on top of the auto-fit key size. */
    public static final float MIN_KEYBOARD_ZOOM = 0.5f;
    public static final float MAX_KEYBOARD_ZOOM = 2.5f;
    public static final float DEFAULT_KEYBOARD_ZOOM = 1.0f;

    public static KeybindViewerConfig global() {
        KeybindViewerConfig local = INSTANCE;
        if (local == null) {
            synchronized (KeybindViewerConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new KeybindViewerConfig();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private final Path file;
    private Data data = new Data();

    private KeybindViewerConfig() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.file = root.resolve("config").resolve(Constants.MOD_ID).resolve("keybind_viewer.json");
        load();
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            data = new Data();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            data = loaded == null ? new Data() : loaded;
        } catch (IOException | JsonSyntaxException ignored) {
            data = new Data();
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public boolean hideNonSelectedMod() {
        return data.hideNonSelectedMod;
    }

    public boolean toggleHideNonSelectedMod() {
        data.hideNonSelectedMod = !data.hideNonSelectedMod;
        save();
        return data.hideNonSelectedMod;
    }

    public boolean comboKeysNonConflicting() {
        return data.comboKeysNonConflicting == null || data.comboKeysNonConflicting;
    }

    public boolean toggleComboKeysNonConflicting() {
        data.comboKeysNonConflicting = !comboKeysNonConflicting();
        save();
        return data.comboKeysNonConflicting;
    }

    public boolean mousePanelCollapsed() {
        return data.mousePanelCollapsed;
    }

    public void setMousePanelCollapsed(boolean collapsed) {
        data.mousePanelCollapsed = collapsed;
        save();
    }

    public boolean detailPanelCollapsed() {
        return data.detailPanelCollapsed;
    }

    public void setDetailPanelCollapsed(boolean collapsed) {
        data.detailPanelCollapsed = collapsed;
        save();
    }

    public UITheme.Skin uiSkin() {
        if (data.uiSkin == null) return UITheme.Skin.MODERN;
        try {
            return UITheme.Skin.valueOf(data.uiSkin);
        } catch (IllegalArgumentException ignored) {
            return UITheme.Skin.MODERN;
        }
    }

    public void setUiSkin(UITheme.Skin skin) {
        data.uiSkin = skin == null ? null : skin.name();
        save();
    }

    /** Id of the active custom UI texture pack (null/blank = the default loose pack). */
    public String uiTexturePack() {
        return data.uiTexturePack;
    }

    public void setUiTexturePack(String packId) {
        data.uiTexturePack = packId;
        save();
    }

    /** Global UI scale applied by every mod screen (was the hardcoded 2.0x). Clamped to a sane range. */
    public float uiScale() {
        float value = data.uiScale == null ? DEFAULT_UI_SCALE : data.uiScale;
        return clamp(value, MIN_UI_SCALE, MAX_UI_SCALE);
    }

    public void setUiScale(float scale) {
        data.uiScale = clamp(scale, MIN_UI_SCALE, MAX_UI_SCALE);
        save();
    }

    /** Extra zoom for the keyboard diagram alone, multiplying the auto-fit key-unit cap. */
    public float keyboardZoom() {
        float value = data.keyboardZoom == null ? DEFAULT_KEYBOARD_ZOOM : data.keyboardZoom;
        return clamp(value, MIN_KEYBOARD_ZOOM, MAX_KEYBOARD_ZOOM);
    }

    public void setKeyboardZoom(float zoom) {
        data.keyboardZoom = clamp(zoom, MIN_KEYBOARD_ZOOM, MAX_KEYBOARD_ZOOM);
        save();
    }

    /** User-dragged X offset for a top-level panel, layered on top of the responsive auto-layout. */
    public int panelOffsetX(String panelId) {
        int[] offset = data.panelOffsets == null ? null : data.panelOffsets.get(panelId);
        return offset == null || offset.length < 2 ? 0 : offset[0];
    }

    public int panelOffsetY(String panelId) {
        int[] offset = data.panelOffsets == null ? null : data.panelOffsets.get(panelId);
        return offset == null || offset.length < 2 ? 0 : offset[1];
    }

    /** Updates a panel offset in memory only; call {@link #save()} once the drag finishes. */
    public void setPanelOffset(String panelId, int offsetX, int offsetY) {
        if (data.panelOffsets == null) data.panelOffsets = new HashMap<>();
        data.panelOffsets.put(panelId, new int[] {offsetX, offsetY});
    }

    public boolean hasPanelOffsets() {
        return data.panelOffsets != null && !data.panelOffsets.isEmpty();
    }

    /** Clears every dragged panel offset and persists, snapping all panels back to auto-layout. */
    public void resetPanelOffsets() {
        if (data.panelOffsets != null) data.panelOffsets.clear();
        save();
    }

    private static float clamp(float value, float min, float max) {
        // Guard against NaN/Infinity from a hand-edited or corrupted config: those slip through a plain
        // Math.max/Math.min (all NaN comparisons are false) and would poison the render scale.
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public KeyboardLayoutData.Style defaultLayoutStyle() {
        if (data.defaultLayout == null || data.defaultLayout.isBlank()) {
            return KeyboardLayoutData.Style.ANSI_104;
        }
        try {
            return KeyboardLayoutData.Style.valueOf(data.defaultLayout);
        } catch (IllegalArgumentException ignored) {
            return KeyboardLayoutData.Style.ANSI_104;
        }
    }

    public void setDefaultLayoutStyle(KeyboardLayoutData.Style style) {
        data.defaultLayout = style == null ? null : style.name();
        save();
    }

    private static final class Data {
        boolean hideNonSelectedMod;
        Boolean comboKeysNonConflicting = Boolean.TRUE;
        String defaultLayout;
        boolean mousePanelCollapsed;
        boolean detailPanelCollapsed;
        String uiSkin;
        String uiTexturePack;
        Float uiScale;
        Float keyboardZoom;
        Map<String, int[]> panelOffsets;
    }
}
