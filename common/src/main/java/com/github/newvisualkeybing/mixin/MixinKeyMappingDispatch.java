package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.keyboard.KeybindComboStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Suppresses vanilla single-key dispatch when the mapping has a combo binding
 * whose modifier key is not currently held — so K alone won't trigger an
 * action that the user has remapped to Ctrl+K.
 */
@Mixin(KeyMapping.class)
public class MixinKeyMappingDispatch {

    @Inject(method = "click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onClick(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping mapping = newvisualkeybing$lookupMapping(key);
        if (mapping != null && newvisualkeybing$shouldSuppress(key, mapping)) {
            ci.cancel();
        }
    }

    @Inject(method = "set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void newvisualkeybing$onSet(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeyMapping mapping = newvisualkeybing$lookupMapping(key);
        if (mapping == null) return;
        if (held) {
            if (newvisualkeybing$shouldSuppress(key, mapping)) {
                mapping.setDown(false);
                ci.cancel();
            }
        }
    }

    private static KeyMapping newvisualkeybing$lookupMapping(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return null;
        Map<InputConstants.Key, KeyMapping> map = newvisualkeybing$mapField();
        if (map == null) return null;
        return map.get(key);
    }

    private static boolean newvisualkeybing$shouldSuppress(InputConstants.Key key, KeyMapping mapping) {
        KeybindComboStore store = KeybindComboStore.global();
        KeybindComboStore.ComboBinding combo = store.triggerCombo(key, mapping);
        if (combo == null) return false;
        if (combo.firstKey == null || combo.secondKey == null) return false;
        if (combo.firstKey.equals(combo.secondKey)) return false;
        return !KeybindComboStore.isKeyHeld(combo.firstKey);
    }

    @SuppressWarnings("unchecked")
    private static Map<InputConstants.Key, KeyMapping> newvisualkeybing$mapField() {
        try {
            java.lang.reflect.Field field = newvisualkeybing$resolveMapField();
            if (field == null) return null;
            return (Map<InputConstants.Key, KeyMapping>) field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static volatile java.lang.reflect.Field newvisualkeybing$cachedField;
    private static volatile boolean newvisualkeybing$lookupFailed;

    private static java.lang.reflect.Field newvisualkeybing$resolveMapField() {
        if (newvisualkeybing$lookupFailed) return null;
        java.lang.reflect.Field cached = newvisualkeybing$cachedField;
        if (cached != null) return cached;
        for (String name : new String[] { "MAP", "f_90810_", "field_1665" }) {
            try {
                java.lang.reflect.Field f = KeyMapping.class.getDeclaredField(name);
                f.setAccessible(true);
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && Map.class.isAssignableFrom(f.getType())) {
                    newvisualkeybing$cachedField = f;
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        try {
            for (java.lang.reflect.Field f : KeyMapping.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object value = f.get(null);
                if (!(value instanceof Map<?, ?> m) || m.isEmpty()) continue;
                if (m.keySet().iterator().next() instanceof InputConstants.Key) {
                    newvisualkeybing$cachedField = f;
                    return f;
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        newvisualkeybing$lookupFailed = true;
        return null;
    }
}
