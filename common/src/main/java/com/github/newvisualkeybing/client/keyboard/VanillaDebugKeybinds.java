package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class VanillaDebugKeybinds {

    private static final String DEBUG_CATEGORY_ID = "minecraft:debug";
    private static final String DEBUG_KEY_PREFIX = "key.debug.";
    private static final String DEBUG_OVERLAY_KEY = "key.debug.overlay";
    private static final String DEBUG_MODIFIER_KEY = "key.debug.modifier";
    private static final InputConstants.Key DEFAULT_DEBUG_MODIFIER =
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F3);

    private VanillaDebugKeybinds() {
    }

    public static boolean isDebugCombination(KeyMapping mapping) {
        return mapping != null
                && isDebugCombination(mapping.getName(), categoryKey(mapping));
    }

    public static boolean isDebugCombination(String translationKey, String categoryKey) {
        return isDebugCategory(categoryKey)
                && translationKey != null
                && translationKey.startsWith(DEBUG_KEY_PREFIX)
                && !DEBUG_OVERLAY_KEY.equals(translationKey)
                && !DEBUG_MODIFIER_KEY.equals(translationKey);
    }

    public static String modifierInputName() {
        return currentModifierKey().getName();
    }

    public static String displayCurrentCombo(KeyMapping mapping) {
        return displayCurrentCombo(currentKey(mapping));
    }

    public static String displayCurrentCombo(InputConstants.Key triggerKey) {
        return displayName(currentModifierKey()) + " + " + displayName(triggerKey);
    }

    public static String displayDefaultCombo(InputConstants.Key triggerKey) {
        return displayName(DEFAULT_DEBUG_MODIFIER) + " + " + displayName(triggerKey);
    }

    private static InputConstants.Key currentModifierKey() {
        KeyMapping mapping = findDebugModifierMapping();
        InputConstants.Key key = currentKey(mapping);
        return key == InputConstants.UNKNOWN ? DEFAULT_DEBUG_MODIFIER : key;
    }

    private static KeyMapping findDebugModifierMapping() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) return null;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (DEBUG_MODIFIER_KEY.equals(mapping.getName())) return mapping;
        }
        return null;
    }

    private static InputConstants.Key currentKey(KeyMapping mapping) {
        if (mapping == null) return InputConstants.UNKNOWN;
        return ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
    }

    private static String displayName(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return "";
        return key.getDisplayName().getString();
    }

    private static String categoryKey(KeyMapping mapping) {
        return mapping.getCategory().id().toString();
    }

    private static boolean isDebugCategory(String categoryKey) {
        return DEBUG_CATEGORY_ID.equals(categoryKey);
    }
}
