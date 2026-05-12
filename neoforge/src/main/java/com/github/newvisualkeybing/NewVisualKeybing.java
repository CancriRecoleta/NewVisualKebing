package com.github.newvisualkeybing;

import com.github.newvisualkeybing.client.NewVisualKeybingNeoForgeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class NewVisualKeybing {

    public NewVisualKeybing(IEventBus eventBus) {

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NewVisualKeybingNeoForgeClient.register(eventBus);
        }
        CommonClass.init();
    }
}
