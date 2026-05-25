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
import java.util.LinkedHashSet;
import java.util.List;
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
    private final java.util.List<Runnable> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private KeybindComboStore() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.storeFile = root.resolve("config").resolve(Constants.MOD_ID).resolve("combo_keybinds.json");
        load();
        try {
            KeybindConfigWatcher.global().watch(
                    storeFile.getFileName().toString(),
                    this::serializeForCompare,
                    this::reloadFromDisk);
        } catch (Throwable ignored) {
            // Watcher is best-effort; failure should not stop the store from working.
        }
    }

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

    /** Deep-copied snapshot for inclusion in profile exports. */
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
     * Replace every stored combo with the supplied list. Called during profile import so
     * a profile fully owns the chord configuration alongside its key bindings.
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

    public synchronized int size() {
        return data.combos.size();
    }


    /**
     * Returns the distinct set of trigger ({@code secondKey}) keys whose combos use the
     * given key as their modifier ({@code firstKey}). Used by the dispatch mixin to
     * precisely re-sync chord state when a modifier is pressed or released without
     * walking every combo.
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

    private volatile Set<Integer> cachedParticipantSet;
    private volatile int[] cachedParticipantSorted;
    private volatile long cachedParticipantsVersion = Long.MIN_VALUE;

    private void ensureParticipantCache() {
        long v = version;
        if (cachedParticipantsVersion == v && cachedParticipantSet != null) return;
        synchronized (this) {
            if (cachedParticipantsVersion == version && cachedParticipantSet != null) return;
            Set<Integer> result = new LinkedHashSet<>();
            for (ComboBinding combo : data.combos) {
                Integer first = resolveVirtualKey(combo.firstKey);
                Integer second = resolveVirtualKey(combo.secondKey);
                if (first != null) result.add(first);
                if (second != null) result.add(second);
            }
            int[] sorted = new int[result.size()];
            int i = 0;
            for (Integer key : result) sorted[i++] = key;
            java.util.Arrays.sort(sorted);
            cachedParticipantSet = Collections.unmodifiableSet(result);
            cachedParticipantSorted = sorted;
            cachedParticipantsVersion = version;
        }
    }

    /** Returns the set of virtual key codes that participate in any combo (cached). */
    public Set<Integer> participantVirtualKeys() {
        ensureParticipantCache();
        return cachedParticipantSet;
    }

    /**
     * Primitive-int participation test that hits a cached sorted array — no boxing,
     * no rebuild per call. Hot path: called per mouse button per frame from
     * {@link com.github.newvisualkeybing.client.screen.KeybindMouseRenderer}.
     */
    public boolean isParticipant(int virtualKey) {
        ensureParticipantCache();
        int[] arr = cachedParticipantSorted;
        return arr.length > 0 && java.util.Arrays.binarySearch(arr, virtualKey) >= 0;
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

    public static KeyMapping findMapping(String mappingName) {
        if (mappingName == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return null;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (Objects.equals(mapping.getName(), mappingName)) return mapping;
        }
        return null;
    }

    public static InputConstants.Key currentKey(KeyMapping mapping) {
        if (mapping == null) return InputConstants.UNKNOWN;
        return ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
    }

    public synchronized boolean hasCompleteCombo(String mappingName) {
        ComboBinding combo = findByMapping(mappingName);
        return isComplete(combo);
    }

    public synchronized boolean hasCurrentCombo(String mappingName) {
        KeyMapping mapping = findMapping(mappingName);
        return mapping != null && matchesCurrentCombo(mapping);
    }

    public synchronized boolean sameCombo(String mappingA, String mappingB) {
        if (Objects.equals(mappingA, mappingB)) return true;
        ComboBinding a = findByMapping(mappingA);
        ComboBinding b = findByMapping(mappingB);
        if (!isComplete(a) || !isComplete(b)) return false;
        return Objects.equals(a.firstKey, b.firstKey) && Objects.equals(a.secondKey, b.secondKey);
    }

    public synchronized boolean combosConflict(String mappingA, String mappingB) {
        ComboBinding a = findByMapping(mappingA);
        ComboBinding b = findByMapping(mappingB);
        if (!isComplete(a) || !isComplete(b)) return true;
        return sameInput(a.secondKey, b.secondKey) && sameInput(a.firstKey, b.firstKey);
    }

    public synchronized boolean matchesCurrentCombo(KeyMapping mapping) {
        if (mapping == null) return false;
        ComboBinding combo = findByMapping(mapping.getName());
        if (!isComplete(combo)) return false;
        return Objects.equals(combo.secondKey, currentKey(mapping).getName());
    }

    public synchronized boolean isComboTarget(KeyMapping mapping, InputConstants.Key triggerKey) {
        if (mapping == null || triggerKey == null || triggerKey == InputConstants.UNKNOWN) return false;
        ComboBinding combo = findByMapping(mapping.getName());
        if (!isComplete(combo)) return false;
        return sameInput(combo.secondKey, triggerKey.getName())
                && sameInput(currentKey(mapping).getName(), triggerKey.getName());
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

    public synchronized Match findMatchingCombo(InputConstants.Key triggerKey) {
        if (triggerKey == null || triggerKey == InputConstants.UNKNOWN) return null;
        String triggerName = triggerKey.getName();
        Match fallback = null;
        for (ComboBinding combo : data.combos) {
            if (!isComplete(combo)) continue;
            if (!Objects.equals(combo.secondKey, triggerName)) continue;
            KeyMapping mapping = findMapping(combo.mappingName);
            if (mapping == null) continue;
            if (!Objects.equals(currentKey(mapping).getName(), triggerName)) continue;
            Match match = new Match(combo, mapping, isKeyHeld(combo.firstKey));
            if (match.active()) return match;
            if (fallback == null) fallback = match;
        }
        return fallback;
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

    public synchronized void syncComboStates() {
        for (ComboBinding combo : data.combos) {
            if (!isComplete(combo)) continue;
            KeyMapping mapping = findMapping(combo.mappingName);
            if (mapping == null) continue;
            if (!sameInput(combo.secondKey, currentKey(mapping).getName())) continue;
            mapping.setDown(isKeyHeld(combo.firstKey) && isKeyHeld(combo.secondKey));
        }
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

    /**
     * Whether the modifier key of a combo bound to the same trigger as {@code triggerKey}
     * is required. Returns the combo whose modifier needs to be held, or {@code null} when
     * no combo with that trigger exists. Used by the dispatch mixin to decide whether to
     * suppress a vanilla single-key click.
     */
    public synchronized ComboBinding triggerCombo(InputConstants.Key triggerKey, KeyMapping mapping) {
        if (triggerKey == null || mapping == null) return null;
        String triggerName = triggerKey.getName();
        for (ComboBinding combo : data.combos) {
            if (!Objects.equals(combo.mappingName, mapping.getName())) continue;
            if (Objects.equals(combo.secondKey, triggerName)) return combo;
        }
        return null;
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
