package com.github.newvisualkeybing.client;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class NewVisualKeybingFabricClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, Constants.MOD_ID));
    private static KeyMapping openViewerKey;

    @Override
    public void onInitializeClient() {
        openViewerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.newvisualkeybing.open_viewer",
                GLFW.GLFW_KEY_K,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openViewerKey.consumeClick()) {
                client.setScreen(new KeybindViewerScreen(client.screen));
            }
        });
    }
}
