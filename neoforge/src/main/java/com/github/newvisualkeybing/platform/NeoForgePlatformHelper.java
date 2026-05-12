package com.github.newvisualkeybing.platform;

import com.github.newvisualkeybing.platform.services.IPlatformHelper;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public String getModName(String modId) {
        if (modId == null) return null;
        return ModList.get().getModContainerById(modId)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(null);
    }

    @Override
    public ConflictContext getConflictContext(KeyMapping mapping) {
        try {
            IKeyConflictContext ctx = mapping.getKeyConflictContext();
            if (ctx == KeyConflictContext.UNIVERSAL) return ConflictContext.UNIVERSAL;
            if (ctx == KeyConflictContext.IN_GAME) return ConflictContext.IN_GAME;
            if (ctx == KeyConflictContext.GUI) return ConflictContext.GUI;
            return ConflictContext.UNKNOWN;
        } catch (Throwable ignored) {
            return ConflictContext.UNIVERSAL;
        }
    }

    @Override
    public InputModifier getKeyModifier(KeyMapping mapping) {
        try {
            return fromNeoForgeModifier(mapping.getKeyModifier());
        } catch (Throwable ignored) {
            return InputModifier.NONE;
        }
    }

    @Override
    public InputModifier getDefaultKeyModifier(KeyMapping mapping) {
        try {
            return fromNeoForgeModifier(mapping.getDefaultKeyModifier());
        } catch (Throwable ignored) {
            return InputModifier.NONE;
        }
    }

    private static InputModifier fromNeoForgeModifier(KeyModifier modifier) {
        if (modifier == KeyModifier.CONTROL) return InputModifier.CONTROL;
        if (modifier == KeyModifier.SHIFT) return InputModifier.SHIFT;
        if (modifier == KeyModifier.ALT) return InputModifier.ALT;
        if (modifier == KeyModifier.NONE) return InputModifier.NONE;
        return InputModifier.UNKNOWN;
    }
}
