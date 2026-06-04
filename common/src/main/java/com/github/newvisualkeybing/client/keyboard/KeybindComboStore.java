package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.InputModifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;



public final class KeybindComboStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile KeybindComboStore INSTANCE;

    public static KeybindComboStore global() {
        KeybindComboStore local = INSTANCE;
        if (local == null) {
            synchronized (KeybindComboStore.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new KeybindComboStore();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private final Path storeFile;
    private StoreData data = new StoreData();
    private volatile long version;
    /** Lock-free hint mirroring {@code !data.combos.isEmpty()}; lets hot paths skip combo work. */
    private volatile boolean comboPresent;
    private final java.util.List<Runnable> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private KeybindComboStore() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.storeFile = root.resolve("config").resolve(Constants.MOD_ID).resolve("combo_keybinds.json");
        load();
        KeybindConfigWatcher.global().watch(
                storeFile.getFileName().toString(),
                this::serializeForCompare,
                this::reloadFromDisk);
    }

    /** Serialize current state to a string identical to what {@link #save()} writes; used by the watcher. */
    private synchronized String serializeForCompare() {
        return GSON.toJson(data);
    }

    private void reloadFromDisk() {
        load();
        for (Runnable listener : reloadListeners) {
            try { listener.run(); } catch (Throwable ignored) {}
        }
    }

    public void addReloadListener(Runnable listener) {
        if (listener != null) reloadListeners.add(listener);
    }

    public void removeReloadListener(Runnable listener) {
        reloadListeners.remove(listener);
    }

    public synchronized void load() {
        if (!Files.isRegularFile(storeFile)) {
            data = new StoreData();
            bumpVersion();
            return;
        }
        try (Reader reader = Files.newBufferedReader(storeFile, StandardCharsets.UTF_8)) {
            StoreData loaded = GSON.fromJson(reader, StoreData.class);
            data = loaded == null ? new StoreData() : loaded;
            normalize();
        } catch (IOException | JsonSyntaxException e) {
            Constants.LOG.warn("Failed to load combo keybind store: {}", e.toString());
            data = new StoreData();
        }
        bumpVersion();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(storeFile.getParent());
            try (Writer writer = Files.newBufferedWriter(storeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Constants.LOG.warn("Failed to save combo keybind store: {}", e.toString());
        }
    }

    public long version() {
        return version;
    }

    private void bumpVersion() {
        version++;
        comboPresent = data.combos != null && !data.combos.isEmpty();
    }

    /**
     * Lock-free check for whether any combo is configured. Hot paths (per-key dispatch,
     * per-frame rendering) use this to skip all combo bookkeeping in the common no-chord
     * case without taking the store monitor. Kept in sync by {@link #bumpVersion()}.
     */
    public boolean hasCombos() {
        return comboPresent;
    }

    private void normalize() {
        if (data.combos == null) data.combos = new ArrayList<>();
        data.combos.removeIf(combo -> combo == null
                || combo.mappingName == null || combo.mappingName.isBlank()
                || combo.firstKey == null || combo.firstKey.isBlank()
                || combo.secondKey == null || combo.secondKey.isBlank());
    }

    public synchronized List<ComboBinding> combos() {
        return Collections.unmodifiableList(new ArrayList<>(data.combos));
    }

    public synchronized ComboBinding findByMapping(String mappingName) {
        if (mappingName == null) return null;
        for (ComboBinding combo : data.combos) {
            if (mappingName.equals(combo.mappingName)) return combo;
        }
        return null;
    }

    public synchronized void putCombo(KeyMapping mapping,
                                      InputConstants.Key first,
                                      InputConstants.Key second) {
        if (mapping == null || first == null || second == null) return;
        if (first == InputConstants.UNKNOWN || second == InputConstants.UNKNOWN) return;
        String name = mapping.getName();
        ComboBinding existing = findByMapping(name);
        if (existing == null) {
            existing = new ComboBinding();
            existing.mappingName = name;
            data.combos.add(existing);
        }
        existing.category = mapping.getCategory();
        existing.action = Component.translatable(name).getString();
        existing.firstKey = first.getName();
        existing.secondKey = second.getName();
        existing.updatedAt = LocalDateTime.now().toString();
        save();
        bumpVersion();
    }

    public synchronized boolean removeCombo(String mappingName) {
        if (mappingName == null) return false;
        boolean removed = data.combos.removeIf(combo -> mappingName.equals(combo.mappingName));
        if (removed) {
            save();
            bumpVersion();
        }
        return removed;
    }

    public synchronized void clear() {
        if (data.combos.isEmpty()) return;
        data.combos.clear();
        save();
        bumpVersion();
    }

    /**
     * Returns a deep-copied snapshot of every stored combo, suitable for serialization
     * inside a {@link KeybindProfileStore} profile export.
     */
    public synchronized List<ComboBinding> snapshot() {
        List<ComboBinding> copy = new ArrayList<>(data.combos.size());
        for (ComboBinding source : data.combos) {
            if (source == null) continue;
            ComboBinding target = new ComboBinding();
            target.mappingName = source.mappingName;
            target.action = source.action;
            target.category = source.category;
            target.firstKey = source.firstKey;
            target.secondKey = source.secondKey;
            target.updatedAt = source.updatedAt;
            copy.add(target);
        }
        return copy;
    }

    /**
     * Replace the entire set of stored combos with the supplied list. Used by profile
     * import so a profile fully describes the chord configuration alongside key bindings.
     */
    public synchronized void replaceCombos(List<ComboBinding> incoming) {
        data.combos.clear();
        if (incoming != null) {
            for (ComboBinding source : incoming) {
                if (source == null) continue;
                ComboBinding target = new ComboBinding();
                target.mappingName = source.mappingName;
                target.action = source.action;
                target.category = source.category;
                target.firstKey = source.firstKey;
                target.secondKey = source.secondKey;
                target.updatedAt = source.updatedAt == null
                        ? LocalDateTime.now().toString() : source.updatedAt;
                data.combos.add(target);
            }
        }
        normalize();
        save();
        bumpVersion();
    }

    /** Reload combos from disk; used by the hot-reload watcher. */
    public synchronized void reload() {
        load();
    }

    public synchronized int size() {
        return data.combos.size();
    }


    /**
     * Returns the distinct set of trigger ({@code secondKey}) keys whose combos use the given
     * key as their modifier ({@code firstKey}). Used to drive precise re-sync when a modifier
     * is pressed or released, so dispatch never has to walk every combo.
     */
    public synchronized Set<InputConstants.Key> triggersForFirstKey(InputConstants.Key firstKey) {
        if (firstKey == null || firstKey == InputConstants.UNKNOWN) return Collections.emptySet();
        String name = firstKey.getName();
        Set<InputConstants.Key> result = new LinkedHashSet<>();
        for (ComboBinding combo : data.combos) {
            if (!isComplete(combo)) continue;
            if (!sameInput(combo.firstKey, name)) continue;
            parseInput(combo.secondKey).ifPresent(result::add);
        }
        return result;
    }

    /** Returns the set of virtual key codes that participate in any combo. */
    public synchronized Set<Integer> participantVirtualKeys() {
        Set<Integer> result = new LinkedHashSet<>();
        for (ComboBinding combo : data.combos) {
            Integer first = resolveVirtualKey(combo.firstKey);
            Integer second = resolveVirtualKey(combo.secondKey);
            if (first != null) result.add(first);
            if (second != null) result.add(second);
        }
        return result;
    }

    public synchronized List<ComboBinding> combosForVirtualKey(int virtualKey) {
        List<ComboBinding> result = new ArrayList<>();
        for (ComboBinding combo : data.combos) {
            Integer first = resolveVirtualKey(combo.firstKey);
            Integer second = resolveVirtualKey(combo.secondKey);
            if ((first != null && first == virtualKey) || (second != null && second == virtualKey)) {
                result.add(combo);
            }
        }
        return result;
    }


    public static Integer resolveVirtualKey(String inputName) {
        if (inputName == null || inputName.isBlank()) return null;
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(inputName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (key == InputConstants.UNKNOWN) return null;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return KeyboardLayoutData.mouseToVirtual(key.getValue());
        }
        return key.getValue();
    }

    public static String displayName(String inputName) {
        if (inputName == null) return "";
        try {
            InputConstants.Key key = InputConstants.getKey(inputName);
            return key.getDisplayName().getString();
        } catch (IllegalArgumentException ignored) {
            return inputName;
        }
    }

    public static String describeMapping(String mappingName) {
        if (mappingName == null) return "";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null) {
            for (KeyMapping mapping : mc.options.keyMappings) {
                if (Objects.equals(mapping.getName(), mappingName)) {
                    return Component.translatable(mapping.getName()).getString();
                }
            }
        }
        return Component.translatable(mappingName).getString();
    }

    /**
     * Cache of {@code mappingName -> KeyMapping}. This association is fixed once mods finish
     * registering their key mappings — a rebind changes a mapping's bound key (read live via
     * {@link #currentKey}), never its name or identity — so caching it is accuracy-neutral. It
     * turns the combo dispatch from O(combos × keyMappings) into O(combos), the dominant cost on
     * the chord input path. Invalidated defensively from the same {@code resetMapping} hook as the
     * priority enforcer's key index. {@code null} means "rebuild on next access".
     */
    private static volatile Map<String, KeyMapping> mappingByNameCache;

    /** Drop the {@code mappingName -> KeyMapping} cache; rebuilt lazily on next {@link #findMapping}. */
    public static void invalidateMappingCache() {
        mappingByNameCache = null;
    }

    public static KeyMapping findMapping(String mappingName) {
        if (mappingName == null) return null;
        Map<String, KeyMapping> cache = mappingByNameCache;
        if (cache == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return null;
            cache = new HashMap<>();
            // putIfAbsent preserves the original "first mapping with this name wins" semantics.
            for (KeyMapping mapping : mc.options.keyMappings) {
                cache.putIfAbsent(mapping.getName(), mapping);
            }
            mappingByNameCache = cache;
        }
        return cache.get(mappingName);
    }

    public static InputConstants.Key currentKey(KeyMapping mapping) {
        if (mapping == null) return InputConstants.UNKNOWN;
        return ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
    }

    public synchronized boolean hasCurrentCombo(String mappingName) {
        KeyMapping mapping = findMapping(mappingName);
        return mapping != null && matchesCurrentCombo(mapping);
    }

    public synchronized boolean matchesCurrentCombo(KeyMapping mapping) {
        if (mapping == null) return false;
        ComboBinding combo = findByMapping(mapping.getName());
        if (!isComplete(combo)) return false;
        // Key equivalence must match triggerMatches(), which compares with sameInput: otherwise a
        // secondKey and the mapping's live key that denote the SAME key via different
        // strings (hand-edited JSON, an alias, canonical-name drift) would make triggerMatches treat
        // the mapping as a chord trigger while singleKeyMappings (which excludes via this method)
        // still treats it as a plain single — landing it in both lists and firing it as a bare key
        // when the modifier is not held (F13). sameInput parses both names and compares by key.
        return sameInput(combo.secondKey, currentKey(mapping).getName());
    }

    public synchronized String activatorSignature(String mappingName, InputModifier modifier) {
        KeyMapping mapping = findMapping(mappingName);
        if (mapping != null) return activatorSignature(mapping, modifier);
        ComboBinding combo = findByMapping(mappingName);
        if (isComplete(combo)) return activatorSignature(combo.firstKey);
        return modifierSignature(modifier);
    }

    public synchronized String activatorSignature(KeyMapping mapping, InputModifier modifier) {
        if (mapping != null) {
            ComboBinding combo = findByMapping(mapping.getName());
            if (isComplete(combo) && sameInput(combo.secondKey, currentKey(mapping).getName())) {
                return activatorSignature(combo.firstKey);
            }
        }
        return modifierSignature(modifier);
    }

    public synchronized List<Match> triggerMatches(InputConstants.Key triggerKey) {
        if (triggerKey == null || triggerKey == InputConstants.UNKNOWN) return Collections.emptyList();
        String triggerName = triggerKey.getName();
        List<Match> result = new ArrayList<>();
        for (ComboBinding combo : data.combos) {
            if (!isComplete(combo)) continue;
            if (!sameInput(combo.secondKey, triggerName)) continue;
            KeyMapping mapping = findMapping(combo.mappingName);
            if (mapping == null) continue;
            if (!sameInput(currentKey(mapping).getName(), triggerName)) continue;
            result.add(new Match(combo, mapping, isKeyHeld(combo.firstKey)));
        }
        return result;
    }

    /**
     * Drop combos whose mapping has been rebound away from the combo's trigger key. A combo's
     * {@code secondKey} is, by construction, the mapping's bound key — every in-mod rebind path
     * re-creates or removes the combo to keep that true (see {@code KeybindEditScreen}). An
     * <em>external</em> rebind through the vanilla controls screen changes only the key, orphaning
     * the combo and leaving a dormant residual (F14). Invoked from the {@code resetMapping} hook —
     * the single funnel every rebind passes through — this removes the orphan, matching what the
     * mod's own rebind does. Mappings not currently loaded are left untouched (the owning mod may be
     * re-added later); those combos still show as dormant until then. Returns {@code true} if any
     * combo was removed.
     */
    public synchronized boolean reconcileToBoundKeys() {
        boolean removed = data.combos.removeIf(combo -> {
            if (!isComplete(combo)) return false;
            KeyMapping mapping = findMapping(combo.mappingName);
            if (mapping == null) return false;
            return !sameInput(combo.secondKey, currentKey(mapping).getName());
        });
        if (removed) {
            save();
            bumpVersion();
        }
        return removed;
    }

    private static boolean isComplete(ComboBinding combo) {
        return combo != null
                && combo.firstKey != null && !combo.firstKey.isBlank()
                && combo.secondKey != null && !combo.secondKey.isBlank()
                && !sameInput(combo.firstKey, combo.secondKey);
    }

    private static boolean sameInput(String a, String b) {
        if (Objects.equals(a, b)) return true;
        Optional<InputConstants.Key> ka = parseInput(a);
        Optional<InputConstants.Key> kb = parseInput(b);
        return ka.isPresent() && kb.isPresent() && sameKey(ka.get(), kb.get());
    }

    private static Optional<InputConstants.Key> parseInput(String inputName) {
        if (inputName == null || inputName.isBlank()) return Optional.empty();
        try {
            InputConstants.Key key = InputConstants.getKey(inputName);
            return key == InputConstants.UNKNOWN ? Optional.empty() : Optional.of(key);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String activatorSignature(String inputName) {
        Optional<InputConstants.Key> parsed = parseInput(inputName);
        if (parsed.isEmpty()) return "key:" + String.valueOf(inputName);
        InputConstants.Key key = parsed.get();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return switch (key.getValue()) {
                case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "modifier:control";
                case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "modifier:shift";
                case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "modifier:alt";
                default -> "key:" + key.getType() + ":" + key.getValue();
            };
        }
        return "key:" + key.getType() + ":" + key.getValue();
    }

    private static String modifierSignature(InputModifier modifier) {
        if (modifier == null || !modifier.isCombination()) return "none";
        return switch (modifier) {
            case CONTROL -> "modifier:control";
            case SHIFT -> "modifier:shift";
            case ALT -> "modifier:alt";
            default -> "modifier:" + modifier.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static boolean sameKey(InputConstants.Key a, InputConstants.Key b) {
        return a != null && b != null && a.getType() == b.getType() && a.getValue() == b.getValue();
    }

    public static boolean isKeyHeld(String inputName) {
        if (inputName == null) return false;
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(inputName);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (key == InputConstants.UNKNOWN) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long handle = mc.getWindow().getWindow();
        if (handle == 0L) return false;
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(handle, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(handle, key.getValue());
    }

    private static final class StoreData {
        int version = 1;
        List<ComboBinding> combos = new ArrayList<>();
    }

    public static final class ComboBinding {
        public String mappingName;
        public String action;
        public String category;
        public String firstKey;
        public String secondKey;
        public String updatedAt;

        public String displayFirst()  { return displayName(firstKey); }
        public String displaySecond() { return displayName(secondKey); }

        public String comboLabel() {
            return displayFirst() + " + " + displaySecond();
        }
    }

    public record Match(ComboBinding combo, KeyMapping mapping, boolean active) {}
}
