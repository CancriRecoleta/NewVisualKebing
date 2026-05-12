package com.github.newvisualkeybing;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class NewVisualKeybing {

    public NewVisualKeybing(IEventBus eventBus) {

        CommonClass.init();
    }
}
