package com.github.newvisualkeybing.client.ui;

import java.util.Arrays;

/**
 * Converts an 8-bit coverage grid into a compact list of axis-aligned rectangles of uniform alpha.
 * Horizontal runs of equal alpha are merged first, then runs that line up exactly with a rectangle
 * from the previous row extend it downward. The result is a non-overlapping tiling of every
 * non-zero pixel, packed as {@code x0, y0, x1, y1, alpha} tuples (grid coordinates).
 */
final class AlphaGridRects {

    private AlphaGridRects() {}

    static int[] pack(int[] alpha, int gw, int gh) {
        int[] out = new int[Math.max(20, gw * 5)];
        int size = 0;
        // Indices (into out) of rects whose bottom edge is the row being processed.
        int[] prev = new int[Math.max(4, gw)];
        int prevCount = 0;
        int[] cur = new int[Math.max(4, gw)];
        for (int y = 0; y < gh; y++) {
            int curCount = 0;
            int x = 0;
            int row = y * gw;
            while (x < gw) {
                int a = alpha[row + x];
                if (a <= 0) {
                    x++;
                    continue;
                }
                int x0 = x;
                x++;
                while (x < gw && alpha[row + x] == a) x++;
                int match = -1;
                for (int i = 0; i < prevCount; i++) {
                    int idx = prev[i];
                    if (out[idx] == x0 && out[idx + 2] == x && out[idx + 4] == a && out[idx + 3] == y) {
                        match = idx;
                        break;
                    }
                }
                if (match >= 0) {
                    out[match + 3] = y + 1;
                } else {
                    if (size + 5 > out.length) out = Arrays.copyOf(out, out.length * 2);
                    match = size;
                    out[size++] = x0;
                    out[size++] = y;
                    out[size++] = x;
                    out[size++] = y + 1;
                    out[size++] = a;
                }
                if (curCount == cur.length) cur = Arrays.copyOf(cur, cur.length * 2);
                cur[curCount++] = match;
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
            prevCount = curCount;
        }
        return Arrays.copyOf(out, size);
    }
}
