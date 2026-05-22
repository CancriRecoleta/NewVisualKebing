package com.github.newvisualkeybing.client.keyboard;

import com.github.newvisualkeybing.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class KeybindViewerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile KeybindViewerConfig INSTANCE;

    public static KeybindViewerConfig global() {
        KeybindViewerConfig local = INSTANCE;
        if (local == null) {
            synchronized (KeybindViewerConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = new KeybindViewerConfig();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    private final Path file;
    private Data data = new Data();

    private KeybindViewerConfig() {
        Path root = Minecraft.getInstance().options.getFile().toPath().toAbsolutePath().getParent();
        if (root == null) root = Path.of(".");
        this.file = root.resolve("config").resolve(Constants.MOD_ID).resolve("keybind_viewer.json");
        load();
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            data = new Data();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Data loaded = GSON.fromJson(reader, Data.class);
            data = loaded == null ? new Data() : loaded;
        } catch (IOException | JsonSyntaxException ignored) {
            data = new Data();
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public boolean hideNonSelectedMod() {
        return data.hideNonSelectedMod;
    }

    public boolean toggleHideNonSelectedMod() {
        data.hideNonSelectedMod = !data.hideNonSelectedMod;
        save();
        return data.hideNonSelectedMod;
    }

    public boolean comboKeysNonConflicting() {
        return data.comboKeysNonConflicting == null || data.comboKeysNonConflicting;
    }

    public boolean toggleComboKeysNonConflicting() {
        data.comboKeysNonConflicting = !comboKeysNonConflicting();
        save();
        return data.comboKeysNonConflicting;
    }

    public KeyboardLayoutData.Style defaultLayoutStyle() {
        if (data.defaultLayout == null || data.defaultLayout.isBlank()) {
            return KeyboardLayoutData.Style.ANSI_104;
        }
        try {
            return KeyboardLayoutData.Style.valueOf(data.defaultLayout);
        } catch (IllegalArgumentException ignored) {
            return KeyboardLayoutData.Style.ANSI_104;
        }
    }

    public void setDefaultLayoutStyle(KeyboardLayoutData.Style style) {
        data.defaultLayout = style == null ? null : style.name();
        save();
    }

    private static final class Data {
        boolean hideNonSelectedMod;
        Boolean comboKeysNonConflicting = Boolean.TRUE;
        String defaultLayout;
    }
}
