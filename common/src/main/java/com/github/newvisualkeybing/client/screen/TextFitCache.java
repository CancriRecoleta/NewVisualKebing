package com.github.newvisualkeybing.client.screen;

import net.minecraft.client.gui.Font;

import java.util.LinkedHashMap;
import java.util.Map;

final class TextFitCache {
    private static final int MAX_ENTRIES = 768;
    private static final int MODE_CHARS = 1;
    private static final int MODE_PLAIN = 2;
    private static final Map<Key, String> CACHE = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private TextFitCache() {}

    static void clear() {
        CACHE.clear();
    }

    static String fitByChars(Font font, String text, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        Key key = new Key(MODE_CHARS, text, maxW, font.lineHeight);
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        String ellipsis = "..";
        int eW = font.width(ellipsis);
        String result;
        if (maxW <= eW) {
            result = ellipsis;
        } else {
            StringBuilder sb = new StringBuilder();
            int w = 0;
            for (int i = 0; i < text.length(); i++) {
                int cw = font.width(String.valueOf(text.charAt(i)));
                if (w + cw + eW > maxW) break;
                sb.append(text.charAt(i));
                w += cw;
            }
            result = sb.append(ellipsis).toString();
        }
        CACHE.put(key, result);
        return result;
    }

    static String fitPlain(Font font, String text, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(text) <= maxW) return text;
        Key key = new Key(MODE_PLAIN, text, maxW, font.lineHeight);
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        String ellipsis = "..";
        int eW = font.width(ellipsis);
        String result = maxW <= eW ? ellipsis : font.plainSubstrByWidth(text, maxW - eW) + ellipsis;
        CACHE.put(key, result);
        return result;
    }

    private record Key(int mode, String text, int maxW, int lineHeight) {}
}
