package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class KeybindPriorityEnforcer {

    private static volatile Field cachedMapField;
    private static volatile boolean lookupFailed;

    private KeybindPriorityEnforcer() {}


    public static void resetAndEnforce() {
        KeyMapping.resetMapping();
        applyPriority();
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
            Map<InputConstants.Key, Object> liveMap = (Map<InputConstants.Key, Object>) field.get(null);
            if (liveMap == null) return;

            boolean listBacked = isListBacked(field, liveMap);
            Map<InputConstants.Key, List<KeyMapping>> grouped = new HashMap<>();
            for (KeyMapping mapping : mc.options.keyMappings) {
                InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
                if (key == null || key == InputConstants.UNKNOWN) continue;
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(mapping);
            }
            for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : grouped.entrySet()) {
                List<KeyMapping> mappings = entry.getValue();
                mappings.sort(PRIORITY_ORDER);
                KeyMapping winner = mappings.getFirst();
                if (listBacked) {
                    liveMap.put(entry.getKey(), new ArrayList<>(List.of(winner)));
                } else {
                    liveMap.put(entry.getKey(), winner);
                }
            }
        } catch (ClassCastException | IllegalAccessException ignored) {
            lookupFailed = true;
        }
    }

    private static final Comparator<KeyMapping> PRIORITY_ORDER =
            Comparator.comparingInt((KeyMapping mapping) -> KeybindProfileStore.globalPriorityOf(mapping.getName()))
                    .reversed()
                    .thenComparing(KeyMapping::getName);

    private static boolean isListBacked(Field field, Map<InputConstants.Key, Object> liveMap) {
        for (Object value : liveMap.values()) {
            if (value == null) continue;
            return value instanceof List<?>;
        }
        Type genericType = field.getGenericType();
        return genericType != null && genericType.getTypeName().contains("java.util.List");
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
