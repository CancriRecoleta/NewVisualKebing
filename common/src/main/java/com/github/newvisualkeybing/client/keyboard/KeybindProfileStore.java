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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class KeybindProfileStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static volatile KeybindProfileStore INSTANCE;

    public static KeybindProfileStore global() {
        KeybindProfileStore local = INSTANCE;
        if (local == null) {
            synchronized (KeybindProfileStore.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new KeybindProfileStore();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    public static int globalPriorityOf(String mappingName) {
        try {
            return global().priorityOf(mappingName);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean globalConflictIgnored(String mappingName) {
        try {
            return global().isConflictIgnored(mappingName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private final Path storeFile;
    private final Path exportDir;
    private StoreData data = new StoreData();
    private final java.util.List<Runnable> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public KeybindProfileStore() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        Path modDir = root.resolve("config").resolve(Constants.MOD_ID);
        this.storeFile = modDir.resolve("keybind_profiles.json");
        this.exportDir = modDir.resolve("exports");
        load();
        KeybindConfigWatcher.global().watch(
                storeFile.getFileName().toString(),
                this::serializeForCompare,
                this::reloadFromDisk);
    }

    private synchronized String serializeForCompare() {
        return GSON.toJson(data);
    }

    private void reloadFromDisk() {
        load();
        KeybindPriorityEnforcer.resetAndEnforce();
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

    public void load() {
        if (!Files.isRegularFile(storeFile)) {
            data = new StoreData();
            return;
        }
        try (Reader reader = Files.newBufferedReader(storeFile, StandardCharsets.UTF_8)) {
            StoreData loaded = GSON.fromJson(reader, StoreData.class);
            data = loaded == null ? new StoreData() : loaded;
            normalize();
        } catch (IOException | JsonSyntaxException ignored) {
            data = new StoreData();
        }
    }

    public void save() {
        try {
            Files.createDirectories(storeFile.getParent());
            try (Writer writer = Files.newBufferedWriter(storeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public List<Profile> profiles() {
        normalize();
        return data.profiles;
    }

    public Profile selectedProfile() {
        List<Profile> profiles = profiles();
        if (profiles.isEmpty()) return null;
        int index = Math.max(0, Math.min(data.selectedProfile, profiles.size() - 1));
        return profiles.get(index);
    }

    public int selectedIndex() {
        return Math.max(0, Math.min(data.selectedProfile, Math.max(0, profiles().size() - 1)));
    }

    public void select(int index) {
        data.selectedProfile = Math.max(0, Math.min(index, Math.max(0, profiles().size() - 1)));
        save();
    }

    public Profile saveCurrentProfile() {
        return saveCurrentProfile(null);
    }

    public Profile saveCurrentProfile(String requestedName) {
        Profile profile = selectedProfile();
        if (profile == null) {
            profile = new Profile(normalizeProfileName(requestedName, -1));
            data.profiles.add(profile);
            data.selectedProfile = data.profiles.size() - 1;
        } else if (requestedName != null && !requestedName.isBlank()) {
            profile.name = normalizeProfileName(requestedName, selectedIndex());
        }
        profile.updatedAt = LocalDateTime.now().toString();
        profile.bindings = captureBindings();
        profile.combos = KeybindComboStore.global().snapshot();
        save();
        return profile;
    }

    public Profile createProfileFromCurrent() {
        return createProfileFromCurrent(null);
    }

    public Profile createProfileFromCurrent(String requestedName) {
        Profile profile = new Profile(normalizeProfileName(requestedName, -1));
        profile.updatedAt = LocalDateTime.now().toString();
        profile.bindings = captureBindings();
        profile.combos = KeybindComboStore.global().snapshot();
        data.profiles.add(profile);
        data.selectedProfile = data.profiles.size() - 1;
        save();
        return profile;
    }

    public Profile renameSelectedProfile(String requestedName) {
        Profile profile = selectedProfile();
        if (profile == null) return null;
        profile.name = normalizeProfileName(requestedName, selectedIndex());
        profile.updatedAt = LocalDateTime.now().toString();
        save();
        return profile;
    }

    public boolean deleteSelectedProfile() {
        if (profiles().isEmpty()) return false;
        data.profiles.remove(selectedIndex());
        data.selectedProfile = Math.max(0, Math.min(data.selectedProfile, Math.max(0, data.profiles.size() - 1)));
        save();
        return true;
    }

    public boolean applySelectedProfile() {
        Profile profile = selectedProfile();
        if (profile == null) return false;
        Map<String, KeyMapping> byName = currentMappingsByName();
        // Rebuild the live priority map from this profile alone, mirroring the wholesale replace that
        // combos already get below. Merging in place (the old behaviour) left stale priorities for
        // every mapping the profile did not mention, so switching to a heterogeneous profile —
        // imported from a different mod set — kept the previous profile's values (F11). Mappings
        // absent from the profile now fall back to default 0 via priorityOf. Priority is applied for
        // each present mapping independently of whether its saved key still parses.
        data.priorities.clear();
        for (Binding binding : profile.bindings) {
            KeyMapping mapping = byName.get(binding.name);
            if (mapping == null) continue;
            data.priorities.put(mapping.getName(), binding.priority);
            if (binding.key == null) continue;
            InputConstants.Key key;
            try {
                key = InputConstants.getKey(binding.key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            mapping.setKey(key);
        }
        if (profile.combos != null) {
            KeybindComboStore.global().replaceCombos(profile.combos);
        }
        KeybindPriorityEnforcer.resetAndEnforce();
        Minecraft.getInstance().options.save();
        save();
        return true;
    }

    public Path exportSelectedProfile() {
        Profile profile = selectedProfile();
        if (profile == null) return null;
        profile.combos = KeybindComboStore.global().snapshot();
        Profile exported = copyProfile(profile);
        exported.exportedAt = LocalDateTime.now().toString();
        try {
            Files.createDirectories(exportDir);
            String fileName = sanitize(profile.name) + "-" + EXPORT_TIME.format(LocalDateTime.now()) + ".json";
            Path path = exportDir.resolve(fileName);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(exported, writer);
            }
            return path;
        } catch (IOException ignored) {
            return null;
        }
    }

    public Profile importLatestExport() {
        List<ExportEntry> exports = availableExports();
        if (exports.isEmpty()) return null;
        return importExport(exports.get(0).path);
    }

    /**
     * Enumerate every {@code .json} file under {@code exports/}, sorted newest first
     * by file modification time. Each entry carries the parsed profile metadata so the
     * UI can render a chooser without re-parsing.
     */
    public List<ExportEntry> availableExports() {
        List<ExportEntry> entries = new ArrayList<>();
        if (!Files.isDirectory(exportDir)) return entries;
        try (Stream<Path> stream = Files.list(exportDir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        ExportEntry entry = readExportMetadata(path);
                        if (entry != null) entries.add(entry);
                    });
        } catch (IOException ignored) {
        }
        entries.sort(Comparator.comparingLong((ExportEntry e) -> e.modifiedAt).reversed());
        return entries;
    }

    private ExportEntry readExportMetadata(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Profile parsed = GSON.fromJson(reader, Profile.class);
            if (parsed == null) return null;
            ExportEntry entry = new ExportEntry();
            entry.path = path;
            entry.profileName = parsed.name == null ? path.getFileName().toString() : parsed.name;
            entry.bindingCount = parsed.bindings == null ? 0 : parsed.bindings.size();
            entry.comboCount = parsed.combos == null ? 0 : parsed.combos.size();
            entry.exportedAt = parsed.exportedAt;
            try {
                entry.modifiedAt = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignored) {
                entry.modifiedAt = 0L;
            }
            return entry;
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
    }

    /**
     * Import a specific export file. Adds it as a new profile, selects it, and returns
     * the imported profile (or {@code null} on failure). Does not apply automatically;
     * the caller still drives the Apply action.
     */
    public Profile importExport(Path path) {
        if (path == null || !Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Profile imported = GSON.fromJson(reader, Profile.class);
            if (imported == null || imported.bindings == null) return null;
            imported.name = normalizeProfileName(imported.name, -1);
            imported.updatedAt = LocalDateTime.now().toString();
            if (imported.combos == null) imported.combos = new ArrayList<>();
            data.profiles.add(imported);
            data.selectedProfile = data.profiles.size() - 1;
            normalize();
            save();
            return imported;
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
    }

    public int priorityOf(KeyMapping mapping) {
        return priorityOf(mapping.getName());
    }

    public int priorityOf(String mappingName) {
        return data.priorities.getOrDefault(mappingName, 0);
    }

    public void changePriority(KeyMapping mapping, int delta) {
        int priority = Math.max(-999, Math.min(999, priorityOf(mapping) + delta));
        data.priorities.put(mapping.getName(), priority);
        Profile profile = selectedProfile();
        if (profile != null) {
            for (Binding binding : profile.bindings) {
                if (Objects.equals(binding.name, mapping.getName())) {
                    binding.priority = priority;
                    break;
                }
            }
        }
        save();
        KeybindPriorityEnforcer.resetAndEnforce();
    }

    /** Whether this mapping is manually excluded from conflict detection/display. */
    public boolean isConflictIgnored(String mappingName) {
        return mappingName != null && data.conflictIgnored.contains(mappingName);
    }

    public boolean isConflictIgnored(KeyMapping mapping) {
        return mapping != null && isConflictIgnored(mapping.getName());
    }

    /** Toggle the ignore-in-conflict flag for a mapping; returns the new state. */
    public boolean toggleConflictIgnored(KeyMapping mapping) {
        if (mapping == null) return false;
        String name = mapping.getName();
        boolean nowIgnored;
        if (data.conflictIgnored.contains(name)) {
            data.conflictIgnored.remove(name);
            nowIgnored = false;
        } else {
            data.conflictIgnored.add(name);
            nowIgnored = true;
        }
        save();
        return nowIgnored;
    }

    public List<KeyMapping> sortedMappings(KeyMapping[] mappings) {
        List<KeyMapping> list = new ArrayList<>(List.of(mappings));
        list.sort(Comparator
                .comparing(KeyMapping::getCategory)
                .thenComparing((KeyMapping mapping) -> -priorityOf(mapping))
                .thenComparing(mapping -> Component.translatable(mapping.getName()).getString(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public String compactProfileLabel(Profile profile) {
        if (profile == null) return "";
        return profile.name + " (" + profile.bindings.size() + ")";
    }

    private List<Binding> captureBindings() {
        List<Binding> bindings = new ArrayList<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            Binding binding = new Binding();
            binding.name = mapping.getName();
            binding.action = Component.translatable(mapping.getName()).getString();
            binding.category = mapping.getCategory();
            binding.categoryName = Component.translatable(mapping.getCategory()).getString();
            binding.key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey().getName();
            binding.defaultKey = mapping.getDefaultKey().getName();
            binding.priority = priorityOf(mapping);
            bindings.add(binding);
        }
        bindings.sort(Comparator
                .comparing((Binding binding) -> binding.category == null ? "" : binding.category)
                .thenComparing((Binding binding) -> -binding.priority)
                .thenComparing(binding -> binding.action == null ? "" : binding.action, String.CASE_INSENSITIVE_ORDER));
        return bindings;
    }

    private Map<String, KeyMapping> currentMappingsByName() {
        Map<String, KeyMapping> mappings = new LinkedHashMap<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            mappings.put(mapping.getName(), mapping);
        }
        return mappings;
    }

    private void normalize() {
        if (data.profiles == null) data.profiles = new ArrayList<>();
        if (data.priorities == null) data.priorities = new HashMap<>();
        if (data.conflictIgnored == null) data.conflictIgnored = new LinkedHashSet<>();
        for (Profile profile : data.profiles) {
            if (profile.bindings == null) profile.bindings = new ArrayList<>();
            if (profile.name == null || profile.name.isBlank()) profile.name = normalizeProfileName(null, -1);
        }
        // Deliberately does NOT seed data.priorities from profile bindings. The old back-fill merged
        // every profile's priorities into the single live map (first profile wins via putIfAbsent),
        // leaking values across profiles — most visibly right after importing a heterogeneous profile
        // (F11). data.priorities is the persisted live working set: replaced wholesale only by an
        // explicit applySelectedProfile(), and kept in sync per-edit by changePriority().
    }

    private String nextProfileName() {
        return Component.translatable("screen.newvisualkeybing.viewer.profile.default_name",
                data.profiles == null ? 1 : data.profiles.size() + 1).getString();
    }

    private String uniqueProfileName(String baseName) {
        return uniqueProfileName(baseName, -1);
    }

    private String uniqueProfileName(String baseName, int ignoredIndex) {
        String name = baseName;
        int index = 2;
        while (profileNameExists(name, ignoredIndex)) {
            name = baseName + " " + index++;
        }
        return name;
    }

    private boolean profileNameExists(String name) {
        return profileNameExists(name, -1);
    }

    private boolean profileNameExists(String name, int ignoredIndex) {
        for (int i = 0; i < data.profiles.size(); i++) {
            if (i == ignoredIndex) continue;
            Profile profile = data.profiles.get(i);
            if (Objects.equals(profile.name, name)) return true;
        }
        return false;
    }

    private String normalizeProfileName(String requestedName, int ignoredIndex) {
        String baseName = requestedName == null ? "" : requestedName.trim().replaceAll("\\s+", " ");
        if (baseName.isBlank()) baseName = nextProfileName();
        if (baseName.length() > 48) baseName = baseName.substring(0, 48).trim();
        return uniqueProfileName(baseName, ignoredIndex);
    }

    private static Profile copyProfile(Profile source) {
        Profile copy = new Profile(source.name);
        copy.updatedAt = source.updatedAt;
        copy.exportedAt = source.exportedAt;
        for (Binding binding : source.bindings) {
            Binding item = new Binding();
            item.name = binding.name;
            item.action = binding.action;
            item.category = binding.category;
            item.categoryName = binding.categoryName;
            item.key = binding.key;
            item.defaultKey = binding.defaultKey;
            item.priority = binding.priority;
            copy.bindings.add(item);
        }
        if (source.combos != null) {
            for (KeybindComboStore.ComboBinding combo : source.combos) {
                if (combo == null) continue;
                KeybindComboStore.ComboBinding item = new KeybindComboStore.ComboBinding();
                item.mappingName = combo.mappingName;
                item.action = combo.action;
                item.category = combo.category;
                item.firstKey = combo.firstKey;
                item.secondKey = combo.secondKey;
                item.updatedAt = combo.updatedAt;
                copy.combos.add(item);
            }
        }
        return copy;
    }

    private static String sanitize(String value) {
        String safe = value == null ? "profile" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return safe.isBlank() ? "profile" : safe;
    }

    private static final class StoreData {
        int version = 1;
        int selectedProfile;
        List<Profile> profiles = new ArrayList<>();
        Map<String, Integer> priorities = new HashMap<>();
        Set<String> conflictIgnored = new LinkedHashSet<>();
    }

    public static final class Profile {
        public String name;
        public String updatedAt;
        public String exportedAt;
        public List<Binding> bindings = new ArrayList<>();
        public List<KeybindComboStore.ComboBinding> combos = new ArrayList<>();

        public Profile() {
        }

        Profile(String name) {
            this.name = name;
        }
    }

    public static final class ExportEntry {
        public Path path;
        public String profileName;
        public String exportedAt;
        public long modifiedAt;
        public int bindingCount;
        public int comboCount;
    }

    public static final class Binding {
        public String name;
        public String action;
        public String category;
        public String categoryName;
        public String key;
        public String defaultKey;
        public int priority;
    }
}
