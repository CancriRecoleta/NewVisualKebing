package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
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

    private KeybindComboStore() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.storeFile = root.resolve("config").resolve(Constants.MOD_ID).resolve("combo_keybinds.json");
        load();
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

    public synchronized int size() {
        return data.combos.size();
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

    public boolean isParticipant(int virtualKey) {
        return participantVirtualKeys().contains(virtualKey);
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
}
