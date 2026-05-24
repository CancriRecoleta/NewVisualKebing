package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.mixin.KeyMappingAccessor;
import com.github.newvisualkeybing.platform.Services;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.ConflictContext;
import com.github.newvisualkeybing.platform.services.IPlatformHelper.InputModifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public class KeyBindingScanner {

    public enum KeyStatus {
        FREE,
        SELF,
        OTHER_SINGLE,
        COMBO,
        BOUND,
        CONFLICT
    }

    public record KeyBindingInfo(
            String translationKey,
            String actionName,
            String categoryKey,
            String categoryName,
            String modId,
            String modName,
            boolean self,
            ConflictContext conflictContext,
            InputModifier modifier,
            InputModifier defaultModifier,
            String baseKeyName,
            String currentKeyName,
            String defaultKeyName,
            String searchText,
            boolean currentCombo,
            String activatorSignature
    ) {
        public String contextDescription() {
            return switch (conflictContext) {
                case UNIVERSAL -> Component.translatable("screen.newvisualkeybing.viewer.context.universal").getString();
                case IN_GAME   -> Component.translatable("screen.newvisualkeybing.viewer.context.in_game").getString();
                case GUI       -> Component.translatable("screen.newvisualkeybing.viewer.context.gui").getString();
                case UNKNOWN   -> Component.translatable("screen.newvisualkeybing.viewer.context.unknown").getString();
            };
        }
    }

    public record ScanStats(int total, int free, int self, int other, int combo, int bound, int conflict) {}
    public record ModStats(int bindings, int inputs, int conflicts) {}

    private static final Set<String> VANILLA_CATEGORIES = Set.of(
            "gameplay", "inventory", "movement", "multiplayer", "ui", "misc", "narrator", "creative"
    );

    private final Map<Integer, List<KeyBindingInfo>> keyboardBindings = new LinkedHashMap<>();
    private final Map<Integer, List<KeyBindingInfo>> mouseBindings = new LinkedHashMap<>();
    private final Map<Integer, KeyStatus> keyboardStatuses = new HashMap<>();
    private final Map<Integer, KeyStatus> mouseStatuses = new HashMap<>();
    private final Map<Integer, String> keyLabelCache = new HashMap<>();
    private final Map<String, String> actionNameCache = new HashMap<>();
    private final Map<String, String> categoryNameCache = new HashMap<>();
    private final Map<String, String> displayNameCache = new HashMap<>();
    private final Map<String, String> resolvedModIdCache = new HashMap<>();
    private final Map<String, String> resolvedModNameCache = new HashMap<>();
    private final Map<String, String> registeredMods = new LinkedHashMap<>();
    private final Map<String, ModStats> modStats = new HashMap<>();
    private final Map<String, Set<Integer>> modKeys = new HashMap<>();
    private Map<String, String> sortedRegisteredMods = Collections.emptyMap();
    private ScanStats stats = new ScanStats(0, 0, 0, 0, 0, 0, 0);
    private long version;

    private long lastScanTime = -1L;
    private static final long RESCAN_INTERVAL_MS = 3000L;

    private long cachedFilterKeysVersion = -1L;
    private String cachedFilterKeysQuery;
    private Set<Integer> cachedFilterKeys;
    private long cachedFilterStatusVersion = -1L;
    private FilterTab cachedFilterStatusTab;
    private Set<Integer> cachedFilterStatus;
    private long cachedFilterModVersion = -1L;
    private String cachedFilterModId;
    private Set<Integer> cachedFilterMod;

    public boolean refreshIfNeeded() {
        if (System.currentTimeMillis() - lastScanTime <= RESCAN_INTERVAL_MS) return false;
        scan();
        return true;
    }

    public void scan() {
        keyboardBindings.clear();
        mouseBindings.clear();
        keyboardStatuses.clear();
        mouseStatuses.clear();
        registeredMods.clear();
        modStats.clear();
        modKeys.clear();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            sortedRegisteredMods = Collections.emptyMap();
            stats = computeStats();
            version++;
            lastScanTime = System.currentTimeMillis();
            return;
        }

        KeybindComboStore comboStore = KeybindComboStore.global();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            String actionKey = mapping.getName();
            String categoryKey = categoryKey(mapping);
            String modId = resolveModId(actionKey, categoryKey);
            String modName = resolveModName(modId, categoryKey);
            registeredMods.putIfAbsent(modId, modName);

            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (key == InputConstants.UNKNOWN) continue;

            ConflictContext ctx = Services.PLATFORM.getConflictContext(mapping);
            InputModifier modifier = Services.PLATFORM.getKeyModifier(mapping);
            InputModifier defaultModifier = Services.PLATFORM.getDefaultKeyModifier(mapping);
            String baseKeyName = displayName(key);
            String defaultBaseKeyName = displayName(mapping.getDefaultKey());
            String actionName = actionName(actionKey);
            String categoryName = categoryName(categoryKey, mapping);
            String currentKeyName = displayKeyName(modifier, baseKeyName);
            String defaultKeyName = displayKeyName(defaultModifier, defaultBaseKeyName);
            boolean currentCombo = comboStore.matchesCurrentCombo(mapping);
            KeyBindingInfo info = new KeyBindingInfo(
                    actionKey,
                    actionName,
                    categoryKey,
                    categoryName,
                    modId,
                    modName,
                    Constants.MOD_ID.equals(modId),
                    ctx,
                    modifier,
                    defaultModifier,
                    baseKeyName,
                    currentKeyName,
                    defaultKeyName,
                    searchText(actionName, actionKey, categoryName, categoryKey, modId, modName, currentKeyName),
                    currentCombo,
                    comboStore.activatorSignature(mapping, modifier)
            );

            if (key.getType() == InputConstants.Type.MOUSE) {
                mouseBindings.computeIfAbsent(key.getValue(), k -> new ArrayList<>()).add(info);
            } else if (key.getValue() > GLFW.GLFW_KEY_UNKNOWN) {
                keyboardBindings.computeIfAbsent(key.getValue(), k -> new ArrayList<>()).add(info);
            }
        }

        Comparator<KeyBindingInfo> comparator = Comparator
                .comparing(KeyBindingInfo::self).reversed()
                .thenComparing(KeyBindingInfo::modName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(KeyBindingInfo::actionName, String.CASE_INSENSITIVE_ORDER);
        keyboardBindings.values().forEach(list -> list.sort(comparator));
        mouseBindings.values().forEach(list -> list.sort(comparator));

        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : keyboardBindings.entrySet()) {
            keyboardStatuses.put(entry.getKey(), computeStatus(entry.getValue()));
        }
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : mouseBindings.entrySet()) {
            mouseStatuses.put(entry.getKey(), computeStatus(entry.getValue()));
        }

        List<Map.Entry<String, String>> sortedMods = new ArrayList<>(registeredMods.entrySet());
        sortedMods.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : sortedMods) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        sortedRegisteredMods = sorted;
        rebuildModStats();
        stats = computeStats();
        version++;
        lastScanTime = System.currentTimeMillis();
    }

    public KeyStatus getStatus(int glfwKey) {
        return keyboardStatuses.getOrDefault(glfwKey, KeyStatus.FREE);
    }

    public KeyStatus getMouseStatus(int mouseButton) {
        return mouseStatuses.getOrDefault(mouseButton, KeyStatus.FREE);
    }

    public List<KeyBindingInfo> getBindings(int glfwKey) {
        List<KeyBindingInfo> list = keyboardBindings.get(glfwKey);
        return list == null ? Collections.emptyList() : list;
    }

    public List<KeyBindingInfo> getMouseBindings(int mouseButton) {
        List<KeyBindingInfo> list = mouseBindings.get(mouseButton);
        return list == null ? Collections.emptyList() : list;
    }

    public List<KeyBindingInfo> getVirtualBindings(int virtualKey) {
        if (KeyboardLayoutData.isWheel(virtualKey)) return Collections.emptyList();
        if (KeyboardLayoutData.isMouse(virtualKey)) return getMouseBindings(KeyboardLayoutData.virtualToMouseBtn(virtualKey));
        return getBindings(virtualKey);
    }

    public int getBindingCount(int glfwKey) {
        List<KeyBindingInfo> list = keyboardBindings.get(glfwKey);
        return list == null ? 0 : list.size();
    }

    public int getMouseBindingCount(int mouseButton) {
        List<KeyBindingInfo> list = mouseBindings.get(mouseButton);
        return list == null ? 0 : list.size();
    }

    public KeyStatus getVirtualStatus(int virtualKey) {
        if (KeyboardLayoutData.isWheel(virtualKey)) return KeyStatus.FREE;
        if (KeyboardLayoutData.isMouse(virtualKey)) return getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(virtualKey));
        return getStatus(virtualKey);
    }

    public int getVirtualBindingCount(int virtualKey) {
        return getVirtualBindings(virtualKey).size();
    }

    public boolean hasBindingForMod(int virtualKey, String modId) {
        if (modId == null || modId.isBlank()) return false;
        Set<Integer> keys = modKeys.get(modId);
        return keys != null && keys.contains(virtualKey);
    }

    public String primaryModId(int virtualKey) {
        List<KeyBindingInfo> bindings = getVirtualBindings(virtualKey);
        return bindings.isEmpty() ? null : bindings.get(0).modId();
    }

    public String primaryModName(int virtualKey) {
        List<KeyBindingInfo> bindings = getVirtualBindings(virtualKey);
        return bindings.isEmpty() ? "" : bindings.get(0).modName();
    }

    public Map<Integer, List<KeyBindingInfo>> getAllBindings() {
        return Collections.unmodifiableMap(keyboardBindings);
    }

    public Map<Integer, List<KeyBindingInfo>> getAllMouseBindings() {
        return Collections.unmodifiableMap(mouseBindings);
    }

    public Map<String, String> getAllRegisteredMods() {
        return sortedRegisteredMods;
    }

    public ModStats getModStats(String modId) {
        return modStats.getOrDefault(modId, new ModStats(0, 0, 0));
    }

    public long version() {
        return version;
    }

    public Set<Integer> filterByStatus(FilterTab tab) {
        if (tab == null || tab == FilterTab.ALL) return null;
        if (cachedFilterStatusVersion == version && cachedFilterStatusTab == tab) {
            return cachedFilterStatus;
        }
        Set<Integer> matches = new LinkedHashSet<>();
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            if (matchesStatus(getStatus(key.glfwKey()), tab)) matches.add(key.glfwKey());
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            if (matchesStatus(getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(key.glfwKey())), tab)) {
                matches.add(key.glfwKey());
            }
        }
        cachedFilterStatusVersion = version;
        cachedFilterStatusTab = tab;
        cachedFilterStatus = matches;
        return matches;
    }

    public Set<Integer> filterByMod(String modId) {
        if (modId == null || modId.isBlank()) return null;
        if (cachedFilterModVersion == version && modId.equals(cachedFilterModId)) {
            return cachedFilterMod;
        }
        Set<Integer> matches = modKeys.getOrDefault(modId, Collections.emptySet());
        cachedFilterModVersion = version;
        cachedFilterModId = modId;
        cachedFilterMod = matches;
        return matches;
    }

    public Set<Integer> filterKeys(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (cachedFilterKeysVersion == version && q.equals(cachedFilterKeysQuery)) {
            return cachedFilterKeys;
        }
        Set<Integer> matches = new LinkedHashSet<>();

        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            int glfw = key.glfwKey();
            if (getKeyLabel(glfw).toLowerCase(Locale.ROOT).contains(q) || key.label().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(glfw);
                continue;
            }
            for (KeyBindingInfo info : getBindings(glfw)) {
                if (matches(info, q)) { matches.add(glfw); break; }
            }
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            int btn = KeyboardLayoutData.virtualToMouseBtn(key.glfwKey());
            if (getMouseButtonLabel(btn).toLowerCase(Locale.ROOT).contains(q) || key.label().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(key.glfwKey());
                continue;
            }
            for (KeyBindingInfo info : getMouseBindings(btn)) {
                if (matches(info, q)) { matches.add(key.glfwKey()); break; }
            }
        }
        cachedFilterKeysVersion = version;
        cachedFilterKeysQuery = q;
        cachedFilterKeys = matches;
        return matches;
    }

    public ScanStats getStats() {
        return stats;
    }

    private ScanStats computeStats() {
        int free = 0, self = 0, other = 0, combo = 0, bound = 0, conflict = 0;
        int total = KeyboardLayoutData.KEYS.size() + KeyboardLayoutData.MOUSE_KEYS.size();
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.KEYS) {
            switch (getStatus(key.glfwKey())) {
                case FREE -> free++;
                case SELF -> self++;
                case OTHER_SINGLE -> other++;
                case COMBO -> combo++;
                case BOUND -> bound++;
                case CONFLICT -> conflict++;
            }
        }
        for (KeyboardLayoutData.KeyDef key : KeyboardLayoutData.MOUSE_KEYS) {
            switch (getMouseStatus(KeyboardLayoutData.virtualToMouseBtn(key.glfwKey()))) {
                case FREE -> free++;
                case SELF -> self++;
                case OTHER_SINGLE -> other++;
                case COMBO -> combo++;
                case BOUND -> bound++;
                case CONFLICT -> conflict++;
            }
        }
        return new ScanStats(total, free, self, other, combo, bound + self + other + combo, conflict);
    }

    private void rebuildModStats() {
        Map<String, MutableModStats> mutable = new HashMap<>();
        Map<String, LinkedHashSet<Integer>> mutableModKeys = new HashMap<>();
        collectModStats(keyboardBindings, keyboardStatuses, mutable, mutableModKeys, false);
        collectModStats(mouseBindings, mouseStatuses, mutable, mutableModKeys, true);
        modStats.clear();
        for (Map.Entry<String, MutableModStats> entry : mutable.entrySet()) {
            MutableModStats s = entry.getValue();
            modStats.put(entry.getKey(), new ModStats(s.bindings, s.inputs, s.conflicts));
        }
        modKeys.clear();
        for (Map.Entry<String, LinkedHashSet<Integer>> entry : mutableModKeys.entrySet()) {
            modKeys.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
    }

    private static void collectModStats(Map<Integer, List<KeyBindingInfo>> bindings,
                                        Map<Integer, KeyStatus> statuses,
                                        Map<String, MutableModStats> out,
                                        Map<String, LinkedHashSet<Integer>> modKeys,
                                        boolean mouse) {
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : bindings.entrySet()) {
            KeyStatus status = statuses.getOrDefault(entry.getKey(), KeyStatus.FREE);
            Set<String> seenMods = new LinkedHashSet<>();
            for (KeyBindingInfo info : entry.getValue()) {
                MutableModStats stats = out.computeIfAbsent(info.modId(), ignored -> new MutableModStats());
                stats.bindings++;
                seenMods.add(info.modId());
            }
            int virtualKey = mouse ? KeyboardLayoutData.mouseToVirtual(entry.getKey()) : entry.getKey();
            for (String modId : seenMods) {
                MutableModStats stats = out.computeIfAbsent(modId, ignored -> new MutableModStats());
                stats.inputs++;
                if (status == KeyStatus.CONFLICT) stats.conflicts++;
                modKeys.computeIfAbsent(modId, ignored -> new LinkedHashSet<>()).add(virtualKey);
            }
        }
    }

    private static final class MutableModStats {
        int bindings;
        int inputs;
        int conflicts;
    }

    public String getKeyLabel(int glfwKey) {
        return keyLabelCache.computeIfAbsent(glfwKey, key -> {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> "SPACE";
                case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
                case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
                case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
                case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
                case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
                case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
                case GLFW.GLFW_KEY_LEFT_SUPER -> "LWIN";
                case GLFW.GLFW_KEY_RIGHT_SUPER -> "RWIN";
                case GLFW.GLFW_KEY_ESCAPE -> "ESC";
                case GLFW.GLFW_KEY_ENTER -> "ENTER";
                case GLFW.GLFW_KEY_TAB -> "TAB";
                case GLFW.GLFW_KEY_BACKSPACE -> "BKSP";
                case GLFW.GLFW_KEY_DELETE -> "DEL";
                case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
                case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
                case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRT";
                default -> "KEY_" + key;
            };
        });
    }

    public static String getMouseButtonLabel(int mouseButton) {
        return switch (mouseButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB";
            case 3 -> "M4";
            case 4 -> "M5";
            case 5 -> "M6";
            case 6 -> "M7";
            case 7 -> "M8";
            default -> "M" + (mouseButton + 1);
        };
    }


    public String getVirtualKeyLabel(int virtualKey) {
        if (virtualKey == KeyboardLayoutData.WHEEL_UP_VIRTUAL) return "Wheel \u25B2";
        if (virtualKey == KeyboardLayoutData.WHEEL_DOWN_VIRTUAL) return "Wheel \u25BC";
        if (KeyboardLayoutData.isMouse(virtualKey)) {
            return getMouseButtonLabel(KeyboardLayoutData.virtualToMouseBtn(virtualKey));
        }
        return getKeyLabel(virtualKey);
    }

    private static boolean matches(KeyBindingInfo info, String query) {
        return info.searchText().contains(query);
    }


    private static KeyStatus computeStatus(List<KeyBindingInfo> infos) {
        if (infos.isEmpty()) return KeyStatus.FREE;
        boolean comboAware = KeybindViewerConfig.global().comboKeysNonConflicting();
        boolean hasCombo = false;
        boolean hasSelf = false;
        boolean hasOther = false;
        for (KeyBindingInfo info : infos) {
            hasCombo |= isCombination(info) || info.currentCombo();
            if (info.self()) {
                hasSelf = true;
            } else {
                hasOther = true;
            }
        }
        if (infos.size() == 1) {
            if (comboAware && hasCombo) return KeyStatus.COMBO;
            return infos.get(0).self() ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;
        }

        for (int i = 0; i < infos.size(); i++) {
            for (int j = i + 1; j < infos.size(); j++) {
                if (comboAware && !sameActivator(infos.get(i), infos.get(j))) continue;
                ConflictContext ci = infos.get(i).conflictContext();
                ConflictContext cj = infos.get(j).conflictContext();
                if (ci != null && cj != null && ci.conflicts(cj)) return KeyStatus.CONFLICT;
            }
        }
        if (comboAware && hasCombo) return KeyStatus.COMBO;
        if (hasSelf && hasOther) return KeyStatus.CONFLICT;
        return hasSelf ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;
    }

    private static boolean sameActivator(KeyBindingInfo a, KeyBindingInfo b) {
        return a.activatorSignature().equals(b.activatorSignature());
    }

    private static boolean matchesStatus(KeyStatus status, FilterTab tab) {
        return switch (tab) {
            case ALL -> true;
            case FREE -> status == KeyStatus.FREE;
            case SELF -> status == KeyStatus.SELF;
            case OTHER -> status == KeyStatus.OTHER_SINGLE || status == KeyStatus.BOUND;
            case COMBO -> status == KeyStatus.COMBO;
            case CONFLICT -> status == KeyStatus.CONFLICT;
        };
    }

    private static String displayKeyName(InputModifier modifier, String keyName) {
        if (modifier == null || !modifier.isCombination()) return keyName;
        return modifier.displayName() + " + " + keyName;
    }

    private static String categoryKey(KeyMapping mapping) {
        return mapping.getCategory().id().toString();
    }

    private static boolean isCombination(KeyBindingInfo info) {
        return info.modifier() != null && info.modifier().isCombination();
    }


    private String resolveModId(String name, String category) {
        String cacheKey = String.valueOf(name) + '\u001F' + String.valueOf(category);
        return resolvedModIdCache.computeIfAbsent(cacheKey, ignored -> resolveModIdUncached(name, category));
    }

    private static String resolveModIdUncached(String name, String category) {
        
        if (name != null && name.startsWith("key.")) {
            int start = "key.".length();
            int end = name.indexOf('.', start);
            if (end > start) {
                String candidate = name.substring(start, end);
                if (!"minecraft".equals(candidate) && Services.PLATFORM.isModLoaded(candidate)) return candidate;
            }
        }
        
        if (category != null && category.indexOf(':') > 0) {
            String namespace = category.substring(0, category.indexOf(':'));
            if (!"minecraft".equals(namespace) && Services.PLATFORM.isModLoaded(namespace)) return namespace;
        }

        if (category != null && (category.startsWith("key.categories.") || category.startsWith("key.category."))) {
            String suffix = category.substring("key.categories.".length());
            if (category.startsWith("key.category.")) {
                suffix = category.substring("key.category.".length());
            }
            String suffixLower = suffix.toLowerCase(Locale.ROOT);
            if (!VANILLA_CATEGORIES.contains(suffixLower)) {
                if (Services.PLATFORM.isModLoaded(suffix))      return suffix;
                if (Services.PLATFORM.isModLoaded(suffixLower)) return suffixLower;
            }
        }
        
        if (name != null) {
            String found = loadedModIdFromDotted(name, false);
            if (found != null) return found;
        }
        
        if (category != null) {
            String found = loadedModIdFromDotted(category, true);
            if (found != null) return found;
        }
        return "minecraft";
    }

    private String resolveModName(String modId, String categoryKey) {
        String cacheKey = modId + '\u001F' + String.valueOf(categoryKey);
        return resolvedModNameCache.computeIfAbsent(cacheKey, ignored -> resolveModNameUncached(modId, categoryKey));
    }

    private static String resolveModNameUncached(String modId, String categoryKey) {
        if ("minecraft".equals(modId)) return "Minecraft";
        String fromPlatform = Services.PLATFORM.getModName(modId);
        if (fromPlatform != null && !fromPlatform.isBlank()) return fromPlatform;
        if (categoryKey != null) {
            String translated = Component.translatable(categoryKey).getString();
            if (translated != null && !translated.equals(categoryKey) && !translated.isBlank()) return translated;
        }
        return modId;
    }

    private String actionName(String actionKey) {
        return actionNameCache.computeIfAbsent(actionKey, key -> Component.translatable(key).getString());
    }

    private String categoryName(String categoryKey, KeyMapping mapping) {
        return categoryNameCache.computeIfAbsent(categoryKey, ignored -> mapping.getCategory().label().getString());
    }

    private String displayName(InputConstants.Key key) {
        return displayNameCache.computeIfAbsent(key.getName(), ignored -> key.getDisplayName().getString());
    }

    private static String searchText(String actionName, String actionKey, String categoryName, String categoryKey,
                                     String modId, String modName, String currentKeyName) {
        return (actionName + '\n' + actionKey + '\n' + categoryName + '\n' + categoryKey + '\n'
                + modId + '\n' + modName + '\n' + currentKeyName).toLowerCase(Locale.ROOT);
    }

    private static String loadedModIdFromDotted(String value, boolean skipCategories) {
        int start = 0;
        while (start <= value.length()) {
            int end = value.indexOf('.', start);
            if (end < 0) end = value.length();
            if (end > start) {
                String part = value.substring(start, end);
                String lower = part.toLowerCase(Locale.ROOT);
                if (!"key".equals(lower)
                        && !"minecraft".equals(lower)
                        && (!skipCategories || !"categories".equals(lower))
                        && Services.PLATFORM.isModLoaded(lower)) {
                    return lower;
                }
            }
            if (end == value.length()) break;
            start = end + 1;
        }
        return null;
    }
}
