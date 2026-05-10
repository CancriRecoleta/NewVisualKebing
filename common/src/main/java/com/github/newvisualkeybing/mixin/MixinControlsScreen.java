package com.github.newvisualkeybing.mixin;

import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(ControlsScreen.class)
public abstract class MixinControlsScreen extends OptionsSubScreen {

    protected MixinControlsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void newvisualkeybing$replaceVanillaKeybindButton(CallbackInfo ci) {
        int x = this.width / 2 + 5;
        int y = this.height / 6 - 12;

        for (GuiEventListener listener : new ArrayList<>(this.children())) {
            if (listener instanceof Button button
                    && button.x == x
                    && button.y == y
                    && button.getWidth() == 150
                    && button.getMessage().getString().equals(Component.translatable("controls.keybinds").getString())) {
                removeWidget(listener);
                break;
            }
        }

        addRenderableWidget(new Button(x, y, 150, 20, Component.translatable("controls.keybinds"), button ->
                Minecraft.getInstance().setScreen(new KeybindViewerScreen((Screen) (Object) this))));
    }
}
