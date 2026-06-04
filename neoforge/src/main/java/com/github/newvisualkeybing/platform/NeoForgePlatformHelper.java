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
    public java.util.Set<String> getLoadedModIds() {
        // Lambda (not IModInfo::getModId) so we don't import net.neoforged.neoforgespi.language.IModInfo.
        return ModList.get().getMods().stream()
                .map(info -> info.getModId())
                .collect(java.util.stream.Collectors.toSet());
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
    public boolean isContextActive(KeyMapping mapping, com.github.newvisualkeybing.client.keyboard.SceneProbe scene) {
        try {
            return mapping.getKeyConflictContext().isActive();
        } catch (Throwable ignored) {
            return IPlatformHelper.super.isContextActive(mapping, scene);
        }
    }

    /**
     * Delegate the relational conflict test to NeoForge's authoritative {@code IKeyConflictContext},
     * instead of collapsing custom contexts to {@code UNKNOWN} and approximating with the 4-value
     * enum. NeoForge allows asymmetric {@code conflicts}, so we OR both directions (favour not missing
     * a real conflict). Falls back to the enum approximation if the native call fails.
     */
    @Override
    public boolean contextsConflict(KeyMapping a, KeyMapping b) {
        try {
            IKeyConflictContext ca = a.getKeyConflictContext();
            IKeyConflictContext cb = b.getKeyConflictContext();
            return ca.conflicts(cb) || cb.conflicts(ca);
        } catch (Throwable ignored) {
            return IPlatformHelper.super.contextsConflict(a, b);
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
