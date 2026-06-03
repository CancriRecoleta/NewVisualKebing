package com.github.newvisualkeybing.client.keyboard;

import net.minecraft.client.Minecraft;

/**
 * Immutable snapshot of the activeness-relevant dimensions of the current scene, captured once and
 * shared by both the runtime dispatch ({@link KeybindPriorityEnforcer#resolveByPriority}) and the
 * conflict-display scan, so the two can never disagree about "what scene is this".
 *
 * <p>Vanilla/Fabric {@link net.minecraft.client.KeyMapping} carries no per-binding conflict context,
 * so only {@code IN_GAME}/{@code GUI}/{@code UNIVERSAL} can be derived from these dimensions today
 * (see {@link com.github.newvisualkeybing.platform.services.IPlatformHelper#isContextActive}). The
 * record is intentionally the single extension point for finer scene dimensions (container vs chat
 * screen, riding, sneaking, game mode, F3) should richer contexts be introduced later.
 */
public record SceneProbe(boolean hasLevel, boolean screenOpen) {

    /** A neutral probe used before the client is ready; treats nothing as in-game and no screen open. */
    public static final SceneProbe EMPTY = new SceneProbe(false, false);

    /** Capture the current scene on the render/main thread. Cheap; safe to call per dispatch event. */
    public static SceneProbe capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return EMPTY;
        boolean hasLevel = mc.level != null && mc.player != null;
        return new SceneProbe(hasLevel, mc.screen != null);
    }
}
