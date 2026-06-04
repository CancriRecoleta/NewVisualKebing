package com.github.newvisualkeybing.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key newvisualkeybing$getKey();

    @Accessor("clickCount")
    int newvisualkeybing$getClickCount();

    @Accessor("clickCount")
    void newvisualkeybing$setClickCount(int clickCount);

    /**
     * Raw {@code isDown} field. Read directly to bypass the loader's {@code isDown()} accessor, which
     * on Forge/NeoForge additionally gates on {@code getKeyModifier().isActive()} (see
     * {@code MixinKeyMappingDispatch#newvisualkeybing$rawDown}).
     */
    @Accessor("isDown")
    boolean newvisualkeybing$isDown();
}
