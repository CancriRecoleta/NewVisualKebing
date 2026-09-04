package com.github.newvisualkeybing.client.screen;

import com.github.newvisualkeybing.client.ui.SilhouetteRaster;

/**
 * Geometry + cached rasterization of a top-down mouse for one body size.
 *
 * <p>The body is an egg: two superellipse halves meeting at the widest row ({@link #WIDEST_T}),
 * a blunter one for the nose (button end) and a rounder one for the tail (palm end). Every
 * feature — button plates cut out of the body, the wheel slot, the seam between plate and palm,
 * palm shading bands, glow outlines — is expressed as a clipped region of that egg (or of the egg
 * inset by a few pixels), rasterized once with {@link SilhouetteRaster} and replayed per frame.
 *
 * <p>All coordinates are body-local pixels: the body occupies {@code [0, w) x [0, h)}. This class
 * has no Minecraft dependencies so the shape can be previewed and tested offline.
 */
final class MouseSilhouette {

    /** Fraction of the height where the body is widest. */
    static final float WIDEST_T = 0.58f;
    /** Superellipse exponents: higher = blunter/squarer end. */
    static final float NOSE_EXP = 3.0f;
    static final float TAIL_EXP = 3.3f;
    /** Button plate depth (seam position) as a fraction of the height. */
    static final float SEAM_T = 0.43f;
    /** Wheel slot geometry as fractions of the body size. */
    static final float WHEEL_W_T = 0.21f;
    static final float WHEEL_TOP_T = 0.055f;
    /** Gap between a button plate and the wheel slot, and between plate edge and seam. */
    static final int PLATE_GAP = 2;
    static final int PALM_BANDS = 4;

    final int w;
    final int h;
    final int seamY;
    final int wheelX;
    final int wheelY;
    final int wheelW;
    final int wheelH;
    /** Height of the wheel-up / wheel-down tick zones at the ends of the slot. */
    final int tickH;
    /** Body-local rect of the middle-button (wheel) hit zone. */
    final int mmbY;
    final int mmbH;

    final int[] body;
    final int[] bodyStroke;
    final int[] shadow;
    final int[] seam;
    final int[] seamLight;
    final int[][] palmBands = new int[PALM_BANDS][];

    final int[] leftPlate;
    final int[] rightPlate;
    final int[] leftPlateStroke;
    final int[] rightPlateStroke;
    final int[] leftPlateGlow;
    final int[] rightPlateGlow;
    final int[] leftPlateGloss;
    final int[] rightPlateGloss;
    final int[] leftPlateEdge;
    final int[] rightPlateEdge;
    final int[] leftPlateCombo;
    final int[] rightPlateCombo;

    /** Plate hit rects (body-local): x0, y0, x1, y1. */
    final int leftPlateX1;
    final int rightPlateX0;

    private final Egg outline;

