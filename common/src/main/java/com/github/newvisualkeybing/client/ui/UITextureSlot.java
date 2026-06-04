package com.github.newvisualkeybing.client.ui;

/**
 * Every UI surface that a custom texture pack (under {@code config/newvisualkeybing/ui_textures/})
 * can override. Each slot declares the PNG file name it loads from, how the image is scaled to a
 * target rectangle, its default nine-slice border inset, whether it is tinted by a runtime colour,
 * and the recommended source size. The human-readable description is localised via {@link #descKey()}
 * (filled in by the lang files) and shown in the auto-generated README.
 *
 * @see UITextureStore
 */
public enum UITextureSlot {

    // id (file name w/o .png), scale mode, default 9-slice border (px), tintable, rec. W, rec. H
    PANEL("panel", ScaleMode.NINE_SLICE, 10, false, 48, 48),
    BUTTON("button", ScaleMode.NINE_SLICE, 4, false, 80, 24),
    BUTTON_HOVER("button_hover", ScaleMode.NINE_SLICE, 4, false, 80, 24),
    BUTTON_DISABLED("button_disabled", ScaleMode.NINE_SLICE, 4, false, 80, 24),
    EDITBOX("editbox", ScaleMode.NINE_SLICE, 3, false, 64, 24),
    EDITBOX_FOCUSED("editbox_focused", ScaleMode.NINE_SLICE, 3, false, 64, 24),
    TOOLTIP("tooltip", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEY("key", ScaleMode.NINE_SLICE, 6, true, 32, 32),
    KEY_FREE("key_free", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEY_SELF("key_self", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEY_OTHER("key_other", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEY_COMBO("key_combo", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEY_CONFLICT("key_conflict", ScaleMode.NINE_SLICE, 6, false, 32, 32),
    KEYBOARD_CHASSIS("keyboard_chassis", ScaleMode.NINE_SLICE, 12, false, 64, 64),
    MOUSE_BODY("mouse_body", ScaleMode.STRETCH, 0, false, 80, 116),
    MOUSE_BUTTON("mouse_button", ScaleMode.NINE_SLICE, 6, true, 32, 32),
    BACKGROUND("background", ScaleMode.TILE, 0, false, 32, 32);

    /** How {@link UITextureStore} maps a source image onto a target rectangle. */
    public enum ScaleMode {
        /** Corners kept at native size; edges/centre stretched. Honours {@code border}. */
        NINE_SLICE,
        /** Whole image stretched to fill the rectangle. */
        STRETCH,
        /** Image repeated at native size to fill the rectangle. */
        TILE
    }

    private final String id;
    private final ScaleMode scaleMode;
    private final int defaultBorder;
    private final boolean tintable;
    private final int recommendedWidth;
    private final int recommendedHeight;

    UITextureSlot(String id, ScaleMode scaleMode, int defaultBorder, boolean tintable,
                  int recommendedWidth, int recommendedHeight) {
        this.id = id;
        this.scaleMode = scaleMode;
        this.defaultBorder = defaultBorder;
        this.tintable = tintable;
        this.recommendedWidth = recommendedWidth;
        this.recommendedHeight = recommendedHeight;
    }

    /** File name without extension; the loader reads {@code <id>.png}. */
    public String id() { return id; }

    public String fileName() { return id + ".png"; }

    public ScaleMode scaleMode() { return scaleMode; }

    public int defaultBorder() { return defaultBorder; }

    public boolean tintable() { return tintable; }

    public int recommendedWidth() { return recommendedWidth; }

    public int recommendedHeight() { return recommendedHeight; }

    /** Translation key for this slot's human description (resolved against the active language). */
    public String descKey() {
        return "newvisualkeybing.ui_textures.slot." + id + ".desc";
    }
}
