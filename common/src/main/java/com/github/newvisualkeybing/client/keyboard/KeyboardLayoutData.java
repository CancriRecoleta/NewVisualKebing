package com.github.newvisualkeybing.client.keyboard;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class KeyboardLayoutData {

    public static final int BASE_UNIT = 24;
    public static final int BASE_GAP = 2;
    public static final int MOUSE_BTN_BASE = -100;
    
    public static final int WHEEL_UP_VIRTUAL = MOUSE_BTN_BASE - 8;
    public static final int WHEEL_DOWN_VIRTUAL = MOUSE_BTN_BASE - 9;

    
    public enum Style {
        ANSI_104("ANSI 104",       22.50f, 6.10f),
        KEYS_98("98 Keys",         21.50f, 6.10f),
        TKL_87("TKL 87",           18.25f, 6.10f),
        COMPACT_60("60%",          15.00f, 5.10f),
        MAC_FULL("Mac Full",       21.50f, 6.10f),
        MAC_COMPACT("Mac Compact", 15.25f, 6.10f);

        private final String label;
        private final float widthU;
        private final float heightU;

        Style(String label, float widthU, float heightU) {
            this.label = label;
            this.widthU = widthU;
            this.heightU = heightU;
        }

        public String label()   { return label; }
        public float widthU()   { return widthU; }
        public float heightU()  { return heightU; }

        public Style next() {
            Style[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private KeyboardLayoutData() {
    }

    public record KeyDef(int glfwKey, String label, String subLabel, float gridX, float gridY, float width, float height) {
        public int screenX(int baseX, float scale) {
            return baseX + Math.round(gridX * (scale + BASE_GAP));
        }

        public int screenY(int baseY, float scale) {
            return baseY + Math.round(gridY * (scale + BASE_GAP));
        }

        public int screenW(float scale) {
            return Math.round(width * scale + (width - 1) * BASE_GAP);
        }

        public int screenH(float scale) {
            return Math.round(height * scale + (height - 1) * BASE_GAP);
        }
    }

    public static int mouseToVirtual(int mouseButton) {
        return MOUSE_BTN_BASE - mouseButton;
    }

    public static int virtualToMouseBtn(int virtualKey) {
        return MOUSE_BTN_BASE - virtualKey;
    }

    public static boolean isMouse(int virtualKey) {
        return virtualKey <= MOUSE_BTN_BASE && virtualKey >= MOUSE_BTN_BASE - 9;
    }

    public static boolean isWheel(int virtualKey) {
        return virtualKey == WHEEL_UP_VIRTUAL || virtualKey == WHEEL_DOWN_VIRTUAL;
    }

    public static boolean isRealMouseButton(int virtualKey) {
        return isMouse(virtualKey) && !isWheel(virtualKey);
    }

    
    public static int totalWidthPx(float scale) {
        return totalWidthPx(Style.ANSI_104, scale);
    }

    public static int totalHeightPx(float scale) {
        return totalHeightPx(Style.ANSI_104, scale);
    }

    public static int totalWidthPx(Style style, float scale) {
        return Math.round(style.widthU() * scale + (style.widthU() - 1) * BASE_GAP);
    }

    public static int totalHeightPx(Style style, float scale) {
        return Math.round(style.heightU() * scale + (style.heightU() - 1) * BASE_GAP);
    }


    
    public enum KeyZone {
        ALPHA,      
        MODIFIER,   
        FUNCTION,   
        EDIT,       
        ARROW,      
        NUMPAD      
    }

    public static KeyZone zoneOf(int glfwKey) {
        if (glfwKey == GLFW.GLFW_KEY_ESCAPE) return KeyZone.FUNCTION;
        if (glfwKey >= GLFW.GLFW_KEY_F1 && glfwKey <= GLFW.GLFW_KEY_F25) return KeyZone.FUNCTION;
        if (glfwKey == GLFW.GLFW_KEY_PRINT_SCREEN
                || glfwKey == GLFW.GLFW_KEY_SCROLL_LOCK
                || glfwKey == GLFW.GLFW_KEY_PAUSE) return KeyZone.FUNCTION;
        if (glfwKey == GLFW.GLFW_KEY_INSERT || glfwKey == GLFW.GLFW_KEY_HOME
                || glfwKey == GLFW.GLFW_KEY_PAGE_UP || glfwKey == GLFW.GLFW_KEY_DELETE
                || glfwKey == GLFW.GLFW_KEY_END || glfwKey == GLFW.GLFW_KEY_PAGE_DOWN) return KeyZone.EDIT;
        if (glfwKey == GLFW.GLFW_KEY_UP || glfwKey == GLFW.GLFW_KEY_DOWN
                || glfwKey == GLFW.GLFW_KEY_LEFT || glfwKey == GLFW.GLFW_KEY_RIGHT) return KeyZone.ARROW;
        if (glfwKey == GLFW.GLFW_KEY_NUM_LOCK
                || (glfwKey >= GLFW.GLFW_KEY_KP_0 && glfwKey <= GLFW.GLFW_KEY_KP_EQUAL)) return KeyZone.NUMPAD;
        if (glfwKey == GLFW.GLFW_KEY_LEFT_SHIFT || glfwKey == GLFW.GLFW_KEY_RIGHT_SHIFT
                || glfwKey == GLFW.GLFW_KEY_LEFT_CONTROL || glfwKey == GLFW.GLFW_KEY_RIGHT_CONTROL
                || glfwKey == GLFW.GLFW_KEY_LEFT_ALT || glfwKey == GLFW.GLFW_KEY_RIGHT_ALT
                || glfwKey == GLFW.GLFW_KEY_LEFT_SUPER || glfwKey == GLFW.GLFW_KEY_RIGHT_SUPER
                || glfwKey == GLFW.GLFW_KEY_TAB || glfwKey == GLFW.GLFW_KEY_CAPS_LOCK
                || glfwKey == GLFW.GLFW_KEY_ENTER || glfwKey == GLFW.GLFW_KEY_BACKSPACE
                || glfwKey == GLFW.GLFW_KEY_SPACE || glfwKey == GLFW.GLFW_KEY_MENU) return KeyZone.MODIFIER;
        return KeyZone.ALPHA;
    }

    
    public static final float TOTAL_WIDTH_U = Style.ANSI_104.widthU();
    public static final float TOTAL_HEIGHT_U = Style.ANSI_104.heightU();

    public static final List<KeyDef> MOUSE_KEYS;
    
    public static final List<KeyDef> KEYS;

    private static final Map<Style, List<KeyDef>> LAYOUTS = new EnumMap<>(Style.class);

    public static List<KeyDef> getKeys(Style style) {
        return LAYOUTS.get(style);
    }

    static {
        List<KeyDef> mouse = new ArrayList<>();
        mouse.add(new KeyDef(MOUSE_BTN_BASE, "LMB", null, 0, 0, 2, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 2, "MMB", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 1, "RMB", null, 0, 0, 2, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 3, "M4", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 4, "M5", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 5, "M6", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 6, "M7", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(MOUSE_BTN_BASE - 7, "M8", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(WHEEL_UP_VIRTUAL, "\u25B2", null, 0, 0, 1, 1));
        mouse.add(new KeyDef(WHEEL_DOWN_VIRTUAL, "\u25BC", null, 0, 0, 1, 1));
        MOUSE_KEYS = Collections.unmodifiableList(mouse);

        LAYOUTS.put(Style.ANSI_104,   Collections.unmodifiableList(buildAnsi104()));
        LAYOUTS.put(Style.KEYS_98,    Collections.unmodifiableList(build98()));
        LAYOUTS.put(Style.TKL_87,     Collections.unmodifiableList(buildTkl87()));
        LAYOUTS.put(Style.COMPACT_60, Collections.unmodifiableList(buildCompact60()));
        LAYOUTS.put(Style.MAC_FULL,    Collections.unmodifiableList(buildMacFull()));
        LAYOUTS.put(Style.MAC_COMPACT, Collections.unmodifiableList(buildMacCompact()));
        KEYS = LAYOUTS.get(Style.ANSI_104);
    }

    private static KeyDef kd(int glfwKey, String label, String subLabel, float gridX, float gridY, float width, float height) {
        return new KeyDef(glfwKey, label, subLabel, gridX, gridY, width, height);
    }


    private static List<KeyDef> buildAnsi104() {
        List<KeyDef> keys = new ArrayList<>();

        keys.add(kd(GLFW.GLFW_KEY_ESCAPE, "Esc", null, 0.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F1, "F1", null, 1.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F2, "F2", null, 2.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F3, "F3", null, 3.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F4, "F4", null, 4.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F5, "F5", null, 5.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F6, "F6", null, 6.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F7, "F7", null, 7.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F8, "F8", null, 8.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F9, "F9", null, 10.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F10, "F10", null, 11.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F11, "F11", null, 12.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F12, "F12", null, 13.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PRINT_SCREEN, "Prt", null, 15.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SCROLL_LOCK, "Scr", null, 16.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAUSE, "Pau", null, 17.25f, 0.00f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_GRAVE_ACCENT, "`", "~", 0.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_1, "1", "!", 1.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_2, "2", "@", 2.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_3, "3", "#", 3.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_4, "4", "$", 4.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_5, "5", "%", 5.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_6, "6", "^", 6.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_7, "7", "&", 7.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_8, "8", "*", 8.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_9, "9", "(", 9.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_0, "0", ")", 10.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MINUS, "-", "_", 11.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_EQUAL, "=", "+", 12.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSPACE, "Bksp", null, 13.00f, 1.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_INSERT, "Ins", null, 15.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_HOME, "Home", null, 16.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_UP, "PgUp", null, 17.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_NUM_LOCK, "Num", null, 18.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DIVIDE, "/", null, 19.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_MULTIPLY, "*", null, 20.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_SUBTRACT, "-", null, 21.50f, 1.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_TAB, "Tab", null, 0.00f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Q, "Q", null, 1.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_W, "W", null, 2.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_E, "E", null, 3.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_R, "R", null, 4.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_T, "T", null, 5.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Y, "Y", null, 6.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_U, "U", null, 7.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_I, "I", null, 8.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_O, "O", null, 9.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_P, "P", null, 10.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_BRACKET, "[", "{", 11.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_BRACKET, "]", "}", 12.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSLASH, "\\", "|", 13.50f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DELETE, "Del", null, 15.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_END, "End", null, 16.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_DOWN, "PgDn", null, 17.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_7, "7", null, 18.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_8, "8", null, 19.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_9, "9", null, 20.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ADD, "+", null, 21.50f, 2.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_CAPS_LOCK, "Caps", null, 0.00f, 3.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_A, "A", null, 1.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_S, "S", null, 2.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_D, "D", null, 3.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F, "F", null, 4.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_G, "G", null, 5.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_H, "H", null, 6.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_J, "J", null, 7.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_K, "K", null, 8.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_L, "L", null, 9.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SEMICOLON, ";", ":", 10.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_APOSTROPHE, "'", "\"", 11.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_ENTER, "Enter", null, 12.75f, 3.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_4, "4", null, 18.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_5, "5", null, 19.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_6, "6", null, 20.50f, 3.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_SHIFT, "Shift", null, 0.00f, 4.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Z, "Z", null, 2.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_X, "X", null, 3.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_C, "C", null, 4.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_V, "V", null, 5.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_B, "B", null, 6.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_N, "N", null, 7.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_M, "M", null, 8.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_COMMA, ",", "<", 9.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PERIOD, ".", ">", 10.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SLASH, "/", "?", 11.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift", null, 12.25f, 4.10f, 2.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_UP, "\u2191", null, 16.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_1, "1", null, 18.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_2, "2", null, 19.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_3, "3", null, 20.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ENTER, "Ent", null, 21.50f, 4.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl", null, 0.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_SUPER, "Win", null, 1.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_ALT, "Alt", null, 2.50f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SPACE, "Space", null, 3.75f, 5.10f, 6.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_ALT, "Alt", null, 10.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SUPER, "Win", null, 11.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MENU, "Menu", null, 12.50f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", null, 13.50f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT, "\u2190", null, 15.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DOWN, "\u2193", null, 16.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT, "\u2192", null, 17.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_0, "0", null, 18.50f, 5.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DECIMAL, ".", null, 20.50f, 5.10f, 1.00f, 1.0f));
        return keys;
    }


    private static List<KeyDef> buildTkl87() {
        List<KeyDef> keys = new ArrayList<>();
        for (KeyDef k : buildAnsi104()) {
            if (isKeypadKey(k.glfwKey())) continue;
            keys.add(k);
        }
        return keys;
    }

    private static boolean isKeypadKey(int g) {
        if (g == GLFW.GLFW_KEY_NUM_LOCK) return true;
        return g >= GLFW.GLFW_KEY_KP_0 && g <= GLFW.GLFW_KEY_KP_EQUAL;
    }

    private static List<KeyDef> buildCompact60() {
        List<KeyDef> keys = new ArrayList<>();
        for (KeyDef k : buildAnsi104()) {
            int g = k.glfwKey();
            if (k.gridY() < 0.5f) continue;
            if (isKeypadKey(g)) continue;
            if (g == GLFW.GLFW_KEY_INSERT || g == GLFW.GLFW_KEY_HOME
                    || g == GLFW.GLFW_KEY_PAGE_UP
                    || g == GLFW.GLFW_KEY_DELETE || g == GLFW.GLFW_KEY_END
                    || g == GLFW.GLFW_KEY_PAGE_DOWN
                    || g == GLFW.GLFW_KEY_PRINT_SCREEN || g == GLFW.GLFW_KEY_SCROLL_LOCK
                    || g == GLFW.GLFW_KEY_PAUSE) continue;
            if (g == GLFW.GLFW_KEY_UP || g == GLFW.GLFW_KEY_DOWN
                    || g == GLFW.GLFW_KEY_LEFT || g == GLFW.GLFW_KEY_RIGHT) continue;
            keys.add(new KeyDef(k.glfwKey(), k.label(), k.subLabel(),
                    k.gridX(), k.gridY() - 1.10f, k.width(), k.height()));
        }
        return keys;
    }

    private static List<KeyDef> build98() {
        List<KeyDef> keys = new ArrayList<>();

        keys.add(kd(GLFW.GLFW_KEY_ESCAPE,        "Esc",  null,  0.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F1,            "F1",   null,  1.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F2,            "F2",   null,  2.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F3,            "F3",   null,  3.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F4,            "F4",   null,  4.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F5,            "F5",   null,  5.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F6,            "F6",   null,  6.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F7,            "F7",   null,  7.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F8,            "F8",   null,  8.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F9,            "F9",   null, 10.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F10,           "F10",  null, 11.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F11,           "F11",  null, 12.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F12,           "F12",  null, 13.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PRINT_SCREEN,  "Prt",  null, 15.25f, 0.00f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_GRAVE_ACCENT,  "`",    "~",   0.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_1,             "1",    "!",   1.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_2,             "2",    "@",   2.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_3,             "3",    "#",   3.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_4,             "4",    "$",   4.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_5,             "5",    "%",   5.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_6,             "6",    "^",   6.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_7,             "7",    "&",   7.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_8,             "8",    "*",   8.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_9,             "9",    "(",   9.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_0,             "0",    ")",  10.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MINUS,         "-",    "_",  11.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_EQUAL,         "=",    "+",  12.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSPACE,     "Bksp", null, 13.00f, 1.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_HOME,          "Home", null, 15.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_UP,       "PgUp", null, 16.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_NUM_LOCK,      "Num",  null, 17.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DIVIDE,     "/",    null, 18.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_MULTIPLY,   "*",    null, 19.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_SUBTRACT,   "-",    null, 20.50f, 1.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_TAB,           "Tab",  null,  0.00f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Q,             "Q",    null,  1.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_W,             "W",    null,  2.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_E,             "E",    null,  3.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_R,             "R",    null,  4.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_T,             "T",    null,  5.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Y,             "Y",    null,  6.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_U,             "U",    null,  7.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_I,             "I",    null,  8.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_O,             "O",    null,  9.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_P,             "P",    null, 10.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_BRACKET,  "[",    "{",  11.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_BRACKET, "]",    "}",  12.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSLASH,     "\\",   "|",  13.50f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DELETE,        "Del",  null, 15.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_DOWN,     "PgDn", null, 16.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_7,          "7",    null, 17.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_8,          "8",    null, 18.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_9,          "9",    null, 19.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ADD,        "+",    null, 20.50f, 2.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_CAPS_LOCK,     "Caps", null,  0.00f, 3.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_A,             "A",    null,  1.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_S,             "S",    null,  2.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_D,             "D",    null,  3.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F,             "F",    null,  4.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_G,             "G",    null,  5.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_H,             "H",    null,  6.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_J,             "J",    null,  7.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_K,             "K",    null,  8.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_L,             "L",    null,  9.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SEMICOLON,     ";",    ":",  10.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_APOSTROPHE,    "'",    "\"", 11.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_ENTER,         "Enter",null, 12.75f, 3.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_4,          "4",    null, 17.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_5,          "5",    null, 18.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_6,          "6",    null, 19.50f, 3.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_SHIFT,    "Shift",null,  0.00f, 4.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Z,             "Z",    null,  2.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_X,             "X",    null,  3.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_C,             "C",    null,  4.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_V,             "V",    null,  5.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_B,             "B",    null,  6.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_N,             "N",    null,  7.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_M,             "M",    null,  8.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_COMMA,         ",",    "<",   9.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PERIOD,        ".",    ">",  10.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SLASH,         "/",    "?",  11.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SHIFT,   "Shift",null, 12.25f, 4.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_UP,            "\u2191",    null, 14.00f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_1,          "1",    null, 17.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_2,          "2",    null, 18.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_3,          "3",    null, 19.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_ENTER,      "Ent",  null, 20.50f, 4.10f, 1.00f, 2.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_CONTROL,  "Ctrl", null,  0.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_SUPER,    "Win",  null,  1.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_ALT,      "Alt",  null,  2.50f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SPACE,         "Space",null,  3.75f, 5.10f, 6.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_ALT,     "Alt",  null, 10.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", null, 11.25f, 5.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT,          "\u2190",    null, 13.00f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DOWN,          "\u2193",    null, 14.00f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT,         "\u2192",    null, 15.00f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_0,          "0",    null, 17.50f, 5.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DECIMAL,    ".",    null, 19.50f, 5.10f, 1.00f, 1.0f));

        return keys;
    }

    private static List<KeyDef> buildMacFull() {
        List<KeyDef> keys = new ArrayList<>();

        keys.add(kd(GLFW.GLFW_KEY_ESCAPE,        "esc",  null,  0.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F1,            "F1",   null,  1.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F2,            "F2",   null,  2.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F3,            "F3",   null,  3.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F4,            "F4",   null,  4.50f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F5,            "F5",   null,  5.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F6,            "F6",   null,  6.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F7,            "F7",   null,  7.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F8,            "F8",   null,  8.75f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F9,            "F9",   null, 10.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F10,           "F10",  null, 11.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F11,           "F11",  null, 12.00f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F12,           "F12",  null, 13.00f, 0.00f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_GRAVE_ACCENT,  "`",    "~",   0.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_1,             "1",    "!",   1.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_2,             "2",    "@",   2.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_3,             "3",    "#",   3.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_4,             "4",    "$",   4.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_5,             "5",    "%",   5.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_6,             "6",    "^",   6.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_7,             "7",    "&",   7.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_8,             "8",    "*",   8.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_9,             "9",    "(",   9.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_0,             "0",    ")",  10.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MINUS,         "-",    "_",  11.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_EQUAL,         "=",    "+",  12.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSPACE,     "delete", null, 13.00f, 1.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_HOME,          "Home", null, 15.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_UP,       "PgUp", null, 16.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_NUM_LOCK,      "Clr",  null, 17.25f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_EQUAL,      "=",    null, 18.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DIVIDE,     "/",    null, 19.50f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_MULTIPLY,   "*",    null, 20.50f, 1.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_TAB,           "tab",  null,  0.00f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Q,             "Q",    null,  1.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_W,             "W",    null,  2.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_E,             "E",    null,  3.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_R,             "R",    null,  4.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_T,             "T",    null,  5.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Y,             "Y",    null,  6.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_U,             "U",    null,  7.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_I,             "I",    null,  8.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_O,             "O",    null,  9.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_P,             "P",    null, 10.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_BRACKET,  "[",    "{",  11.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_BRACKET, "]",    "}",  12.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSLASH,     "\\",   "|",  13.50f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DELETE,        "\u2326", null, 15.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_END,           "End",  null, 16.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PAGE_DOWN,     "PgDn", null, 17.25f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_7,          "7",    null, 18.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_8,          "8",    null, 19.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_9,          "9",    null, 20.50f, 2.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_CAPS_LOCK,     "caps lock", null, 0.00f, 3.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_A,             "A",    null,  1.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_S,             "S",    null,  2.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_D,             "D",    null,  3.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F,             "F",    null,  4.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_G,             "G",    null,  5.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_H,             "H",    null,  6.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_J,             "J",    null,  7.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_K,             "K",    null,  8.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_L,             "L",    null,  9.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SEMICOLON,     ";",    ":",  10.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_APOSTROPHE,    "'",    "\"", 11.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_ENTER,         "return", null, 12.75f, 3.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_4,          "4",    null, 18.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_5,          "5",    null, 19.50f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_6,          "6",    null, 20.50f, 3.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_SHIFT,    "\u21E7 shift", null, 0.00f, 4.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Z,             "Z",    null,  2.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_X,             "X",    null,  3.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_C,             "C",    null,  4.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_V,             "V",    null,  5.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_B,             "B",    null,  6.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_N,             "N",    null,  7.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_M,             "M",    null,  8.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_COMMA,         ",",    "<",   9.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PERIOD,        ".",    ">",  10.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SLASH,         "/",    "?",  11.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SHIFT,   "\u21E7 shift", null, 12.25f, 4.10f, 2.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_UP,            "\u2191", null, 16.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_1,          "1",    null, 18.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_2,          "2",    null, 19.50f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_3,          "3",    null, 20.50f, 4.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_CONTROL,  "\u2303 ctrl",   null,  0.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_ALT,      "\u2325 option", null,  1.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_SUPER,    "\u2318 cmd",    null,  2.50f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SPACE,         "",         null,  4.00f, 5.10f, 6.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SUPER,   "\u2318 cmd",    null, 10.00f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_ALT,     "\u2325 option", null, 11.50f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_CONTROL, "\u2303 ctrl",   null, 13.00f, 5.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT,          "\u2190",        null, 15.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DOWN,          "\u2193",        null, 16.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT,         "\u2192",        null, 17.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_0,          "0",        null, 18.50f, 5.10f, 2.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_KP_DECIMAL,    ".",        null, 20.50f, 5.10f, 1.00f, 1.0f));

        return keys;
    }

    private static List<KeyDef> buildMacCompact() {
        List<KeyDef> keys = new ArrayList<>();

        keys.add(kd(GLFW.GLFW_KEY_ESCAPE, "esc",  null,  0.00f, 0.00f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F1,     "F1",   null,  1.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F2,     "F2",   null,  2.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F3,     "F3",   null,  3.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F4,     "F4",   null,  4.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F5,     "F5",   null,  5.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F6,     "F6",   null,  6.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F7,     "F7",   null,  7.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F8,     "F8",   null,  8.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F9,     "F9",   null,  9.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F10,    "F10",  null, 10.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F11,    "F11",  null, 11.25f, 0.00f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F12,    "F12",  null, 12.25f, 0.00f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_GRAVE_ACCENT,  "`",    "~",   0.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_1,             "1",    "!",   1.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_2,             "2",    "@",   2.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_3,             "3",    "#",   3.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_4,             "4",    "$",   4.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_5,             "5",    "%",   5.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_6,             "6",    "^",   6.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_7,             "7",    "&",   7.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_8,             "8",    "*",   8.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_9,             "9",    "(",   9.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_0,             "0",    ")",  10.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_MINUS,         "-",    "_",  11.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_EQUAL,         "=",    "+",  12.00f, 1.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSPACE,     "delete", null, 13.00f, 1.10f, 2.25f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_TAB,           "tab",  null,  0.00f, 2.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Q,             "Q",    null,  1.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_W,             "W",    null,  2.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_E,             "E",    null,  3.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_R,             "R",    null,  4.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_T,             "T",    null,  5.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Y,             "Y",    null,  6.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_U,             "U",    null,  7.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_I,             "I",    null,  8.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_O,             "O",    null,  9.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_P,             "P",    null, 10.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_BRACKET,  "[",    "{",  11.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_BRACKET, "]",    "}",  12.50f, 2.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_BACKSLASH,     "\\",   "|",  13.50f, 2.10f, 1.75f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_CAPS_LOCK,     "caps lock", null, 0.00f, 3.10f, 1.75f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_A,             "A",    null,  1.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_S,             "S",    null,  2.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_D,             "D",    null,  3.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_F,             "F",    null,  4.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_G,             "G",    null,  5.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_H,             "H",    null,  6.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_J,             "J",    null,  7.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_K,             "K",    null,  8.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_L,             "L",    null,  9.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SEMICOLON,     ";",    ":",  10.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_APOSTROPHE,    "'",    "\"", 11.75f, 3.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_ENTER,         "return", null, 12.75f, 3.10f, 2.50f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_SHIFT,    "\u21E7 shift", null, 0.00f, 4.10f, 2.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_Z,             "Z",    null,  2.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_X,             "X",    null,  3.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_C,             "C",    null,  4.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_V,             "V",    null,  5.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_B,             "B",    null,  6.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_N,             "N",    null,  7.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_M,             "M",    null,  8.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_COMMA,         ",",    "<",   9.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_PERIOD,        ".",    ">",  10.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SLASH,         "/",    "?",  11.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SHIFT,   "\u21E7 shift", null, 12.25f, 4.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_UP,            "\u2191",       null, 13.25f, 4.10f, 1.00f, 1.0f));

        keys.add(kd(GLFW.GLFW_KEY_LEFT_CONTROL,  "\u2303 ctrl",   null, 0.00f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_ALT,      "\u2325 option", null, 1.25f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT_SUPER,    "\u2318 cmd",    null, 2.50f, 5.10f, 1.25f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_SPACE,         "",         null, 3.75f, 5.10f, 5.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_SUPER,   "\u2318 cmd",    null, 9.25f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT_ALT,     "\u2325 option", null, 10.75f, 5.10f, 1.50f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_LEFT,          "\u2190",        null, 12.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_DOWN,          "\u2193",        null, 13.25f, 5.10f, 1.00f, 1.0f));
        keys.add(kd(GLFW.GLFW_KEY_RIGHT,         "\u2192",        null, 14.25f, 5.10f, 1.00f, 1.0f));

        return keys;
    }
}
