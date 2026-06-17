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
import java.util.HashSet;
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
            boolean conflictIgnored
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

    /** The only namespaces vanilla uses inside a 3-segment key name (e.g. {@code key.hotbar.1}). */
    private static final Set<String> VANILLA_KEY_NAMESPACES = Set.of("hotbar");

    /** Tokens that appear in keybind translation keys but are never a mod id, so they must not be
     *  fuzzily matched to a loaded mod (which would create bogus groups like "gui"). */
    private static final Set<String> GENERIC_TOKENS = Set.of(
            "key", "keys", "keybind", "keybinds", "keybinding", "keybindings", "binding", "bindings",
            "categories", "category", "minecraft", "gui", "hud", "misc", "mod", "mods", "client",
            "common", "main", "input", "inputs", "control", "controls", "setting", "settings",
            "options", "option", "menu", "menus", "screen", "screens", "action", "actions",
            "toggle", "open", "close", "show", "hide");

    /** Lazily-cached lower-cased ids of all loaded mods (fixed for the JVM run). */
    private static volatile Set<String> cachedLoadedModIds;

    private final Map<Integer, List<KeyBindingInfo>> keyboardBindings = new LinkedHashMap<>();
    private final Map<Integer, List<KeyBindingInfo>> mouseBindings = new LinkedHashMap<>();
    private final Map<Integer, KeyStatus> keyboardStatuses = new HashMap<>();
    private final Map<Integer, KeyStatus> mouseStatuses = new HashMap<>();
    private final Map<Integer, String> keyLabelCache = new HashMap<>();
    private final Map<String, String> registeredMods = new LinkedHashMap<>();
    private final Map<String, ModStats> modStats = new HashMap<>();
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

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            sortedRegisteredMods = Collections.emptyMap();
            stats = computeStats();
            version++;
            lastScanTime = System.currentTimeMillis();
            return;
        }

        for (KeyMapping mapping : minecraft.options.keyMappings) {
            String actionKey = mapping.getName();
            String categoryKey = mapping.getCategory();
            String modId = resolveModId(actionKey, categoryKey);
            String modName = resolveModName(modId, categoryKey);
            registeredMods.putIfAbsent(modId, modName);

            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).newvisualkeybing$getKey();
            if (key == InputConstants.UNKNOWN) continue;

            ConflictContext ctx = Services.PLATFORM.getConflictContext(mapping);
            InputModifier modifier = Services.PLATFORM.getKeyModifier(mapping);
            InputModifier defaultModifier = Services.PLATFORM.getDefaultKeyModifier(mapping);
            String baseKeyName = key.getDisplayName().getString();
            String defaultBaseKeyName = mapping.getDefaultKey().getDisplayName().getString();
            KeyBindingInfo info = new KeyBindingInfo(
                    actionKey,
                    Component.translatable(actionKey).getString(),
                    categoryKey,
                    Component.translatable(categoryKey).getString(),
                    modId,
                    modName,
                    Constants.MOD_ID.equals(modId),
                    ctx,
                    modifier,
                    defaultModifier,
                    baseKeyName,
                    displayKeyName(modifier, baseKeyName),
                    displayKeyName(defaultModifier, defaultBaseKeyName),
                    KeybindProfileStore.globalConflictIgnored(actionKey)
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

        sortedRegisteredMods = registeredMods.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
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
        for (KeyBindingInfo info : getVirtualBindings(virtualKey)) {
            if (modId.equals(info.modId())) return true;
        }
        return false;
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
            // getVirtualStatus guards wheels (FREE) before converting to a mouse-button index, matching
            // getVirtualBindings/getVirtualStatus elsewhere. Calling virtualToMouseBtn on a wheel
            // virtual key yields an out-of-range button number, so go through the guarded accessor.
            if (matchesStatus(getVirtualStatus(key.glfwKey()), tab)) {
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
        Set<Integer> matches = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : keyboardBindings.entrySet()) {
            for (KeyBindingInfo info : entry.getValue()) {
                if (modId.equals(info.modId())) { matches.add(entry.getKey()); break; }
            }
        }
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : mouseBindings.entrySet()) {
            for (KeyBindingInfo info : entry.getValue()) {
                if (modId.equals(info.modId())) {
                    matches.add(KeyboardLayoutData.mouseToVirtual(entry.getKey()));
                    break;
                }
            }
        }
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
            int virtual = key.glfwKey();
            // Wheels have no mouse-button index; only their own label is searchable (getVirtualBindings
            // returns empty for them). Real buttons keep the mouse-button label + their bindings.
            boolean wheel = KeyboardLayoutData.isWheel(virtual);
            String btnLabel = wheel ? "" : getMouseButtonLabel(KeyboardLayoutData.virtualToMouseBtn(virtual));
            if (btnLabel.toLowerCase(Locale.ROOT).contains(q) || key.label().toLowerCase(Locale.ROOT).contains(q)) {
                matches.add(virtual);
                continue;
            }
            for (KeyBindingInfo info : getVirtualBindings(virtual)) {
                if (matches(info, q)) { matches.add(virtual); break; }
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
            // getVirtualStatus guards wheels (never converts a wheel virtual key to a mouse button).
            switch (getVirtualStatus(key.glfwKey())) {
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
        collectModStats(keyboardBindings, keyboardStatuses, mutable);
        collectModStats(mouseBindings, mouseStatuses, mutable);
        modStats.clear();
        for (Map.Entry<String, MutableModStats> entry : mutable.entrySet()) {
            MutableModStats s = entry.getValue();
            modStats.put(entry.getKey(), new ModStats(s.bindings, s.inputs, s.conflicts));
        }
    }

    private static void collectModStats(Map<Integer, List<KeyBindingInfo>> bindings,
                                        Map<Integer, KeyStatus> statuses,
                                        Map<String, MutableModStats> out) {
        for (Map.Entry<Integer, List<KeyBindingInfo>> entry : bindings.entrySet()) {
            KeyStatus status = statuses.getOrDefault(entry.getKey(), KeyStatus.FREE);
            Set<String> seenMods = new LinkedHashSet<>();
            for (KeyBindingInfo info : entry.getValue()) {
                MutableModStats stats = out.computeIfAbsent(info.modId(), ignored -> new MutableModStats());
                stats.bindings++;
                seenMods.add(info.modId());
            }
            for (String modId : seenMods) {
                MutableModStats stats = out.computeIfAbsent(modId, ignored -> new MutableModStats());
                stats.inputs++;
                if (status == KeyStatus.CONFLICT) stats.conflicts++;
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
        return info.actionName().toLowerCase(Locale.ROOT).contains(query)
                || info.translationKey().toLowerCase(Locale.ROOT).contains(query)
                || info.categoryName().toLowerCase(Locale.ROOT).contains(query)
                || info.categoryKey().toLowerCase(Locale.ROOT).contains(query)
                || info.modId().toLowerCase(Locale.ROOT).contains(query)
                || info.modName().toLowerCase(Locale.ROOT).contains(query)
                || info.currentKeyName().toLowerCase(Locale.ROOT).contains(query)
                || Pinyin.matches(info.actionName(), query)
                || Pinyin.matches(info.categoryName(), query)
                || Pinyin.matches(info.modName(), query);
    }


    private static KeyStatus computeStatus(List<KeyBindingInfo> infos) {
        if (infos.isEmpty()) return KeyStatus.FREE;
        boolean comboAware = KeybindViewerConfig.global().comboKeysNonConflicting();
        boolean hasCombo = infos.stream().anyMatch(info -> isCombination(info) || hasStoredCombo(info));
        if (infos.size() == 1) {
            if (comboAware && hasCombo) return KeyStatus.COMBO;
            return infos.get(0).self() ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;
        }

        // Bindings manually marked as ignore-in-conflict never contribute to a CONFLICT verdict.
        for (int i = 0; i < infos.size(); i++) {
            if (infos.get(i).conflictIgnored()) continue;
            for (int j = i + 1; j < infos.size(); j++) {
                if (infos.get(j).conflictIgnored()) continue;
                if (comboAware && !sameActivator(infos.get(i), infos.get(j))) continue;
                if (contextsConflict(infos.get(i), infos.get(j))) return KeyStatus.CONFLICT;
            }
        }
        if (comboAware && hasCombo) return KeyStatus.COMBO;
        boolean hasSelf = infos.stream().anyMatch(info -> info.self() && !info.conflictIgnored());
        boolean hasOther = infos.stream().anyMatch(info -> !info.self() && !info.conflictIgnored());
        if (hasSelf && hasOther) return KeyStatus.CONFLICT;
        boolean anySelf = infos.stream().anyMatch(KeyBindingInfo::self);
        return anySelf ? KeyStatus.SELF : KeyStatus.OTHER_SINGLE;
    }

    private static boolean hasStoredCombo(KeyBindingInfo info) {
        return KeybindComboStore.global().hasCurrentCombo(info.translationKey());
    }

    /**
     * Relational conflict test between two same-key bindings. Resolves the live {@link KeyMapping}s
     * and delegates to {@code Services.PLATFORM.contextsConflict} so Forge uses the authoritative
     * native {@code IKeyConflictContext.conflicts} (custom mod contexts keep their real semantics).
     * Falls back to the captured 4-value context approximation if either mapping cannot be resolved.
     */
    private static boolean contextsConflict(KeyBindingInfo a, KeyBindingInfo b) {
        KeyMapping ma = KeybindComboStore.findMapping(a.translationKey());
        KeyMapping mb = KeybindComboStore.findMapping(b.translationKey());
        if (ma != null && mb != null) {
            return Services.PLATFORM.contextsConflict(ma, mb);
        }
        ConflictContext ca = a.conflictContext();
        ConflictContext cb = b.conflictContext();
        return ca != null && cb != null && ca.conflicts(cb);
    }

    private static boolean sameActivator(KeyBindingInfo a, KeyBindingInfo b) {
        KeybindComboStore store = KeybindComboStore.global();
        return store.activatorSignature(a.translationKey(), a.modifier())
                .equals(store.activatorSignature(b.translationKey(), b.modifier()));
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

    private static boolean isCombination(KeyBindingInfo info) {
        return info.modifier() != null && info.modifier().isCombination();
    }


    /** Resolve the owning mod id for a mapping, using the same heuristics as the scan. */
    public static String modIdOf(KeyMapping mapping) {
        if (mapping == null) return "minecraft";
        return resolveModId(mapping.getName(), mapping.getCategory());
    }

    private static String resolveModId(String name, String category) {
        Set<String> loaded = loadedModIds();

        // 1) Any token that IS a loaded mod id (exact). Safe even for vanilla keys and the most
        //    reliable signal — classify strictly by real modid whenever the key names it.
        for (String t : tokenize(name)) if (loaded.contains(t)) return t;
        for (String t : tokenize(category)) if (loaded.contains(t)) return t;

        // 2) Decide whether this even looks modded. Vanilla keys are key.<flat> / key.hotbar.N with a
        //    vanilla category and must never be fuzzily matched to a mod (avoids false positives).
        String ns = deriveNamespace(name);
        String catNs = customCategoryNamespace(category);
        boolean modded = ns != null || catNs != null || (name != null && !name.startsWith("key."));
        if (!modded) return "minecraft";

        // 3) Map a name/category token onto a real loaded mod id by prefix/substring, so e.g. an
        //    "xaero"/"worldmap" token resolves to the actual "xaerominimap"/"xaeroworldmap" id.
        String matched = matchLoadedModId(loaded, tokenize(name), tokenize(category));
        if (matched != null) return matched;

        // 4) No loaded id matched: fall back to a non-generic namespace so the binding still groups
        //    under the mod (never a generic token like "gui").
        if (ns != null && !isGenericToken(ns)) return ns;
        if (catNs != null && !isGenericToken(catNs)) return catNs;
        return "minecraft";
    }

    private static Set<String> loadedModIds() {
        Set<String> cached = cachedLoadedModIds;
        if (cached == null) {
            cached = new HashSet<>();
            for (String id : Services.PLATFORM.getLoadedModIds()) {
                if (id != null && !id.isBlank()) cached.add(id.toLowerCase(Locale.ROOT));
            }
            cachedLoadedModIds = cached;
        }
        return cached;
    }

    private static List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        if (s == null) return tokens;
        for (String part : s.toLowerCase(Locale.ROOT).split("[._]")) {
            if (!part.isEmpty()) tokens.add(part);
        }
        return tokens;
    }

    private static boolean isGenericToken(String t) {
        return t == null || t.length() < 3 || GENERIC_TOKENS.contains(t);
    }

    /** Best loaded mod id matching any non-generic token by prefix/substring; longest token wins. */
    private static String matchLoadedModId(Set<String> loaded, List<String> nameTokens, List<String> catTokens) {
        if (loaded.isEmpty()) return null;
        List<String> tokens = new ArrayList<>(nameTokens);
        tokens.addAll(catTokens);
        tokens.sort((a, b) -> b.length() - a.length()); // most specific tokens first
        for (String t : tokens) {
            if (t.length() < 4 || isGenericToken(t)) continue;
            String best = null;
            for (String mid : loaded) {
                if ("minecraft".equals(mid)) continue;
                if (mid.equals(t) || mid.startsWith(t) || t.startsWith(mid) || mid.contains(t)) {
                    if (best == null || mid.length() < best.length()) best = mid;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** Best-effort mod namespace from a key name known not to be a confirmed-loaded mod, or null. */
    private static String deriveNamespace(String name) {
        if (name == null || name.isEmpty()) return null;
        String[] parts = name.split("\\.");
        if (!name.startsWith("key.")) {
            // "<modid>.keybind.<action>" style (e.g. Iris): the first segment is the namespace.
            String t = parts[0].toLowerCase(Locale.ROOT);
            return t.isEmpty() || "minecraft".equals(t) ? null : t;
        }
        if (parts.length >= 3) {
            // "key.<modid>.<action>".
            String t = parts[1].toLowerCase(Locale.ROOT);
            return t.isEmpty() || "minecraft".equals(t) || VANILLA_KEY_NAMESPACES.contains(t) ? null : t;
        }
        if (parts.length == 2) {
            // "key.<word>": vanilla flat keys never contain an underscore, so only a modid-prefixed
            // word (e.g. xaero_open_settings) is treated as modded — grouped by that prefix.
            String w = parts[1].toLowerCase(Locale.ROOT);
            int underscore = w.indexOf('_');
            if (underscore > 1) return w.substring(0, underscore);
        }
        return null;
    }

    /** The leading token of a non-vanilla {@code key.categories.*} group, or null for vanilla. */
    private static String customCategoryNamespace(String category) {
        if (category == null || !category.startsWith("key.categories.")) return null;
        String suffix = category.substring("key.categories.".length()).toLowerCase(Locale.ROOT);
        if (suffix.isEmpty() || VANILLA_CATEGORIES.contains(suffix)) return null;
        int dot = suffix.indexOf('.');
        return dot > 0 ? suffix.substring(0, dot) : suffix;
    }

    private static String resolveModName(String modId, String categoryKey) {
        if ("minecraft".equals(modId)) return "Minecraft";
        String fromPlatform = Services.PLATFORM.getModName(modId);
        if (fromPlatform != null && !fromPlatform.isBlank()) return fromPlatform;
        // Only borrow the category's display name when it is a mod's own custom category; reusing a
        // vanilla category label (e.g. "Gameplay") would mislabel the mod.
        if (customCategoryNamespace(categoryKey) != null) {
            String translated = Component.translatable(categoryKey).getString();
            if (translated != null && !translated.equals(categoryKey) && !translated.isBlank()) return translated;
        }
        return modId;
    }
}
