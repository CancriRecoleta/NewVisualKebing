package com.github.newvisualkeybing.client.ui;

/**
 * Destination for axis-aligned solid quads. The rasterizers in this package are written against
 * this tiny interface instead of {@code GuiGraphics} so they stay free of Minecraft classes (and
 * therefore unit-testable / previewable offline); {@link UITheme} adapts it to {@code GuiGraphics#fill}.
 *
 * <p>Coordinates follow the {@code GuiGraphics.fill} convention: {@code [x0, x1) x [y0, y1)} in
 * pixels, {@code argb} packed as 0xAARRGGBB.
 */
@FunctionalInterface
public interface QuadSink {
    void fill(int x0, int y0, int x1, int y1, int argb);
}
