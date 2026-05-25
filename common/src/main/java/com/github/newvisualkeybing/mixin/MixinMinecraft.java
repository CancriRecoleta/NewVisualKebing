package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.Constants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(CallbackInfo info) {
        Constants.LOG.info("This line is printed by a common mixin from {}!", Constants.MOD_NAME);
        Constants.LOG.info("MC Version: {}", Minecraft.getInstance().getVersionType());
    }

    @Inject(at = @At("TAIL"), method = "tick")
    private void newvisualkeybing$tick(CallbackInfo info) {
        com.github.newvisualkeybing.client.keyboard.KeybindModifierSuppressor.tick();
    }
}
