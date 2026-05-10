package com.github.newvisualkeybing;

import com.github.newvisualkeybing.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Items;

public class CommonClass {

    public static void init() {
        Constants.LOG.info(
                "Hello from Common init on {}! we are currently in a {} environment!",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName()
        );
        Constants.LOG.info("The ID for diamonds is {}", Registry.ITEM.getKey(Items.DIAMOND));

        if (Services.PLATFORM.isModLoaded(Constants.MOD_ID)) {
            Constants.LOG.info("Hello to {}", Constants.MOD_ID);
        }
    }
}
