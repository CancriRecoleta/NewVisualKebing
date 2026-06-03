package com.github.newvisualkeybing.platform.services;

import com.github.newvisualkeybing.client.keyboard.SceneProbe;
import net.minecraft.client.KeyMapping;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    /** Ids of every loaded mod, used to attribute keybinds to their owning mod. */
    default java.util.Set<String> getLoadedModIds() {
        return java.util.Set.of();
    }

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    
    default String getModName(String modId) { return null; }


    default ConflictContext getConflictContext(KeyMapping mapping) {
        return ConflictContext.UNIVERSAL;
    }

    /**
     * Whether two same-key bindings would actually contend in the same scene. This is the
     * <em>relational</em> conflict test used by the viewer and the edit screen. The default falls
     * back to the coarse 4-value {@link ConflictContext#conflicts} approximation; the Forge platform
     * overrides it to delegate to the authoritative native {@code IKeyConflictContext.conflicts},
     * so mod-defined custom contexts (collapsed to {@link ConflictContext#UNKNOWN} by
     * {@link #getConflictContext}) keep their real mutual-exclusion semantics instead of being
     * forced to "UNKNOWN == UNKNOWN ⇒ conflict" (false positive) or "UNKNOWN vs IN_GAME ⇒ no
     * conflict" (false negative).
     */
    default boolean contextsConflict(KeyMapping a, KeyMapping b) {
        ConflictContext ca = getConflictContext(a);
        ConflictContext cb = getConflictContext(b);
        return ca != null && cb != null && ca.conflicts(cb);
    }

    /**
     * Whether {@code mapping}'s conflict context can fire in the given scene. Used by priority
     * resolution to fall through from a higher-priority binding that cannot trigger here (e.g. a
     * GUI-only binding while in-game) to a lower-priority one that can. The default derives
     * activeness from the conflict context and the scene; platforms with a richer context API
     * override (Forge delegates to the native {@code IKeyConflictContext.isActive}).
     */
    default boolean isContextActive(KeyMapping mapping, SceneProbe scene) {
        return switch (getConflictContext(mapping)) {
            case IN_GAME -> scene.hasLevel() && !scene.screenOpen();
            case GUI -> scene.screenOpen();
            case UNIVERSAL, UNKNOWN -> true;
        };
    }

    /** Convenience overload that captures the current scene; prefer the scene-aware variant in loops. */
    default boolean isContextActive(KeyMapping mapping) {
        return isContextActive(mapping, SceneProbe.capture());
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
