package com.github.newvisualkeybing.client;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NewVisualKeybingNeoForgeClient {

    private static final KeyMapping OPEN_VIEWER_KEY = new KeyMapping(
            "key.newvisualkeybing.open_viewer",
            GLFW.GLFW_KEY_K,
            "key.categories.newvisualkeybing"
    );

    private NewVisualKeybingNeoForgeClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_VIEWER_KEY);
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class RuntimeEvents {

        private RuntimeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_VIEWER_KEY.consumeClick()) {
                minecraft.setScreen(new KeybindViewerScreen(minecraft.screen));
            }
        }
    }
}
