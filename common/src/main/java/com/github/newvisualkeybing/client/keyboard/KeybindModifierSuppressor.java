package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defers a chord modifier's own single-key action so a follow-up chord trigger has a
 * chance to claim it. Without this, pressing {@code Ctrl} (when Ctrl is bound to sneak)
 * would activate sneak immediately, then {@code T} would fire the chord on top — both
 * actions firing for what the user intended as a single chord input.
 *
 * <p>Strategy: when the modifier is pressed, capture the deferred press/click in a
 * pending entry and cancel the vanilla event. Then one of three things happens:
 * <ul>
 *   <li>A chord that uses this modifier fires within the lookahead window — the
 *       dispatch mixin calls {@link #consume(String)} and we drop the pending entry,
 *       so the modifier's own action never fires.</li>
 *   <li>The modifier is released before the window closes — we replay the press/click
 *       as a momentary tap (giving the single-key action a chance to register).</li>
 *   <li>The window expires while the key is still held — we replay just the press so
 *       hold-style mappings (sneak, sprint) start tracking from that moment.</li>
 * </ul>
 *
 * <p>Lives outside the dispatch mixin class so it can be safely referenced from both
 * the dispatch and tick mixins regardless of how Mixin merges static helpers.
 */
public final class KeybindModifierSuppressor {

    /** Window during which a modifier press is held back, in game ticks (~50 ms each). */
    public static final int LOOKAHEAD_TICKS = 3;

    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private KeybindModifierSuppressor() {}

    /**
     * Mark vanilla's {@code setDown(true)} for {@code key} as deferred. Returns true
     * when vanilla should be cancelled, false when no target mapping exists and vanilla
     * should proceed unchanged.
     */
    public static boolean deferPress(InputConstants.Key key) {
        if (!hasChordRole(key)) return false;
        KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
        if (mapWinner == null) return false;
        PENDING.computeIfAbsent(key.getName(),
                k -> new Pending(mapWinner, LOOKAHEAD_TICKS)).pressDeferred = true;
        return true;
    }

    /**
     * Mark vanilla's {@code click} for {@code key} as deferred. Returns true when
     * vanilla should be cancelled.
     */
    public static boolean deferClick(InputConstants.Key key) {
        if (!hasChordRole(key)) return false;
        KeyMapping mapWinner = KeybindPriorityEnforcer.mapWinner(key);
        if (mapWinner == null) return false;
        PENDING.computeIfAbsent(key.getName(),
                k -> new Pending(mapWinner, LOOKAHEAD_TICKS)).clickDeferred = true;
        return true;
    }

    /**
     * Handle a release for a key that may have a deferred press. Replays the deferred
     * press/click as a momentary tap and clears the entry. Returns true when an entry
     * existed (caller should cancel vanilla); false otherwise.
     */
    public static boolean handleRelease(InputConstants.Key key) {
        Pending pending = PENDING.remove(key.getName());
        if (pending == null) return false;
        replay(pending);
        if (pending.pressDeferred) {
            pending.mapping.setDown(false);
        }
        return true;
    }

    /**
     * Cancel any pending defer for a modifier key. Called when a chord using that
     * modifier actually fires, so the modifier's own action is dropped.
     */
    public static void consume(String modifierName) {
        if (modifierName == null) return;
        PENDING.remove(modifierName);
    }

    /**
     * Drive aging of pending entries; called once per game tick from the client tick
     * mixin. Replays and removes entries whose window expired (or whose key is no
     * longer held — a quick tap that ended before we got the next tick).
     */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        Iterator<Map.Entry<String, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Pending> entry = it.next();
            Pending pending = entry.getValue();
            if (!KeybindComboStore.isKeyHeld(entry.getKey())) {
                replay(pending);
                if (pending.pressDeferred) {
                    pending.mapping.setDown(false);
                }
                it.remove();
                continue;
            }
            pending.ticksLeft--;
            if (pending.ticksLeft <= 0) {
                replay(pending);
                it.remove();
            }
        }
    }

    /** True when the key is the {@code firstKey} of at least one complete combo. */
    private static boolean hasChordRole(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return false;
        Set<InputConstants.Key> triggers = KeybindComboStore.global().triggersForFirstKey(key);
        return !triggers.isEmpty();
    }

    private static void replay(Pending pending) {
        if (pending == null || pending.mapping == null) return;
        if (pending.pressDeferred) {
            pending.mapping.setDown(true);
        }
        if (pending.clickDeferred) {
            KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) pending.mapping;
            accessor.newvisualkeybing$setClickCount(accessor.newvisualkeybing$getClickCount() + 1);
        }
    }

    private static final class Pending {
        final KeyMapping mapping;
        int ticksLeft;
        boolean pressDeferred;
        boolean clickDeferred;

        Pending(KeyMapping mapping, int ticks) {
            this.mapping = mapping;
            this.ticksLeft = ticks;
        }
    }
}
