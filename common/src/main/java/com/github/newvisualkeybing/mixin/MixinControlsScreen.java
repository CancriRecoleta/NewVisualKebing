package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ControlsScreen.class)
public abstract class MixinControlsScreen extends OptionsSubScreen {

    protected MixinControlsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Redirect(
            method = "addOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;",
                    ordinal = 1
            )
    )
    private Button.Builder newvisualkeybing$replaceVanillaKeybindButton(Component message, Button.OnPress onPress) {
        return Button.builder(message, button ->
                Minecraft.getInstance().setScreen(new KeybindViewerScreen((Screen) (Object) this)));
    }
}
