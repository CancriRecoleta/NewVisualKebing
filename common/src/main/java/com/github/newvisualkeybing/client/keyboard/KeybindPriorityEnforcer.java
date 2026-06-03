package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.Services;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.InputModifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class KeybindPriorityEnforcer {

    /**
     * Lazily-built index of {@code boundKey -> mappings bound to it}, so {@link #singleKeyMappings}
     * is an O(1) lookup instead of a full scan of every key mapping on each key event. The bound
     * key of a mapping only ever changes through {@link KeyMapping#resetMapping()} (both vanilla
     * rebinds and this mod's edits funnel through it), so that is the single invalidation point —
     * see {@code MixinKeyMappingDispatch#newvisualkeybing$onResetMapping}. {@code null} means "rebuild
     * on next access". Combo state is intentionally NOT part of the index; it is applied afterwards.
     */
    private static volatile Map<InputConstants.Key, List<KeyMapping>> cachedKeyIndex;

    private KeybindPriorityEnforcer() {}

    /** Drop the cached key index; rebuilt lazily on the next {@link #singleKeyMappings} call. */
    public static void invalidateKeyIndex() {
        cachedKeyIndex = null;
    }


    /**
     * Re-publish the key bindings after an edit or rebind. Priority is <em>not</em> pushed into any
     * static per-key map (that would be a no-op on Forge, where {@code KeyMapping.MAP} is a
     * {@code KeyMappingLookup}, and redundant on Fabric); instead
     * {@link com.github.newvisualkeybing.mixin.MixinKeyMappingDispatch} enforces priority live at
     * dispatch time. The {@link KeyMapping#resetMapping()} call is still load-bearing: the mixin's
     * {@code resetMapping} TAIL hook invalidates {@link #invalidateKeyIndex() this enforcer's key
     * index} and the combo cache off this very call, so the next dispatch sees the new bindings.
     */
    public static void resetAndEnforce() {
        KeyMapping.resetMapping();
    }

    /**
     * Collect every NON-chord mapping currently bound to {@code key} by scanning the live
     * key-mapping list directly. This is the basis of the full-key no-conflict dispatch: when a key
     * is pressed, <em>all</em> plain single-key bindings on it are activated together, instead of
     * letting only vanilla's single per-key winner through.
     */
    public static List<KeyMapping> singleKeyMappings(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return new ArrayList<>();
        // O(1) lookup against the bound-key index instead of scanning every mapping per key event.
        List<KeyMapping> bound = keyIndex().get(key);
        if (bound == null || bound.isEmpty()) return new ArrayList<>();
        KeybindComboStore combos = KeybindComboStore.global();
        // When no chords are configured (the common case) the per-mapping combo check is a pure
        // no-op, so just hand back a defensive copy of the (typically 1-element) index bucket.
        if (!combos.hasCombos()) return new ArrayList<>(bound);
        List<KeyMapping> result = new ArrayList<>(bound.size());
        for (KeyMapping mapping : bound) {
            if (combos.matchesCurrentCombo(mapping)) continue;
            result.add(mapping);
        }
        return result;
    }

    /**
     * Snapshot of every key that currently has at least one binding bound to it. Used by the
     * {@code setAll()} re-sync (see {@code MixinKeyMappingDispatch#newvisualkeybing$onSetAll}) to
     * restore chord/priority state after vanilla re-polls each binding's {@code isDown} from its own
     * key alone. Returns an immutable copy so callers can iterate safely across index rebuilds.
     */
    public static Set<InputConstants.Key> boundKeys() {
        return Set.copyOf(keyIndex().keySet());
    }

    /**
     * The bound-key index, rebuilt lazily after {@link #invalidateKeyIndex()}. Returns an empty
     * (uncached) map while the client/options are not yet available, so the real index is built
     * on the first real lookup rather than being frozen empty during early startup.
     */
    private static Map<InputConstants.Key, List<KeyMapping>> keyIndex() {
        Map<InputConstants.Key, List<KeyMapping>> index = cachedKeyIndex;
        if (index != null) return index;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return Collections.emptyMap();
        index = buildKeyIndex(mc);
        cachedKeyIndex = index;
        return index;
    }

    private static Map<InputConstants.Key, List<KeyMapping>> buildKeyIndex(Minecraft mc) {
        Map<InputConstants.Key, List<KeyMapping>> index = new HashMap<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            InputConstants.Key bound = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (bound == null || bound == InputConstants.UNKNOWN) continue;
            // InputConstants.Key instances are interned singletons (vanilla itself keys its own
            // per-key index by them), so they are sound HashMap keys.
            index.computeIfAbsent(bound, k -> new ArrayList<>(2)).add(mapping);
        }
        return index;
    }

    /**
     * Resolve which of {@code candidates} (all bound to the same key) should actually fire. The
     * candidate set is narrowed in three stages, then the highest surviving priority tier fires:
     * <ol>
     *   <li><b>Modifier gating</b> — replicate Forge's {@code KeyMappingLookup.getAll}: only the
     *       bucket matching the currently-held modifier (Ctrl &gt; Shift &gt; Alt precedence) is
     *       eligible, falling back to the plain (no-modifier) bucket only when no binding requires
     *       the held modifier. This stops a bare-key press from also firing a native {@code Ctrl+G}
     *       binding once this mixin has taken over dispatch.</li>
     *   <li><b>Scene gating</b> — drop bindings whose conflict context cannot trigger in the current
     *       scene, so a higher-priority binding that is inactive here never blocks a lower one that
     *       can fire (scene-aware fall-through).</li>
     *   <li><b>Priority tiering</b> — among what survives, only the highest priority tier fires;
     *       equal-priority bindings in that tier all fire together.</li>
     * </ol>
     * Stages 1 and 2 use a single shared {@link SceneProbe} snapshot so dispatch and display agree.
     * Returns every member of the winning tier (possibly empty).
     */
    public static List<KeyMapping> resolveByPriority(List<KeyMapping> candidates) {
        if (candidates.isEmpty()) return candidates;
        return resolveActive(activeModifierBucket(candidates));
    }

    /**
     * Priority resolution for chord (combo) candidates. Chords are already modifier-resolved by the
     * mod's own first/second-key mechanism, so native {@link #activeModifierBucket modifier gating}
     * is skipped (it would wrongly drop a chord whose mapping also declares a native key modifier);
     * scene gating and priority tiering still apply.
     */
    public static List<KeyMapping> resolveCombosByPriority(List<KeyMapping> candidates) {
        if (candidates.isEmpty()) return candidates;
        return resolveActive(candidates);
    }

    /** Scene-gate {@code bucket} then return every member of its highest surviving priority tier. */
    private static List<KeyMapping> resolveActive(List<KeyMapping> bucket) {
        SceneProbe scene = SceneProbe.capture();
        List<KeyMapping> active = new ArrayList<>(bucket.size());
        for (KeyMapping km : bucket) {
            if (Services.PLATFORM.isContextActive(km, scene)) active.add(km);
        }
        if (active.isEmpty()) return Collections.emptyList();
        int best = Integer.MIN_VALUE;
        for (KeyMapping km : active) {
            best = Math.max(best, KeybindProfileStore.globalPriorityOf(km.getName()));
        }
        List<KeyMapping> result = new ArrayList<>();
        for (KeyMapping km : active) {
            if (KeybindProfileStore.globalPriorityOf(km.getName()) == best) result.add(km);
        }
        return result;
    }

    /**
     * The subset of {@code candidates} whose key modifier matches the currently-held one, mirroring
     * Forge's {@code KeyMappingLookup.getAll}. The single active modifier follows Forge's
     * Ctrl&gt;Shift&gt;Alt precedence (via {@link Screen#hasControlDown()} etc., which work on both
     * loaders); when a modifier is held but no candidate requires it, dispatch falls back to the
     * plain bucket so an unrelated held modifier never swallows a plain binding. On Fabric every
     * mapping reports {@link InputModifier#NONE}, so this collapses to the plain bucket and is a
     * no-op — preserving vanilla behaviour there.
     */
    private static List<KeyMapping> activeModifierBucket(List<KeyMapping> candidates) {
        InputModifier active = currentModifier();
        List<KeyMapping> primary = filterByModifier(candidates, active);
        if (active != InputModifier.NONE && primary.isEmpty()) {
            return filterByModifier(candidates, InputModifier.NONE);
        }
        return primary;
    }

    private static List<KeyMapping> filterByModifier(List<KeyMapping> candidates, InputModifier wanted) {
        List<KeyMapping> result = new ArrayList<>(candidates.size());
        for (KeyMapping km : candidates) {
            if (normalizeModifier(Services.PLATFORM.getKeyModifier(km)) == wanted) result.add(km);
        }
        return result;
    }

    /** Forge's active-modifier precedence; both loaders expose these static helpers. */
    private static InputModifier currentModifier() {
        if (Screen.hasControlDown()) return InputModifier.CONTROL;
        if (Screen.hasShiftDown()) return InputModifier.SHIFT;
        if (Screen.hasAltDown()) return InputModifier.ALT;
        return InputModifier.NONE;
    }

    /** Treat {@link InputModifier#UNKNOWN} as the plain bucket so it never strands a binding. */
    private static InputModifier normalizeModifier(InputModifier modifier) {
        return modifier == null || modifier == InputModifier.UNKNOWN ? InputModifier.NONE : modifier;
    }
}
