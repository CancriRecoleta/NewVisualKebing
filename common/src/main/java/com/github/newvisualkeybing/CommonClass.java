package com.github.newvisualkeybing;

import com.github.newvisualkeybing.platform.Services;

/**
 * Shared loader-independent initialization entry point.
 */
public final class CommonClass {

    private CommonClass() {
    }

    /**
     * Runs common mod initialization for every supported loader.
     */
    public static void init() {
        Constants.LOG.info(
                "{} initialized on {} in a {} environment",
                Constants.MOD_NAME,
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName()
        );
    }
}
