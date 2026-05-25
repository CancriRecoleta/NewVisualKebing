package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;


public final class KeybindPriorityEnforcer {

    private static volatile Field cachedMapField;
    private static volatile boolean lookupFailed;

    private KeybindPriorityEnforcer() {}


    public static void resetAndEnforce() {
        KeyMapping.resetMapping();
        applyPriority();
    }

    /**
     * Read the live {@code KeyMapping.MAP} winner for the given key, or {@code null} if
     * the field cannot be located or no mapping is bound. Used by the dispatch mixin to
     * decide whether vanilla {@code set}/{@code click} would touch a chord mapping that
     * we want to override.
     */
    public static KeyMapping mapWinner(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return null;
        Field field = cachedMapField;
        if (field == null && !lookupFailed) {
            field = locateMapField();
            if (field == null) {
                lookupFailed = true;
                return null;
            }
            cachedMapField = field;
        }
        if (field == null) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<InputConstants.Key, KeyMapping> liveMap = (Map<InputConstants.Key, KeyMapping>) field.get(null);
            return liveMap == null ? null : liveMap.get(key);
        } catch (IllegalAccessException ignored) {
            lookupFailed = true;
            return null;
        }
    }

    public static void applyPriority() {
        if (lookupFailed) return;
        Field field = cachedMapField;
        if (field == null) {
            field = locateMapField();
            if (field == null) {
                lookupFailed = true;
                return;
            }
            cachedMapField = field;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        try {
            @SuppressWarnings("unchecked")
            Map<InputConstants.Key, KeyMapping> liveMap = (Map<InputConstants.Key, KeyMapping>) field.get(null);
            if (liveMap == null) return;

            KeybindComboStore combos = KeybindComboStore.global();
            Map<InputConstants.Key, KeyMapping> winners = new HashMap<>();
            for (KeyMapping mapping : mc.options.keyMappings) {
                InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
                if (key == null || key == InputConstants.UNKNOWN) continue;
                KeyMapping current = winners.get(key);
                if (current == null || beats(combos, mapping, current)) {
                    winners.put(key, mapping);
                }
            }
            for (Map.Entry<InputConstants.Key, KeyMapping> entry : winners.entrySet()) {
                liveMap.put(entry.getKey(), entry.getValue());
            }
        } catch (IllegalAccessException ignored) {
            lookupFailed = true;
        }
    }


    /**
     * Pick the better of two same-key candidates. Mappings whose current key is the trigger
     * of a complete chord are demoted: a non-chord mapping always wins, so single-key
     * presses fall through to the non-chord mapping while the chord is dispatched manually
     * by {@link com.github.newvisualkeybing.mixin.MixinKeyMappingDispatch}.
     */
    private static boolean beats(KeybindComboStore combos, KeyMapping candidate, KeyMapping current) {
        boolean candidateChord = combos.matchesCurrentCombo(candidate);
        boolean currentChord = combos.matchesCurrentCombo(current);
        if (currentChord != candidateChord) return currentChord;
        return KeybindProfileStore.globalPriorityOf(candidate.getName())
                > KeybindProfileStore.globalPriorityOf(current.getName());
    }

    private static Field locateMapField() {
        String[] candidates = { "MAP", "f_90810_", "field_1665" };
        for (String name : candidates) {
            try {
                Field f = KeyMapping.class.getDeclaredField(name);
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        try {
            for (Field f : KeyMapping.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value = f.get(null);
                if (!(value instanceof Map<?, ?> m) || m.isEmpty()) continue;
                Object firstKey = m.keySet().iterator().next();
                if (firstKey instanceof InputConstants.Key) {
                    return f;
                }
            }
        } catch (IllegalAccessException e) {
            Constants.LOG.warn("Failed to locate KeyMapping MAP field via scan: {}", e.toString());
        }
        Constants.LOG.warn("[{}] Could not locate KeyMapping MAP field; priority will not affect runtime dispatch.",
                Constants.MOD_NAME);
        return null;
    }
}
