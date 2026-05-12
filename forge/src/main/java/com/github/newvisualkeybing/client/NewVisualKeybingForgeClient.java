package com.github.newvisualkeybing.client;

import com.github.newvisualkeybing.Constants;
import com.github.newvisualkeybing.client.screen.KeybindViewerScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NewVisualKeybingForgeClient {

    private static final KeyMapping OPEN_VIEWER_KEY = new KeyMapping(
            "key.newvisualkeybing.open_viewer",
            GLFW.GLFW_KEY_K,
            "key.categories.newvisualkeybing"
    );

    private NewVisualKeybingForgeClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_VIEWER_KEY);
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    public static final class RuntimeEvents {

        private RuntimeEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_VIEWER_KEY.consumeClick()) {
                minecraft.setScreen(new KeybindViewerScreen(minecraft.screen));
            }
        }
    }
}
