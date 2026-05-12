package com.github.newvisualkeybing.client;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class NewVisualKeybingNeoForgeClient {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, Constants.MOD_ID));
    private static final KeyMapping OPEN_VIEWER_KEY = new KeyMapping(
            "key.newvisualkeybing.open_viewer",
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    private NewVisualKeybingNeoForgeClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(NewVisualKeybingNeoForgeClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(RuntimeEvents::onClientTick);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_VIEWER_KEY);
    }

    public static final class RuntimeEvents {

        private RuntimeEvents() {
        }

        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_VIEWER_KEY.consumeClick()) {
                minecraft.setScreen(new KeybindViewerScreen(minecraft.screen));
            }
        }
    }
}
