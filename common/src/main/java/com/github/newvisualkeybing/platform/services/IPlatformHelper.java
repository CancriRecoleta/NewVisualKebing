package com.github.newvisualkeybing.platform.services;

import net.minecraft.client.KeyMapping;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    
    default String getModName(String modId) { return null; }

    
    default ConflictContext getConflictContext(KeyMapping mapping) {
        return ConflictContext.UNIVERSAL;
    }

    default InputModifier getKeyModifier(KeyMapping mapping) {
        return InputModifier.NONE;
    }

    default InputModifier getDefaultKeyModifier(KeyMapping mapping) {
        return InputModifier.NONE;
    }

    enum ConflictContext {
        UNIVERSAL, IN_GAME, GUI, UNKNOWN;

        public boolean conflicts(ConflictContext other) {
            if (this == UNIVERSAL || other == UNIVERSAL) return true;
            return this == other;
        }
    }

    enum InputModifier {
        NONE(""),
        CONTROL("Ctrl"),
        SHIFT("Shift"),
        ALT("Alt"),
        UNKNOWN("");

        private final String displayName;

        InputModifier(String displayName) {
            this.displayName = displayName;
        }

        public boolean isCombination() {
            return this != NONE && this != UNKNOWN;
        }

        public String displayName() {
            return displayName;
        }
    }
}
