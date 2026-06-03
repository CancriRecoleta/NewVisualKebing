package com.github.newvisualkeybing.client.keyboard;

import net.minecraft.network.chat.Component;

public enum FilterTab {
    ALL("screen.newvisualkeybing.viewer.filter.all"),
    FREE("screen.newvisualkeybing.viewer.filter.free"),
    SELF("screen.newvisualkeybing.viewer.filter.self"),
    OTHER("screen.newvisualkeybing.viewer.filter.other"),
    COMBO("screen.newvisualkeybing.viewer.filter.combo"),
    CONFLICT("screen.newvisualkeybing.viewer.filter.conflict");

    private final String translationKey;

    FilterTab(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getLabel() {
        return Component.translatable(translationKey).getString();
    }
}