    MouseSilhouette(int w, int h) {
        this.w = w;
        this.h = h;
        this.outline = new Egg(w, h, 0);

        seamY = Math.round(h * SEAM_T);
        wheelW = Math.max(8, Math.round(w * WHEEL_W_T));
        wheelX = (w - wheelW) / 2;
        wheelY = Math.max(3, Math.round(h * WHEEL_TOP_T));
        wheelH = Math.max(12, seamY - wheelY - PLATE_GAP * 2 - 2);
        tickH = Math.max(6, Math.min(11, Math.round(wheelH * 0.26f)));
        mmbY = wheelY + tickH;
        mmbH = wheelH - tickH * 2;
        leftPlateX1 = wheelX - PLATE_GAP;
        rightPlateX0 = wheelX + wheelW + PLATE_GAP;

        Egg in1 = new Egg(w, h, 1);
        Egg in2 = new Egg(w, h, 2);
        Egg in3 = new Egg(w, h, 3);
        Egg out1 = new Egg(w, h, -1);

        body = SilhouetteRaster.fill(outline, 0, 0, w, h);
        // Layers whose edges are hidden (under the body, or inside it) skip anti-aliasing so
        // their rows collapse into a handful of quads.
        shadow = SilhouetteRaster.fill(out1, -1, -1, w + 1, h + 1, false);
        bodyStroke = SilhouetteRaster.stroke(outline, 0, 0, w, h, in1, 1, 1, w - 1, h - 1);

        // Seam between the button plate and the palm: a dark hairline with a light line under it,
        // both clipped to the inset body so they never poke through the outline.
        seam = SilhouetteRaster.fill(in1, 1, seamY, w - 1, seamY + 1);
        seamLight = SilhouetteRaster.fill(in2, 2, seamY + 1, w - 2, seamY + 2);

        // Palm shading: disjoint horizontal slices, each a little darker toward the tail.
        int palmTop = seamY + 2;
        int palmH = h - palmTop;
        for (int i = 0; i < PALM_BANDS; i++) {
            int y0 = palmTop + palmH * (i + 1) / (PALM_BANDS + 1);
            int y1 = i == PALM_BANDS - 1 ? h - 1 : palmTop + palmH * (i + 2) / (PALM_BANDS + 1);
            palmBands[i] = SilhouetteRaster.fill(in1, 1, y0, w - 1, y1, false);
        }

        // Plates: body ∩ rect, stroked so that both the outline arc and the straight seam / wheel
        // edges get a 1px border.
        int plateY1 = seamY - PLATE_GAP;
        leftPlate = SilhouetteRaster.fill(outline, 0, 0, leftPlateX1, plateY1);
        rightPlate = SilhouetteRaster.fill(outline, rightPlateX0, 0, w, plateY1);
        leftPlateStroke = SilhouetteRaster.stroke(outline, 0, 0, leftPlateX1, plateY1,
                in1, 1, 1, leftPlateX1 - 1, plateY1 - 1);
        rightPlateStroke = SilhouetteRaster.stroke(outline, rightPlateX0, 0, w, plateY1,
                in1, rightPlateX0 + 1, 1, w - 1, plateY1 - 1);
        leftPlateGlow = SilhouetteRaster.stroke(out1, -1, -1, leftPlateX1 + 1, plateY1 + 1,
                outline, 0, 0, leftPlateX1, plateY1);
        rightPlateGlow = SilhouetteRaster.stroke(out1, rightPlateX0 - 1, -1, w + 1, plateY1 + 1,
                outline, rightPlateX0, 0, w, plateY1);
        int glossY1 = Math.max(4, Math.round(plateY1 * 0.55f));
        leftPlateGloss = SilhouetteRaster.fill(in2, 2, 2, leftPlateX1 - 2, glossY1, false);
        rightPlateGloss = SilhouetteRaster.fill(in2, rightPlateX0 + 2, 2, w - 2, glossY1, false);
        int edgeH = Math.max(2, Math.round(h / 58f));
        leftPlateEdge = SilhouetteRaster.fill(in3, 3, plateY1 - 2 - edgeH, leftPlateX1 - 2, plateY1 - 2);
        rightPlateEdge = SilhouetteRaster.fill(in3, rightPlateX0 + 2, plateY1 - 2 - edgeH, w - 3, plateY1 - 2);
        int comboY0 = Math.max(3, Math.round(h * 0.07f));
        leftPlateCombo = SilhouetteRaster.fill(in3, 3, comboY0, leftPlateX1 - 3, comboY0 + 3);
        rightPlateCombo = SilhouetteRaster.fill(in3, rightPlateX0 + 3, comboY0, w - 3, comboY0 + 3);
    }

    /** True when the body-local point lies inside the egg outline. */
    boolean insideBody(double x, double y) {
        if (y < 0 || y >= h) return false;
        float yc = (float) y + 0.5f;
        return x >= outline.left(yc) && x < outline.right(yc);
    }

    /** Left body edge at the centre of body-local row {@code y}. */
    float leftEdge(int y) {
        return outline.left(y + 0.5f);
    }

    float rightEdge(int y) {
        return outline.right(y + 0.5f);
    }

    /**
     * Egg outline for the body inset by {@code inset} pixels on every side (negative expands).
     * Symmetric about the vertical centre line; the half-width follows a nose superellipse above
     * the widest row and a tail superellipse below it, both reaching the full half-width there.
     */
    static final class Egg implements SilhouetteRaster.Profile {
        private final float cx;
        private final float top;
        private final float height;
        private final float halfW;

        Egg(int w, int h, int inset) {
            this.cx = w / 2f;
            this.top = inset;
            this.height = h - inset * 2f;
            this.halfW = w / 2f - inset;
        }

        float halfWidth(float y) {
            if (height <= 0 || halfW <= 0) return 0;
            float t = (y - top) / height;
            if (t <= 0 || t >= 1) return 0;
            float k;
            if (t <= WIDEST_T) {
                float u = (WIDEST_T - t) / WIDEST_T;
                k = (float) Math.pow(1 - Math.pow(u, NOSE_EXP), 1 / NOSE_EXP);
            } else {
                float u = (t - WIDEST_T) / (1 - WIDEST_T);
                k = (float) Math.pow(1 - Math.pow(u, TAIL_EXP), 1 / TAIL_EXP);
            }
            return halfW * k;
        }

        @Override
        public float left(float y) {
            return cx - halfWidth(y);
        }

        @Override
        public float right(float y) {
            return cx + halfWidth(y);
        }
    }
}
