package com.github.newvisualkeybing.platform;

import com.github.newvisualkeybing.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public java.util.Set<String> getLoadedModIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String getModName(String modId) {
        if (modId == null) return null;
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getName())
                .orElse(null);
    }
}
