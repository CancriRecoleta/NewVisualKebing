package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight, self-contained Hanzi&rarr;pinyin matcher for the search fields. Lets users type a
 * latin query (full pinyin, syllable prefixes, or first-letter initials) to find Chinese action
 * and category names, e.g. {@code "qj"}, {@code "qian"} or {@code "qianjin"} all match
 * {@code "前进"}. Backed by a compact toneless table bundled as a resource; readings for
 * polyphonic characters are all considered.
 */
public final class Pinyin {

    private static final String RESOURCE = "/assets/" + Constants.MOD_ID + "/pinyin.txt";

    /** codepoint -> toneless readings (lowercase, deduped). Loaded once, lazily. */
    private static volatile Map<Integer, String[]> table;

    private Pinyin() {}

    private static Map<Integer, String[]> table() {
        Map<Integer, String[]> local = table;
        if (local != null) return local;
        synchronized (Pinyin.class) {
            if (table != null) return table;
            table = load();
            return table;
        }
    }

    private static Map<Integer, String[]> load() {
        Map<Integer, String[]> map = new HashMap<>(24000);
        try (InputStream in = Pinyin.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Constants.LOG.warn("Pinyin data resource missing: {}", RESOURCE);
                return map;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    int sp = line.indexOf(' ');
                    if (sp <= 0) continue;
                    int cp;
                    try {
                        cp = Integer.parseInt(line.substring(0, sp), 16);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    map.put(cp, line.substring(sp + 1).split(","));
                }
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to load pinyin data: {}", e.toString());
        }
        return map;
    }

    /**
     * Whether {@code text} matches {@code query} by pinyin. {@code query} is treated as a latin
     * pinyin fragment; a Chinese character may be satisfied by a full reading, a reading prefix, or
     * its initial letter, and the run must be contiguous (but may start anywhere in {@code text}).
     */
    public static boolean matches(String text, String query) {
        if (text == null || query == null) return false;
        if (query.isEmpty()) return false;
        // A pinyin query is plain latin letters; bail out early for anything else so we don't pay
        // the matching cost for queries that can only be plain-substring matches.
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c < 'a' || c > 'z') return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Map<Integer, String[]> map = table();
        // matchFrom(ti, qi) depends only on (ti, qi), so one memo is shared across all start offsets.
        boolean[][] visited = new boolean[lower.length() + 1][query.length() + 1];
        boolean[][] failed = new boolean[lower.length() + 1][query.length() + 1];
        for (int start = 0; start < lower.length(); start++) {
            if (matchFrom(lower, start, query, 0, map, visited, failed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchFrom(String text, int ti, String query, int qi,
                                     Map<Integer, String[]> map, boolean[][] visited, boolean[][] failed) {
        if (qi >= query.length()) return true;
        if (ti >= text.length()) return false;
        if (visited[ti][qi]) return !failed[ti][qi];
        visited[ti][qi] = true;

        boolean result = false;
        char c = text.charAt(ti);
        String[] readings = map.get((int) c);
        if (readings != null) {
            for (String r : readings) {
                // Query ends partway through this syllable, e.g. "qi" within "qian".
                if (r.length() >= query.length() - qi && r.startsWith(query.substring(qi))) {
                    result = true;
                    break;
                }
                // Consume the full syllable and continue with the next character.
                if (query.regionMatches(qi, r, 0, r.length())
                        && matchFrom(text, ti + 1, query, qi + r.length(), map, visited, failed)) {
                    result = true;
                    break;
                }
                // Consume just the initial letter (abbreviated pinyin).
                if (query.charAt(qi) == r.charAt(0)
                        && matchFrom(text, ti + 1, query, qi + 1, map, visited, failed)) {
                    result = true;
                    break;
                }
            }
        } else if (query.charAt(qi) == c) {
            // Non-Han character: must match literally and contiguously.
            result = matchFrom(text, ti + 1, query, qi + 1, map, visited, failed);
        }

        failed[ti][qi] = !result;
        return result;
    }
}
